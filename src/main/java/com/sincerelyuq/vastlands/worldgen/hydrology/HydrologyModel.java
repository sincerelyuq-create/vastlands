package com.sincerelyuq.vastlands.worldgen.hydrology;

import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.smoothstep;

import com.sincerelyuq.vastlands.worldgen.geography.GeographyModel;
import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The Vastlands rivers and lakes: a pure function from (seed, x, z) to the channels, valleys and
 * water bodies of a block column. Pure Java (no game classes), thread-safe; every cache only holds
 * values that are pure functions of their keys.
 *
 * <p>Layers:
 * <ol>
 *     <li>{@link DrainageDomain}: drainage, discharge, lakes and basin ownership on a 128-block
 *     grid, per 16 384-block tile.</li>
 *     <li>{@link BasinRivers}: river paths, widths, water levels, falls, per basin.</li>
 *     <li>{@link WaterIndex}: the segments and lakes near a 512-block region.</li>
 *     <li>{@link #sample}: the carved height and the water bodies of one column.</li>
 * </ol>
 *
 * <p>Water levels are given as water tops (the Y of the highest water block; the sea's is
 * {@code seaLevel - 1}). Every river and lake top is {@code seaTop + 2k}.
 */
public final class HydrologyModel {
    /** Exact terrain surface of the height field at any point (uncached). */
    public interface TerrainHeights {
        double height(double x, double z);
    }

    /** Work done by all models in this JVM, for diagnostics (previews, tests, logs). */
    public static final Counters COUNTERS = new Counters();

    public static final class Counters {
        public final java.util.concurrent.atomic.AtomicLong domains = new java.util.concurrent.atomic.AtomicLong();
        public final java.util.concurrent.atomic.AtomicLong domainNanos = new java.util.concurrent.atomic.AtomicLong();
        public final java.util.concurrent.atomic.AtomicLong basins = new java.util.concurrent.atomic.AtomicLong();
        public final java.util.concurrent.atomic.AtomicLong basinNanos = new java.util.concurrent.atomic.AtomicLong();
        public final java.util.concurrent.atomic.AtomicLong segments = new java.util.concurrent.atomic.AtomicLong();

        @Override
        public String toString() {
            return String.format(java.util.Locale.ROOT, "%d tiles in %.1f s, %d basins in %.1f s (%d segments)",
                    this.domains.get(), this.domainNanos.get() / 1e9, this.basins.get(), this.basinNanos.get() / 1e9, this.segments.get());
        }
    }

    private final GeographyModel geography;
    private final TerrainHeights heights;
    private final double seaLevel;
    private final int seaTop;
    private final LruCache<Long, DrainageDomain> domains = new LruCache<>(32);
    private final LruCache<Long, WaterIndex> regions = new LruCache<>(256);
    /** Cells per side of a cached block of elevation samples (16 KB each). */
    private static final int ELEVATION_TILE = 64;
    private final LruCache<Long, float[]> elevationTiles = new LruCache<>(400);
    /** Raggedness of banks (fine) and valley walls (coarse), and how strongly rivers meander. */
    private final SimplexNoise bankNoise;
    private final SimplexNoise bankDetail;
    private final SimplexNoise wallNoise;
    private final SimplexNoise wallDetail;
    private final SimplexNoise meanderNoise;
    /** Height of the bands of valley walls and gorges (blocks): steeper and gentler in turn, like ledges. */
    private static final double LEDGE = 8.0;

    public HydrologyModel(GeographyModel geography, TerrainHeights heights, long seed) {
        this.geography = geography;
        this.heights = heights;
        this.seaLevel = geography.seaLevel();
        this.seaTop = (int) Math.ceil(this.seaLevel) - 1;
        long s = SimplexNoise.mix(seed ^ 0x48594452L);
        this.bankNoise = new SimplexNoise(s + 1);
        this.bankDetail = new SimplexNoise(s + 2);
        this.wallNoise = new SimplexNoise(s + 3);
        this.wallDetail = new SimplexNoise(s + 4);
        this.meanderNoise = new SimplexNoise(s + 5);
    }

    /**
     * Largest angle (radians) by which a river's direction swings about its valley line at a
     * point: 0.35 (gentle bends) to 1.45 (deep meanders, the river at times running across its
     * valley), varying over thousands of blocks.
     */
    double meanderAngle(double x, double z) {
        return 0.35 + 1.1 * smoothstep(-0.55, 0.55, this.meanderNoise.sample(x / 2600.0, z / 2600.0));
    }

    public GeographyModel geography() {
        return this.geography;
    }

    public double seaLevel() {
        return this.seaLevel;
    }

    /** Y of the top water block of the sea (and of rivers that reach it). */
    public int seaTop() {
        return this.seaTop;
    }

    double terrainHeight(double x, double z) {
        return this.heights.height(x, z);
    }

    /** The highest allowed water top at or below {@code level}: {@code seaTop + 2k}, never below the sea. */
    public int quantizeLevel(double level) {
        if (!(level < 1.0e6)) {
            return Integer.MAX_VALUE / 2;
        }
        int k = (int) Math.floor((level - this.seaTop) / 2.0);
        return this.seaTop + 2 * Math.max(0, k);
    }

    /**
     * Smooth regional elevation at the centres of an {@code n × n} block of drainage cells. The
     * samples are cached in tiles of {@value #ELEVATION_TILE} × {@value #ELEVATION_TILE} cells on a
     * global lattice, so the overlapping squares of neighbouring drainage tiles share them; missing
     * tiles are computed in parallel.
     */
    float[] elevations(long ci0, long cj0, int n) {
        long tx0 = Math.floorDiv(ci0, ELEVATION_TILE);
        long tz0 = Math.floorDiv(cj0, ELEVATION_TILE);
        long tx1 = Math.floorDiv(ci0 + n - 1, ELEVATION_TILE);
        long tz1 = Math.floorDiv(cj0 + n - 1, ELEVATION_TILE);
        List<long[]> tiles = new ArrayList<>();
        for (long tz = tz0; tz <= tz1; tz++) {
            for (long tx = tx0; tx <= tx1; tx++) {
                tiles.add(new long[] {tx, tz});
            }
        }
        float[] out = new float[n * n];
        tiles.parallelStream().forEach(tile -> {
            float[] values = this.elevationTiles.get(DrainageDomain.key(tile[0], tile[1]), key -> this.elevationTile(tile[0], tile[1]));
            long baseI = tile[0] * ELEVATION_TILE;
            long baseJ = tile[1] * ELEVATION_TILE;
            for (int b = 0; b < ELEVATION_TILE; b++) {
                long j = baseJ + b - cj0;
                if (j < 0 || j >= n) {
                    continue;
                }
                for (int a = 0; a < ELEVATION_TILE; a++) {
                    long i = baseI + a - ci0;
                    if (i >= 0 && i < n) {
                        out[(int) (j * n + i)] = values[b * ELEVATION_TILE + a];
                    }
                }
            }
        });
        return out;
    }

    private float[] elevationTile(long tileX, long tileZ) {
        float[] values = new float[ELEVATION_TILE * ELEVATION_TILE];
        double cell = DrainageDomain.CELL;
        for (int b = 0; b < ELEVATION_TILE; b++) {
            for (int a = 0; a < ELEVATION_TILE; a++) {
                values[b * ELEVATION_TILE + a] = (float) this.geography.smoothElevation(
                        (tileX * ELEVATION_TILE + a + 0.5) * cell, (tileZ * ELEVATION_TILE + b + 0.5) * cell);
            }
        }
        return values;
    }

    DrainageDomain domain(int tileX, int tileZ) {
        return this.domains.get(DrainageDomain.key(tileX, tileZ), key -> new DrainageDomain(this, tileX, tileZ));
    }

    /** Computes the drainage of a block of tiles in parallel. */
    void prefetchDomains(int tx0, int tx1, int tz0, int tz1) {
        List<CompletableFuture<?>> pending = new ArrayList<>();
        for (int tx = tx0; tx <= tx1; tx++) {
            for (int tz = tz0; tz <= tz1; tz++) {
                int x = tx;
                int z = tz;
                pending.add(CompletableFuture.runAsync(() -> this.domain(x, z)));
            }
        }
        for (CompletableFuture<?> future : pending) {
            future.join();
        }
    }

    int lakeWaterAt(DrainageDomain domain, double x, double z) {
        return domain.lakeWater.getOrDefault(DrainageDomain.cellKeyAt(x, z), -1);
    }

    WaterIndex index(int regionX, int regionZ) {
        return this.regions.get(DrainageDomain.key(regionX, regionZ), key -> new WaterIndex(this, regionX, regionZ));
    }

    /**
     * Rivers and lakes at one block column. Mutable scratch object, reused by one thread.
     */
    public static final class Column {
        /** Ground surface after channels, valleys and levees (only meaningful where it was sampled). */
        public double carved;
        /** Distinct water tops of the bodies (river pools, lakes) whose region contains the column, ascending. */
        public final int[] tops = new int[8];
        public int topCount;
        /** Inside the wetted channel of a river wide enough for the river biome. */
        public boolean riverBiome;
        /** Nearest channel: width, water top and distance beyond its edge (for diagnostics). */
        public double riverWidth;
        public int riverTop;
        public double riverEdge = Double.POSITIVE_INFINITY;
        /** Water top of a lake whose region contains the column, or {@link Integer#MIN_VALUE}. */
        public int lakeTop = Integer.MIN_VALUE;
        /**
         * Lowest water top of the channels whose wetted width contains the column, or
         * {@link Integer#MAX_VALUE}. Such a column is always water, down to below its top: at the
         * foot of a fall the bilinear ground can be higher than the lower water.
         */
        public int channelTop = Integer.MAX_VALUE;
        /** Top of a boulder covering the column (at or above the water it stands in), or {@link Integer#MIN_VALUE}. */
        public int rockTop = Integer.MIN_VALUE;
        /** Some channel's valley reaches the column (it may be reshaped). */
        public boolean near;

        void reset() {
            this.topCount = 0;
            this.riverBiome = false;
            this.riverWidth = 0.0;
            this.riverTop = Integer.MIN_VALUE;
            this.riverEdge = Double.POSITIVE_INFINITY;
            this.lakeTop = Integer.MIN_VALUE;
            this.channelTop = Integer.MAX_VALUE;
            this.rockTop = Integer.MIN_VALUE;
            this.near = false;
        }

        void addTop(int top) {
            int i = 0;
            while (i < this.topCount && this.tops[i] < top) {
                i++;
            }
            if (i < this.topCount && this.tops[i] == top) {
                return;
            }
            if (this.topCount == this.tops.length) {
                return;
            }
            System.arraycopy(this.tops, i, this.tops, i + 1, this.topCount - i);
            this.tops[i] = top;
            this.topCount++;
        }
    }

    /**
     * Samples the rivers and lakes at a block column. {@code terrainHeight} is the terrain surface
     * there; {@code out.carved} receives it reshaped by channels (carved down to their beds,
     * valleys and gorges around them) and levees (raised to one block above the water where the
     * terrain beside a channel would be lower than its water).
     */
    public void sample(int x, int z, double terrainHeight, Column out) {
        this.sample(x, z, terrainHeight, out, false);
    }

    /**
     * The water bodies of a column only ({@code tops}, {@code channelTop}, {@code lakeTop},
     * {@code rockTop}): segments are only looked at within reach of their water, so it is much
     * cheaper than {@link #sample}; {@code carved} is not computed.
     */
    public void sampleWater(int x, int z, Column out) {
        this.sample(x, z, Double.POSITIVE_INFINITY, out, true);
    }

    private void sample(int x, int z, double terrainHeight, Column out, boolean waterOnly) {
        out.reset();
        int regionX = Math.floorDiv(x, WaterIndex.SIZE);
        int regionZ = Math.floorDiv(z, WaterIndex.SIZE);
        WaterIndex index = this.index(regionX, regionZ);
        int[] refs = index.bucket(x - regionX * WaterIndex.SIZE, z - regionZ * WaterIndex.SIZE);
        double carve = Double.POSITIVE_INFINITY;
        double levee = Double.NEGATIVE_INFINITY;
        double cap = Double.POSITIVE_INFINITY;
        double bankJitter = Double.NaN;
        double wallJitter = 0.0;
        for (int ref : refs) {
            BasinRivers b = index.owners[ref];
            int k = index.segments[ref];
            if ((b.flags[k] & BasinRivers.IN_LAKE) != 0) {
                continue;
            }
            double cut = b.cutoff[k];
            // Water reaches no farther than the channel, its bank and the jitter of the bank.
            double reach = waterOnly ? Math.max(b.rA[k], b.rB[k]) + b.bank[k] + 6.0 : cut;
            double ax = b.ax[k];
            double az = b.az[k];
            double dx = b.bx[k] - ax;
            double dz = b.bz[k] - az;
            if (x < Math.min(ax, b.bx[k]) - reach || x > Math.max(ax, b.bx[k]) + reach
                    || z < Math.min(az, b.bz[k]) - reach || z > Math.max(az, b.bz[k]) + reach) {
                continue;
            }
            double len2 = dx * dx + dz * dz;
            double t = len2 > 1e-9 ? ((x - ax) * dx + (z - az) * dz) / len2 : 0.0;
            t = t < 0.0 ? 0.0 : (t > 1.0 ? 1.0 : t);
            double px = ax + t * dx - x;
            double pz = az + t * dz - z;
            double d = Math.sqrt(px * px + pz * pz);
            if (d >= reach) {
                continue;
            }
            int upFall = b.upFall[k];
            if (upFall >= 0 && b.fallSide(upFall, x, z) < 0.0) {
                continue;
            }
            int downFall = b.downFall[k];
            if (downFall >= 0 && b.fallSide(downFall, x, z) > 0.0) {
                continue;
            }
            out.near = true;
            double r = b.rA[k] + (b.rB[k] - b.rA[k]) * t;
            double depth = b.dA[k] + (b.dB[k] - b.dA[k]) * t;
            int top = b.waterTop[k];
            // Ragged banks and valley walls: the distance from the channel is jittered by noise of
            // the column, a few blocks at the water's edge, more on the walls beyond the bank.
            if (bankJitter != bankJitter) {
                bankJitter = 0.7 * this.bankNoise.sample(x / 16.0, z / 16.0) + 0.3 * this.bankDetail.sample(x / 6.0, z / 6.0);
                wallJitter = waterOnly ? 0.0 : 0.7 * this.wallNoise.sample(x / 56.0, z / 56.0) + 0.3 * this.wallDetail.sample(x / 20.0, z / 20.0);
            }
            double bank = b.bank[k];
            // The water's reach takes the bank's jitter only (the walls' starts beyond the bank), so it
            // never lies farther than the bank's jitter beyond the bank.
            double raw = d;
            d = Math.max(0.0, raw + Math.min(3.0, 0.8 + 0.06 * r) * bankJitter);
            if (!waterOnly) {
                double wall = Math.max(0.0, d + Math.min(12.0, 2.5 + 0.08 * (b.floodplain[k] + r)) * wallJitter
                        * smoothstep(bank + 1.0, bank + 8.0, raw - r));
                double p = profile(wall, r, top, depth, b.floodplain[k], b.wall[k], cut);
                carve = Math.min(carve, p);
                cap = Math.min(cap, wall <= r + 1.0 ? p : p + 3.0 * (wall - r - 1.0));
                if (top > this.seaTop) {
                    levee = Math.max(levee, top + 1.0 - 4.0 * Math.max(0.0, r + 1.0 - wall) - Math.max(0.0, wall - (r + bank)));
                }
            }
            if (d <= r + bank) {
                out.addTop(top);
            }
            if (d <= r && top < out.channelTop) {
                out.channelTop = top;
            }
            if (d <= r && (b.flags[k] & BasinRivers.RIVER_BIOME) != 0) {
                out.riverBiome = true;
            }
            if (d - r < out.riverEdge) {
                out.riverEdge = d - r;
                out.riverWidth = 2.0 * r;
                out.riverTop = top;
            }
        }
        int[] lakes = index.lakeTops(DrainageDomain.cellKeyAt(x, z));
        if (lakes != null) {
            for (int top : lakes) {
                out.addTop(top);
                out.lakeTop = Math.max(out.lakeTop, top);
            }
        }
        // Boulders: rounded, irregular blocks of rock standing out of the water.
        for (int ref : index.rockBucket(x - regionX * WaterIndex.SIZE, z - regionZ * WaterIndex.SIZE)) {
            BasinRivers b = index.rockOwners[ref];
            int q = index.rockIds[ref];
            double dx = x + 0.5 - b.rockX[q];
            double dz = z + 0.5 - b.rockZ[q];
            double radius = b.rockR[q];
            double d2 = dx * dx + dz * dz;
            if (d2 > 1.9 * radius * radius) {
                continue;
            }
            long seed = b.rockSeed[q];
            double angle = Math.atan2(dz, dx);
            double outline = radius * (1.0 + 0.22 * Math.sin(2.0 * angle + 6.0 * BasinRivers.unit(seed, 7))
                    + 0.12 * Math.sin(3.0 * angle + 6.0 * BasinRivers.unit(seed, 8)));
            double u = Math.sqrt(d2) / outline;
            if (u > 1.0) {
                continue;
            }
            int rockTop = b.rockBase[q] + (int) Math.floor(b.rockHeight[q] * Math.sqrt(1.0 - u * u) + 0.5);
            out.rockTop = Math.max(out.rockTop, rockTop);
        }
        double carved = Math.min(terrainHeight, carve);
        if (levee > Double.NEGATIVE_INFINITY) {
            carved = Math.max(carved, Math.min(levee, cap));
        }
        out.carved = carved;
    }

    /**
     * Whether any river pool or lake can hold water in the block box (inclusive bounds): false
     * when no segment's channel and bank reach it and no lake region covers it. Cheap; lets block
     * steps skip chunks far from water.
     */
    public boolean mayHoldWater(int minX, int minZ, int maxX, int maxZ) {
        for (int rx = Math.floorDiv(minX, WaterIndex.SIZE); rx <= Math.floorDiv(maxX, WaterIndex.SIZE); rx++) {
            for (int rz = Math.floorDiv(minZ, WaterIndex.SIZE); rz <= Math.floorDiv(maxZ, WaterIndex.SIZE); rz++) {
                WaterIndex index = this.index(rx, rz);
                int x0 = Math.max(minX, rx * WaterIndex.SIZE);
                int x1 = Math.min(maxX, rx * WaterIndex.SIZE + WaterIndex.SIZE - 1);
                int z0 = Math.max(minZ, rz * WaterIndex.SIZE);
                int z1 = Math.min(maxZ, rz * WaterIndex.SIZE + WaterIndex.SIZE - 1);
                for (int bx = x0 - Math.floorMod(x0, WaterIndex.BUCKET); bx <= x1; bx += WaterIndex.BUCKET) {
                    for (int bz = z0 - Math.floorMod(z0, WaterIndex.BUCKET); bz <= z1; bz += WaterIndex.BUCKET) {
                        for (int ref : index.bucket(bx - rx * WaterIndex.SIZE, bz - rz * WaterIndex.SIZE)) {
                            BasinRivers b = index.owners[ref];
                            int k = index.segments[ref];
                            // Channel, bank and the jitter of the bank (see sample).
                            double reach = Math.max(b.rA[k], b.rB[k]) + b.bank[k] + 6.0;
                            if (Math.max(b.ax[k], b.bx[k]) + reach >= minX && Math.min(b.ax[k], b.bx[k]) - reach <= maxX
                                    && Math.max(b.az[k], b.bz[k]) + reach >= minZ && Math.min(b.az[k], b.bz[k]) - reach <= maxZ) {
                                return true;
                            }
                        }
                    }
                }
                for (long ci = Math.floorDiv(x0, DrainageDomain.CELL); ci <= Math.floorDiv(x1, DrainageDomain.CELL); ci++) {
                    for (long cj = Math.floorDiv(z0, DrainageDomain.CELL); cj <= Math.floorDiv(z1, DrainageDomain.CELL); cj++) {
                        if (index.lakeTops(DrainageDomain.key(ci, cj)) != null) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Cross-section of a channel and its valley: a parabolic bed below the water top inside the
     * channel, a one-block bank, a floodplain rising one block per 20, valley walls (in bands of
     * steeper and gentler slope where they are steep, like the ledges of a gorge), and a steep
     * rise at the cutoff so the shape always meets the terrain before it.
     */
    static double profile(double d, double r, int top, double depth, double floodplain, double wall, double cutoff) {
        if (d <= r) {
            double u = r > 1e-6 ? d / r : 1.0;
            return top - depth * (1.0 - u * u);
        }
        double e = d - r;
        double p = top + Math.min(e, 1.0);
        if (e > 1.0) {
            p += 0.05 * Math.min(e - 1.0, floodplain);
            if (e > 1.0 + floodplain) {
                double rise = wall * (e - 1.0 - floodplain);
                double ledges = 0.55 * smoothstep(0.9, 1.6, wall);
                p += rise + ledges * LEDGE / (2.0 * Math.PI) * Math.sin(2.0 * Math.PI * rise / LEDGE);
            }
        }
        if (d > cutoff - 16.0) {
            p += 12.0 * (d - (cutoff - 16.0));
        }
        return p;
    }

    /** The river segments nearest to a point, for diagnostics. */
    public String describe(int x, int z) {
        WaterIndex index = this.index(Math.floorDiv(x, WaterIndex.SIZE), Math.floorDiv(z, WaterIndex.SIZE));
        StringBuilder text = new StringBuilder();
        int[] refs = index.bucket(Math.floorMod(x, WaterIndex.SIZE), Math.floorMod(z, WaterIndex.SIZE));
        for (int ref : refs) {
            BasinRivers b = index.owners[ref];
            int k = index.segments[ref];
            double mx = (b.ax[k] + b.bx[k]) / 2;
            double mz = (b.az[k] + b.bz[k]) / 2;
            double dist = Math.hypot(mx - x, mz - z);
            if (dist < 40) {
                text.append(String.format(java.util.Locale.ROOT, "seg basin %d k %d (%.0f,%.0f)-(%.0f,%.0f) r %.1f-%.1f d %.1f-%.1f top %d fp %.1f wall %.2f cut %.0f flags %d%n",
                        b.basin, k, b.ax[k], b.az[k], b.bx[k], b.bz[k], b.rA[k], b.rB[k], b.dA[k], b.dB[k], b.waterTop[k], b.floodplain[k], b.wall[k], b.cutoff[k], b.flags[k]));
            }
        }
        return text.toString();
    }

    /** Falls of 6 blocks or more of the basins near a point: x, z, upper and lower water top. */
    public List<double[]> falls(int x, int z) {
        WaterIndex index = this.index(Math.floorDiv(x, WaterIndex.SIZE), Math.floorDiv(z, WaterIndex.SIZE));
        List<double[]> falls = new ArrayList<>();
        for (BasinRivers b : new java.util.LinkedHashSet<>(java.util.Arrays.asList(index.owners))) {
            falls.addAll(b.fallList);
        }
        return falls;
    }

    /** Statistics of the rivers around a point, for previews and tests. */
    public record Stats(int segments, int falls, int tallFalls, double tallestFall) {
    }

    public Stats stats(int x, int z) {
        WaterIndex index = this.index(Math.floorDiv(x, WaterIndex.SIZE), Math.floorDiv(z, WaterIndex.SIZE));
        java.util.Set<BasinRivers> basins = new java.util.HashSet<>(java.util.Arrays.asList(index.owners));
        int segments = 0;
        int falls = 0;
        int tall = 0;
        double tallest = 0.0;
        for (BasinRivers b : basins) {
            segments += b.segments;
            falls += b.falls;
            tall += b.tallFalls;
            tallest = Math.max(tallest, b.tallestFall);
        }
        return new Stats(segments, falls, tall, tallest);
    }
}
