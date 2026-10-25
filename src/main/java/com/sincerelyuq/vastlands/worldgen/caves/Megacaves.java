package com.sincerelyuq.vastlands.worldgen.caves;

import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.clamp;
import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.clamp01;
import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.smoothstep;

import com.sincerelyuq.vastlands.worldgen.geography.GeographyModel;
import com.sincerelyuq.vastlands.worldgen.geography.GeographySample;
import com.sincerelyuq.vastlands.worldgen.noise.NoiseMath;
import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Megacaves: underground landscapes hundreds of blocks across. At most one per {@value #CELL}-block
 * cell in each of two zones, the upper one (above the aquitard, under hills and mountains up to
 * 300+ blocks tall) and the deep one (below it, up to ~160 blocks tall); the two grids are offset,
 * so megacaves stack above each other in places. Kinds:
 * <ul>
 *     <li>{@link Kind#HALL}: a vaulted hall with a broad, terraced and boulder-strewn floor, a
 *     ceiling of breakdown lobes and cupolas, pillars and partition walls;</li>
 *     <li>{@link Kind#LAKE}: a wide, lower cavern around a basin that holds a lake with islands;</li>
 *     <li>{@link Kind#CANYON}: an underground canyon several times as long as wide and up to 220
 *     blocks tall, a V-shaped floor, ledges along its walls and natural bridges across;</li>
 *     <li>{@link Kind#ABYSS}: a vertical void with sheer stepped walls closing into a bell-shaped
 *     vault, often a lake at its foot, bridges high above;</li>
 *     <li>{@link Kind#COMPLEX}: a multi-level cavern: a ragged rock shelf with holes halfway up
 *     divides it into two floors, partitions split it into chambers;</li>
 *     <li>{@link Kind#PILLARS}: a forest of columns, some broken into stalagmite stubs and
 *     pendants.</li>
 * </ul>
 * The outline is a lobed, elongated superellipse whose walls step back in uneven terraces; the
 * ceiling falls towards the walls (steep in canyons and abysses), the floor rises towards them in
 * talus slopes, and noise gives floors hills, basins, benches and rubble and ceilings hanging
 * lobes and domes. A few connector passages lead out of every megacave at its floor and halfway up
 * (multiple entrances and exits), and an upper megacave close enough to the surface may have a
 * collapse shaft (a cenote) open to the sky.
 *
 * <p>Fluids: a megacave is its own fluid domain reaching to {@value #FOOTPRINT} of its outline
 * (see {@link FluidDomains#locate}): its lake, lava lake or dry floor is one level for the whole
 * cavern, dammed against the surrounding domains like any domain edge. Under the sea it is part of
 * the sea; elsewhere it takes the water table of the domain at its centre when that lies inside
 * it, or a lake of its own: partly flooded caverns, lakes with islands, rare submerged ones;
 * volcanic deep megacaves hold lava lakes.
 *
 * <p>Megacaves of neighbouring cells never overlap: a megacave's bounds stay within 0.47 of the
 * distance to every neighbouring cell's centre, so each column needs the 3 × 3 cells around it.
 * Pure function of the seed and cell coordinates; built megacaves cached per thread.
 */
final class Megacaves {
    static final double CELL = 1024.0;
    /** The megacave's fluid domain ends at this multiple of its outline. */
    static final double FOOTPRINT = 1.15;
    static final int UPPER = 0;
    static final int DEEP = 1;
    private static final double CONNECTOR_REACH = 130.0;

    enum Kind {
        HALL,
        LAKE,
        CANYON,
        ABYSS,
        COMPLEX,
        PILLARS
    }

    /** One megacave (or an empty cell). Immutable. */
    static final class Megacave {
        final long key;
        final int zone;
        final boolean exists;
        final Kind kind;
        final double x, z;
        final double radius;
        final double cos, sin, elongation;
        /** Floor at the centre and the vault's height above it. */
        final double floor, height;
        /**
         * How far the floor rises towards the walls, the superellipse powers of the floor (high:
         * flat floor, steep talus at the walls) and of the vault (low: a dome whose sides slope in),
         * and the vault's exponent (low: fuller dome).
         */
        final double rise, power, dome, vault;
        /** Share of the height the walls rise sheer before the vault closes in (abysses; 0 for the others). */
        final double bell;
        /** Basin depth at the centre (lakes). */
        final double basin;
        /** Horizontal bounding radius of everything the megacave adds (connectors included). */
        final double bound;
        final long seed;
        final FluidDomains.Domain domain;
        final double pillarCell, pillarChance, pillarRadius;
        /** Mid shelf (complex): Y of its top and thickness, NaN for none. */
        final double shelf, shelfThickness;
        /** Partition walls: sub-chamber cell size, NaN for none. */
        final double partitionCell;
        /** Natural bridges: {x, z, tx, tz, deck Y, thickness, half width}. */
        final double[][] bridges;
        /** Climate and rock at the centre (for cave biomes). */
        final double wetness, volcanic, temperature, moisture, theme;
        final CavePrimitive[] extras;

        Megacave(long key, int zone) {
            this.key = key;
            this.zone = zone;
            this.exists = false;
            this.kind = Kind.HALL;
            this.x = this.z = this.radius = this.cos = this.sin = this.elongation = 0.0;
            this.floor = this.height = this.rise = this.power = this.dome = this.vault = this.bell = this.basin = this.bound = 0.0;
            this.seed = 0L;
            this.domain = null;
            this.pillarCell = this.pillarChance = this.pillarRadius = 0.0;
            this.shelf = this.shelfThickness = this.partitionCell = Double.NaN;
            this.bridges = new double[0][];
            this.wetness = this.volcanic = this.temperature = this.moisture = this.theme = 0.0;
            this.extras = new CavePrimitive[0];
        }

        Megacave(long key, int zone, Kind kind, double x, double z, double radius, double angle, double elongation, double floor, double height,
                 double rise, double power, double dome, double vault, double bell, double basin, double bound, long seed, FluidDomains.Domain domain,
                 double pillarCell, double pillarChance, double pillarRadius, double shelf, double shelfThickness, double partitionCell,
                 double[][] bridges, CaveGeology.Geology geo, GeographySample g, double theme, CavePrimitive[] extras) {
            this.key = key;
            this.zone = zone;
            this.exists = true;
            this.kind = kind;
            this.x = x;
            this.z = z;
            this.radius = radius;
            this.cos = Math.cos(angle);
            this.sin = Math.sin(angle);
            this.elongation = elongation;
            this.floor = floor;
            this.height = height;
            this.rise = rise;
            this.power = power;
            this.dome = dome;
            this.vault = vault;
            this.bell = bell;
            this.basin = basin;
            this.bound = bound;
            this.seed = seed;
            this.domain = domain;
            this.pillarCell = pillarCell;
            this.pillarChance = pillarChance;
            this.pillarRadius = pillarRadius;
            this.shelf = shelf;
            this.shelfThickness = shelfThickness;
            this.partitionCell = partitionCell;
            this.bridges = bridges;
            this.wetness = geo.wetness();
            this.volcanic = geo.volcanic();
            this.temperature = g.temperature();
            this.moisture = g.moisture();
            this.theme = theme;
            this.extras = extras;
        }

        /** Highest point of the vault. */
        double top() {
            return this.floor + 1.2 * this.height;
        }

        /** Lowest point of the floor. */
        double bottom() {
            return this.floor - 0.14 * this.height - this.basin - 5.0;
        }
    }

    /** A megacave's section at one column. Mutable, reused per thread. */
    static final class Section {
        Megacave cave;
        double x, z;
        double q;
        /** Blocks per unit of q along the ray from the centre here. */
        double blocksPerQ;
        double floor, ceiling;
        /** Slope corrections turning vertical distances into distances to the floor and ceiling. */
        double floorScale, ceilingScale;
        double phase;
        SimplexNoise terraces;
        Noise3 walls;
        /** Nearest pillar: distance, radius, kind (0 none, 1 column, 2 stalagmite stub, 3 pendant) and stub height share. */
        double pillarDistance, pillarRadius, pillarStub;
        int pillarKind;
        /** Partition wall: rock below this Y where the column lies on a wall, else -infinity. */
        double partitionTop;
        double partitionDistance;
        /** Shelf rock interval, lo > hi for none. */
        double shelfLo, shelfHi;
        /** The terrace band last evaluated and its wall factor (heights come in runs). */
        long terraceBand;
        double terraceFactor;
        /** Bridge rock intervals. */
        final double[] bridgeLo = new double[3];
        final double[] bridgeHi = new double[3];
        int bridgeCount;

        boolean present() {
            return this.cave != null;
        }

        double low() {
            return this.floor - 2.0;
        }

        double high() {
            return this.ceiling + 2.0;
        }

        /** Signed distance to the megacave's rock at a height (negative inside). */
        double sdf(double y) {
            Megacave c = this.cave;
            // Terraced walls: bands about 6 blocks high whose edges wander up and down along the wall.
            long band = (long) Math.floor((y + this.phase + 1000.0) / 6.0);
            if (band != this.terraceBand) {
                double wobble = this.terraces.sample(this.x / 23.0 + band * 3.7, this.z / 23.0 - band * 1.9);
                this.terraceBand = band;
                // Alcoves and overhangs: the wall recedes by up to a fifth at heights picked by a
                // ~40-block noise; never outwards, the footprint bounds the fluid domain.
                double recess = 0.5 + 0.5 * this.walls.sample(this.x / 80.0, band * 6.0 / 40.0, this.z / 80.0);
                this.terraceFactor = (1.0 + 0.05 * (FluidDomains.unit(c.seed ^ band, 3) - 0.5) + 0.04 * wobble) * (1.0 - 0.22 * recess * recess);
            }
            double wall = (this.q / this.terraceFactor - 1.0) * this.blocksPerQ;
            double d = Math.max(wall, Math.max((this.floor - y) * this.floorScale, (y - this.ceiling) * this.ceilingScale));
            if (d > 0.0) {
                return d;
            }
            if (this.pillarKind != 0) {
                double span = this.ceiling - this.floor;
                double r;
                if (this.pillarKind == 1) {
                    double t = (y - 0.5 * (this.ceiling + this.floor)) / (0.5 * span);
                    r = this.pillarRadius * (1.0 + 1.4 * t * t);
                } else if (this.pillarKind == 2) {
                    double t = (y - this.floor) / (this.pillarStub * span);
                    r = t >= 1.0 ? -1.0 : this.pillarRadius * 1.4 * (1.0 - t);
                } else {
                    double t = (this.ceiling - y) / (this.pillarStub * span);
                    r = t >= 1.0 ? -1.0 : this.pillarRadius * 1.4 * (1.0 - t);
                }
                d = Math.max(d, r - this.pillarDistance);
            }
            if (y < this.partitionTop) {
                d = Math.max(d, Math.min(this.partitionDistance, this.partitionTop - y));
            }
            if (y >= this.shelfLo && y <= this.shelfHi) {
                d = Math.max(d, Math.min(y - this.shelfLo, this.shelfHi - y) + 0.5);
            }
            for (int k = 0; k < this.bridgeCount; k++) {
                if (y >= this.bridgeLo[k] && y <= this.bridgeHi[k]) {
                    d = Math.max(d, Math.min(y - this.bridgeLo[k], this.bridgeHi[k] - y) + 0.5);
                }
            }
            return d;
        }
    }

    private final long seed;
    private final GeographyModel geography;
    private final CaveGeology geology;
    private final FluidDomains domains;
    private final CaveNetwork.Ground ground;
    private final CaveSettings settings;
    private final SimplexNoise outline, relief, rubble, ceilings, masks, province;
    private final Noise3 walls;
    private final ThreadLocal<KeyedCache<Megacave>> cache = ThreadLocal.withInitial(() -> new KeyedCache<>(256));
    private final ThreadLocal<double[]> scratch = ThreadLocal.withInitial(() -> new double[2]);

    Megacaves(long seed, GeographyModel geography, CaveGeology geology, FluidDomains domains, CaveNetwork.Ground ground, CaveSettings settings) {
        this.seed = SimplexNoise.mix(seed ^ 0x4D45474143415645L);
        this.geography = geography;
        this.geology = geology;
        this.domains = domains;
        this.ground = ground;
        this.settings = settings;
        this.outline = new SimplexNoise(seed + 801);
        this.relief = new SimplexNoise(seed + 802);
        this.rubble = new SimplexNoise(seed + 803);
        this.ceilings = new SimplexNoise(seed + 804);
        this.masks = new SimplexNoise(seed + 805);
        this.province = new SimplexNoise(seed + 806);
        this.walls = new Noise3(seed + 807);
    }

    /** The megacave of a zone whose footprint contains a point, and its q there, or null. */
    Megacave covering(double x, double z, int zone, double[] q) {
        long ci = Math.floorDiv((long) Math.floor(x - offset(zone)), (long) CELL);
        long cj = Math.floorDiv((long) Math.floor(z - offset(zone)), (long) CELL);
        for (long j = cj - 1; j <= cj + 1; j++) {
            for (long i = ci - 1; i <= ci + 1; i++) {
                Megacave m = this.megacave(i, j, zone);
                if (!m.exists || Math.abs(x - m.x) > m.bound || Math.abs(z - m.z) > m.bound) {
                    continue;
                }
                double v = this.q(m, x, z, q);
                if (v < FOOTPRINT || (v - FOOTPRINT) * q[1] < FluidDomains.EDGE_REACH + 2.0) {
                    q[0] = v;
                    return m;
                }
            }
        }
        return null;
    }

    /** Extra pieces (connectors, cenotes) of the megacaves near a column. */
    void gather(int x, int z, Consumer<CavePrimitive> sink) {
        for (int zone = 0; zone < 2; zone++) {
            long ci = Math.floorDiv((long) x - (long) offset(zone), (long) CELL);
            long cj = Math.floorDiv((long) z - (long) offset(zone), (long) CELL);
            for (long j = cj - 1; j <= cj + 1; j++) {
                for (long i = ci - 1; i <= ci + 1; i++) {
                    Megacave m = this.megacave(i, j, zone);
                    if (!m.exists || Math.abs(x - m.x) > m.bound || Math.abs(z - m.z) > m.bound) {
                        continue;
                    }
                    for (CavePrimitive p : m.extras) {
                        if (p.covers(x, z) && p.reaches(x, z)) {
                            sink.accept(p);
                        }
                    }
                }
            }
        }
    }

    /** The section of a zone's megacave at a column, if its outline reaches it. */
    void section(double x, double z, int zone, Section out) {
        out.cave = null;
        long ci = Math.floorDiv((long) Math.floor(x - offset(zone)), (long) CELL);
        long cj = Math.floorDiv((long) Math.floor(z - offset(zone)), (long) CELL);
        double[] qq = this.scratch.get();
        for (long j = cj - 1; j <= cj + 1; j++) {
            for (long i = ci - 1; i <= ci + 1; i++) {
                Megacave m = this.megacave(i, j, zone);
                if (!m.exists || Math.abs(x - m.x) > m.bound || Math.abs(z - m.z) > m.bound) {
                    continue;
                }
                double q = this.q(m, x, z, qq);
                if (q >= 1.06) {
                    continue;
                }
                this.fill(m, x, z, q, qq[1], out);
                return;
            }
        }
    }

    /** All megacaves whose bounds meet a square, for diagnostics, tests and later systems. */
    List<Megacave> near(int minX, int minZ, int maxX, int maxZ) {
        List<Megacave> out = new ArrayList<>();
        for (int zone = 0; zone < 2; zone++) {
            long i0 = Math.floorDiv((long) minX - (long) offset(zone), (long) CELL) - 1;
            long i1 = Math.floorDiv((long) maxX - (long) offset(zone), (long) CELL) + 1;
            long j0 = Math.floorDiv((long) minZ - (long) offset(zone), (long) CELL) - 1;
            long j1 = Math.floorDiv((long) maxZ - (long) offset(zone), (long) CELL) + 1;
            for (long j = j0; j <= j1; j++) {
                for (long i = i0; i <= i1; i++) {
                    Megacave m = this.megacave(i, j, zone);
                    if (m.exists && m.x + m.bound >= minX && m.x - m.bound <= maxX && m.z + m.bound >= minZ && m.z - m.bound <= maxZ) {
                        out.add(m);
                    }
                }
            }
        }
        return out;
    }

    private static double offset(int zone) {
        return zone == UPPER ? 0.0 : 0.5 * CELL;
    }

    /** Normalised distance from the centre (1 at the outline); {@code out[1]} receives the blocks per unit of q. */
    private double q(Megacave m, double x, double z, double[] out) {
        double dx = x - m.x;
        double dz = z - m.z;
        double u = (dx * m.cos + dz * m.sin) / (1.0 + m.elongation);
        double v = (-dx * m.sin + dz * m.cos) / (1.0 - m.elongation);
        double r = Math.sqrt(u * u + v * v);
        double scale = 0.35 * m.radius + 20.0;
        double lobes = 0.8 + 0.2 * (1.0 + this.outline.sample(x / scale + m.seed % 97, z / scale));
        double q = r / (m.radius * lobes);
        if (out != null) {
            double dist = Math.sqrt(dx * dx + dz * dz);
            out[1] = q > 1e-6 ? dist / q : m.radius;
        }
        return q;
    }

    private void fill(Megacave m, double x, double z, double q, double blocksPerQ, Section out) {
        out.cave = m;
        out.x = x;
        out.z = z;
        out.q = q;
        out.terraceBand = Long.MIN_VALUE;
        out.blocksPerQ = Math.max(1.0, blocksPerQ);
        out.terraces = this.outline;
        out.walls = this.walls;
        out.phase = 5.0 * this.outline.sample(x / 37.0 + 50.0, z / 37.0);
        double qc = Math.min(q, 0.999999);
        double p = m.power;
        double qp = Math.pow(qc, p);
        double s = Math.pow(1.0 - qp, 1.0 / p);
        // d s / d q, for the slope of the floor and ceiling along the ray.
        double ds = -qp / Math.max(qc, 1e-6) * Math.pow(Math.max(s, 1e-4), 1.0 - p);
        double h = m.height;
        double inner = smoothstep(0.15, 0.5, s);
        // Floor: talus rising to the walls, hills and basins, benches, boulders and rubble.
        double floor = m.floor + m.rise * (1.0 - s);
        double hills = NoiseMath.fbm(this.relief, x / (0.4 * m.radius + 30.0) + m.seed % 53, z / (0.4 * m.radius + 30.0), 2, 0.5);
        floor += 0.15 * h * hills * inner;
        if (m.basin > 0.0) {
            floor -= m.basin * s * s;
            // Islands: knolls rising out of the basin.
            double knoll = this.relief.sample(x / 45.0 + 300.0, z / 45.0);
            floor += 0.8 * m.basin * smoothstep(0.35, 0.8, knoll) * inner;
        }
        if (m.kind != Kind.ABYSS && this.masks.sample(x / 70.0 + m.seed % 31, z / 70.0) > -0.1) {
            // Benches: the floor in soft steps.
            double step = 5.0;
            double stepped = Math.floor(floor / step) * step;
            floor = stepped + step * smoothstep(0.75, 1.0, (floor - stepped) / step);
        }
        floor += 1.4 * this.rubble.sample(x / 9.0, z / 9.0) + 4.5 * smoothstep(0.5, 0.95, this.rubble.sample(x / 15.0 + 70.0, z / 15.0));
        // Ceiling: the vault, a dome falling to the walls, with hanging breakdown lobes, ribs and cupolas.
        double pc = m.dome;
        double qpc = Math.pow(qc, pc);
        double sc = Math.pow(1.0 - qpc, 1.0 / pc);
        double dsc = -qpc / Math.max(qc, 1e-6) * Math.pow(Math.max(sc, 1e-4), 1.0 - pc);
        double vaultShape = m.bell + (1.0 - m.bell) * Math.pow(Math.max(sc, 1e-6), m.vault);
        double ceiling = m.floor + h * vaultShape;
        double lobes = 0.5 + 0.5 * this.ceilings.sample(x / 13.0, z / 13.0);
        double ribs = NoiseMath.ridged(this.ceilings, x / 60.0 + 40.0, z / 60.0, 2);
        double cupola = this.ceilings.sample(x / (0.3 * m.radius + 20.0) - 80.0, z / (0.3 * m.radius + 20.0));
        double domes = Math.max(0.0, cupola) - 0.4 * Math.max(0.0, -cupola);
        ceiling += (-0.07 * h * lobes - 0.06 * h * ribs + 0.2 * h * domes) * sc;
        out.floor = floor;
        out.ceiling = ceiling;
        double floorSlope = Math.abs(m.rise * ds) / out.blocksPerQ;
        double ceilingSlope = Math.abs(h * (1.0 - m.bell) * m.vault * Math.pow(Math.max(sc, 1e-4), m.vault - 1.0) * dsc) / out.blocksPerQ;
        out.floorScale = Math.max(0.05, 1.0 / Math.sqrt(1.0 + floorSlope * floorSlope));
        out.ceilingScale = Math.max(0.05, 1.0 / Math.sqrt(1.0 + ceilingSlope * ceilingSlope));
        this.pillars(m, x, z, s, out);
        this.partitions(m, x, z, s, out);
        // A ragged shelf halfway up, with holes: a second floor.
        out.shelfLo = 1.0;
        out.shelfHi = 0.0;
        if (m.shelf == m.shelf && s > 0.2 && ceiling - m.shelf > 10.0 && m.shelf - m.shelfThickness - floor > 8.0) {
            double mask = this.masks.sample(x / 48.0 - 200.0, z / 48.0) + 0.35 * this.masks.sample(x / 13.0, z / 13.0 + 90.0);
            if (mask > -0.15) {
                double ragged = 1.5 * this.rubble.sample(x / 7.0 - 40.0, z / 7.0);
                out.shelfLo = m.shelf - m.shelfThickness + ragged - 2.0 * smoothstep(-0.15, 0.4, mask);
                out.shelfHi = m.shelf + 0.5 * ragged;
            }
        }
        out.bridgeCount = 0;
        for (double[] b : m.bridges) {
            if (out.bridgeCount == out.bridgeLo.length) {
                break;
            }
            double dx = x - b[0];
            double dz = z - b[1];
            double across = Math.abs(-dx * b[3] + dz * b[2]);
            double along = dx * b[2] + dz * b[3];
            double width = b[6] * (0.75 + 0.5 * (0.5 + 0.5 * this.rubble.sample(x / 11.0 + b[0], z / 11.0)));
            if (across < width) {
                // Thicker towards the walls, the underside arched.
                double t = clamp01(Math.abs(along) / (m.radius * (1.0 - m.elongation) * 1.1));
                double thickness = b[5] * (1.0 + 2.5 * t * t * t) * (1.0 - 0.3 * across / width);
                int k = out.bridgeCount++;
                out.bridgeHi[k] = b[4] + 0.7 * this.rubble.sample(x / 6.0, z / 6.0 + b[1]);
                out.bridgeLo[k] = b[4] - thickness;
            }
        }
    }

    private void pillars(Megacave m, double x, double z, double s, Section out) {
        out.pillarKind = 0;
        if (m.pillarChance <= 0.0 || s < 0.15) {
            return;
        }
        double cell = m.pillarCell;
        long pi = (long) Math.floor(x / cell);
        long pj = (long) Math.floor(z / cell);
        double best = Double.NEGATIVE_INFINITY;
        for (long b = pj - 1; b <= pj + 1; b++) {
            for (long a = pi - 1; a <= pi + 1; a++) {
                long h = SimplexNoise.mix(m.seed ^ a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL);
                if (FluidDomains.unit(h, 2) >= m.pillarChance) {
                    continue;
                }
                double sx = (a + 0.2 + 0.6 * FluidDomains.unit(h, 0)) * cell;
                double sz = (b + 0.2 + 0.6 * FluidDomains.unit(h, 1)) * cell;
                if (this.q(m, sx, sz, null) > 0.85) {
                    continue;
                }
                double r = m.pillarRadius * (0.45 + 0.85 * FluidDomains.unit(h, 3));
                double d = Math.hypot(x - sx, z - sz);
                if (r - d > best) {
                    best = r - d;
                    out.pillarDistance = d;
                    out.pillarRadius = r;
                    double kind = FluidDomains.unit(h, 4);
                    out.pillarKind = kind < 0.68 ? 1 : (kind < 0.84 ? 2 : 3);
                    out.pillarStub = 0.2 + 0.45 * FluidDomains.unit(h, 5);
                }
            }
        }
    }

    private void partitions(Megacave m, double x, double z, double s, Section out) {
        out.partitionTop = Double.NEGATIVE_INFINITY;
        if (m.partitionCell != m.partitionCell || s < 0.3) {
            return;
        }
        double cell = m.partitionCell;
        // Warped, so walls wander instead of drawing a polygon grid.
        double wx = x + 0.3 * cell * this.masks.sample(x / (0.6 * cell) + 21.0, z / (0.6 * cell));
        double wz = z + 0.3 * cell * this.masks.sample(x / (0.6 * cell), z / (0.6 * cell) - 21.0);
        long pi = (long) Math.floor(wx / cell);
        long pj = (long) Math.floor(wz / cell);
        double d1 = Double.POSITIVE_INFINITY;
        double d2 = Double.POSITIVE_INFINITY;
        for (long b = pj - 1; b <= pj + 1; b++) {
            for (long a = pi - 1; a <= pi + 1; a++) {
                long h = SimplexNoise.mix(m.seed ^ 0x50415254L ^ a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL);
                double sx = (a + 0.15 + 0.7 * FluidDomains.unit(h, 0)) * cell;
                double sz = (b + 0.15 + 0.7 * FluidDomains.unit(h, 1)) * cell;
                double d = Math.hypot(wx - sx, wz - sz);
                if (d < d1) {
                    d2 = d1;
                    d1 = d;
                } else if (d < d2) {
                    d2 = d;
                }
            }
        }
        double edge = 0.5 * (d2 - d1);
        double half = 2.0 + 5.0 * (0.5 + 0.5 * this.masks.sample(x / 30.0 + 11.0, z / 30.0));
        // Gaps in the walls: passages from chamber to chamber; most edges stay open.
        double gate = this.masks.sample(x / 45.0 - 60.0, z / 45.0 + 15.0);
        if (edge < half && gate > 0.2) {
            double share = 0.35 + 0.65 * smoothstep(0.2, 0.7, gate);
            out.partitionDistance = half - edge;
            out.partitionTop = out.floor + share * (out.ceiling - out.floor) * smoothstep(0.3, 0.55, s);
        }
    }

    private Megacave megacave(long i, long j, int zone) {
        long key = (i << 32 ^ (j & 0xFFFFFFFFL)) * 2 + zone;
        return this.cache.get().get(key, k -> this.build(k, i, j, zone));
    }

    private double centreX(long i, long j, int zone) {
        long h = this.cellHash(i, j, zone);
        return (i + 0.25 + 0.5 * FluidDomains.unit(h, 0)) * CELL + offset(zone);
    }

    private double centreZ(long i, long j, int zone) {
        long h = this.cellHash(i, j, zone);
        return (j + 0.25 + 0.5 * FluidDomains.unit(h, 1)) * CELL + offset(zone);
    }

    private long cellHash(long i, long j, int zone) {
        return SimplexNoise.mix(this.seed ^ i * 0x9E3779B97F4A7C15L ^ j * 0xC2B2AE3D27D4EB4FL ^ (zone + 1) * 0x165667B19E3779F9L);
    }

    private Megacave build(long key, long i, long j, int zone) {
        long h = this.cellHash(i, j, zone);
        double x = this.centreX(i, j, zone);
        double z = this.centreZ(i, j, zone);
        GeographySample g = this.geography.sample(x, z);
        CaveGeology.Geology geo = this.geology.at(x, z, g);
        double clustered = smoothstep(-0.6, 0.4, NoiseMath.fbm(this.province, x / 4200.0 + 7.0 * zone, z / 4200.0, 2, 0.5));
        double chance = this.settings.megacaves() * (zone == UPPER ? 0.75 : 0.55) * (0.35 + 0.95 * clustered)
                * clamp(0.55 + 0.35 * geo.density(), 0.4, 1.2);
        if (FluidDomains.unit(h, 2) >= chance) {
            return new Megacave(key, zone);
        }
        // Never overlapping the neighbours' bounds: within 0.47 of the nearest neighbouring centre.
        double nearest = Double.POSITIVE_INFINITY;
        for (long b = j - 1; b <= j + 1; b++) {
            for (long a = i - 1; a <= i + 1; a++) {
                if (a != i || b != j) {
                    nearest = Math.min(nearest, Math.hypot(this.centreX(a, b, zone) - x, this.centreZ(a, b, zone) - z));
                }
            }
        }
        double maxBound = 0.47 * nearest;
        // Kind, from the room above and below and the rock.
        // Rough ground (before river channels), less a margin for valleys; columns keep their cover
        // under the exact ground anyway.
        double surface = this.ground.estimate(x, z) - 12.0;
        double aquitard = this.domains.aquitard(x, z);
        double lowest = zone == UPPER ? aquitard + FluidDomains.AQUITARD_HALF + 14.0 : -236.0;
        double ceilingCap = zone == UPPER ? Double.POSITIVE_INFINITY : aquitard - FluidDomains.AQUITARD_HALF - 22.0;
        double u = FluidDomains.unit(h, 3);
        double roomGuess = Math.min(surface - CaveNetwork.COVER - 14.0, ceilingCap) - lowest;
        Kind kind = this.kind(h, roomGuess, geo, zone);
        double radius = (70.0 + 280.0 * Math.pow(u, 1.8)) * this.settings.megacaveScale();
        double elongation = 0.1 + 0.3 * FluidDomains.unit(h, 4);
        double power = 2.6 + 1.4 * FluidDomains.unit(h, 5);
        double dome = 1.7 + 0.7 * FluidDomains.unit(h, 15);
        double vault = 0.55 + 0.35 * FluidDomains.unit(h, 6);
        double bell = 0.0;
        double heightShare;
        switch (kind) {
            case LAKE -> {
                heightShare = 0.3 + 0.15 * FluidDomains.unit(h, 7);
                radius *= 1.1;
            }
            case CANYON -> {
                elongation = 0.6 + 0.15 * FluidDomains.unit(h, 4);
                power = 2.0;
                dome = 1.4;
                vault = 0.3;
                heightShare = 1.0;
                radius *= 0.8;
            }
            case ABYSS -> {
                radius = Math.min(radius, 50.0 + 70.0 * FluidDomains.unit(h, 8)) * this.settings.megacaveScale();
                elongation = 0.05 + 0.15 * FluidDomains.unit(h, 4);
                power = 6.0;
                // A bell: sheer walls for the lower half, then a round dome closing in (a vault
                // that stays high out to the walls looked like a box).
                bell = 0.45 + 0.15 * FluidDomains.unit(h, 16);
                dome = 2.0;
                vault = 0.8 + 0.4 * FluidDomains.unit(h, 6);
                heightShare = 2.5;
            }
            case COMPLEX -> heightShare = 0.55 + 0.2 * FluidDomains.unit(h, 7);
            case PILLARS -> {
                heightShare = 0.35 + 0.15 * FluidDomains.unit(h, 7);
                dome = 2.6;
            }
            default -> heightShare = 0.32 + 0.2 * FluidDomains.unit(h, 7);
        }
        double angle = Math.PI * FluidDomains.unit(h, 9);
        double reach = this.settings.megacaveScale() * CONNECTOR_REACH;
        radius = Math.min(radius, (maxBound - reach) / ((1.0 + elongation) * 1.3));
        if (radius < 45.0) {
            return new Megacave(key, zone);
        }
        // The ground over the footprint, ring by ring: the vault is a dome, highest at the centre
        // and low near the walls, so each ring only needs room for the vault's height there (with
        // its cupolas) under the lowest ground of the ring and its cover of rock.
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double[] rings = {0.0, 0.35, 0.7, 0.95};
        double[] grounds = new double[rings.length];
        grounds[0] = surface;
        for (int r = 1; r < rings.length; r++) {
            double lowestHere = Double.POSITIVE_INFINITY;
            for (int k = 0; k < 12; k++) {
                double a = (k + 0.5 * r) * Math.PI / 6.0;
                double uu = Math.cos(a) * radius * rings[r] * (1.0 + elongation);
                double vv = Math.sin(a) * radius * rings[r] * (1.0 - elongation);
                lowestHere = Math.min(lowestHere, this.ground.estimate(x + uu * cos - vv * sin, z + uu * sin + vv * cos) - 12.0);
            }
            grounds[r] = lowestHere;
        }
        double top = Math.min(grounds[0] - CaveNetwork.COVER - 6.0, ceilingCap);
        double room = top - lowest;
        double height = Math.min(heightShare * radius, kind == Kind.ABYSS ? 300.0 : 220.0);
        height = Math.max(height, 36.0);
        double basin = kind == Kind.LAKE ? 0.25 * height : 0.0;
        // floor - 0.14 h - basin - 5 >= lowest and floor + 1.2 h <= top (at the centre).
        double fits = (room - 5.0) / (1.34 + basin / height);
        if (fits < 30.0) {
            return new Megacave(key, zone);
        }
        if (height > fits) {
            basin *= fits / height;
            height = fits;
        }
        double floorLo = lowest + 0.14 * height + basin + 5.0;
        double floorHi = top - 1.2 * height;
        double floor = floorLo + (floorHi - floorLo) * Math.pow(FluidDomains.unit(h, 10), 1.6);
        for (int r = 1; r < rings.length; r++) {
            double profile = bell + (1.0 - bell) * Math.pow(Math.max(1e-6, 1.0 - Math.pow(rings[r], dome)), vault / dome);
            double allowed = Math.min(grounds[r] - CaveNetwork.COVER - 6.0, ceilingCap) - floor;
            if (profile > 0.05) {
                height = Math.min(height, allowed / (1.2 * profile));
            }
        }
        if (height < 30.0) {
            return new Megacave(key, zone);
        }
        if (kind == Kind.LAKE) {
            basin = Math.min(basin, 0.25 * height);
        }
        if (kind == Kind.ABYSS && height < 110.0) {
            kind = Kind.HALL;
            power = 3.0;
            dome = 2.0;
            vault = 0.6;
            bell = 0.0;
        }
        double rise = (kind == Kind.CANYON ? 0.55 : 0.3) * height;
        double bound = radius * (1.0 + elongation) * 1.3 + reach;
        long seed = SimplexNoise.mix(h ^ 0x5345454444L);
        FluidDomains.Domain domain = this.domain(key, x, z, zone, kind, floor, height, basin, geo, h);
        double pillarChance = switch (kind) {
            case PILLARS -> 0.8;
            case HALL, COMPLEX -> 0.25;
            case LAKE -> 0.2;
            default -> 0.0;
        };
        double pillarCell = clamp(radius / 5.0, 24.0, 60.0) * (kind == Kind.PILLARS ? 0.7 : 1.0);
        double pillarRadius = Math.min(0.32 * pillarCell, 3.5 + 0.04 * radius);
        double shelf = Double.NaN;
        double shelfThickness = 0.0;
        if (kind == Kind.COMPLEX || kind == Kind.ABYSS && FluidDomains.unit(h, 11) < 0.4) {
            shelf = floor + height * (0.4 + 0.15 * FluidDomains.unit(h, 12));
            shelfThickness = 5.0 + 4.0 * FluidDomains.unit(h, 13);
        }
        double partitionCell = (kind == Kind.HALL || kind == Kind.COMPLEX || kind == Kind.LAKE) && radius >= 110.0 ? 0.55 * radius : Double.NaN;
        double[][] bridges = this.bridges(h, kind, x, z, cos, sin, radius, elongation, floor, height);
        CavePrimitive[] extras = this.extras(h, x, z, zone, cos, sin, radius, elongation, floor, height, domain);
        for (CavePrimitive p : extras) {
            bound = Math.max(bound, Math.max(Math.max(Math.abs(p.minX - x), Math.abs(p.maxX - x)), Math.max(Math.abs(p.minZ - z), Math.abs(p.maxZ - z))));
        }
        return new Megacave(key, zone, kind, x, z, radius, angle, elongation, floor, height, rise, power, dome, vault, bell, basin, bound, seed, domain,
                pillarCell, pillarChance, pillarRadius, shelf, shelfThickness, partitionCell, bridges, geo, g, FluidDomains.unit(h, 14), extras);
    }

    private Kind kind(long h, double room, CaveGeology.Geology geo, int zone) {
        double canyon = room > 120.0 ? 0.08 + 0.35 * geo.fracture() : 0.0;
        double abyss = room > 140.0 ? 0.06 + 0.25 * geo.vertical() : 0.0;
        double lake = (0.12 + 0.2 * geo.wetness()) * this.settings.water();
        double complex = room > 110.0 ? 0.14 : 0.0;
        double pillars = 0.12 + 0.1 * geo.karst();
        double hall = 0.35;
        double total = canyon + abyss + lake + complex + pillars + hall;
        double r = FluidDomains.unit(h, 20) * total;
        if ((r -= canyon) < 0.0) {
            return Kind.CANYON;
        }
        if ((r -= abyss) < 0.0) {
            return Kind.ABYSS;
        }
        if ((r -= lake) < 0.0) {
            return Kind.LAKE;
        }
        if ((r -= complex) < 0.0) {
            return Kind.COMPLEX;
        }
        if ((r -= pillars) < 0.0) {
            return Kind.PILLARS;
        }
        return Kind.HALL;
    }

    /** The megacave's own fluid domain: the sea, the surrounding water table, a lake of its own, lava or dry. */
    private FluidDomains.Domain domain(long key, double x, double z, int zone, Kind kind, double floor, double height, double basin,
                                       CaveGeology.Geology geo, long h) {
        long domainKey = key ^ 0x7E00000000000000L;
        FluidDomains.Domain around = this.domains.voronoi(x, z, zone == DEEP);
        if (around.sea) {
            return new FluidDomains.Domain(domainKey, x, z, CaveFluid.WATER, around.level, true);
        }
        double low = floor - basin;
        double top = floor + height;
        double u = FluidDomains.unit(h, 30);
        if (zone == DEEP && around.fluid == CaveFluid.LAVA && u < 0.75 * this.settings.lava()) {
            int level = (int) Math.floor(Math.min(-100.0, low + 4.0 + 10.0 * FluidDomains.unit(h, 31)));
            return new FluidDomains.Domain(domainKey, x, z, CaveFluid.LAVA, level, false);
        }
        if (around.fluid == CaveFluid.WATER && around.level > low + 3 && around.level < top - 0.35 * height) {
            return new FluidDomains.Domain(domainKey, x, z, CaveFluid.WATER, around.level, false);
        }
        double lake = (kind == Kind.LAKE ? 0.9 : (kind == Kind.ABYSS ? 0.6 : 0.22 + 0.35 * geo.wetness())) * this.settings.water();
        if (zone == DEEP) {
            lake *= 1.0 - 0.6 * geo.volcanic();
        }
        if (u < lake) {
            double share = FluidDomains.unit(h, 32) < 0.06 ? 0.85 : 0.04 + 0.3 * FluidDomains.unit(h, 33);
            if (kind == Kind.LAKE) {
                share = 0.12 + 0.25 * FluidDomains.unit(h, 33);
            }
            int level = (int) Math.floor(Math.min(CaveModel.MAX_LEVEL, floor + share * height));
            return new FluidDomains.Domain(domainKey, x, z, CaveFluid.WATER, level, false);
        }
        if (zone == DEEP && u < lake + 0.25 * geo.volcanic() * this.settings.lava()) {
            int level = (int) Math.floor(Math.min(-100.0, low + 3.0 + 8.0 * FluidDomains.unit(h, 31)));
            return new FluidDomains.Domain(domainKey, x, z, CaveFluid.LAVA, level, false);
        }
        return new FluidDomains.Domain(domainKey, x, z, CaveFluid.AIR, FluidDomains.NO_LEVEL, false);
    }

    private double[][] bridges(long h, Kind kind, double x, double z, double cos, double sin, double radius, double elongation, double floor, double height) {
        int count = switch (kind) {
            case CANYON -> 1 + (int) (FluidDomains.unit(h, 40) * 3.0);
            case ABYSS -> (int) (FluidDomains.unit(h, 40) * 3.0);
            case HALL -> height > 60.0 && FluidDomains.unit(h, 40) < 0.3 ? 1 : 0;
            default -> 0;
        };
        double[][] out = new double[count][];
        for (int k = 0; k < count; k++) {
            double along = (FluidDomains.unit(h, 41 + k) - 0.5) * 1.2 * radius * (1.0 + elongation);
            double bx = x + cos * along;
            double bz = z + sin * along;
            // Across the short axis.
            double tx = -sin;
            double tz = cos;
            double deck = floor + height * (0.35 + 0.4 * FluidDomains.unit(h, 45 + k));
            out[k] = new double[] {bx, bz, tx, tz, deck, 4.0 + 3.0 * FluidDomains.unit(h, 48 + k), 3.0 + 3.5 * FluidDomains.unit(h, 51 + k)};
        }
        return out;
    }

    /**
     * Connectors (passages leading out of the megacave at its floor and halfway up, so it has
     * several entrances and exits) and a cenote: a collapse shaft from the vault to the sky.
     */
    private CavePrimitive[] extras(long h, double x, double z, int zone, double cos, double sin, double radius, double elongation, double floor,
                                   double height, FluidDomains.Domain domain) {
        List<CavePrimitive> out = new ArrayList<>();
        int connectors = 3 + (int) (FluidDomains.unit(h, 60) * 3.0);
        double reach = this.settings.megacaveScale() * CONNECTOR_REACH;
        for (int k = 0; k < connectors; k++) {
            double a = 2.0 * Math.PI * (k + 0.6 * FluidDomains.unit(h, 61 + k)) / connectors;
            double uu = Math.cos(a) * (1.0 + elongation);
            double vv = Math.sin(a) * (1.0 - elongation);
            double dx = uu * cos - vv * sin;
            double dz = uu * sin + vv * cos;
            double len = Math.sqrt(dx * dx + dz * dz);
            dx /= len;
            dz /= len;
            double edge = radius * len;
            double start = 0.6 * edge;
            double end = edge + reach * (0.5 + 0.5 * FluidDomains.unit(h, 67 + k));
            double y = floor + (k % 2 == 0 ? 6.0 : height * (0.3 + 0.3 * FluidDomains.unit(h, 73 + k)));
            double ey = y + (FluidDomains.unit(h, 79 + k) - 0.5) * 40.0;
            double r = 4.0 + 6.0 * FluidDomains.unit(h, 85 + k);
            // Under the cover of rock at its far end.
            double ground = this.ground.estimate(x + dx * end, z + dz * end) - 20.0;
            ey = Math.min(ey, ground - CaveNetwork.COVER - 4.0 - r);
            if (zone == DEEP) {
                ey = Math.min(ey, this.domains.aquitard(x + dx * end, z + dz * end) - FluidDomains.AQUITARD_HALF - 6.0 - r);
            } else {
                ey = Math.max(ey, this.domains.aquitard(x + dx * end, z + dz * end) + FluidDomains.AQUITARD_HALF + 6.0 + r);
            }
            out.add(CavePrimitive.segment(x + dx * start, y, z + dz * start, x + dx * end, ey, z + dz * end, r * 1.4, r, 0.8,
                    0.0, 0.0, 0.7, 1.0 + 0.12 * r, h + 90 + k));
        }
        // A cenote: where the vault comes within ~110 blocks of the surface over a dry floor or a lake.
        if (zone == UPPER && FluidDomains.unit(h, 95) < 0.3 * this.settings.entrances() && domain.fluid != CaveFluid.LAVA) {
            double a = 2.0 * Math.PI * FluidDomains.unit(h, 96);
            double off = 0.35 * radius * FluidDomains.unit(h, 97);
            double cx = x + Math.cos(a) * off;
            double cz = z + Math.sin(a) * off;
            double ground = this.ground.estimate(cx, cz);
            double vault = floor + 0.9 * height;
            if (ground > 66.0 && ground - vault < 110.0 && ground - vault > 10.0 && (domain.fluid == CaveFluid.AIR || domain.level < vault - 10)) {
                double r = clamp(0.12 * radius, 7.0, 22.0);
                out.add(CavePrimitive.funnel(cx, vault - 4.0, cz, ground + 4.0, r, ground, 1.5 + 0.1 * r, h + 98));
            }
        }
        return out.toArray(new CavePrimitive[0]);
    }
}
