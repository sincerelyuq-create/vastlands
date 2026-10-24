package com.sincerelyuq.vastlands.worldgen.hydrology;

import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.clamp01;
import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.smoothstep;

import com.sincerelyuq.vastlands.worldgen.geography.GeographyModel;
import com.sincerelyuq.vastlands.worldgen.geography.GeographySample;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Coarse drainage of one tile of the world: which way water runs on a grid of
 * {@value #CELL}-block cells, how much of it, where depressions hold lakes, and which river basins
 * the tile owns.
 *
 * <p>A tile is a square of {@value #CORE} × {@value #CORE} cells (16 384 blocks). Its drainage is
 * computed over the tile plus a margin of {@value #MARGIN} cells on every side, from the smooth
 * regional elevation of the geography ({@link GeographyModel#smoothElevation}):
 * <ol>
 *     <li>Routing: a priority flood from the sea (Barnes et al. 2014) in which every cell is
 *     processed in order of its own elevation ("breaching"): water runs down into a depression
 *     and leaves it by the lowest pass, so every land cell drains to the sea.</li>
 *     <li>Lakes: a second, filling flood gives the spill level of every depression; deep, wide
 *     ones become lakes a few blocks below their spill level (lower in dry climates, where lakes
 *     can be terminal: no outflow).</li>
 *     <li>Discharge: runoff (wetter climates and mountains give more) accumulated downstream.</li>
 *     <li>Basins: each land cell belongs to the river mouth (or terminal lake) it drains to.</li>
 * </ol>
 * The tile owns the basins whose mouth lies in its core, if the whole basin lies inside the
 * computed square (a few cells clear of its border). Everything here is a pure function of the
 * geography and the tile's position, and every basin is owned by exactly one tile, so a basin's
 * rivers are the same whichever chunk asks first. Measured basins reach at most ~15 000 blocks
 * from their mouth, so the 16 384-block margin covers them.
 *
 * <p>Only the owned river cells and lakes are kept; the raster is dropped after construction.
 */
final class DrainageDomain {
    static final int CELL = 128;
    static final int CORE = 128;
    static final int MARGIN = 128;
    static final int SIZE = CORE + 2 * MARGIN;
    /** An owned basin stays this many cells clear of the border of the computed square. */
    static final int CLEARANCE = 4;
    /** Moisture and climate are sampled on a global lattice of this many cells and interpolated. */
    private static final int CLIMATE_STEP = 4;
    /** Discharge (runoff-weighted cells) at which a river begins. */
    static final float RIVER_DISCHARGE = 24.0F;
    /** Depressions shallower than this are breached by their river instead of holding a lake. */
    private static final double LAKE_MIN_DEPTH = 7.0;
    private static final int LAKE_MIN_CELLS = 2;
    /** Lake surface below the spill level of its depression (more in dry climates). */
    private static final double LAKE_FREEBOARD = 6.0;

    static final int DOWN_RIVER = 0;
    static final int DOWN_OCEAN = 1;
    static final int DOWN_LAKE = 2;
    static final int DOWN_END = 3;

    final int tileX;
    final int tileZ;
    /** Owned river cells: global cell key, discharge, the cell they drain to and how. */
    final long[] cellKeys;
    final float[] discharge;
    final long[] downKeys;
    final byte[] downKinds;
    /** Basin key of each owned river cell (mouth cell key, or the anchor of a terminal lake). */
    final long[] basins;
    /** Index of the lake whose water covers the cell, or -1. */
    final int[] lakeOf;
    final Map<Long, Integer> cellIndex;
    final List<Lake> lakes;
    /** Global cell key → indices of the owned lakes whose region contains the cell. */
    final Map<Long, int[]> lakeRegions;
    /** Global cell key → index of the owned lake whose water covers the cell. */
    final Map<Long, Integer> lakeWater;
    /** Owned basin key → indices of its river cells. */
    final Map<Long, int[]> basinCells;
    private final HydrologyModel model;
    private final LruCache<Long, BasinRivers> rivers = new LruCache<>(1 << 14);

    /**
     * A lake: its water surface (the top water block, {@code level - 1} in sea-level terms) and the
     * coarse cells where its water may lie.
     */
    record Lake(long anchor, long basin, int waterTop, boolean terminal, long[] cells, long[] region) {
    }

    static long key(long ci, long cj) {
        return ci << 32 | (cj & 0xFFFFFFFFL);
    }

    static int keyX(long key) {
        return (int) (key >> 32);
    }

    static int keyZ(long key) {
        return (int) key;
    }

    static long cellKeyAt(double x, double z) {
        return key((long) Math.floor(x / CELL), (long) Math.floor(z / CELL));
    }

    DrainageDomain(HydrologyModel model, int tileX, int tileZ) {
        long start = System.nanoTime();
        this.model = model;
        this.tileX = tileX;
        this.tileZ = tileZ;
        GeographyModel geography = model.geography();
        double sea = model.seaLevel();
        int n = SIZE;
        long ci0 = (long) tileX * CORE - MARGIN;
        long cj0 = (long) tileZ * CORE - MARGIN;

        float[] h = model.elevations(ci0, cj0, n);
        float[] moisture = climateField(geography, ci0, cj0, n);
        boolean[] ocean = new boolean[n * n];
        for (int c = 0; c < n * n; c++) {
            ocean[c] = h[c] < sea;
        }

        // 1. Routing by a breaching priority flood: keys are the cells' own elevations.
        int[] down = new int[n * n];
        int[] order = new int[n * n];
        int popped = flood(h, ocean, n, down, order, false, null);
        // 2. Spill levels by a filling flood.
        float[] fill = new float[n * n];
        flood(h, ocean, n, new int[n * n], new int[n * n], true, fill);

        // Lakes in deep depressions.
        int[] lakeId = new int[n * n];
        Arrays.fill(lakeId, -1);
        List<LocalLake> localLakes = findLakes(model, h, fill, ocean, moisture, n, lakeId);

        // 3. Discharge: runoff accumulated downstream in reverse flood order; lakes lose water to
        // evaporation, terminal lakes keep all of it.
        float[] acc = new float[n * n];
        for (int k = popped - 1; k >= 0; k--) {
            int c = order[k];
            if (ocean[c]) {
                continue;
            }
            acc[c] += runoff(h[c], moisture[c]);
            int d = down[c];
            if (d < 0 || ocean[d]) {
                continue;
            }
            float out = acc[c];
            if (lakeId[c] >= 0 && lakeId[d] != lakeId[c]) {
                LocalLake lake = localLakes.get(lakeId[c]);
                out *= lake.terminal ? 0.0F : (float) (1.0 - lake.evaporation);
            }
            acc[d] += out;
        }

        // 4. Basins, in flood order (a cell's downstream neighbour comes first).
        long[] basin = new long[n * n];
        Arrays.fill(basin, Long.MIN_VALUE);
        for (int k = 0; k < popped; k++) {
            int c = order[k];
            if (ocean[c] || down[c] == -2) {
                continue;
            }
            int lake = lakeId[c];
            if (lake >= 0 && localLakes.get(lake).terminal) {
                basin[c] = localLakes.get(lake).anchor(ci0, cj0, n);
                continue;
            }
            int d = down[c];
            if (d == -2) {
                continue;
            }
            if (ocean[d]) {
                basin[c] = key(ci0 + c % n, cj0 + c / n);
            } else if (down[d] == -2) {
                basin[c] = Long.MIN_VALUE;
            } else {
                basin[c] = basin[d];
            }
        }
        // Completeness: an owned basin must stay clear of the computed square's border.
        Map<Long, int[]> extent = new HashMap<>();
        for (int c = 0; c < n * n; c++) {
            if (basin[c] == Long.MIN_VALUE) {
                continue;
            }
            int i = c % n;
            int j = c / n;
            int[] box = extent.computeIfAbsent(basin[c], b -> new int[] {i, j, i, j});
            box[0] = Math.min(box[0], i);
            box[1] = Math.min(box[1], j);
            box[2] = Math.max(box[2], i);
            box[3] = Math.max(box[3], j);
        }
        long coreMinX = (long) tileX * CORE;
        long coreMinZ = (long) tileZ * CORE;
        Map<Long, Boolean> owned = new HashMap<>();
        for (Map.Entry<Long, int[]> entry : extent.entrySet()) {
            int[] box = entry.getValue();
            long mx = keyX(entry.getKey());
            long mz = keyZ(entry.getKey());
            boolean inCore = mx >= coreMinX && mx < coreMinX + CORE && mz >= coreMinZ && mz < coreMinZ + CORE;
            boolean complete = box[0] >= CLEARANCE && box[1] >= CLEARANCE && box[2] < n - CLEARANCE && box[3] < n - CLEARANCE;
            owned.put(entry.getKey(), inCore && complete);
        }

        // Owned lakes.
        this.lakes = new ArrayList<>();
        int[] lakeIndex = new int[localLakes.size()];
        Arrays.fill(lakeIndex, -1);
        this.lakeRegions = new HashMap<>();
        this.lakeWater = new HashMap<>();
        for (int l = 0; l < localLakes.size(); l++) {
            LocalLake local = localLakes.get(l);
            long lakeBasin = basin[local.lowest];
            if (lakeBasin == Long.MIN_VALUE || !owned.getOrDefault(lakeBasin, false)) {
                continue;
            }
            lakeIndex[l] = this.lakes.size();
            long[] cells = new long[local.cells.size()];
            for (int k = 0; k < cells.length; k++) {
                int c = local.cells.get(k);
                cells[k] = key(ci0 + c % n, cj0 + c / n);
            }
            long[] region = local.region.stream().mapToLong(c -> key(ci0 + c % n, cj0 + c / n)).toArray();
            Lake lake = new Lake(local.anchor(ci0, cj0, n), lakeBasin, local.waterTop, local.terminal, cells, region);
            for (long cell : region) {
                this.lakeRegions.merge(cell, new int[] {lakeIndex[l]}, DrainageDomain::concat);
            }
            for (long cell : cells) {
                this.lakeWater.put(cell, lakeIndex[l]);
            }
            this.lakes.add(lake);
        }

        // Owned river cells.
        List<Integer> rivers = new ArrayList<>();
        for (int c = 0; c < n * n; c++) {
            if (!ocean[c] && acc[c] >= RIVER_DISCHARGE && basin[c] != Long.MIN_VALUE && owned.getOrDefault(basin[c], false)) {
                rivers.add(c);
            }
        }
        int count = rivers.size();
        this.cellKeys = new long[count];
        this.discharge = new float[count];
        this.downKeys = new long[count];
        this.downKinds = new byte[count];
        this.basins = new long[count];
        this.lakeOf = new int[count];
        this.cellIndex = new HashMap<>(count * 2);
        Map<Long, List<Integer>> byBasin = new HashMap<>();
        for (int r = 0; r < count; r++) {
            int c = rivers.get(r);
            int d = down[c];
            this.cellKeys[r] = key(ci0 + c % n, cj0 + c / n);
            this.discharge[r] = acc[c];
            this.basins[r] = basin[c];
            this.lakeOf[r] = lakeId[c] >= 0 ? lakeIndex[lakeId[c]] : -1;
            this.downKeys[r] = d >= 0 ? key(ci0 + d % n, cj0 + d / n) : Long.MIN_VALUE;
            byte kind;
            if (d < 0) {
                kind = DOWN_END;
            } else if (ocean[d]) {
                kind = DOWN_OCEAN;
            } else if (lakeId[d] >= 0 && lakeId[d] != lakeId[c] && localLakes.get(lakeId[d]).terminal) {
                kind = DOWN_LAKE;
            } else if (acc[d] >= RIVER_DISCHARGE) {
                kind = DOWN_RIVER;
            } else {
                // The outflow of a lake that loses most of its water: the river ends in the lake.
                kind = DOWN_END;
            }
            this.downKinds[r] = kind;
            this.cellIndex.put(this.cellKeys[r], r);
            byBasin.computeIfAbsent(basin[c], b -> new ArrayList<>()).add(r);
        }
        this.basinCells = new HashMap<>();
        for (Map.Entry<Long, List<Integer>> entry : byBasin.entrySet()) {
            this.basinCells.put(entry.getKey(), entry.getValue().stream().mapToInt(Integer::intValue).toArray());
        }
        HydrologyModel.COUNTERS.domains.incrementAndGet();
        HydrologyModel.COUNTERS.domainNanos.addAndGet(System.nanoTime() - start);
    }

    /** The rivers of an owned basin, computed on first use. */
    BasinRivers rivers(long basin) {
        return this.rivers.get(basin, b -> new BasinRivers(this.model, this, b));
    }

    private static int[] concat(int[] a, int[] b) {
        int[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    /** Moisture per cell, bilinear between samples on a global lattice. */
    private static float[] climateField(GeographyModel geography, long ci0, long cj0, int n) {
        long li0 = Math.floorDiv(ci0, CLIMATE_STEP);
        long lj0 = Math.floorDiv(cj0, CLIMATE_STEP);
        int m = n / CLIMATE_STEP + 3;
        double[] lattice = new double[m * m];
        for (int b = 0; b < m; b++) {
            for (int a = 0; a < m; a++) {
                GeographySample g = geography.sample((li0 + a) * CLIMATE_STEP * (double) CELL, (lj0 + b) * CLIMATE_STEP * (double) CELL);
                lattice[b * m + a] = g.moisture();
            }
        }
        float[] out = new float[n * n];
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                double u = (ci0 + i + 0.5) / CLIMATE_STEP - li0;
                double v = (cj0 + j + 0.5) / CLIMATE_STEP - lj0;
                int a = (int) Math.floor(u);
                int b = (int) Math.floor(v);
                double fu = u - a;
                double fv = v - b;
                double m0 = lattice[b * m + a] * (1 - fu) + lattice[b * m + a + 1] * fu;
                double m1 = lattice[(b + 1) * m + a] * (1 - fu) + lattice[(b + 1) * m + a + 1] * fu;
                out[j * n + i] = (float) (m0 * (1 - fv) + m1 * fv);
            }
        }
        return out;
    }

    /**
     * Runoff of one cell: dry climates give little, wet ones much; mountains catch more rain and
     * snow, so rivers rise in them.
     */
    static float runoff(double elevation, double moisture) {
        double climate = 0.25 + 0.95 * smoothstep(-0.6, 0.45, moisture);
        double orographic = 1.0 + 0.9 * smoothstep(90.0, 280.0, elevation);
        return (float) (climate * orographic);
    }

    /**
     * Priority flood from the sea and from the border of the square. With {@code filling}, a cell's
     * key is at least its parent's (depressions fill up, {@code fill} receives the levels);
     * otherwise keys are the cells' own elevations, so water runs into depressions and out over
     * their lowest pass. {@code down} receives each cell's downstream neighbour (-1 sea, -2
     * border), {@code order} the processing order. Ties are broken by insertion order; both are
     * the same in every square that contains a basin entirely.
     */
    private static int flood(float[] h, boolean[] ocean, int n, int[] down, int[] order, boolean filling, float[] fill) {
        CellHeap heap = new CellHeap(n * n);
        boolean[] seen = new boolean[n * n];
        for (int c = 0; c < n * n; c++) {
            int i = c % n;
            int j = c / n;
            boolean border = i == 0 || j == 0 || i == n - 1 || j == n - 1;
            if (ocean[c] || border) {
                seen[c] = true;
                down[c] = ocean[c] ? -1 : -2;
                if (fill != null) {
                    fill[c] = h[c];
                }
                heap.push(c, h[c]);
            }
        }
        int count = 0;
        while (!heap.isEmpty()) {
            float key = heap.peekKey();
            int c = heap.pop();
            order[count++] = c;
            int i = c % n;
            int j = c / n;
            for (int d = 0; d < 8; d++) {
                int ii = i + DX[d];
                int jj = j + DZ[d];
                if (ii < 0 || jj < 0 || ii >= n || jj >= n) {
                    continue;
                }
                int nb = jj * n + ii;
                if (seen[nb]) {
                    continue;
                }
                seen[nb] = true;
                down[nb] = c;
                float k = filling ? Math.max(h[nb], key) : h[nb];
                if (fill != null) {
                    fill[nb] = k;
                }
                heap.push(nb, k);
            }
        }
        return count;
    }

    private static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] DZ = {0, 0, 1, -1, 1, -1, 1, -1};

    private static final class LocalLake {
        final List<Integer> cells = new ArrayList<>();
        final List<Integer> region = new ArrayList<>();
        int lowest = -1;
        int waterTop;
        boolean terminal;
        double evaporation;

        long anchor(long ci0, long cj0, int n) {
            return key(ci0 + this.lowest % n, cj0 + this.lowest / n);
        }
    }

    /**
     * Lakes: connected depressions (cells below their spill level) deep and wide enough. The lake
     * surface lies {@value #LAKE_FREEBOARD} blocks below the spill level, lower in dry climates;
     * very dry lakes are terminal. Lake cells are the depression cells near or below the surface;
     * the region (where its water may lie at block level) adds one ring of cells.
     */
    private static List<LocalLake> findLakes(HydrologyModel model, float[] h, float[] fill, boolean[] ocean, float[] moisture, int n, int[] lakeId) {
        List<LocalLake> lakes = new ArrayList<>();
        boolean[] visited = new boolean[n * n];
        int[] stack = new int[n * n];
        List<Integer> component = new ArrayList<>();
        for (int start = 0; start < n * n; start++) {
            if (visited[start] || ocean[start] || fill[start] - h[start] < 0.5F) {
                continue;
            }
            component.clear();
            int top = 0;
            stack[top++] = start;
            visited[start] = true;
            float spill = 0.0F;
            int lowest = start;
            double aridity = 0.0;
            while (top > 0) {
                int c = stack[--top];
                component.add(c);
                spill = Math.max(spill, fill[c]);
                if (h[c] < h[lowest]) {
                    lowest = c;
                }
                aridity += smoothstep(0.05, -0.5, moisture[c]);
                int i = c % n;
                int j = c / n;
                for (int d = 0; d < 8; d++) {
                    int ii = i + DX[d];
                    int jj = j + DZ[d];
                    if (ii < 0 || jj < 0 || ii >= n || jj >= n) {
                        continue;
                    }
                    int nb = jj * n + ii;
                    if (!visited[nb] && !ocean[nb] && fill[nb] - h[nb] >= 0.5F) {
                        visited[nb] = true;
                        stack[top++] = nb;
                    }
                }
            }
            double depth = spill - h[lowest];
            if (depth < LAKE_MIN_DEPTH || component.size() < LAKE_MIN_CELLS) {
                continue;
            }
            aridity /= component.size();
            double level = spill - LAKE_FREEBOARD - aridity * 0.5 * depth;
            int waterTop = model.quantizeLevel(level);
            if (waterTop < h[lowest] + 3.0) {
                continue;
            }
            LocalLake lake = new LocalLake();
            lake.lowest = lowest;
            lake.waterTop = waterTop;
            lake.terminal = aridity > 0.6;
            lake.evaporation = clamp01(0.15 + 0.7 * aridity);
            int id = lakes.size();
            for (int c : component) {
                if (h[c] < waterTop + 4.0) {
                    lake.cells.add(c);
                    lakeId[c] = id;
                }
            }
            if (lake.cells.size() < LAKE_MIN_CELLS) {
                for (int c : lake.cells) {
                    lakeId[c] = -1;
                }
                continue;
            }
            Set<Integer> region = new HashSet<>();
            for (int c : lake.cells) {
                int i = c % n;
                int j = c / n;
                for (int dj = -1; dj <= 1; dj++) {
                    for (int di = -1; di <= 1; di++) {
                        int ii = i + di;
                        int jj = j + dj;
                        if (ii >= 0 && jj >= 0 && ii < n && jj < n) {
                            region.add(jj * n + ii);
                        }
                    }
                }
            }
            lake.region.addAll(region);
            lake.region.sort(Integer::compare);
            lakes.add(lake);
        }
        return lakes;
    }

    /** Binary min-heap of cell indices keyed by (elevation, insertion order). */
    private static final class CellHeap {
        private final int[] cells;
        private final float[] keys;
        private final int[] seqs;
        private int size;
        private int seq;

        CellHeap(int capacity) {
            this.cells = new int[capacity];
            this.keys = new float[capacity];
            this.seqs = new int[capacity];
        }

        boolean isEmpty() {
            return this.size == 0;
        }

        float peekKey() {
            return this.keys[0];
        }

        void push(int cell, float key) {
            int i = this.size++;
            int s = this.seq++;
            while (i > 0) {
                int parent = (i - 1) >> 1;
                if (!less(key, s, this.keys[parent], this.seqs[parent])) {
                    break;
                }
                this.cells[i] = this.cells[parent];
                this.keys[i] = this.keys[parent];
                this.seqs[i] = this.seqs[parent];
                i = parent;
            }
            this.cells[i] = cell;
            this.keys[i] = key;
            this.seqs[i] = s;
        }

        int pop() {
            int result = this.cells[0];
            int last = --this.size;
            int cell = this.cells[last];
            float key = this.keys[last];
            int s = this.seqs[last];
            int i = 0;
            while (true) {
                int child = 2 * i + 1;
                if (child >= last) {
                    break;
                }
                if (child + 1 < last && less(this.keys[child + 1], this.seqs[child + 1], this.keys[child], this.seqs[child])) {
                    child++;
                }
                if (!less(this.keys[child], this.seqs[child], key, s)) {
                    break;
                }
                this.cells[i] = this.cells[child];
                this.keys[i] = this.keys[child];
                this.seqs[i] = this.seqs[child];
                i = child;
            }
            this.cells[i] = cell;
            this.keys[i] = key;
            this.seqs[i] = s;
            return result;
        }

        private static boolean less(float k1, int s1, float k2, int s2) {
            return k1 < k2 || (k1 == k2 && s1 < s2);
        }
    }
}
