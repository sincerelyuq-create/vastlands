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
 * Galleries: the trunk passages of the caves, much larger and longer than the tunnels of the
 * {@link CaveNetwork}, on their own jittered lattice of {@value #CELL}-block cells and two levels:
 * <ul>
 *     <li>an upper level whose floor lies 18..48 blocks below the regional base level (where
 *     valleys cut down to): below the valley floors, cut by the network's levels around it;</li>
 *     <li>a deep level with its floor at Y -200..-130, under the aquitard.</li>
 * </ul>
 * Where the upper fluid domain at a node is flooded and its water table lies within reach, the
 * gallery floor is set 2..6 blocks under the water: the gallery is an underground river, a long
 * still-water channel under an open vault, falling over spillways where it crosses into a domain
 * of another level.
 *
 * <p>Size classes: large passages (radius 12..30: 25..60 blocks wide), major corridors (radius
 * 30..55: 60..110) and rare colossal ones (55..85), scaled by the region's passage scale. Each
 * gallery is a meandering spline through two displaced control points, its radius changing at
 * every control point (from a third to one and a half of its own) and through a constriction in a
 * third of them, so a gallery alternates between narrow connections and vast halls. Most are
 * phreatic: wide, lower than wide, with flat breakdown floors; in fractured rock some are canyons,
 * narrow and up to three times as tall as wide. Where three galleries meet the junction is a
 * vaulted hall. Gallery nodes near steep ground may open to the surface through a very large
 * mouth.
 *
 * <p>Pure function of the seed and lattice coordinates; cells cached per thread. Pieces reach at
 * most two cells beyond their own cell (meanders are at most {@value #MEANDER} of the edge and radii
 * at most {@value #MAX_RADIUS}).
 */
final class Galleries {
    static final double CELL = 288.0;
    static final int UPPER = 0;
    static final int DEEP = 1;
    private static final int LEVELS = 2;
    private static final double MEANDER = 0.22;
    private static final double MAX_RADIUS = 85.0;
    private static final int SEGMENTS = 9;

    private static final class Node {
        final double x, z, surface, slopeX, slopeZ, aquitard;
        final CaveGeology.Geology geology;
        /** Lowest ground within 64 blocks: the galleries keep their cover under it. */
        final double lowGround;
        /** Floor height of the node on each level, NaN where inactive. */
        final double[] floor = new double[LEVELS];
        /** True where the upper floor lies just under a water table (an underground river). */
        boolean river;

        Node(double x, double z, double surface, double slopeX, double slopeZ, double aquitard, double lowGround, CaveGeology.Geology geology) {
            this.x = x;
            this.z = z;
            this.surface = surface;
            this.slopeX = slopeX;
            this.slopeZ = slopeZ;
            this.aquitard = aquitard;
            this.lowGround = lowGround;
            this.geology = geology;
        }

        boolean active(int level) {
            return this.floor[level] == this.floor[level];
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
    private final CaveNetwork.Ground ground;
    private final SimplexNoise development;
    private final SimplexNoise undulation;
    private final ThreadLocal<KeyedCache<Node>> nodes = ThreadLocal.withInitial(() -> new KeyedCache<>(512));
    private final ThreadLocal<KeyedCache<Cell>> cells = ThreadLocal.withInitial(() -> new KeyedCache<>(256));

    Galleries(long seed, GeographyModel geography, CaveGeology geology, FluidDomains domains, CaveSettings settings, CaveNetwork.Ground ground) {
        this.seed = SimplexNoise.mix(seed ^ 0x47414C4C45525953L);
        this.geography = geography;
        this.geology = geology;
        this.domains = domains;
        this.settings = settings;
        this.ground = ground;
        this.development = new SimplexNoise(seed + 701);
        this.undulation = new SimplexNoise(seed + 702);
    }

    /** Every gallery piece whose bounds contain the column. */
    void gather(int x, int z, Consumer<CavePrimitive> sink) {
        long ci = (long) Math.floor(x / CELL);
        long cj = (long) Math.floor(z / CELL);
        for (long j = cj - 2; j <= cj + 2; j++) {
            for (long i = ci - 2; i <= ci + 2; i++) {
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
        double x = (i + 0.15 + 0.7 * unit(h, 0)) * CELL;
        double z = (j + 0.15 + 0.7 * unit(h, 1)) * CELL;
        double surface = this.ground.estimate(x, z);
        double low = surface;
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4.0;
            low = Math.min(low, this.ground.estimate(x + 56.0 * Math.cos(a), z + 56.0 * Math.sin(a)));
        }
        double slopeX = (this.ground.estimate(x + 10.0, z) - this.ground.estimate(x - 10.0, z)) / 20.0;
        double slopeZ = (this.ground.estimate(x, z + 10.0) - this.ground.estimate(x, z - 10.0)) / 20.0;
        GeographySample g = this.geography.sample(x, z);
        CaveGeology.Geology geo = this.geology.at(x, z, g);
        double aquitard = this.domains.aquitard(x, z);
        Node node = new Node(x, z, surface, slopeX, slopeZ, aquitard, low, geo);
        double share = clamp((0.25 + 0.35 * geo.density()) * this.settings.frequency(), 0.0, 0.9) * this.settings.largePassages();
        for (int k = 0; k < LEVELS; k++) {
            double strength = smoothstep(-0.5, 0.35, this.development.sample(x / 1700.0 + 23.1 * k, z / 1700.0 - 5.7 * k));
            double wave = this.undulation.sample(x / 400.0 + 9.3 * k, z / 400.0);
            node.floor[k] = Double.NaN;
            if (unit(h, 10 + k) >= share * (0.35 + 0.9 * strength)) {
                continue;
            }
            double floor;
            double lowest;
            double highest;
            if (k == UPPER) {
                double base = clamp(g.baseHeight(), -40.0, 170.0);
                floor = base - 18.0 - 30.0 * unit(h, 20) + 8.0 * wave;
                lowest = aquitard + FluidDomains.AQUITARD_HALF + 10.0;
                highest = low - CaveNetwork.COVER - 24.0;
                // An underground river where the water table is within reach.
                FluidDomains.Domain domain = this.domains.voronoi(x, z, false);
                if (domain.fluid == CaveFluid.WATER && !domain.sea && unit(h, 21) < 0.6) {
                    double river = domain.level - 2.0 - 4.0 * unit(h, 22);
                    if (river >= lowest && river <= highest) {
                        floor = river;
                        node.river = true;
                    }
                }
            } else {
                floor = -200.0 + 70.0 * unit(h, 20) + 10.0 * wave;
                lowest = -232.0;
                highest = Math.min(aquitard - FluidDomains.AQUITARD_HALF - 40.0, low - 60.0);
            }
            if (floor < lowest) {
                floor = lowest;
            }
            if (floor <= highest) {
                node.floor[k] = floor;
            }
        }
        return node;
    }

    private boolean linked(Node u, Node v, int level, long key, boolean diagonal) {
        if (!u.active(level) || !v.active(level)) {
            return false;
        }
        double density = 0.5 * (u.geology.density() + v.geology.density());
        double p = clamp(0.35 + 0.25 * density * this.settings.density(), 0.1, 0.85) * (diagonal ? 0.45 : 1.0);
        return unit(SimplexNoise.mix(key), level) < p;
    }

    private long edgeKey(long i, long j, int type) {
        return this.hash(i, j, 100 + type);
    }

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
                this.gallery(n00, n10, k, this.edgeKey(i, j, 0), out);
            }
            if (this.linked(n00, n01, k, this.edgeKey(i, j, 1), false)) {
                this.gallery(n00, n01, k, this.edgeKey(i, j, 1), out);
            }
            Node d0 = main ? n00 : n10;
            Node d1 = main ? n11 : n01;
            if (this.linked(d0, d1, k, this.edgeKey(i, j, 2), true)) {
                this.gallery(d0, d1, k, this.edgeKey(i, j, 2), out);
            }
        }
        this.junctions(i, j, n00, out);
        return new Cell(i, j, out);
    }

    /** Size class and cross-section of the gallery of an edge: radius, shape (height over width) and floor cut. */
    private double[] profile(Node u, Node v, int level, long key) {
        long h = SimplexNoise.mix(key + level * 0x9E3779B97F4A7C15L);
        double scale = Math.sqrt(0.5 * (u.geology.scale() + v.geology.scale())) * this.settings.passageWidth();
        double fracture = 0.5 * (u.geology.fracture() + v.geology.fracture());
        double karst = 0.5 * (u.geology.karst() + v.geology.karst());
        double c = unit(h, 1);
        double w = unit(h, 2);
        double radius;
        if (c < 0.03 * this.settings.largePassages()) {
            radius = 55.0 + 30.0 * w;
        } else if (c < 0.27) {
            radius = 30.0 + 25.0 * w;
        } else {
            radius = 12.0 + 18.0 * w * w;
        }
        radius *= scale;
        double shape;
        double floorCut;
        if (level == UPPER && unit(h, 3) < 0.55 * fracture * (1.0 - 0.5 * karst)) {
            // A canyon: narrow and tall.
            radius = Math.max(8.0, 0.4 * radius);
            shape = 1.6 + 1.3 * unit(h, 4);
            floorCut = 0.85;
        } else {
            shape = 0.42 + 0.28 * unit(h, 4);
            floorCut = 0.35 + 0.3 * unit(h, 5);
        }
        return new double[] {clamp(radius, 6.0, MAX_RADIUS), shape, floorCut};
    }

    /** A gallery along an edge: a Catmull-Rom spline through two displaced control points. */
    private void gallery(Node u, Node v, int level, long key, List<CavePrimitive> out) {
        long h = SimplexNoise.mix(key + level * 0x632BE59BD9B4E019L);
        double[] profile = this.profile(u, v, level, key);
        double radius = profile[0];
        double shape = profile[1];
        double floorCut = profile[2];
        double dx = v.x - u.x;
        double dz = v.z - u.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        double px = -dz / len;
        double pz = dx / len;
        // Control points: the two nodes and two meanders between them.
        double[] cx = new double[4];
        double[] cz = new double[4];
        double[] floors = new double[4];
        double[] radii = new double[4];
        cx[0] = u.x;
        cz[0] = u.z;
        cx[3] = v.x;
        cz[3] = v.z;
        floors[0] = u.floor[level];
        floors[3] = v.floor[level];
        int constriction = unit(h, 30) < 0.33 ? 1 + (int) (unit(h, 31) * 2.0) : -1;
        for (int k = 0; k < 4; k++) {
            double gauss = 2.0 * (unit(h, 40 + k) + unit(h, 44 + k) + unit(h, 48 + k) - 1.5);
            radii[k] = radius * clamp(Math.exp(0.3 * gauss), 0.55, 1.5);
        }
        for (int k = 1; k <= 2; k++) {
            double t = k / 3.0;
            double off = (unit(h, 10 + k) - 0.5) * 2.0 * MEANDER * len;
            cx[k] = u.x + dx * t + px * off;
            cz[k] = u.z + dz * t + pz * off;
            double drift = (unit(h, 20 + k) - 0.5) * Math.min(16.0, 0.05 * len);
            floors[k] = floors[0] + (floors[3] - floors[0]) * t + (u.river && v.river ? 0.0 : drift);
            if (k == constriction) {
                radii[k] = Math.max(6.0, radius * (0.3 + 0.15 * unit(h, 32)));
            }
        }
        boolean river = level == UPPER && u.river && v.river;
        double roughness = 1.2 + 0.1 * radius;
        double prevX = 0, prevY = 0, prevZ = 0, prevR = 0;
        for (int s = 0; s <= SEGMENTS; s++) {
            double t = 3.0 * s / SEGMENTS;
            int k = Math.min(2, (int) Math.floor(t));
            double f = t - k;
            double x = catmull(cx, k, f);
            double z = catmull(cz, k, f);
            double floor = floors[k] + (floors[k + 1] - floors[k]) * f;
            double r = radii[k] + (radii[k + 1] - radii[k]) * (f * f * (3.0 - 2.0 * f));
            // Keep the vault under the cover of rock across its whole width: narrow it under low ground.
            double ground = this.ground.estimate(x, z);
            double reach = 0.8 * r;
            ground = Math.min(ground, Math.min(this.ground.estimate(x + px * reach, z + pz * reach), this.ground.estimate(x - px * reach, z - pz * reach)));
            double room = ground - CaveNetwork.COVER - 6.0 - floor;
            double height = r * shape * (1.0 + floorCut);
            if (height > room) {
                r = Math.max(0.0, room / (shape * (1.0 + floorCut)));
            }
            double y = floor + r * shape * floorCut;
            if (s > 0 && (r >= 4.0 || prevR >= 4.0)) {
                double ra = Math.max(prevR, 2.0);
                double rb = Math.max(r, 2.0);
                out.add(CavePrimitive.segment(prevX, prevY, prevZ, x, y, z, ra, rb, shape, 0.0, 0.0, floorCut, roughness, h + s)
                        .withRiver(river));
            }
            prevX = x;
            prevY = y;
            prevZ = z;
            prevR = r;
        }
    }

    private static double catmull(double[] p, int k, double t) {
        double p0 = p[Math.max(0, k - 1)];
        double p1 = p[k];
        double p2 = p[k + 1];
        double p3 = p[Math.min(3, k + 2)];
        if (k == 0) {
            p0 = 2.0 * p1 - p2;
        }
        if (k + 2 > 3) {
            p3 = 2.0 * p2 - p1;
        }
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5 * (2.0 * p1 + (-p0 + p2) * t + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2 + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3);
    }

    /** Halls where galleries meet, and mouths to the surface at the upper level. */
    private void junctions(long i, long j, Node n, List<CavePrimitive> out) {
        long h = this.hash(i, j, 1);
        for (int k = 0; k < LEVELS; k++) {
            if (!n.active(k)) {
                continue;
            }
            int degree = 0;
            double widest = 0.0;
            double shape = 0.6;
            long[][] edges = {
                    {i, j, 0, i + 1, j}, {i, j, 1, i, j + 1}, {i - 1, j, 0, i - 1, j}, {i, j - 1, 1, i, j - 1}
            };
            for (long[] e : edges) {
                Node a = this.node(e[0], e[1]);
                Node b = e[2] == 0 ? this.node(e[0] + 1, e[1]) : this.node(e[0], e[1] + 1);
                long key = this.edgeKey(e[0], e[1], (int) e[2]);
                if (this.linked(a, b, k, key, false)) {
                    degree++;
                    double[] p = this.profile(a, b, k, key);
                    if (p[0] > widest) {
                        widest = p[0];
                        shape = p[1];
                    }
                }
            }
            if (degree >= 3 && unit(h, 10 + k) < 0.6 * this.settings.chambers() && shape < 1.0) {
                double rh = Math.min(MAX_RADIUS, 1.35 * widest + 6.0);
                double rv = Math.min(0.75 * rh, widest * shape * 1.25 + 4.0);
                double floorDepth = 0.5;
                double centre = n.floor[k] + rv * floorDepth;
                double ceiling = k == UPPER ? n.lowGround - CaveNetwork.COVER - 6.0 : n.aquitard - FluidDomains.AQUITARD_HALF - 6.0;
                rv = Math.min(rv, (ceiling - centre) / 1.05);
                if (rv >= 6.0) {
                    out.add(CavePrimitive.chamber(n.x, centre, n.z, rh, rv, floorDepth, Math.PI * unit(h, 20 + k), 0.1 + 0.3 * unit(h, 30 + k),
                            2.0 + 0.06 * rh, h + 40 + k).withRiver(n.river && k == UPPER));
                }
            }
            if (k == UPPER && degree >= 1) {
                this.mouth(n, h, widest, out);
            }
        }
    }

    /** A very large cave mouth from the upper gallery level up to a hillside or cliff. */
    private void mouth(Node n, long h, double widest, List<CavePrimitive> out) {
        double slope = Math.sqrt(n.slopeX * n.slopeX + n.slopeZ * n.slopeZ);
        if (slope < 0.3 || n.surface < 70.0 || unit(h, 50) >= 0.22 * this.settings.entrances()) {
            return;
        }
        double y = n.floor[UPPER] + 6.0;
        double depth = n.surface - y;
        double length = depth / slope * 1.25 + 16.0;
        if (length > 1.4 * CELL) {
            return;
        }
        double r = clamp(0.45 * widest, 8.0, 22.0);
        double ex = n.x - n.slopeX / slope * length;
        double ez = n.z - n.slopeZ / slope * length;
        out.add(CavePrimitive.entrance(n.x, y, n.z, ex, y + 0.04 * length, ez, r, r * 1.4, 0.75, 1.5 + 0.1 * r, h + 60));
    }

    private long hash(long i, long j, int salt) {
        return SimplexNoise.mix(this.seed ^ i * 0x9E3779B97F4A7C15L ^ j * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L);
    }

    private static double unit(long h, int channel) {
        return FluidDomains.unit(h, channel);
    }
}
