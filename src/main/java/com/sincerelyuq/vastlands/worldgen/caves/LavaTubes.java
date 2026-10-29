package com.sincerelyuq.vastlands.worldgen.caves;

import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.clamp;
import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.fbm;
import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.smoothstep;

import com.sincerelyuq.vastlands.worldgen.geography.GeographyModel;
import com.sincerelyuq.vastlands.worldgen.geography.GeographySample;
import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Lava tubes: rare winding passages in the lowest rock of the world, from a few blocks above the
 * bedrock (floors at Y {@code minY + 9}, -247 in Vastlands worlds) up to Y {@value #TOP}, in the
 * lava domains of the deep zone (see {@link FluidDomains}).
 *
 * <p>Nodes sit on a jittered lattice of {@value #CELL}-block cells; a node exists only in a lava
 * domain, inside the regions of a 3 000-block field (more of them in volcanic rock), so the tubes
 * form a few branching systems hundreds of blocks long under some lava provinces and none under
 * most of the world. Tubes join neighbouring nodes of the same domain, so a tube never runs into
 * the dam between two domains; they are quadratic curves with a vertical bump, wide and low with
 * flat floors (mostly 8..20 blocks wide and 5..14 high, from 6 up to 28 and 18), their radius
 * swelling and narrowing along them, and some junctions open into low rooms over pools of lava.
 *
 * <p>The lava is the domain's: every open block up to its level is lava, every one above it air,
 * as in all the caves. The floors wander up and down across the band while the levels of lava
 * domains lie from Y -236 up, so some tubes are flooded to their vaults, some hold a lava river
 * under an open vault, and where a floor rises above the level the tube runs dry along a shore of
 * its own lava. Nothing is placed: the dams, the aquitard and the clearance under rivers and the
 * sea work on these pieces as on every other, so the lava stays in its domain, level and held by
 * rock, and never meets water.
 *
 * <p>Pure function of the seed and lattice coordinates; cells are cached per thread.
 */
final class LavaTubes {
    static final double CELL = 96.0;
    /** Cells around a column searched for pieces (a tube ends at a neighbouring node). */
    static final int REACH_CELLS = 2;
    /** Highest tube floor (blocks); the lowest is {@link #BEDROCK_ROCK} above the bottom of the world. */
    static final double TOP = -206.0;
    /** Floors stay this far above the bottom of the world: the caves keep 7 blocks (bedrock and stone) below them. */
    static final double BEDROCK_ROCK = 9.0;
    private static final double MAX_ROOM = 26.0;

    private static final class Node {
        final double x, z, floor;
        final FluidDomains.Domain domain;
        final double volcanic;

        Node(double x, double z, double floor, FluidDomains.Domain domain, double volcanic) {
            this.x = x;
            this.z = z;
            this.floor = floor;
            this.domain = domain;
            this.volcanic = volcanic;
        }

        boolean active() {
            return this.domain != null;
        }
    }

    private static final Node NONE = new Node(0.0, 0.0, Double.NaN, null, 0.0);

    private final long seed;
    private final GeographyModel geography;
    private final CaveGeology geology;
    private final FluidDomains domains;
    private final CaveSettings settings;
    private final int minY;
    private final SimplexNoise gate, undulation;
    private final ThreadLocal<KeyedCache<Node>> nodes = ThreadLocal.withInitial(() -> new KeyedCache<>(512));
    private final ThreadLocal<KeyedCache<CaveNetwork.Cell>> cells = ThreadLocal.withInitial(() -> new KeyedCache<>(256));

    LavaTubes(long seed, GeographyModel geography, CaveGeology geology, FluidDomains domains, CaveSettings settings, int minY) {
        this.seed = SimplexNoise.mix(seed ^ 0x4C41564154554245L);
        this.geography = geography;
        this.geology = geology;
        this.domains = domains;
        this.settings = settings;
        this.minY = minY;
        this.gate = new SimplexNoise(seed + 1001);
        this.undulation = new SimplexNoise(seed + 1002);
    }

    /** Every piece whose bounds contain the column. */
    void gather(int x, int z, Consumer<CavePrimitive> sink) {
        long ci = (long) Math.floor(x / CELL);
        long cj = (long) Math.floor(z / CELL);
        for (long j = cj - REACH_CELLS; j <= cj + REACH_CELLS; j++) {
            for (long i = ci - REACH_CELLS; i <= ci + REACH_CELLS; i++) {
                CaveNetwork.Cell cell = this.cell(i, j);
                if (cell.primitives.length == 0 || x < cell.minX || x > cell.maxX || z < cell.minZ || z > cell.maxZ) {
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

    CaveNetwork.Cell cell(long i, long j) {
        return this.cells.get().get(i << 32 ^ (j & 0xFFFFFFFFL), k -> this.buildCell(i, j));
    }

    /** Strength of the tube regions at a point (0 outside them). */
    double region(double x, double z) {
        return smoothstep(0.12, 0.4, fbm(this.gate, x / 3000.0, z / 3000.0, 2, 0.5));
    }

    private Node node(long i, long j) {
        return this.nodes.get().get(i << 32 ^ (j & 0xFFFFFFFFL), k -> this.buildNode(i, j));
    }

    private Node buildNode(long i, long j) {
        long h = this.hash(i, j, 0);
        double x = (i + 0.15 + 0.7 * unit(h, 0)) * CELL;
        double z = (j + 0.15 + 0.7 * unit(h, 1)) * CELL;
        double region = this.region(x, z);
        if (region <= 0.0) {
            return NONE;
        }
        FluidDomains.Domain domain = this.domains.owner(x, z, true);
        if (domain.fluid != CaveFluid.LAVA) {
            return NONE;
        }
        GeographySample g = this.geography.sample(x, z);
        CaveGeology.Geology geo = this.geology.at(x, z, g);
        double presence = (0.6 + 0.3 * geo.volcanic()) * region * this.settings.lavaTubes();
        if (unit(h, 2) >= presence) {
            return NONE;
        }
        // The floor wanders over the band (a 420-block undulation and the node's own jitter).
        double floor = -229.0 + 13.0 * this.undulation.sample(x / 420.0, z / 420.0) + 10.0 * (unit(h, 3) - 0.5);
        floor = clamp(floor, this.minY + BEDROCK_ROCK, TOP);
        // Tubes in the lava or just above it: a node far below the level would only add lava
        // nobody sees, one far above it a dry passage like any other.
        if (floor < domain.level - 22.0 || floor > domain.level + 8.0) {
            return NONE;
        }
        return new Node(x, z, floor, domain, geo.volcanic());
    }

    /** Whether the tube between two nodes exists (both in the same lava domain). */
    private boolean linked(Node u, Node v, long key, boolean diagonal) {
        if (!u.active() || !v.active() || u.domain.key != v.domain.key) {
            return false;
        }
        return unit(SimplexNoise.mix(key), 0) < (diagonal ? 0.35 : 0.7);
    }

    private long edgeKey(long i, long j, int type) {
        return this.hash(i, j, 100 + type);
    }

    /** The diagonal of quad (i, j): true joins (i, j)-(i+1, j+1), false (i+1, j)-(i, j+1). */
    private boolean mainDiagonal(long i, long j) {
        return unit(this.hash(i, j, 7), 0) < 0.5;
    }

    private CaveNetwork.Cell buildCell(long i, long j) {
        Node n00 = this.node(i, j);
        Node n10 = this.node(i + 1, j);
        Node n01 = this.node(i, j + 1);
        Node n11 = this.node(i + 1, j + 1);
        List<CavePrimitive> out = new ArrayList<>();
        if (!n00.active() && !n10.active() && !n01.active() && !n11.active()) {
            return new CaveNetwork.Cell(i, j, out);
        }
        boolean main = this.mainDiagonal(i, j);
        if (this.linked(n00, n10, this.edgeKey(i, j, 0), false)) {
            this.tube(n00, n10, this.edgeKey(i, j, 0), out);
        }
        if (this.linked(n00, n01, this.edgeKey(i, j, 1), false)) {
            this.tube(n00, n01, this.edgeKey(i, j, 1), out);
        }
        Node d0 = main ? n00 : n10;
        Node d1 = main ? n11 : n01;
        if (this.linked(d0, d1, this.edgeKey(i, j, 2), true)) {
            this.tube(d0, d1, this.edgeKey(i, j, 2), out);
        }
        if (n00.active()) {
            this.room(i, j, n00, out);
        }
        return new CaveNetwork.Cell(i, j, out);
    }

    /** Tubes meeting at node (i, j). */
    private int degree(long i, long j, Node n) {
        int degree = 0;
        degree += this.linked(n, this.node(i + 1, j), this.edgeKey(i, j, 0), false) ? 1 : 0;
        degree += this.linked(n, this.node(i, j + 1), this.edgeKey(i, j, 1), false) ? 1 : 0;
        degree += this.linked(this.node(i - 1, j), n, this.edgeKey(i - 1, j, 0), false) ? 1 : 0;
        degree += this.linked(this.node(i, j - 1), n, this.edgeKey(i, j - 1, 1), false) ? 1 : 0;
        if (this.mainDiagonal(i, j)) {
            degree += this.linked(n, this.node(i + 1, j + 1), this.edgeKey(i, j, 2), true) ? 1 : 0;
        }
        if (this.mainDiagonal(i - 1, j - 1)) {
            degree += this.linked(this.node(i - 1, j - 1), n, this.edgeKey(i - 1, j - 1, 2), true) ? 1 : 0;
        }
        if (!this.mainDiagonal(i - 1, j)) {
            degree += this.linked(n, this.node(i - 1, j + 1), this.edgeKey(i - 1, j, 2), true) ? 1 : 0;
        }
        if (!this.mainDiagonal(i, j - 1)) {
            degree += this.linked(this.node(i + 1, j - 1), n, this.edgeKey(i, j - 1, 2), true) ? 1 : 0;
        }
        return degree;
    }

    /**
     * A tube between two nodes: wide and low, a flat floor at the nodes' floors (with a bump in
     * between), the radius log-normal around 6 blocks (3..14) and swelling along the tube.
     */
    private void tube(Node u, Node v, long key, List<CavePrimitive> out) {
        long h = SimplexNoise.mix(key ^ 0x54554245L);
        double dx = v.x - u.x;
        double dz = v.z - u.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        double px = -dz / len;
        double pz = dx / len;
        double bend = (unit(h, 1) - 0.5) * 0.7 * len;
        double gauss = 2.0 * (unit(h, 3) + unit(h, 4) + unit(h, 5) - 1.5);
        double radius = clamp(6.0 * Math.exp(0.4 * gauss) * (unit(h, 6) < 0.17 ? 1.4 : 1.0), 3.0, 14.0);
        double shape = 0.55 + 0.3 * unit(h, 7) - (u.volcanic + v.volcanic) * 0.05;
        double floorCut = 0.55 + 0.25 * unit(h, 8);
        double rv = radius * shape;
        // Axis heights over the floors; the bump keeps the middle within the band.
        double y0 = u.floor + floorCut * rv;
        double y1 = v.floor + floorCut * rv;
        double low = this.minY + BEDROCK_ROCK + floorCut * rv;
        double cy = clamp(0.5 * (y0 + y1) + (unit(h, 2) - 0.5) * 10.0, low, TOP + floorCut * rv);
        double cx = 0.5 * (u.x + v.x) + px * bend;
        double cz = 0.5 * (u.z + v.z) + pz * bend;
        double waves = Math.max(1.0, Math.round(len / 45.0));
        double phase = 6.2831853 * unit(h, 9);
        double roughness = 0.8 + 0.1 * radius;
        double xPrev = u.x, yPrev = y0, zPrev = u.z;
        double rPrev = radius * (1.0 + 0.3 * Math.sin(phase));
        for (int s = 1; s <= 4; s++) {
            double t = s / 4.0;
            double a = (1 - t) * (1 - t);
            double b = 2 * t * (1 - t);
            double c = t * t;
            double qx = a * u.x + b * cx + c * v.x;
            double qy = a * y0 + b * cy + c * y1;
            double qz = a * u.z + b * cz + c * v.z;
            double r = radius * (1.0 + 0.3 * Math.sin(phase + 6.2831853 * waves * t));
            out.add(CavePrimitive.segment(xPrev, yPrev, zPrev, qx, qy, qz, rPrev, r, shape, 0.0, 0.0, floorCut, roughness, h + s));
            xPrev = qx;
            yPrev = qy;
            zPrev = qz;
            rPrev = r;
        }
    }

    /** Junctions of three or more tubes may open into a low room over a pool of lava; some dead ends end in one. */
    private void room(long i, long j, Node n, List<CavePrimitive> out) {
        int degree = this.degree(i, j, n);
        long h = this.hash(i, j, 1);
        double chance = degree >= 3 ? 0.4 : (degree == 1 ? 0.2 : 0.0);
        if (degree == 0 || unit(h, 0) >= chance) {
            return;
        }
        double rh = Math.min(MAX_ROOM, (degree >= 3 ? 11.0 : 8.0) + 15.0 * Math.pow(unit(h, 1), 1.4));
        double rv = rh * (0.3 + 0.15 * unit(h, 2));
        double floorDepth = 0.5;
        double centre = n.floor + floorDepth * rv;
        if (centre - floorDepth * rv < this.minY + BEDROCK_ROCK) {
            return;
        }
        out.add(CavePrimitive.chamber(n.x, centre, n.z, rh, rv, floorDepth, Math.PI * unit(h, 3), 0.1 + 0.3 * unit(h, 4), 1.2 + 0.06 * rh, h + 7));
    }

    private long hash(long i, long j, int salt) {
        return SimplexNoise.mix(this.seed ^ i * 0x9E3779B97F4A7C15L ^ j * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L);
    }

    static double unit(long h, int channel) {
        return FluidDomains.unit(h, channel);
    }
}
