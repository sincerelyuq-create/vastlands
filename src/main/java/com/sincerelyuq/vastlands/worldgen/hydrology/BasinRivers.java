package com.sincerelyuq.vastlands.worldgen.hydrology;

import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.clamp;
import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.smoothstep;

import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The rivers of one drainage basin as channel segments with widths, depths and water levels.
 *
 * <p>Built from the basin's coarse river cells ({@link DrainageDomain}):
 * <ol>
 *     <li>Paths: each river cell gets a node point at the lowest terrain of nine candidates in the
 *     cell; reaches (source or confluence to the next confluence, lake or mouth) are smooth
 *     Catmull-Rom curves through their node points, resampled every {@value #STEP} blocks.</li>
 *     <li>Size: width grows with the square root of the discharge (hydraulic geometry), depth with
 *     the width; sources taper in.</li>
 *     <li>Water levels, from the sources down: the highest level that keeps both banks above the
 *     water (running minimum of the terrain beside the channel), split into flat pools. Levels
 *     are {@code seaTop + 2k}: any two water surfaces differ by 0 or at least 2 blocks, which
 *     keeps vanilla water from turning the edge of a waterfall into new sources. Where the
 *     terrain falls steeply, pools are long and end in tall falls with a gorge below (the gorge
 *     deepens upstream towards the next fall); in gentle terrain they end in 2-block weirs.
 *     A river enters a lake or the sea at its level; a tributary meets the main river at the main
 *     river's level, or falls into its valley from a hanging valley.</li>
 *     <li>Bed shape at falls: a sill (the bed rises to one block below the upper water) and a
 *     plunge pool below.</li>
 * </ol>
 * Immutable after construction; a pure function of the basin's cells and the terrain.
 */
final class BasinRivers {
    /** Spacing of curve vertices, in blocks. */
    static final double STEP = 4.0;
    private static final int BANK_EVERY = 4;
    /** Banks are kept at least this far above the water top (blocks of surface height). */
    private static final double FREEBOARD = 1.5;
    private static final double SILL_LENGTH = 6.0;
    private static final double MAX_WIDTH = 110.0;
    private static final double POOL_MAX_LENGTH = 1200.0;

    static final byte IN_LAKE = 1;
    static final byte RIVER_BIOME = 2;
    /** A river drops to a lake's level this far before it enters the lake (plus its half width). */
    private static final double LAKE_ENTRY = 24.0;

    final long basin;
    // Segments.
    final double[] ax, az, bx, bz;
    final float[] rA, rB, dA, dB;
    final int[] waterTop;
    final float[] floodplain, wall, cutoff, bank;
    final byte[] flags;
    /**
     * Falls cut the channel square across. A segment below a fall does not reach back past the
     * line through the fall (point and downstream normal; NaN if no fall is near upstream), a
     * segment above one does not reach past it: the gorge below a fall must not carve into the
     * pool above it, and the pool above must not spill over the gorge.
     */
    final float[] upX, upZ, upNx, upNz, downX, downZ, downNx, downNz;
    final int segments;
    /** Effective water tops of the basin's lakes (indices into the domain's lakes). */
    final Map<Integer, Integer> lakeTops;
    // Statistics for previews and tests.
    final int falls;
    final int tallFalls;
    final double tallestFall;
    /** Falls of 6 blocks or more: x, z, upper water top, lower water top. */
    final List<double[]> fallList = new ArrayList<>();

    private static final class Reach {
        final List<double[]> points = new ArrayList<>();
        final List<Float> discharge = new ArrayList<>();
        int startCell;
        int endCell = -1;
        boolean source;
        boolean junctionEnd;
        // Vertices.
        double[] x, z, s, r, depth, b, bHigh, u, steep;
        int[] lake;
        int[] top;
        int[] segTop;
        double endLevel;
    }

    BasinRivers(HydrologyModel model, DrainageDomain domain, long basin) {
        long start = System.nanoTime();
        this.basin = basin;
        int[] cells = domain.basinCells.getOrDefault(basin, new int[0]);
        Map<Integer, Integer> lakeTops = new HashMap<>();
        for (int l = 0; l < domain.lakes.size(); l++) {
            if (domain.lakes.get(l).basin() == basin) {
                lakeTops.put(l, domain.lakes.get(l).waterTop());
            }
        }

        // Node points: the lowest terrain among nine candidates of each river cell.
        Map<Integer, double[]> node = new HashMap<>();
        for (int r : cells) {
            long key = domain.cellKeys[r];
            double cx = (DrainageDomain.keyX(key) + 0.5) * DrainageDomain.CELL;
            double cz = (DrainageDomain.keyZ(key) + 0.5) * DrainageDomain.CELL;
            if (domain.lakeOf[r] >= 0) {
                node.put(r, new double[] {cx, cz});
                continue;
            }
            double best = Double.POSITIVE_INFINITY;
            double[] point = null;
            for (int a = -1; a <= 1; a++) {
                for (int c = -1; c <= 1; c++) {
                    double x = cx + a * 40.0;
                    double z = cz + c * 40.0;
                    double h = model.terrainHeight(x, z);
                    if (h < best) {
                        best = h;
                        point = new double[] {x, z};
                    }
                }
            }
            node.put(r, point);
        }

        // Reaches between sources, confluences and ends.
        Map<Integer, Integer> upstream = new HashMap<>();
        for (int r : cells) {
            if (domain.downKinds[r] == DrainageDomain.DOWN_RIVER) {
                Integer d = domain.cellIndex.get(domain.downKeys[r]);
                if (d != null) {
                    upstream.merge(d, 1, Integer::sum);
                }
            }
        }
        List<Reach> reaches = new ArrayList<>();
        for (int r : cells) {
            int up = upstream.getOrDefault(r, 0);
            if (up == 1) {
                continue;
            }
            Reach reach = new Reach();
            reach.startCell = r;
            reach.source = up == 0;
            int cur = r;
            while (true) {
                reach.points.add(node.get(cur));
                reach.discharge.add(domain.discharge[cur]);
                byte kind = domain.downKinds[cur];
                Integer next = kind == DrainageDomain.DOWN_RIVER ? domain.cellIndex.get(domain.downKeys[cur]) : null;
                if (next == null) {
                    if (kind == DrainageDomain.DOWN_OCEAN || kind == DrainageDomain.DOWN_LAKE) {
                        long d = domain.downKeys[cur];
                        reach.points.add(new double[] {(DrainageDomain.keyX(d) + 0.5) * DrainageDomain.CELL, (DrainageDomain.keyZ(d) + 0.5) * DrainageDomain.CELL});
                        reach.discharge.add(domain.discharge[cur]);
                    }
                    break;
                }
                if (upstream.getOrDefault(next, 0) >= 2) {
                    reach.points.add(node.get(next));
                    reach.discharge.add(domain.discharge[cur]);
                    reach.endCell = next;
                    reach.junctionEnd = true;
                    break;
                }
                cur = next;
            }
            if (reach.points.size() >= 2) {
                reaches.add(reach);
            }
        }
        Map<Integer, List<Reach>> incoming = new HashMap<>();
        Map<Integer, Reach> outgoing = new HashMap<>();
        for (Reach reach : reaches) {
            if (reach.junctionEnd) {
                incoming.computeIfAbsent(reach.endCell, c -> new ArrayList<>()).add(reach);
            }
            if (!reach.source) {
                outgoing.put(reach.startCell, reach);
            }
        }

        for (Reach reach : reaches) {
            this.shape(model, domain, reach, lakeTops);
        }
        // Levels in topological order: a confluence's outgoing reach after all incoming ones.
        List<Reach> order = new ArrayList<>();
        Map<Reach, Boolean> done = new HashMap<>();
        for (Reach reach : reaches) {
            visit(reach, incoming, done, order);
        }
        int[] fallCounts = new int[2];
        double[] tallest = new double[1];
        // A lake's surface drops to the lowest inflow arriving at it; rivers that passed it before
        // that inflow was computed are computed again, until the lake surfaces settle.
        for (int pass = 0; pass < 4; pass++) {
            Map<Integer, Integer> before = new HashMap<>(lakeTops);
            for (Reach reach : order) {
                double cap = Double.POSITIVE_INFINITY;
                if (!reach.source) {
                    for (Reach in : incoming.getOrDefault(reach.startCell, List.of())) {
                        cap = Math.min(cap, in.endLevel);
                    }
                }
                this.levels(model, domain, reach, cap, lakeTops);
            }
            if (before.equals(lakeTops)) {
                break;
            }
        }
        // Rivers drop to a lake's level in their own channel before they reach its shore.
        for (Reach reach : reaches) {
            enterLakes(reach, lakeTops);
        }
        // Tributaries meet the main river at its level, or fall into its valley.
        for (Map.Entry<Integer, List<Reach>> entry : incoming.entrySet()) {
            Reach out = outgoing.get(entry.getKey());
            if (out == null) {
                continue;
            }
            int mainTop = out.segTop[0];
            for (Reach in : entry.getValue()) {
                joinAt(in, out, mainTop);
            }
        }

        // Segments.
        int total = 0;
        for (Reach reach : reaches) {
            total += reach.x.length - 1;
        }
        this.segments = total;
        this.ax = new double[total];
        this.az = new double[total];
        this.bx = new double[total];
        this.bz = new double[total];
        this.rA = new float[total];
        this.rB = new float[total];
        this.dA = new float[total];
        this.dB = new float[total];
        this.waterTop = new int[total];
        this.floodplain = new float[total];
        this.wall = new float[total];
        this.cutoff = new float[total];
        this.bank = new float[total];
        this.flags = new byte[total];
        this.upX = new float[total];
        this.upZ = new float[total];
        this.upNx = new float[total];
        this.upNz = new float[total];
        this.downX = new float[total];
        this.downZ = new float[total];
        this.downNx = new float[total];
        this.downNz = new float[total];
        int k = 0;
        for (Reach reach : reaches) {
            k = this.emit(reach, k, fallCounts, tallest);
        }
        this.lakeTops = lakeTops;
        this.falls = fallCounts[0];
        this.tallFalls = fallCounts[1];
        this.tallestFall = tallest[0];
        HydrologyModel.COUNTERS.basins.incrementAndGet();
        HydrologyModel.COUNTERS.segments.addAndGet(total);
        HydrologyModel.COUNTERS.basinNanos.addAndGet(System.nanoTime() - start);
    }

    private static void visit(Reach reach, Map<Integer, List<Reach>> incoming, Map<Reach, Boolean> done, List<Reach> order) {
        if (done.containsKey(reach)) {
            return;
        }
        done.put(reach, true);
        if (!reach.source) {
            for (Reach in : incoming.getOrDefault(reach.startCell, List.of())) {
                visit(in, incoming, done, order);
            }
        }
        order.add(reach);
    }

    /** Smooth path, width, depth and the terrain beside the channel. */
    private void shape(HydrologyModel model, DrainageDomain domain, Reach reach, Map<Integer, Integer> lakeTops) {
        List<double[]> pts = reach.points;
        int m = pts.size();
        List<double[]> verts = new ArrayList<>();
        List<Double> flow = new ArrayList<>();
        verts.add(pts.get(0));
        flow.add((double) reach.discharge.get(0));
        for (int i = 0; i < m - 1; i++) {
            double[] p0 = pts.get(Math.max(0, i - 1));
            double[] p1 = pts.get(i);
            double[] p2 = pts.get(i + 1);
            double[] p3 = pts.get(Math.min(m - 1, i + 2));
            double length = Math.hypot(p2[0] - p1[0], p2[1] - p1[1]);
            int steps = Math.max(1, (int) Math.ceil(length / STEP));
            for (int t = 1; t <= steps; t++) {
                verts.add(catmullRom(p0, p1, p2, p3, (double) t / steps));
                double f = (double) t / steps;
                flow.add(reach.discharge.get(i) * (1 - f) + reach.discharge.get(i + 1) * f);
            }
        }
        int n = verts.size();
        reach.x = new double[n];
        reach.z = new double[n];
        reach.s = new double[n];
        reach.r = new double[n];
        reach.depth = new double[n];
        reach.b = new double[n];
        reach.bHigh = new double[n];
        reach.u = new double[n];
        reach.steep = new double[n];
        reach.lake = new int[n];
        reach.top = new int[n];
        for (int i = 0; i < n; i++) {
            reach.x[i] = verts.get(i)[0];
            reach.z[i] = verts.get(i)[1];
            if (i > 0) {
                reach.s[i] = reach.s[i - 1] + Math.hypot(reach.x[i] - reach.x[i - 1], reach.z[i] - reach.z[i - 1]);
            }
            double width = widthFor(flow.get(i));
            if (reach.source) {
                width *= 0.35 + 0.65 * smoothstep(0.0, 64.0, reach.s[i]);
            }
            reach.r[i] = width * 0.5;
            reach.depth[i] = 1.5 + 0.075 * width;
            int lake = model.lakeWaterAt(domain, reach.x[i], reach.z[i]);
            reach.lake[i] = lakeTops.containsKey(lake) ? lake : -1;
        }
        // Terrain beside the channel: just outside the water and at the edge of the bank.
        int samples = (n - 1) / BANK_EVERY + 1;
        double[] low = new double[samples];
        double[] high = new double[samples];
        for (int q = 0; q < samples; q++) {
            int i = Math.min(n - 1, q * BANK_EVERY);
            double tx = reach.x[Math.min(n - 1, i + 1)] - reach.x[Math.max(0, i - 1)];
            double tz = reach.z[Math.min(n - 1, i + 1)] - reach.z[Math.max(0, i - 1)];
            double len = Math.hypot(tx, tz);
            double nx = len > 1e-9 ? -tz / len : 1.0;
            double nz = len > 1e-9 ? tx / len : 0.0;
            double lo = Double.POSITIVE_INFINITY;
            double hi = Double.NEGATIVE_INFINITY;
            double near = reach.r[i] + 3.0;
            double far = reach.r[i] + bankWidth(reach.r[i]);
            for (double d : new double[] {-far, -near, near, far}) {
                double h = model.terrainHeight(reach.x[i] + nx * d, reach.z[i] + nz * d);
                lo = Math.min(lo, h);
                hi = Math.max(hi, h);
            }
            low[q] = lo;
            high[q] = hi;
        }
        for (int i = 0; i < n; i++) {
            int q0 = Math.min(samples - 1, i / BANK_EVERY);
            int q1 = Math.min(samples - 1, q0 + 1);
            reach.b[i] = Math.min(low[q0], low[q1]);
            reach.bHigh[i] = Math.max(high[q0], high[q1]);
        }
    }

    /**
     * River width in blocks from the discharge (runoff-weighted 128-block cells): about 8 blocks
     * where a river begins, 25-30 for a river draining a few hundred cells, 50 for a thousand, and
     * up to {@value #MAX_WIDTH} for the largest rivers of a continent.
     */
    static double widthFor(double discharge) {
        return Math.min(MAX_WIDTH, 3.0 + 0.8 * Math.pow(discharge, 0.6));
    }

    static double bankWidth(double halfWidth) {
        return 4.0 + 0.2 * halfWidth;
    }

    /** Water levels of one reach: running minimum of the banks, lakes, then pools and falls. */
    private void levels(HydrologyModel model, DrainageDomain domain, Reach reach, double cap, Map<Integer, Integer> lakeTops) {
        int n = reach.x.length;
        double[] u = reach.u;
        double level = cap;
        for (int i = 0; i < n; i++) {
            int lake = reach.lake[i];
            if (lake >= 0) {
                // Entering a lake: its surface is the level; an inflow arriving lower lowers it.
                int top = Math.min(lakeTops.get(lake), model.quantizeLevel(level));
                lakeTops.put(lake, top);
                level = top;
            } else {
                level = Math.min(level, reach.b[i] - FREEBOARD);
            }
            u[i] = level;
        }
        // Steepness of the level over ~±64 blocks.
        int w = (int) Math.round(64.0 / STEP);
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - w);
            int c = Math.min(n - 1, i + w);
            double span = reach.s[c] - reach.s[a];
            reach.steep[i] = span > 1.0 ? (u[a] - u[c]) / span : 0.0;
        }
        // Pools: long, gorge-headed pools with tall falls where the river is steep, 2-block weirs
        // where it is gentle.
        long seed = SimplexNoise.mix(this.basin * 31 + reach.startCell);
        int i = 0;
        while (i < n) {
            boolean lakeHere = reach.lake[i] >= 0;
            double steep = reach.steep[i];
            double hash = (SimplexNoise.mix(seed + i) >>> 11) * 0x1.0p-53;
            double gorge = steep < 0.04 ? 2.0 : clamp(steep * 90.0 * (0.4 + 1.2 * hash), 2.0, 40.0);
            int e = i;
            while (e + 1 < n && (reach.lake[e + 1] >= 0) == lakeHere && u[i] - u[e + 1] <= gorge
                    && reach.s[e + 1] - reach.s[i] <= POOL_MAX_LENGTH) {
                e++;
            }
            int top = model.quantizeLevel(u[e]);
            for (int k = i; k <= e; k++) {
                reach.top[k] = top;
            }
            i = e + 1;
        }
        // A segment carries the level of its downstream vertex, so a step lies at a vertex and
        // the banks of both segments next to it stay above their water.
        reach.segTop = new int[n - 1];
        for (int k = 0; k < n - 1; k++) {
            reach.segTop[k] = reach.top[k + 1];
        }
        reach.endLevel = reach.top[n - 1];
    }

    /**
     * Where a reach meets the river below a confluence at a higher level, it drops to that level
     * where it enters the main river's valley: a fall from a hanging valley.
     */
    private static void joinAt(Reach in, Reach out, int mainTop) {
        int n = in.segTop.length;
        if (in.segTop[n - 1] <= mainTop) {
            return;
        }
        double drop = in.segTop[n - 1] - mainTop;
        double wallSlope = wallSlope(out.steep[0]);
        double valley = out.r[0] + floodplain(out.r[0] * 2.0, out.steep[0]) + 1.0 + drop / wallSlope + 3.0;
        double end = in.s[in.s.length - 1];
        for (int k = 0; k < n; k++) {
            if (end - in.s[k + 1] < valley) {
                in.segTop[k] = Math.min(in.segTop[k], mainTop);
            }
        }
    }

    /**
     * Where a reach enters a lake above the lake's surface, its last stretch before the shore is
     * lowered to the lake: the fall lies in the river's own channel, and the channel reaches the
     * lake water instead of ending on its shore.
     */
    private static void enterLakes(Reach reach, Map<Integer, Integer> lakeTops) {
        int n = reach.x.length;
        for (int i = 1; i < n; i++) {
            int lake = reach.lake[i];
            if (lake < 0 || reach.lake[i - 1] >= 0) {
                continue;
            }
            int top = lakeTops.get(lake);
            double entry = reach.s[i];
            for (int k = i - 1; k >= 0 && entry - reach.s[k + 1] < LAKE_ENTRY + reach.r[i]; k--) {
                reach.segTop[k] = Math.min(reach.segTop[k], top);
            }
        }
    }

    static double wallSlope(double steep) {
        return 0.7 + 1.3 * smoothstep(0.02, 0.25, steep);
    }

    static double floodplain(double width, double steep) {
        return 2.0 + (1.0 - smoothstep(0.01, 0.08, steep)) * clamp(0.8 * width, 2.0, 80.0);
    }

    /** Writes the reach's segments, with sills above and plunge pools below every step. */
    private int emit(Reach reach, int k, int[] fallCounts, double[] tallest) {
        int n = reach.x.length;
        double[] startDepth = reach.depth.clone();
        double[] endDepth = reach.depth.clone();
        for (int v = 1; v < n - 1; v++) {
            int upper = reach.segTop[v - 1];
            int lower = reach.segTop[v];
            if (upper <= lower) {
                continue;
            }
            double drop = upper - lower;
            boolean intoLake = reach.lake[v] >= 0;
            fallCounts[0]++;
            if (drop >= 8 && !intoLake) {
                fallCounts[1]++;
            }
            tallest[0] = Math.max(tallest[0], drop);
            if (drop >= 6 && !intoLake) {
                this.fallList.add(new double[] {reach.x[v], reach.z[v], upper, lower});
            }
            double sv = reach.s[v];
            for (int q = v; q >= 0 && sv - reach.s[q] <= SILL_LENGTH; q--) {
                double t = (sv - reach.s[q]) / SILL_LENGTH;
                endDepth[q] = Math.min(endDepth[q], 1.0 + (reach.depth[q] - 1.0) * t);
                if (q < v) {
                    startDepth[q] = Math.min(startDepth[q], 1.0 + (reach.depth[q] - 1.0) * t);
                }
            }
            double plunge = clamp(2.0 + 0.4 * drop, 3.0, 14.0);
            double length = 8.0 + 0.5 * drop;
            for (int q = v; q < n && reach.s[q] - sv <= length; q++) {
                double d = plunge * (1.0 - (reach.s[q] - sv) / length);
                startDepth[q] = Math.max(startDepth[q], d);
                if (q > v) {
                    endDepth[q] = Math.max(endDepth[q], d);
                }
            }
        }
        // Valley shape per vertex, smoothed along the river so neighbouring segments carve the
        // same valley (no facets where the shape of one segment ends and the next begins).
        double[] fp = new double[n];
        double[] ws = new double[n];
        double[] above = new double[n];
        int half = (int) Math.round(48.0 / STEP);
        for (int v = 0; v < n; v++) {
            double steep = 0.0;
            double high = 0.0;
            int count = 0;
            for (int q = Math.max(0, v - half); q <= Math.min(n - 1, v + half); q++) {
                steep += reach.steep[q];
                high = Math.max(high, reach.bHigh[q] - reach.top[q]);
                count++;
            }
            steep /= count;
            fp[v] = floodplain(2.0 * reach.r[v], steep);
            ws[v] = wallSlope(steep);
            above[v] = Math.max(0.0, high);
        }
        // Falls between pools of the river (not into or out of a lake).
        List<Integer> falls = new ArrayList<>();
        for (int v = 1; v < n - 1; v++) {
            if (reach.segTop[v - 1] != reach.segTop[v] && !(reach.lake[v - 1] >= 0 && reach.lake[v] >= 0) && !(reach.lake[v] >= 0 && reach.lake[v + 1] >= 0)) {
                falls.add(v);
            }
        }
        for (int v = 0; v < n - 1; v++) {
            this.upX[k] = Float.NaN;
            this.downX[k] = Float.NaN;
            double reachOut = 330.0;
            for (int f : falls) {
                double tx = reach.x[Math.min(n - 1, f + 1)] - reach.x[f - 1];
                double tz = reach.z[Math.min(n - 1, f + 1)] - reach.z[f - 1];
                double len = Math.hypot(tx, tz);
                if (len < 1e-9) {
                    continue;
                }
                tx /= len;
                tz /= len;
                // Only segments wholly on their side of the line: a meander looping back keeps its channel.
                double a = (reach.x[v] - reach.x[f]) * tx + (reach.z[v] - reach.z[f]) * tz;
                double b = (reach.x[v + 1] - reach.x[f]) * tx + (reach.z[v + 1] - reach.z[f]) * tz;
                if (f <= v && reach.s[v] - reach.s[f] < reachOut && a >= -1e-6 && b >= -1e-6) {
                    this.upX[k] = (float) reach.x[f];
                    this.upZ[k] = (float) reach.z[f];
                    this.upNx[k] = (float) tx;
                    this.upNz[k] = (float) tz;
                } else if (f >= v + 1 && reach.s[f] - reach.s[v + 1] < reachOut && a <= 1e-6 && b <= 1e-6 && Float.isNaN(this.downX[k])) {
                    this.downX[k] = (float) reach.x[f];
                    this.downZ[k] = (float) reach.z[f];
                    this.downNx[k] = (float) tx;
                    this.downNz[k] = (float) tz;
                }
            }
            this.ax[k] = reach.x[v];
            this.az[k] = reach.z[v];
            this.bx[k] = reach.x[v + 1];
            this.bz[k] = reach.z[v + 1];
            this.rA[k] = (float) reach.r[v];
            this.rB[k] = (float) reach.r[v + 1];
            this.dA[k] = (float) startDepth[v];
            this.dB[k] = (float) endDepth[v + 1];
            this.waterTop[k] = reach.segTop[v];
            double width = 2.0 * Math.max(reach.r[v], reach.r[v + 1]);
            double f = 0.5 * (fp[v] + fp[v + 1]);
            double w = 0.5 * (ws[v] + ws[v + 1]);
            this.floodplain[k] = (float) f;
            this.wall[k] = (float) w;
            double r = Math.max(reach.r[v], reach.r[v + 1]);
            this.cutoff[k] = (float) Math.min(320.0, r + 1.0 + f + (Math.max(above[v], above[v + 1]) + 8.0) / w + 24.0);
            this.bank[k] = (float) bankWidth(r);
            byte flags = 0;
            if (reach.lake[v] >= 0 && reach.lake[v + 1] >= 0) {
                flags |= IN_LAKE;
            }
            if (width >= 6.0) {
                flags |= RIVER_BIOME;
            }
            this.flags[k] = flags;
            k++;
        }
        return k;
    }

    /** Centripetal Catmull-Rom point between p1 and p2. */
    private static double[] catmullRom(double[] p0, double[] p1, double[] p2, double[] p3, double t) {
        double t0 = 0.0;
        double t1 = t0 + Math.sqrt(Math.max(1e-6, Math.hypot(p1[0] - p0[0], p1[1] - p0[1])));
        double t2 = t1 + Math.sqrt(Math.max(1e-6, Math.hypot(p2[0] - p1[0], p2[1] - p1[1])));
        double t3 = t2 + Math.sqrt(Math.max(1e-6, Math.hypot(p3[0] - p2[0], p3[1] - p2[1])));
        double tt = t1 + (t2 - t1) * t;
        double[] out = new double[2];
        for (int c = 0; c < 2; c++) {
            double a1 = (t1 - tt) / (t1 - t0) * p0[c] + (tt - t0) / (t1 - t0) * p1[c];
            double a2 = (t2 - tt) / (t2 - t1) * p1[c] + (tt - t1) / (t2 - t1) * p2[c];
            double a3 = (t3 - tt) / (t3 - t2) * p2[c] + (tt - t2) / (t3 - t2) * p3[c];
            double b1 = (t2 - tt) / (t2 - t0) * a1 + (tt - t0) / (t2 - t0) * a2;
            double b2 = (t3 - tt) / (t3 - t1) * a2 + (tt - t1) / (t3 - t1) * a3;
            out[c] = (t2 - tt) / (t2 - t1) * b1 + (tt - t1) / (t2 - t1) * b2;
        }
        return out;
    }

    /** Bounding box of segment k including its reach of influence. */
    double minX(int k) {
        return Math.min(this.ax[k], this.bx[k]) - this.cutoff[k];
    }

    double maxX(int k) {
        return Math.max(this.ax[k], this.bx[k]) + this.cutoff[k];
    }

    double minZ(int k) {
        return Math.min(this.az[k], this.bz[k]) - this.cutoff[k];
    }

    double maxZ(int k) {
        return Math.max(this.az[k], this.bz[k]) + this.cutoff[k];
    }

    @Override
    public String toString() {
        return "BasinRivers[" + this.segments + " segments, falls " + this.falls + "]";
    }
}
