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
 *     Catmull-Rom curves through their node points. On that valley line the river meanders: a
 *     sine-generated curve (the shape of real meanders) whose wavelength grows with the river's
 *     width and whose amplitude is held to the flat ground beside the valley line, so meanders
 *     swing across floodplains and stay small in narrow valleys. A river keeps its meander phase
 *     through confluences. The path is resampled every {@value #STEP} blocks.</li>
 *     <li>Size: width grows with the square root of the discharge (hydraulic geometry), a little
 *     wider in bends and varying along the river, depth with the width; sources taper in.</li>
 *     <li>Water levels, from the sources down: the highest level that keeps both banks above the
 *     water (running minimum of the terrain beside the channel), split into flat pools. Levels
 *     are {@code seaTop + 2k}: any two water surfaces differ by 0 or at least 2 blocks, which
 *     keeps vanilla water from turning the edge of a waterfall into new sources. Where the
 *     terrain falls steeply, pools are long and end in tall falls with a gorge below (the gorge
 *     deepens upstream towards the next fall); in gentle terrain they end in 2-block ledges.
 *     A river enters a lake or the sea at its level; a tributary meets the main river at the main
 *     river's level, or falls into its valley from a hanging valley.</li>
 *     <li>Falls: the lip is a ragged line across the river, bowed upstream into a horseshoe at
 *     tall falls (see {@link #fallSide}); above it a sill (the bed rises to one block below the
 *     upper water), below it a plunge pool, wider than the river and round at tall falls.
 *     Boulders stand in plunge pools, on the lips of tall falls (splitting the curtain), on
 *     ledges and in steep streams.</li>
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
    /** A pool shorter than this between two falls joins the pool below them. */
    private static final double SHORT_POOL = 64.0;
    /** Falls at least this tall get a horseshoe lip, a round plunge pool and boulders. */
    private static final int TALL_FALL = 6;
    /** A fall line cuts the segments within this distance along the river (blocks). */
    private static final double FALL_REACH = 330.0;

    static final byte IN_LAKE = 1;
    static final byte RIVER_BIOME = 2;
    /** The segment is widened into the plunge pool of a tall fall. */
    static final byte POOL = 4;
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
     * Falls cut the channel across along their lip line. A segment below a fall does not reach
     * upstream of the lip of {@code upFall} (an index into the falls, or -1), a segment above one
     * not downstream of the lip of {@code downFall}: the gorge below a fall must not carve into
     * the pool above it, and the pool above must not spill over the gorge.
     */
    final int[] upFall, downFall;
    final int segments;
    // Fall lines: a point on the river, the downstream direction, the half width of the lip
    // (channel and bank), how far its middle is bowed upstream (fraction of the half width), how
    // ragged it is (blocks) and a seed.
    final double[] fallX, fallZ;
    final float[] fallNx, fallNz, fallR, fallBow, fallJag;
    final long[] fallSeed;
    // Boulders: centre, radius, the water top they stand in, height above it, shape seed.
    final double[] rockX, rockZ;
    final float[] rockR;
    final int[] rockBase, rockHeight;
    final long[] rockSeed;
    final int rocks;
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
        /** Meander phase at the end of the reach (a multiple of pi: the river crosses its valley line). */
        double phaseEnd;
    }

    private record Fall(double x, double z, double nx, double nz, double r, double bow, double jag, long seed) {
    }

    private record Rock(double x, double z, double r, int base, int height, long seed) {
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

        // Topological order: a confluence's outgoing reach after all incoming ones.
        List<Reach> order = new ArrayList<>();
        Map<Reach, Boolean> done = new HashMap<>();
        for (Reach reach : reaches) {
            visit(reach, incoming, done, order);
        }
        // Paths in that order, so the river below a confluence continues the meanders of the
        // largest river above it.
        for (Reach reach : order) {
            double phase = unit(SimplexNoise.mix(basin * 31 + reach.startCell), 0) < 0.5 ? 0.0 : Math.PI;
            if (!reach.source) {
                Reach main = null;
                for (Reach in : incoming.getOrDefault(reach.startCell, List.of())) {
                    if (main == null || in.discharge.get(in.discharge.size() - 1) > main.discharge.get(main.discharge.size() - 1)) {
                        main = in;
                    }
                }
                if (main != null) {
                    phase = main.phaseEnd;
                }
            }
            this.shape(model, domain, reach, lakeTops, phase);
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
        this.upFall = new int[total];
        this.downFall = new int[total];
        List<Fall> fallLines = new ArrayList<>();
        List<Rock> rockList = new ArrayList<>();
        int k = 0;
        for (Reach reach : reaches) {
            k = this.emit(reach, k, fallCounts, tallest, fallLines, rockList);
        }
        int fallCount = fallLines.size();
        this.fallX = new double[fallCount];
        this.fallZ = new double[fallCount];
        this.fallNx = new float[fallCount];
        this.fallNz = new float[fallCount];
        this.fallR = new float[fallCount];
        this.fallBow = new float[fallCount];
        this.fallJag = new float[fallCount];
        this.fallSeed = new long[fallCount];
        for (int i = 0; i < fallCount; i++) {
            Fall f = fallLines.get(i);
            this.fallX[i] = f.x();
            this.fallZ[i] = f.z();
            this.fallNx[i] = (float) f.nx();
            this.fallNz[i] = (float) f.nz();
            this.fallR[i] = (float) f.r();
            this.fallBow[i] = (float) f.bow();
            this.fallJag[i] = (float) f.jag();
            this.fallSeed[i] = f.seed();
        }
        this.rocks = rockList.size();
        this.rockX = new double[this.rocks];
        this.rockZ = new double[this.rocks];
        this.rockR = new float[this.rocks];
        this.rockBase = new int[this.rocks];
        this.rockHeight = new int[this.rocks];
        this.rockSeed = new long[this.rocks];
        for (int i = 0; i < this.rocks; i++) {
            Rock rock = rockList.get(i);
            this.rockX[i] = rock.x();
            this.rockZ[i] = rock.z();
            this.rockR[i] = (float) rock.r();
            this.rockBase[i] = rock.base();
            this.rockHeight[i] = rock.height();
            this.rockSeed[i] = rock.seed();
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

    /** Path (valley line and meanders), width, depth and the terrain beside the channel. */
    private void shape(HydrologyModel model, DrainageDomain domain, Reach reach, Map<Integer, Integer> lakeTops, double phase) {
        // The valley line: a Catmull-Rom curve through the node points, one vertex per block.
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
            int steps = Math.max(1, (int) Math.ceil(length));
            for (int t = 1; t <= steps; t++) {
                verts.add(t == steps ? p2 : catmullRom(p0, p1, p2, p3, (double) t / steps));
                double f = (double) t / steps;
                flow.add(reach.discharge.get(i) * (1 - f) + reach.discharge.get(i + 1) * f);
            }
        }
        int nb = verts.size();
        double[] baseX = new double[nb];
        double[] baseZ = new double[nb];
        double[] baseS = new double[nb];
        double[] baseFlow = new double[nb];
        for (int i = 0; i < nb; i++) {
            baseX[i] = verts.get(i)[0];
            baseZ[i] = verts.get(i)[1];
            baseFlow[i] = flow.get(i);
            if (i > 0) {
                baseS[i] = baseS[i - 1] + Math.hypot(baseX[i] - baseX[i - 1], baseZ[i] - baseZ[i - 1]);
            }
        }
        double[][] path = this.meander(model, domain, reach, baseX, baseZ, baseS, baseFlow, phase);
        double[] px = path[0];
        double[] pz = path[1];
        double[] sigma = path[2];
        double[] phi = path[3];
        double[] amp = path[4];

        int n = px.length;
        reach.x = px;
        reach.z = pz;
        reach.s = new double[n];
        reach.r = new double[n];
        reach.depth = new double[n];
        reach.b = new double[n];
        reach.bHigh = new double[n];
        reach.u = new double[n];
        reach.steep = new double[n];
        reach.lake = new int[n];
        reach.top = new int[n];
        long seed = SimplexNoise.mix(this.basin * 0x9E3779B97F4A7C15L + reach.startCell * 0x632BE59BD9B4E019L + 5);
        double baseWidth = widthFor(baseFlow[nb / 2]);
        int cursor = 0;
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                reach.s[i] = reach.s[i - 1] + Math.hypot(px[i] - px[i - 1], pz[i] - pz[i - 1]);
            }
            while (cursor < nb - 2 && baseS[cursor + 1] < sigma[i]) {
                cursor++;
            }
            while (cursor > 0 && baseS[cursor] > sigma[i]) {
                cursor--;
            }
            double span = baseS[cursor + 1] - baseS[cursor];
            double f = span > 1e-9 ? clamp((sigma[i] - baseS[cursor]) / span, 0.0, 1.0) : 0.0;
            double width = widthFor(baseFlow[cursor] + (baseFlow[cursor + 1] - baseFlow[cursor]) * f);
            // A little wider in the bends of meanders, narrower where they cross the valley, and
            // varying along the river.
            double bendiness = Math.min(1.0, amp[i] / width);
            width *= 1.0 + 0.1 * (2.0 * Math.abs(Math.sin(phi[i])) - 1.0) * bendiness
                    + 0.08 * valueNoise(reach.s[i] / (6.0 * baseWidth + 24.0), seed);
            if (reach.source) {
                width *= 0.35 + 0.65 * smoothstep(0.0, 64.0, reach.s[i]);
            }
            width = Math.min(MAX_WIDTH, width);
            reach.r[i] = width * 0.5;
            reach.depth[i] = 1.5 + 0.075 * width;
            int lake = model.lakeWaterAt(domain, px[i], pz[i]);
            reach.lake[i] = lakeTops.containsKey(lake) ? lake : -1;
        }
        // Terrain beside the channel: just outside the water and at the edge of the bank.
        int samples = (n - 1) / BANK_EVERY + 1;
        double[] low = new double[samples];
        double[] high = new double[samples];
        for (int q = 0; q < samples; q++) {
            int i = Math.min(n - 1, q * BANK_EVERY);
            double tx = px[Math.min(n - 1, i + 1)] - px[Math.max(0, i - 1)];
            double tz = pz[Math.min(n - 1, i + 1)] - pz[Math.max(0, i - 1)];
            double len = Math.hypot(tx, tz);
            double nx = len > 1e-9 ? -tz / len : 1.0;
            double nz = len > 1e-9 ? tx / len : 0.0;
            double lo = Double.POSITIVE_INFINITY;
            double hi = Double.NEGATIVE_INFINITY;
            double near = reach.r[i] + 3.0;
            double far = reach.r[i] + bankWidth(reach.r[i]);
            for (double d : new double[] {-far, -near, near, far}) {
                double h = model.terrainHeight(px[i] + nx * d, pz[i] + nz * d);
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
     * Meanders on a valley line (vertices every block, arc length {@code baseS}): a sine-generated
     * curve, the shape real meanders take. Its direction swings by up to an angle ω (from
     * {@link HydrologyModel#meanderAngle}) about the valley line; to first order that is a lateral
     * offset {@code A (sin φ + c3 sin 3φ)} and a shift along the valley {@code -B sin 2φ}, with
     * {@code A, B, c3} from Bessel functions of ω. The reach holds a whole number of half waves,
     * so it starts and ends on its valley line (confluences, mouths and lakes stay where the
     * drainage put them), with the wavelength varying along it. The amplitude is held to the flat
     * ground beside the valley line and to its curvature.
     *
     * @return x, z, valley arc length, phase and amplitude of the vertices, about every
     *         {@value #STEP} blocks along the river
     */
    private double[][] meander(HydrologyModel model, DrainageDomain domain, Reach reach, double[] baseX, double[] baseZ, double[] baseS,
                               double[] baseFlow, double phase0) {
        int nb = baseX.length;
        double length = baseS[nb - 1];
        long seed = SimplexNoise.mix(this.basin * 0x9E3779B97F4A7C15L + reach.startCell * 31L + 7);
        double[] halfWidth = new double[nb];
        double meanWidth = 0.0;
        for (int i = 0; i < nb; i++) {
            halfWidth[i] = 0.5 * widthFor(baseFlow[i]);
            meanWidth += 2.0 * halfWidth[i];
        }
        meanWidth /= nb;
        // Meander wavelength along the valley: 11..16 river widths, longer for small streams.
        double wavelength = Math.max(110.0 + 40.0 * unit(seed, 4), (11.0 + 5.0 * unit(seed, 1)) * meanWidth);
        int halfWaves = (int) Math.round(length / (0.5 * wavelength));
        reach.phaseEnd = phase0 + halfWaves * Math.PI;
        if (halfWaves < 1 || nb < 8) {
            return resample(baseX, baseZ, baseS, null, null, null, null, null, 0, phase0, 0.0, 0.0);
        }
        double a1 = 0.7 * (unit(seed, 2) - 0.5);
        double a2 = 0.5 * (unit(seed, 3) - 0.5);
        // Smoothed normals of the valley line.
        double[] nx = new double[nb];
        double[] nz = new double[nb];
        for (int i = 0; i < nb; i++) {
            double tx = baseX[Math.min(nb - 1, i + 6)] - baseX[Math.max(0, i - 6)];
            double tz = baseZ[Math.min(nb - 1, i + 6)] - baseZ[Math.max(0, i - 6)];
            double len = Math.hypot(tx, tz);
            nx[i] = len > 1e-9 ? -tz / len : 1.0;
            nz[i] = len > 1e-9 ? tx / len : 0.0;
        }
        // Shape at every vertex: amplitude, shift along the valley, third harmonic.
        double[] amp = new double[nb];
        double[] shift = new double[nb];
        double[] third = new double[nb];
        for (int i = 0; i < nb; i++) {
            double t = baseS[i] / length;
            double stretch = 1.0 + a1 * Math.cos(2.0 * Math.PI * t) + a2 * Math.cos(4.0 * Math.PI * t);
            double localWavelength = 2.0 * length / (halfWaves * stretch);
            double omega = model.meanderAngle(baseX[i], baseZ[i]);
            double j0 = bessel(0, omega);
            double j1 = bessel(1, omega);
            amp[i] = j1 * localWavelength / (Math.PI * j0);
            shift[i] = bessel(2, omega) * localWavelength / (2.0 * Math.PI * j0);
            third[i] = bessel(3, omega) / (3.0 * j1);
        }
        // Room: flat ground on both sides of the valley line (every 32 blocks), and the curvature
        // of the valley line (a meander must not fold over its inner side).
        int every = 32;
        double[] allowed = new double[nb];
        int last = -1;
        for (int i = 0; ; i = Math.min(nb - 1, i + every)) {
            allowed[i] = amp[i] < 2.0 ? 0.0 : room(model, domain, baseX[i], baseZ[i], nx[i], nz[i], amp[i], halfWidth[i]);
            if (last >= 0) {
                for (int q = last + 1; q < i; q++) {
                    double f = (double) (q - last) / (i - last);
                    allowed[q] = allowed[last] + (allowed[i] - allowed[last]) * f;
                }
            }
            last = i;
            if (i == nb - 1) {
                break;
            }
        }
        for (int i = 0; i < nb; i++) {
            int a = Math.max(0, i - 16);
            int c = Math.min(nb - 1, i + 16);
            double turn = Math.abs(Math.atan2(nx[a] * nz[c] - nz[a] * nx[c], nx[a] * nx[c] + nz[a] * nz[c]));
            double span = baseS[c] - baseS[a];
            if (turn > 1e-6 && span > 1e-6) {
                allowed[i] = Math.min(allowed[i], 0.45 * span / turn);
            }
        }
        // Hold the amplitude to the tightest room within a quarter wavelength, then smooth it.
        int window = Math.max(1, (int) (0.25 * wavelength));
        double[] held = minFilter(allowed, window);
        double[] smooth = boxFilter(held, window);
        // Stretches of deep loops and of gentle bends along the river.
        for (int i = 0; i < nb; i++) {
            double strength = 0.3 + 0.7 * smoothstep(-0.5, 0.5, valueNoise(baseS[i] / (1.7 * wavelength), seed + 9));
            double q = amp[i] > 1e-9 ? clamp(smooth[i] / amp[i], 0.0, strength) : 0.0;
            amp[i] *= q;
            shift[i] *= q;
        }
        return resample(baseX, baseZ, baseS, nx, nz, amp, shift, third, halfWaves, phase0, a1, a2);
    }

    /**
     * Points of the meandering river about every {@value #STEP} blocks along it; with no
     * amplitude given, the valley line itself. The first and last points are the valley line's.
     */
    private static double[][] resample(double[] baseX, double[] baseZ, double[] baseS, double[] nx, double[] nz,
                                       double[] amp, double[] shift, double[] third, int halfWaves, double phase0, double a1, double a2) {
        int nb = baseX.length;
        double length = baseS[nb - 1];
        List<double[]> out = new ArrayList<>();
        out.add(new double[] {baseX[0], baseZ[0], 0.0, phase0, amp == null ? 0.0 : amp[0]});
        double prevX = baseX[0];
        double prevZ = baseZ[0];
        double along = 0.0;
        int cursor = 0;
        for (int i = 1; i < nb; i++) {
            double x;
            double z;
            double sigma;
            double phi = phase0;
            double a = 0.0;
            if (amp == null) {
                x = baseX[i];
                z = baseZ[i];
                sigma = baseS[i];
            } else {
                double t = baseS[i] / length;
                double warp = t + a1 * Math.sin(2.0 * Math.PI * t) / (2.0 * Math.PI) + a2 * Math.sin(4.0 * Math.PI * t) / (4.0 * Math.PI);
                phi = phase0 + halfWaves * Math.PI * warp;
                a = amp[i];
                double lateral = a * (Math.sin(phi) + third[i] * Math.sin(3.0 * phi));
                sigma = clamp(baseS[i] - shift[i] * Math.sin(2.0 * phi), 0.0, length);
                while (cursor < nb - 2 && baseS[cursor + 1] < sigma) {
                    cursor++;
                }
                while (cursor > 0 && baseS[cursor] > sigma) {
                    cursor--;
                }
                double span = baseS[cursor + 1] - baseS[cursor];
                double f = span > 1e-9 ? clamp((sigma - baseS[cursor]) / span, 0.0, 1.0) : 0.0;
                double cx = baseX[cursor] + (baseX[cursor + 1] - baseX[cursor]) * f;
                double cz = baseZ[cursor] + (baseZ[cursor + 1] - baseZ[cursor]) * f;
                double mx = nx[cursor] + (nx[cursor + 1] - nx[cursor]) * f;
                double mz = nz[cursor] + (nz[cursor + 1] - nz[cursor]) * f;
                double ml = Math.hypot(mx, mz);
                x = cx + mx / ml * lateral;
                z = cz + mz / ml * lateral;
            }
            along += Math.hypot(x - prevX, z - prevZ);
            prevX = x;
            prevZ = z;
            if (i == nb - 1) {
                // The exact end of the valley line; a point just before it is dropped.
                if (along < 0.5 * STEP && out.size() > 1) {
                    out.remove(out.size() - 1);
                }
                out.add(new double[] {baseX[i], baseZ[i], length, phi, a});
            } else if (along >= STEP) {
                out.add(new double[] {x, z, sigma, phi, a});
                along = 0.0;
            }
        }
        double[][] result = new double[5][out.size()];
        for (int i = 0; i < out.size(); i++) {
            for (int c = 0; c < 5; c++) {
                result[c][i] = out.get(i)[c];
            }
        }
        return result;
    }

    /**
     * How far a meander may swing from the valley line at a point: the distance to rising ground
     * on the nearer side (probed at three distances), less the river's half width and a margin.
     * None near a lake or the sea: rivers run straight into them instead of swinging along their
     * shores.
     */
    private static double room(HydrologyModel model, DrainageDomain domain, double x, double z, double nx, double nz, double amp, double halfWidth) {
        double reach = amp + halfWidth + 8.0;
        double ground = model.terrainHeight(x, z);
        if (ground < model.seaLevel() || model.lakeWaterAt(domain, x, z) >= 0) {
            return 0.0;
        }
        double room = reach;
        for (int side = -1; side <= 1; side += 2) {
            double free = reach;
            double previous = 0.0;
            for (double f : new double[] {0.35, 0.7, 1.0}) {
                double d = f * reach;
                double px = x + side * nx * d;
                double pz = z + side * nz * d;
                double h = model.terrainHeight(px, pz);
                if (h < model.seaLevel() || model.lakeWaterAt(domain, px, pz) >= 0) {
                    return 0.0;
                }
                if (h > ground + 6.0 + 0.04 * d) {
                    free = previous > 0.0 ? previous : 0.5 * d;
                    break;
                }
                previous = d;
            }
            room = Math.min(room, free);
        }
        return Math.max(0.0, room - halfWidth - 4.0);
    }

    private static double[] minFilter(double[] values, int window) {
        int n = values.length;
        double[] out = new double[n];
        // Two passes of a running minimum over blocks of the window (van Herk / Gil-Werman).
        double[] left = new double[n];
        double[] right = new double[n];
        int block = 2 * window + 1;
        for (int i = 0; i < n; i++) {
            left[i] = i % block == 0 ? values[i] : Math.min(left[i - 1], values[i]);
        }
        for (int i = n - 1; i >= 0; i--) {
            right[i] = (i % block == block - 1 || i == n - 1) ? values[i] : Math.min(right[i + 1], values[i]);
        }
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - window);
            int c = Math.min(n - 1, i + window);
            out[i] = Math.min(right[a], left[c]);
        }
        return out;
    }

    private static double[] boxFilter(double[] values, int window) {
        int n = values.length;
        double[] prefix = new double[n + 1];
        for (int i = 0; i < n; i++) {
            prefix[i + 1] = prefix[i] + values[i];
        }
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - window);
            int c = Math.min(n - 1, i + window);
            out[i] = (prefix[c + 1] - prefix[a]) / (c - a + 1);
        }
        return out;
    }

    /** Bessel function of the first kind J_n(x) for small x (power series). */
    static double bessel(int order, double x) {
        double half = 0.5 * x;
        double term = 1.0;
        for (int i = 1; i <= order; i++) {
            term *= half / i;
        }
        double sum = term;
        double square = half * half;
        for (int k = 1; k < 20; k++) {
            term *= -square / (k * (double) (k + order));
            sum += term;
        }
        return sum;
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
        // Pools: long, gorge-headed pools with tall falls where the river is steep, 2-block ledges
        // where it is gentle.
        long seed = SimplexNoise.mix(this.basin * 31 + reach.startCell);
        int i = 0;
        while (i < n) {
            boolean lakeHere = reach.lake[i] >= 0;
            double steep = reach.steep[i];
            double hash = (SimplexNoise.mix(seed + i) >>> 11) * 0x1.0p-53;
            double gorge = steep < 0.04 ? 2.0 : clamp(steep * 100.0 * (0.4 + 1.2 * hash), 2.0, 56.0);
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
        // Falls in quick succession make one tall fall: a short pool between two falls joins the
        // pool below it (its gorge reaches back to the upper fall).
        boolean merged = true;
        while (merged) {
            merged = false;
            int a = 0;
            while (a < n) {
                int e = a;
                while (e + 1 < n && reach.top[e + 1] == reach.top[a]) {
                    e++;
                }
                if (a > 0 && e < n - 1 && reach.top[a - 1] > reach.top[a] && reach.top[e + 1] < reach.top[a]
                        && reach.s[e] - reach.s[a] < SHORT_POOL && reach.lake[a] < 0 && reach.lake[e + 1] < 0
                        && reach.top[a - 1] - reach.top[e + 1] <= 80) {
                    for (int k = a; k <= e; k++) {
                        reach.top[k] = reach.top[e + 1];
                    }
                    merged = true;
                }
                a = e + 1;
            }
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

    /**
     * Writes the reach's segments, with sills above and plunge pools below every step, the lip
     * lines of its falls and its boulders.
     */
    private int emit(Reach reach, int k, int[] fallCounts, double[] tallest, List<Fall> fallLines, List<Rock> rockList) {
        int n = reach.x.length;
        double[] startDepth = reach.depth.clone();
        double[] endDepth = reach.depth.clone();
        double[] startRadius = reach.r.clone();
        double[] endRadius = reach.r.clone();
        long seed = SimplexNoise.mix(this.basin * 0x632BE59BD9B4E019L + reach.startCell * 0x9E3779B97F4A7C15L + 11);
        // Fall lines by vertex.
        Map<Integer, Integer> lines = new HashMap<>();
        List<Double> fallAt = new ArrayList<>();
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
            fallAt.add(reach.s[v]);
            double tx = reach.x[Math.min(n - 1, v + 1)] - reach.x[v - 1];
            double tz = reach.z[Math.min(n - 1, v + 1)] - reach.z[v - 1];
            double len = Math.hypot(tx, tz);
            tx = len > 1e-9 ? tx / len : 1.0;
            tz = len > 1e-9 ? tz / len : 0.0;
            double radius = reach.r[v];
            double lip = radius + bankWidth(radius);
            boolean tall = drop >= TALL_FALL && !intoLake;
            long fallSeed = SimplexNoise.mix(seed + v * 0x9E3779B97F4A7C15L);
            // Tall falls bow upstream into a horseshoe; ledges run straight or bow either way.
            double bow = tall ? 0.2 + 0.3 * unit(fallSeed, 1) : 0.5 * (unit(fallSeed, 1) - 0.5);
            double jag = tall ? Math.min(3.5, 0.8 + 0.04 * lip) : Math.min(5.0, 1.0 + 0.1 * radius);
            // The pool reaches past the upper river's banks, so the upper water never borders the
            // dry ground beside it.
            double pool = tall ? Math.min(Math.max(0.5 * MAX_WIDTH, lip + 3.0), (lip + 2.0) * (1.05 + 0.3 * unit(fallSeed, 2))) : radius;
            double poolLength = tall ? Math.max(8.0 + 0.5 * drop, 1.5 * pool) : 8.0 + 0.5 * drop;
            // Sill: the bed rises to one block below the upper water towards the lip.
            double sv = reach.s[v];
            double sill = SILL_LENGTH + Math.max(0.0, bow) * lip + jag;
            for (int q = v; q >= 0 && sv - reach.s[q] <= sill; q--) {
                double t = (sv - reach.s[q]) / sill;
                endDepth[q] = Math.min(endDepth[q], 1.0 + (reach.depth[q] - 1.0) * t);
                if (q < v) {
                    startDepth[q] = Math.min(startDepth[q], 1.0 + (reach.depth[q] - 1.0) * t);
                }
            }
            // Plunge pool: deeper below the fall, and round and wider than the river at tall falls.
            double plunge = clamp(2.0 + 0.4 * drop, 3.0, 14.0);
            for (int q = v; q < n && reach.s[q] - sv <= poolLength; q++) {
                double u = (reach.s[q] - sv) / poolLength;
                double d = plunge * (1.0 - u);
                startDepth[q] = Math.max(startDepth[q], d);
                if (q > v) {
                    endDepth[q] = Math.max(endDepth[q], d);
                }
                if (tall) {
                    double widened = pool * (1.0 - smoothstep(0.45, 1.0, u));
                    startRadius[q] = Math.max(startRadius[q], widened);
                    if (q > v) {
                        endRadius[q] = Math.max(endRadius[q], widened);
                    }
                }
            }
            boolean lakeInternal = (reach.lake[v - 1] >= 0 && reach.lake[v] >= 0) || (reach.lake[v] >= 0 && reach.lake[v + 1] >= 0);
            if (lakeInternal || len <= 1e-9) {
                continue;
            }
            Fall fall = new Fall(reach.x[v], reach.z[v], tx, tz, lip, bow, jag, fallSeed);
            lines.put(v, fallLines.size());
            fallLines.add(fall);
            if (intoLake) {
                continue;
            }
            if (tall) {
                // Boulders in the plunge pool, clear of the curtain.
                int count = 2 + (int) (3.0 * unit(fallSeed, 3)) + (int) (pool / 12.0);
                for (int j = 0; j < count; j++) {
                    long rockSeed = SimplexNoise.mix(fallSeed + 101L * (j + 1));
                    double size = 1.1 + 1.5 * unit(rockSeed, 1);
                    double downstream = Math.max(jag + 3.0 + 1.4 * size, (0.35 + 0.9 * unit(rockSeed, 2)) * pool);
                    double u = downstream / poolLength;
                    double width = Math.max(2.0, pool * (1.0 - smoothstep(0.45, 1.0, u)));
                    double across = (2.0 * unit(rockSeed, 3) - 1.0) * 0.75 * width;
                    rockList.add(new Rock(fall.x() + tx * downstream - tz * across, fall.z() + tz * downstream + tx * across,
                            size, lower, 1 + (int) (2.6 * unit(rockSeed, 4)), rockSeed));
                }
                // Rocks on the lip that split the curtain into strands, spread across it.
                int lipRocks = radius < 3.0 ? 0 : Math.min(4, 1 + (int) (unit(fallSeed, 4) * radius / 4.0));
                for (int j = 0; j < lipRocks; j++) {
                    long rockSeed = SimplexNoise.mix(fallSeed + 977L * (j + 1));
                    double size = 1.1 + 1.0 * unit(rockSeed, 1);
                    double slot = (j + 0.2 + 0.6 * unit(rockSeed, 2)) / lipRocks;
                    double across = (2.0 * slot - 1.0) * Math.max(0.0, radius - 1.5);
                    double along = this.lipOffset(fall, across) - 0.4 * size;
                    rockList.add(new Rock(fall.x() + tx * along - tz * across, fall.z() + tz * along + tx * across,
                            size, upper, 2 + (unit(rockSeed, 3) < 0.4 ? 1 : 0), rockSeed));
                }
            } else {
                // Rocks scattered along a ledge.
                int count = (int) (unit(fallSeed, 3) * (1.0 + radius / 6.0));
                for (int j = 0; j < count; j++) {
                    long rockSeed = SimplexNoise.mix(fallSeed + 431L * (j + 1));
                    double size = 0.8 + 1.0 * unit(rockSeed, 1);
                    double across = (2.0 * unit(rockSeed, 2) - 1.0) * Math.max(0.0, radius - 1.5);
                    double along = this.lipOffset(fall, across) + 7.0 * unit(rockSeed, 3) - 3.0;
                    int base = along < this.lipOffset(fall, across) ? upper : lower;
                    rockList.add(new Rock(fall.x() + tx * along - tz * across, fall.z() + tz * along + tx * across,
                            size, base, 1 + (unit(rockSeed, 4) < 0.25 ? 1 : 0), rockSeed));
                }
            }
        }
        // Boulders in steep, narrow streams, away from falls.
        for (int q = 2; q < n - 2; q += 3) {
            double steep = reach.steep[q];
            if (reach.lake[q] >= 0 || steep < 0.05 || reach.r[q] > 12.0) {
                continue;
            }
            long rockSeed = SimplexNoise.mix(seed + 0x2545F4914F6CDD1DL * q);
            if (unit(rockSeed, 0) >= 0.35 * smoothstep(0.05, 0.15, steep)) {
                continue;
            }
            boolean nearFall = false;
            for (double s : fallAt) {
                nearFall |= Math.abs(s - reach.s[q]) < 10.0 + reach.r[q];
            }
            if (nearFall) {
                continue;
            }
            double tx = reach.x[q + 1] - reach.x[q - 1];
            double tz = reach.z[q + 1] - reach.z[q - 1];
            double len = Math.hypot(tx, tz);
            if (len < 1e-9) {
                continue;
            }
            double across = (2.0 * unit(rockSeed, 1) - 1.0) * Math.max(0.0, reach.r[q] - 1.0);
            rockList.add(new Rock(reach.x[q] - tz / len * across, reach.z[q] + tx / len * across,
                    0.8 + 0.7 * unit(rockSeed, 2), reach.segTop[q], 1, rockSeed));
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
        for (int v = 0; v < n - 1; v++) {
            this.upFall[k] = -1;
            this.downFall[k] = -1;
            for (Map.Entry<Integer, Integer> line : lines.entrySet()) {
                int f = line.getKey();
                int index = line.getValue();
                Fall fall = fallLines.get(index);
                // Only segments wholly on their side of the lip: a meander swinging back or sideways
                // keeps its channel. Next to the fall, the side of the straight line through it.
                double a;
                double b;
                double near = 2.0 * fall.r() + 8.0;
                if (Math.min(Math.abs(reach.s[v] - reach.s[f]), Math.abs(reach.s[v + 1] - reach.s[f])) < near) {
                    a = (reach.x[v] - fall.x()) * fall.nx() + (reach.z[v] - fall.z()) * fall.nz();
                    b = (reach.x[v + 1] - fall.x()) * fall.nx() + (reach.z[v + 1] - fall.z()) * fall.nz();
                } else {
                    a = side(fall, reach.x[v], reach.z[v]);
                    b = side(fall, reach.x[v + 1], reach.z[v + 1]);
                }
                if (f <= v && reach.s[v] - reach.s[f] < FALL_REACH && a >= -1e-6 && b >= -1e-6) {
                    // The nearest fall upstream.
                    if (this.upFall[k] < 0 || f > upstreamVertex(lines, this.upFall[k])) {
                        this.upFall[k] = index;
                    }
                } else if (f >= v + 1 && reach.s[f] - reach.s[v + 1] < FALL_REACH && a <= 1e-6 && b <= 1e-6) {
                    // The nearest fall downstream.
                    if (this.downFall[k] < 0 || f < upstreamVertex(lines, this.downFall[k])) {
                        this.downFall[k] = index;
                    }
                }
            }
            this.ax[k] = reach.x[v];
            this.az[k] = reach.z[v];
            this.bx[k] = reach.x[v + 1];
            this.bz[k] = reach.z[v + 1];
            this.rA[k] = (float) startRadius[v];
            this.rB[k] = (float) endRadius[v + 1];
            this.dA[k] = (float) startDepth[v];
            this.dB[k] = (float) endDepth[v + 1];
            this.waterTop[k] = reach.segTop[v];
            double width = 2.0 * Math.max(reach.r[v], reach.r[v + 1]);
            double f = 0.5 * (fp[v] + fp[v + 1]);
            double w = 0.5 * (ws[v] + ws[v + 1]);
            this.floodplain[k] = (float) f;
            this.wall[k] = (float) w;
            double r = Math.max(startRadius[v], endRadius[v + 1]);
            this.cutoff[k] = (float) Math.min(320.0, r + 1.0 + f + (Math.max(above[v], above[v + 1]) + 8.0) / w + 24.0);
            this.bank[k] = (float) bankWidth(r);
            byte flags = 0;
            if (reach.lake[v] >= 0 && reach.lake[v + 1] >= 0) {
                flags |= IN_LAKE;
            }
            if (width >= 6.0) {
                flags |= RIVER_BIOME;
            }
            if (startRadius[v] > reach.r[v] + 1e-6 || endRadius[v + 1] > reach.r[v + 1] + 1e-6) {
                flags |= POOL;
            }
            this.flags[k] = flags;
            k++;
        }
        return k;
    }

    private static int upstreamVertex(Map<Integer, Integer> lines, int index) {
        for (Map.Entry<Integer, Integer> line : lines.entrySet()) {
            if (line.getValue() == index) {
                return line.getKey();
            }
        }
        return -1;
    }

    /**
     * Which side of fall {@code i}'s lip a point lies on: negative upstream, positive downstream.
     * The lip is a line across the river through the fall, bowed upstream by up to
     * {@code bow × r} in the middle (a horseshoe at tall falls) and ragged by up to {@code jag}
     * blocks (two octaves of value noise along it). Beyond the river and its banks it curves
     * downstream, so the valley below ends in a rounded head (an amphitheatre below a tall fall,
     * a curved terrace edge beside a ledge) instead of a straight cut across the valley.
     */
    double fallSide(int i, double x, double z) {
        double px = x - this.fallX[i];
        double pz = z - this.fallZ[i];
        double nx = this.fallNx[i];
        double nz = this.fallNz[i];
        double along = px * nx + pz * nz;
        double across = pz * nx - px * nz;
        return along - lipOffset(across, this.fallR[i], this.fallBow[i], this.fallJag[i], this.fallSeed[i]);
    }

    private static double side(Fall fall, double x, double z) {
        double px = x - fall.x();
        double pz = z - fall.z();
        double across = pz * fall.nx() - px * fall.nz();
        return px * fall.nx() + pz * fall.nz() - lipOffset(across, fall.r(), fall.bow(), fall.jag(), fall.seed());
    }

    private double lipOffset(Fall fall, double across) {
        return lipOffset(across, fall.r(), fall.bow(), fall.jag(), fall.seed());
    }

    /** Distance of the lip downstream of the fall point at a lateral offset (negative: upstream). */
    private static double lipOffset(double across, double r, double bow, double jag, long seed) {
        double u = across / r;
        double shape;
        double ragged = jag;
        if (u * u < 1.0) {
            shape = -bow * r * (1.0 - u * u);
        } else {
            double beyond = Math.abs(across) - r;
            shape = 2.0 * Math.abs(bow) * beyond + beyond * beyond / (1.4 * r + 8.0);
            ragged += Math.min(8.0, 0.1 * beyond);
        }
        return shape + ragged * (0.7 * valueNoise(across / 5.0, seed) + 0.3 * valueNoise(across / 2.2, seed + 1));
    }

    /** Smooth 1D value noise in [-1, 1]. */
    static double valueNoise(double t, long seed) {
        long i = (long) Math.floor(t);
        double f = t - i;
        double a = 2.0 * unit(seed, i) - 1.0;
        double b = 2.0 * unit(seed, i + 1) - 1.0;
        double s = f * f * (3.0 - 2.0 * f);
        return a + (b - a) * s;
    }

    /** Uniform value in [0, 1) for a seed and a channel. */
    static double unit(long seed, long channel) {
        return (SimplexNoise.mix(seed ^ channel * 0x9E3779B97F4A7C15L ^ 0x632BE59BD9B4E019L) >>> 11) * 0x1.0p-53;
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
        return "BasinRivers[" + this.segments + " segments, falls " + this.falls + ", rocks " + this.rocks + "]";
    }
}
