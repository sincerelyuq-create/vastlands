package com.sincerelyuq.vastlands.worldgen.caves;

import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.clamp;
import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.smoothstep;

import com.sincerelyuq.vastlands.worldgen.geography.GeographyModel;
import com.sincerelyuq.vastlands.worldgen.geography.GeographySample;
import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The cave network: tunnels, chambers, shafts, ramps and entrances on a deterministic graph.
 *
 * <p>Nodes sit on a jittered lattice of {@value #CELL}-block cells; every lattice node exists on
 * every <em>level</em>, so levels stack like the passages of real multi-level caves (each level
 * formed at an old water table). Levels:
 * <ul>
 *     <li>nine upper levels at fixed heights relative to the regional base level of the geography
 *     (where valleys cut down to): from 116 below it to 180 above it, so plains have two to four
 *     levels above the aquitard and mountains up to nine, stacked under their peaks;</li>
 *     <li>a coastal level around Y 57 near coasts: sea caves half under water;</li>
 *     <li>three deep levels around Y -104, -152 and -200, below the aquitard.</li>
 * </ul>
 * Each level's heights undulate over ~320 blocks and its development (how many of its nodes and
 * tunnels exist) follows its own 1 300-block field, so one region has a rich middle level and
 * another only a deep one. A node is active on a level if that level lies inside the rock there
 * (below the surface with a cover of rock, above or below the aquitard) and a hash says so, with a
 * chance from the region's cave density (labyrinths of interconnected passages in dense rock,
 * scattered tunnels in quiet regions); a tunnel joins two active neighbours (the four lattice
 * neighbours and one diagonal per quad, so tunnels never cross) with a chance from the density.
 *
 * <p>Tunnel radii follow a log-normal distribution around a regional median (larger in karst and
 * in regions of large passages): most tunnels are 6..16 blocks wide, a tail reaches 25..30, and
 * one in eight is a squeeze. Tunnels are quadratic curves (curvature and a vertical bump per
 * tunnel) with the radius varying along them; karst gets wide low phreatic tubes, fractured rock
 * tall rifts, large tunnels often flat breakdown floors, some upper ones a canyon slot cut into
 * the floor by a stream. Ramps join a level to the next one below at a neighbouring node: steep
 * passages between levels. Junctions with two or more tunnels may be chambers (halls among them),
 * and junctions of three or more are widened into rooms sized by their widest tunnel; a node may
 * hold a shaft or a pit (flaring at its top) down to the next level below, sometimes past it; the
 * highest level of a node near the surface may open to it: an adit into a hillside or cliff
 * (downhill, sometimes a very large mouth), a sea cave mouth on a coast or a submerged cliff, a
 * doline in karst, or a blue hole (a flooded sinkhole) in the sea floor.
 *
 * <p>In the luminous provinces of the middle and deep caves ({@link CaveBiomes#luminous}, only
 * when {@code CaveSettings.luminousCaves} is set: a luminous cave biome is installed) the network
 * is roomier (Agent 8): more nodes, tunnels a third wider and taller, twice as many chambers, a
 * third larger, more of them halls, and some with a second lobe, so the bioluminescent depths
 * open into the wide, irregular spaces their mushroom groves fill.
 *
 * <p>Everything is a pure function of the seed and lattice coordinates (nodes read the geography
 * and the generated ground at their own position), so neighbouring chunks build the same graph.
 * Cells are cached per thread. No piece reaches more than {@value #REACH_CELLS} cells beyond its
 * own cell, so the cells around a column hold every piece reaching it.
 */
final class CaveNetwork {
    static final double CELL = 64.0;
    /** Rock left between ordinary caves and the surface (blocks). */
    static final double COVER = 6.0;
    private static final double[] UPPER_OFFSET = {-116.0, -88.0, -60.0, -32.0, -8.0, 22.0, 62.0, 115.0, 180.0};
    private static final int UPPER_LEVELS = UPPER_OFFSET.length;
    static final int COASTAL = UPPER_LEVELS;
    private static final double[] DEEP_ANCHOR = {-104.0, -152.0, -200.0};
    static final int LEVELS = UPPER_LEVELS + 1 + DEEP_ANCHOR.length;
    /** Longest entrance adit (blocks), so cells within two of a column hold every piece reaching it. */
    private static final double MAX_ADIT = 1.5 * CELL;
    /** Cells around a column that are searched for pieces. */
    static final int REACH_CELLS = 2;
    /** Largest chamber radius: its bounds stay within {@link #REACH_CELLS} cells of its node. */
    private static final double MAX_CHAMBER = 58.0;

    interface Ground {
        /** The generated ground surface (terrain with river channels). */
        double surface(double x, double z);

        /**
         * The terrain surface before river channels, uncached: for planning far from the column
         * being generated (megacaves, galleries), where the exact ground would compute the drainage
         * of distant regions. Columns still keep their cover under the exact ground.
         */
        default double estimate(double x, double z) {
            return this.surface(x, z);
        }
    }

    private static final class Node {
        final double x, z, surface, slopeX, slopeZ;
        double aquitard;
        final CaveGeology.Geology geology;
        /** Height of the node on each level, NaN where inactive. */
        final double[] y = new double[LEVELS];
        /** The node lies in a luminous province on each level (expanded settings only). */
        final boolean[] luminous = new boolean[LEVELS];

        Node(double x, double z, double surface, double slopeX, double slopeZ, CaveGeology.Geology geology) {
            this.x = x;
            this.z = z;
            this.surface = surface;
            this.slopeX = slopeX;
            this.slopeZ = slopeZ;
            this.geology = geology;
        }

        boolean active(int level) {
            return this.y[level] == this.y[level];
        }
    }

    static final class Cell {
        final long i, j;
        final CavePrimitive[] primitives;
        final double minX, maxX, minZ, maxZ;

        Cell(long i, long j, List<CavePrimitive> primitives) {
            this.i = i;
            this.j = j;
            this.primitives = primitives.toArray(new CavePrimitive[0]);
            double x0 = Double.POSITIVE_INFINITY, x1 = Double.NEGATIVE_INFINITY, z0 = Double.POSITIVE_INFINITY, z1 = Double.NEGATIVE_INFINITY;
            for (CavePrimitive p : this.primitives) {
                x0 = Math.min(x0, p.minX);
                x1 = Math.max(x1, p.maxX);
                z0 = Math.min(z0, p.minZ);
                z1 = Math.max(z1, p.maxZ);
            }
            this.minX = x0;
            this.maxX = x1;
            this.minZ = z0;
            this.maxZ = z1;
        }
    }

    private final long seed;
    private final GeographyModel geography;
    private final CaveGeology geology;
    private final FluidDomains domains;
    private final CaveSettings settings;
    private final Ground ground;
    private final int seaTop;
    /** The cave biomes, for the roomier luminous provinces; null when they are not expanded. */
    private final CaveBiomes luminous;
    private final SimplexNoise undulation;
    private final SimplexNoise development;
    private final ThreadLocal<KeyedCache<Node>> nodes = ThreadLocal.withInitial(() -> new KeyedCache<>(1024));
    private final ThreadLocal<KeyedCache<Cell>> cells = ThreadLocal.withInitial(() -> new KeyedCache<>(512));

    CaveNetwork(long seed, GeographyModel geography, CaveGeology geology, FluidDomains domains, CaveSettings settings, Ground ground, int seaTop,
                CaveBiomes luminous) {
        this.seed = SimplexNoise.mix(seed ^ 0x4E4554574F524BL);
        this.geography = geography;
        this.geology = geology;
        this.domains = domains;
        this.settings = settings;
        this.ground = ground;
        this.seaTop = seaTop;
        this.luminous = luminous;
        this.undulation = new SimplexNoise(seed + 301);
        this.development = new SimplexNoise(seed + 302);
    }

    /** Every primitive whose bounds contain the column. */
    void gather(int x, int z, Consumer<CavePrimitive> sink) {
        long ci = (long) Math.floor(x / CELL);
        long cj = (long) Math.floor(z / CELL);
        for (long j = cj - REACH_CELLS; j <= cj + REACH_CELLS; j++) {
            for (long i = ci - REACH_CELLS; i <= ci + REACH_CELLS; i++) {
                Cell cell = this.cell(i, j);
                if (x < cell.minX || x > cell.maxX || z < cell.minZ || z > cell.maxZ) {
                    continue;
                }
                for (CavePrimitive p : cell.primitives) {
                    if (p.covers(x, z) && p.reaches(x, z)) {
                        sink.accept(p);
                    }
                }
            }
        }
    }

    /** The cell for diagnostics and tests. */
    Cell cell(long i, long j) {
        return this.cells.get().get(i << 32 ^ (j & 0xFFFFFFFFL), k -> this.buildCell(i, j));
    }

    private Node node(long i, long j) {
        return this.nodes.get().get(i << 32 ^ (j & 0xFFFFFFFFL), k -> this.buildNode(i, j));
    }

    private Node buildNode(long i, long j) {
        long h = this.hash(i, j, 0);
        double x = (i + 0.2 + 0.6 * unit(h, 0)) * CELL;
        double z = (j + 0.2 + 0.6 * unit(h, 1)) * CELL;
        double surface = this.ground.surface(x, z);
        double slopeX = (this.ground.surface(x + 8.0, z) - this.ground.surface(x - 8.0, z)) / 16.0;
        double slopeZ = (this.ground.surface(x, z + 8.0) - this.ground.surface(x, z - 8.0)) / 16.0;
        GeographySample g = this.geography.sample(x, z);
        CaveGeology.Geology geo = this.geology.at(x, z, g);
        Node node = new Node(x, z, surface, slopeX, slopeZ, geo);
        double aquitard = this.domains.aquitard(x, z);
        node.aquitard = aquitard;
        double base = clamp(g.baseHeight(), -60.0, 170.0);
        double room = surface - COVER - 6.0;
        for (int k = 0; k < LEVELS; k++) {
            double strength = smoothstep(-0.45, 0.3, this.development.sample(x / 1300.0 + 31.7 * k, z / 1300.0 - 7.3 * k));
            double wave = this.undulation.sample(x / 320.0 + 17.1 * k, z / 320.0 - 11.3 * k);
            double jitter = 8.0 * unit(h, 10 + k) - 4.0;
            double y;
            boolean valid;
            if (k < UPPER_LEVELS) {
                y = base + UPPER_OFFSET[k] + 9.0 * wave + jitter;
                valid = y >= aquitard + FluidDomains.AQUITARD_HALF + 10.0 && y <= room;
            } else if (k == COASTAL) {
                y = 57.0 + 4.0 * wave + 0.5 * jitter;
                strength *= smoothstep(0.15, 0.6, geo.coast());
                valid = y <= room && y >= aquitard + FluidDomains.AQUITARD_HALF + 10.0;
            } else {
                y = DEEP_ANCHOR[k - COASTAL - 1] + 14.0 * wave + jitter;
                valid = y <= aquitard - FluidDomains.AQUITARD_HALF - 10.0 && y >= -232.0 && y <= room;
            }
            double presence = clamp((0.12 + 0.75 * Math.sqrt(geo.density()) * (0.45 + 0.55 * strength)) * this.settings.frequency(), 0.0, 0.96);
            if (valid && this.luminous != null && k != COASTAL
                    && this.luminous.luminous((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z), surface)) {
                node.luminous[k] = true;
                presence = Math.min(0.96, presence * (1.0 + 0.3 * this.settings.luminousCaves()));
            }
            if (valid && unit(h, 30 + k) < presence) {
                node.y[k] = y;
            } else {
                node.y[k] = Double.NaN;
            }
        }
        return node;
    }

    /** Whether the tunnel between two nodes exists on a level (cheap: no geometry). */
    private boolean linked(Node u, Node v, int level, long edgeKey, boolean diagonal) {
        if (!u.active(level) || !v.active(level)) {
            return false;
        }
        double density = 0.5 * (u.geology.density() + v.geology.density());
        double p = clamp(0.22 + 0.5 * density * this.settings.density(), 0.05, 0.95) * (diagonal ? 0.5 : 1.0);
        if (level > COASTAL) {
            p *= 0.75;
        }
        return unit(SimplexNoise.mix(edgeKey), level) < p;
    }

    private long edgeKey(long i, long j, int type) {
        return this.hash(i, j, 100 + type);
    }

    /** The diagonal of quad (i, j): true joins (i, j)-(i+1, j+1), false (i+1, j)-(i, j+1). */
    private boolean mainDiagonal(long i, long j) {
        return unit(this.hash(i, j, 7), 0) < 0.5;
    }

    private Cell buildCell(long i, long j) {
        List<CavePrimitive> out = new ArrayList<>();
        Node n00 = this.node(i, j);
        Node n10 = this.node(i + 1, j);
        Node n01 = this.node(i, j + 1);
        Node n11 = this.node(i + 1, j + 1);
        boolean main = this.mainDiagonal(i, j);
        for (int k = 0; k < LEVELS; k++) {
            if (this.linked(n00, n10, k, this.edgeKey(i, j, 0), false)) {
                this.tunnel(n00, n10, k, k, this.edgeKey(i, j, 0), out);
            }
            if (this.linked(n00, n01, k, this.edgeKey(i, j, 1), false)) {
                this.tunnel(n00, n01, k, k, this.edgeKey(i, j, 1), out);
            }
            Node d0 = main ? n00 : n10;
            Node d1 = main ? n11 : n01;
            if (this.linked(d0, d1, k, this.edgeKey(i, j, 2), true)) {
                this.tunnel(d0, d1, k, k, this.edgeKey(i, j, 2), out);
            }
            this.ramp(n00, n10, k, this.edgeKey(i, j, 3), out);
            this.ramp(n00, n01, k, this.edgeKey(i, j, 4), out);
        }
        this.features(i, j, n00, out);
        return new Cell(i, j, out);
    }

    /**
     * A ramp: a steep passage from a node's level down to the next active level of a neighbour
     * (either way along the edge), so systems climb and drop between levels instead of only
     * meeting at shafts.
     */
    private void ramp(Node a, Node b, int level, long key, List<CavePrimitive> out) {
        if (level == COASTAL) {
            return;
        }
        long h = SimplexNoise.mix(key + level * 0x2545F4914F6CDD1DL);
        boolean flip = unit(h, 0) < 0.5;
        Node u = flip ? b : a;
        Node v = flip ? a : b;
        if (!u.active(level)) {
            return;
        }
        double vertical = 0.5 * (u.geology.vertical() + v.geology.vertical());
        double chance = 0.16 * this.settings.vertical() * (0.35 + vertical) * Math.min(1.4, 0.5 + 0.5 * u.geology.density());
        if (unit(h, 1) >= chance) {
            return;
        }
        // The next active level of the neighbour below this one, on the same side of the aquitard.
        int lower = -1;
        for (int k = 0; k < LEVELS; k++) {
            if (k != level && k != COASTAL && v.active(k) && v.y[k] < u.y[level] && (lower < 0 || v.y[k] > v.y[lower])
                    && (k > COASTAL) == (level > COASTAL)) {
                lower = k;
            }
        }
        if (lower < 0) {
            return;
        }
        double drop = u.y[level] - v.y[lower];
        if (drop < 10.0 || drop > 80.0) {
            return;
        }
        this.tunnel(u, v, level, lower, key ^ 0x52414D50L, out);
    }

    /** Number of tunnels meeting at node (i, j) on a level and the widest of them (radius in {@code widest[0]}). */
    private int incident(long i, long j, Node n, int level, double[] widest) {
        int degree = 0;
        double best = 0.0;
        Node e = this.node(i + 1, j);
        if (this.linked(n, e, level, this.edgeKey(i, j, 0), false)) {
            degree++;
            best = Math.max(best, this.radius(n, e, level, this.edgeKey(i, j, 0)));
        }
        Node s = this.node(i, j + 1);
        if (this.linked(n, s, level, this.edgeKey(i, j, 1), false)) {
            degree++;
            best = Math.max(best, this.radius(n, s, level, this.edgeKey(i, j, 1)));
        }
        Node w = this.node(i - 1, j);
        if (this.linked(w, n, level, this.edgeKey(i - 1, j, 0), false)) {
            degree++;
            best = Math.max(best, this.radius(w, n, level, this.edgeKey(i - 1, j, 0)));
        }
        Node north = this.node(i, j - 1);
        if (this.linked(north, n, level, this.edgeKey(i, j - 1, 1), false)) {
            degree++;
            best = Math.max(best, this.radius(north, n, level, this.edgeKey(i, j - 1, 1)));
        }
        if (this.mainDiagonal(i, j)) {
            Node d = this.node(i + 1, j + 1);
            if (this.linked(n, d, level, this.edgeKey(i, j, 2), true)) {
                degree++;
                best = Math.max(best, this.radius(n, d, level, this.edgeKey(i, j, 2)));
            }
        }
        if (this.mainDiagonal(i - 1, j - 1)) {
            Node d = this.node(i - 1, j - 1);
            if (this.linked(d, n, level, this.edgeKey(i - 1, j - 1, 2), true)) {
                degree++;
                best = Math.max(best, this.radius(d, n, level, this.edgeKey(i - 1, j - 1, 2)));
            }
        }
        if (!this.mainDiagonal(i - 1, j)) {
            Node d = this.node(i - 1, j + 1);
            if (this.linked(n, d, level, this.edgeKey(i - 1, j, 2), true)) {
                degree++;
                best = Math.max(best, this.radius(n, d, level, this.edgeKey(i - 1, j, 2)));
            }
        }
        if (!this.mainDiagonal(i, j - 1)) {
            Node d = this.node(i + 1, j - 1);
            if (this.linked(d, n, level, this.edgeKey(i, j - 1, 2), true)) {
                degree++;
                best = Math.max(best, this.radius(d, n, level, this.edgeKey(i, j - 1, 2)));
            }
        }
        widest[0] = best;
        return degree;
    }

    /**
     * Radius of the tunnel of an edge on a level: log-normal around a median from the rock (karst
     * and fractured rock wider, deep levels wider still) and the region's passage scale, with one in
     * eight a squeeze. Deterministic from the edge, so a node knows its widest tunnel.
     */
    private double radius(Node u, Node v, int level, long key) {
        long h = SimplexNoise.mix(key + level * 0x632BE59BD9B4E019L);
        double karst = 0.5 * (u.geology.karst() + v.geology.karst());
        double fracture = 0.5 * (u.geology.fracture() + v.geology.fracture());
        double volcanic = 0.5 * (u.geology.volcanic() + v.geology.volcanic());
        double scale = 0.5 * (u.geology.scale() + v.geology.scale());
        double base;
        if (level > COASTAL) {
            base = 4.4 + 2.0 * volcanic + 1.2 * karst;
        } else if (level == COASTAL) {
            base = 3.8;
        } else {
            base = 3.8 + 2.0 * karst + 0.7 * fracture;
        }
        double gauss = 2.0 * (unit(h, 3) + unit(h, 12) + unit(h, 13) - 1.5);
        double radius = base * scale * this.settings.passageWidth() * Math.exp(0.4 * gauss);
        if (unit(h, 5) < 0.12) {
            radius *= 0.45;
        }
        double roomier = this.roomier(u, v, level);
        return clamp(radius * (1.0 + 0.3 * roomier), 1.6, 16.0 + 4.0 * roomier);
    }

    /** 0, or the luminous expansion where both ends of a tunnel lie in a luminous province on its level. */
    private double roomier(Node u, Node v, int level) {
        return u.luminous[level] && v.luminous[level] ? this.settings.luminousCaves() : 0.0;
    }

    /** A tunnel from node u on a level to node v on {@code toLevel} (the same level, or a lower one for a ramp). */
    private void tunnel(Node u, Node v, int level, int toLevel, long key, List<CavePrimitive> out) {
        long h = SimplexNoise.mix(key + level * 0x632BE59BD9B4E019L);
        double karst = 0.5 * (u.geology.karst() + v.geology.karst());
        double fracture = 0.5 * (u.geology.fracture() + v.geology.fracture());
        double volcanic = 0.5 * (u.geology.volcanic() + v.geology.volcanic());
        boolean deep = level > COASTAL;
        boolean ramp = toLevel != level;
        double y0 = u.y[level];
        double y1 = v.y[toLevel];
        double dx = v.x - u.x;
        double dz = v.z - u.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        double px = -dz / len;
        double pz = dx / len;
        double bend = (unit(h, 1) - 0.5) * len * (0.45 + 0.4 * karst);
        double bump = ramp ? 0.0 : (unit(h, 2) - 0.5) * 2.0 * Math.min(9.0, 0.12 * len);
        double cx = 0.5 * (u.x + v.x) + px * bend;
        double cz = 0.5 * (u.z + v.z) + pz * bend;
        double cy = 0.5 * (y0 + y1) + bump;
        double radius = this.radius(u, v, level, key);
        double shape;
        if (deep) {
            shape = volcanic > 0.4 ? 0.85 : 0.55 + 0.3 * unit(h, 4);
        } else if (level == COASTAL) {
            shape = 0.65 + 0.2 * unit(h, 4);
        } else {
            double tall = fracture * (1.0 - karst);
            // Wide tunnels are lower for their width: phreatic tubes and breakdown passages.
            shape = (0.6 + 0.65 * tall) * (0.85 + 0.3 * unit(h, 4)) * (radius > 8.0 ? 0.85 : 1.0);
        }
        if (ramp) {
            radius *= 0.8;
            shape = Math.max(shape, 0.9);
        } else {
            shape *= 1.0 + 0.15 * this.roomier(u, v, level);
        }
        // The tunnel stays inside the rock: its middle (the curve's apex) must be covered too.
        double mx = 0.25 * u.x + 0.5 * cx + 0.25 * v.x;
        double mz = 0.25 * u.z + 0.5 * cz + 0.25 * v.z;
        double my = 0.25 * y0 + 0.5 * cy + 0.25 * y1;
        if (my + radius * shape + COVER + 2.0 > this.ground.surface(mx, mz)) {
            return;
        }
        double slotDepth = 0.0;
        double slotWidth = 0.0;
        if (!deep && !ramp && unit(h, 6) < 0.45 * Math.max(karst, fracture)) {
            slotDepth = 4.0 + 10.0 * unit(h, 7);
            slotWidth = 0.8 + 0.9 * unit(h, 8) + 0.08 * radius;
        }
        double flat = radius > 7.0 ? 0.55 : 0.3;
        double floorCut = !ramp && unit(h, 9) < flat ? 0.45 + 0.35 * unit(h, 10) : 1.0;
        double waves = Math.max(1.0, Math.round(len / 40.0));
        double phase = 6.2831853 * unit(h, 11);
        double roughness = 0.9 + 0.12 * radius;
        double pxPrev = u.x, pyPrev = y0, pzPrev = u.z;
        double rPrev = radius * (1.0 + 0.25 * Math.sin(phase));
        for (int s = 1; s <= 3; s++) {
            double t = s / 3.0;
            double a = (1 - t) * (1 - t);
            double b = 2 * t * (1 - t);
            double c = t * t;
            double qx = a * u.x + b * cx + c * v.x;
            double qy = a * y0 + b * cy + c * y1;
            double qz = a * u.z + b * cz + c * v.z;
            double r = radius * (1.0 + 0.3 * Math.sin(phase + 6.2831853 * waves * t));
            out.add(CavePrimitive.segment(pxPrev, pyPrev, pzPrev, qx, qy, qz, rPrev, r, shape, slotDepth, slotWidth, floorCut, roughness, h + s));
            pxPrev = qx;
            pyPrev = qy;
            pzPrev = qz;
            rPrev = r;
        }
    }

    private void features(long i, long j, Node n, List<CavePrimitive> out) {
        long h = this.hash(i, j, 1);
        CaveGeology.Geology geo = n.geology;
        int top = -1;
        int[] degrees = new int[LEVELS];
        double[] widest = new double[LEVELS];
        double[] scratch = new double[1];
        for (int k = 0; k < LEVELS; k++) {
            if (!n.active(k)) {
                continue;
            }
            degrees[k] = this.incident(i, j, n, k, scratch);
            widest[k] = scratch[0];
            if (degrees[k] > 0 && (top < 0 || n.y[k] > n.y[top])) {
                top = k;
            }
        }
        for (int k = 0; k < LEVELS; k++) {
            if (degrees[k] == 0) {
                continue;
            }
            boolean deep = k > COASTAL;
            double y = n.y[k];
            // Under the cover of rock, and on its own side of the aquitard.
            double ceiling = deep ? n.aquitard - FluidDomains.AQUITARD_HALF - 4.0 : n.surface - COVER - 4.0;
            double floor = deep ? -238.0 : n.aquitard + FluidDomains.AQUITARD_HALF + 4.0;
            double roomier = n.luminous[k] ? this.settings.luminousCaves() : 0.0;
            double chamberChance = (0.14 + 0.16 * geo.karst() + 0.05 * geo.fracture()) * this.settings.chambers() * (deep ? 1.3 : 1.0)
                    * (1.0 + 1.2 * roomier);
            if (degrees[k] >= 2 && unit(h, 20 + k) < chamberChance) {
                double u = unit(h, 40 + k);
                double rh = (deep ? 12.0 + 30.0 * Math.pow(u, 1.5) : 10.0 + 26.0 * Math.pow(u, 1.5)) * geo.scale() * (1.0 + 0.3 * roomier);
                double rv = rh * (0.4 + 0.3 * unit(h, 60 + k));
                if (unit(h, 70 + k) < 0.1 + 0.18 * roomier) {
                    // A hall: much larger, long and low.
                    rh *= 1.8;
                    rv *= 1.25;
                }
                rh = Math.min(rh, MAX_CHAMBER);
                rv = Math.min(rv, 0.8 * rh);
                double floorDepth = 0.45 + 0.25 * unit(h, 80 + k);
                double centre = y + 0.3 * rv;
                rv = Math.min(rv, Math.min((ceiling - centre) / 1.05, (centre - floor) / (floorDepth + 0.15)));
                if (rv >= 4.0) {
                    double angle = Math.PI * unit(h, 90 + k);
                    double elongation = 0.1 + 0.35 * unit(h, 95 + k);
                    out.add(CavePrimitive.chamber(n.x, centre, n.z, rh, rv, floorDepth, angle, elongation, 1.4 + 0.07 * rh, h + 200 + k));
                    if (roomier > 0.0 && unit(h, 140 + k) < 0.45 * roomier) {
                        // A second lobe off one end of the long axis, lower or higher: an irregular
                        // hall of two bays instead of one oval. Within 0.6 of the radius, so it stays
                        // inside the reach of the node's cells.
                        double side = unit(h, 141 + k) < 0.5 ? -1.0 : 1.0;
                        double offset = rh * (0.4 + 0.2 * unit(h, 142 + k)) * side;
                        double turn = angle + (unit(h, 143 + k) - 0.5) * 1.2;
                        double lobe = rh * (0.4 + 0.2 * unit(h, 144 + k));
                        double lobeRv = Math.min(rv * (0.6 + 0.35 * unit(h, 145 + k)), 0.8 * lobe);
                        double lobeCentre = centre + (unit(h, 146 + k) - 0.5) * 0.6 * rv;
                        lobeRv = Math.min(lobeRv, Math.min((ceiling - lobeCentre) / 1.05, (lobeCentre - floor) / (floorDepth + 0.15)));
                        if (lobeRv >= 4.0) {
                            out.add(CavePrimitive.chamber(n.x + Math.cos(turn) * offset, lobeCentre, n.z + Math.sin(turn) * offset, lobe, lobeRv, floorDepth,
                                    turn + 0.6 * (unit(h, 147 + k) - 0.5), 0.1 + 0.3 * unit(h, 148 + k), 1.4 + 0.07 * lobe, h + 400 + k));
                        }
                    }
                }
            } else if (degrees[k] >= 3 && unit(h, 25 + k) < 0.5 * this.settings.chambers()) {
                // A widened junction: a room a little wider than the widest tunnel meeting here.
                double rh = Math.min(MAX_CHAMBER, 1.45 * widest[k] + 3.0 + 3.0 * unit(h, 26 + k));
                double rv = Math.min(0.75 * rh, 0.9 * widest[k] + 3.0);
                double floorDepth = 0.55;
                double centre = y + 0.15 * rv;
                rv = Math.min(rv, Math.min((ceiling - centre) / 1.05, (centre - floor) / (floorDepth + 0.15)));
                if (rv >= 3.0) {
                    out.add(CavePrimitive.chamber(n.x, centre, n.z, rh, rv, floorDepth, Math.PI * unit(h, 27 + k), 0.05 + 0.2 * unit(h, 28 + k),
                            1.2 + 0.08 * rh, h + 250 + k));
                }
            }
            double shaftChance = (0.07 + 0.2 * geo.fracture() + 0.1 * geo.karst()) * this.settings.vertical() * (0.5 + geo.vertical());
            if (unit(h, 100 + k) < shaftChance) {
                // The next level below this one at the node with a tunnel of its own, sometimes the one after it.
                int skip = unit(h, 110 + k) < 0.25 ? 1 : 0;
                int target = -1;
                double last = y;
                for (int step = 0; step <= skip; step++) {
                    int next = -1;
                    for (int q = 0; q < LEVELS; q++) {
                        if (q != k && degrees[q] > 0 && n.y[q] < last && (next < 0 || n.y[q] > n.y[next])) {
                            next = q;
                        }
                    }
                    if (next < 0) {
                        break;
                    }
                    target = next;
                    last = n.y[next];
                }
                if (target >= 0) {
                    double u = unit(h, 120 + k);
                    double gauss = 2.0 * (u + unit(h, 121 + k) + unit(h, 122 + k) - 1.5);
                    double r = clamp(2.6 * Math.exp(0.45 * gauss) * Math.sqrt(geo.scale()), 1.8, 9.0);
                    double rTop = r;
                    if (unit(h, 123 + k) < 0.12 + 0.15 * geo.karst()) {
                        // A pit: wide at the top, narrowing down.
                        rTop = Math.min(16.0, r * (1.8 + 1.2 * unit(h, 124 + k)));
                    }
                    out.add(CavePrimitive.segment(n.x, y + 1.0, n.z, n.x, n.y[target] - 1.0, n.z, rTop, r * 1.1, 1.0, 0.0, 0.0, 1.0, 1.0 + 0.1 * r,
                            h + 300 + k));
                }
            }
        }
        if (top >= 0) {
            this.entrance(n, top, h, out);
        }
    }

    private void entrance(Node n, int level, long h, List<CavePrimitive> out) {
        CaveGeology.Geology geo = n.geology;
        double y = n.y[level];
        double depth = n.surface - y;
        double slope = Math.sqrt(n.slopeX * n.slopeX + n.slopeZ * n.slopeZ);
        boolean underwater = n.surface < this.seaTop - 2;
        boolean coastal = geo.coast() > 0.25 || underwater;
        double u = unit(h, 500);
        if (coastal && slope >= 0.03) {
            // Sea caves: rising seaward (about 1 in 4) to meet the shore or the sea floor, so gentle
            // coasts get mouths too, half or wholly under water.
            double grade = 0.25;
            double length = depth / (grade + slope) * 1.25 + 8.0;
            double chance = (underwater ? 0.22 : 0.32) * this.settings.seaCaves();
            if (length <= MAX_ADIT && u < chance) {
                boolean large = unit(h, 501) < 0.3;
                double r = large ? 8.0 + 9.0 * unit(h, 502) : 3.5 + 4.0 * unit(h, 502);
                double ex = n.x - n.slopeX / slope * length;
                double ez = n.z - n.slopeZ / slope * length;
                out.add(CavePrimitive.entrance(n.x, y, n.z, ex, y + grade * length, ez, r, r * 1.7, 0.8, 1.2 + 0.1 * r, h + 503));
                return;
            }
        }
        if (slope >= 0.28 && !underwater) {
            double length = depth / slope * 1.3 + 14.0;
            double chance = 0.4 * this.settings.entrances() * (0.6 + 0.6 * geo.karst() + 0.3 * geo.fracture()) * (slope > 0.9 ? 1.5 : 1.0);
            if (length <= MAX_ADIT && u < chance) {
                boolean large = unit(h, 501) < 0.15;
                double r = large ? 9.0 + 9.0 * unit(h, 502) : 3.0 + 3.5 * unit(h, 502);
                double ex = n.x - n.slopeX / slope * length;
                double ez = n.z - n.slopeZ / slope * length;
                // Rising slightly towards the mouth, so the floor drains out of the hill.
                double ey = y + 0.03 * length;
                out.add(CavePrimitive.entrance(n.x, y, n.z, ex, ey, ez, r, r * 1.7, 0.8, 1.2 + 0.1 * r, h + 503));
            }
            return;
        }
        if (underwater) {
            // Blue holes: flooded sinkholes opening into the sea floor (shelf and slopes).
            if (depth <= 50.0 && u < 0.07 * this.settings.seaCaves() * (0.5 + geo.karst())) {
                double r = 3.0 + 5.0 * unit(h, 520);
                out.add(CavePrimitive.funnel(n.x, y - 1.0, n.z, n.surface + 4.0, r, n.surface, 1.2 + 0.1 * r, h + 521));
            }
            return;
        }
        if (slope < 0.22 && depth <= 55.0 && geo.karst() > 0.3
                && u < 0.12 * this.settings.entrances() * geo.karst()) {
            double r = 3.0 + 5.0 * unit(h, 510);
            out.add(CavePrimitive.funnel(n.x, y - 1.0, n.z, n.surface + 4.0, r, n.surface, 1.2 + 0.1 * r, h + 511));
        }
    }

    private long hash(long i, long j, int salt) {
        return SimplexNoise.mix(this.seed ^ i * 0x9E3779B97F4A7C15L ^ j * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L);
    }

    static double unit(long h, int channel) {
        return FluidDomains.unit(h, channel);
    }
}
