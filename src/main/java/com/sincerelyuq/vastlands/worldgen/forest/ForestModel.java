package com.sincerelyuq.vastlands.worldgen.forest;

import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.clamp01;
import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.smoothstep;

import com.sincerelyuq.vastlands.worldgen.geography.GeographySample;
import com.sincerelyuq.vastlands.worldgen.hydrology.HydrologyModel;
import com.sincerelyuq.vastlands.worldgen.hydrology.WaterColumn;
import com.sincerelyuq.vastlands.worldgen.noise.NoiseMath;
import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;
import com.sincerelyuq.vastlands.worldgen.terrain.TerrainColumn;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Vastlands forests as a pure function of the seed and the position: where trees stand, of
 * which kind and size, and what shapes them. No game classes; the world is read through
 * {@link Land} and {@link Biomes}, so the model can be rendered and measured without the game.
 *
 * <p>Three scales:
 * <ol>
 *     <li>Communities: each biome belongs to a {@link ForestType} (biome tags) or to none. The
 *     communities of a 16-block grid are blended over ~40 blocks, so composition and density
 *     change gradually across biome borders, and forests thin out towards open land, the sea and
 *     biomes Vastlands does not manage. Each community responds to the climate of the geography
 *     (temperature with its lapse rate, moisture).</li>
 *     <li>Regional structure: warped noise fields of forest cover (fragmentation, ~1 700
 *     blocks), groves on open land (~170), clearings (~115) and large meadows (~480), stand age
 *     (~950: young woods to old growth) and species patches (~95: birch stands in oak woods,
 *     spruce stands in mixed forest); the terrain (local slope, exposure of ridges against
 *     sheltered valleys, the coast), the treeline (temperature) and the water (riparian
 *     woods along rivers and lakes, wet meadows on some floodplains).</li>
 *     <li>Trees: a jittered lattice of 4-block cells holds at most one tree each, accepted with
 *     the local density; a candidate yields to a larger (higher priority) neighbour closer than
 *     the mean of their spacings, so giants keep room around them and trees never touch. A second
 *     lattice of 3-block cells holds shrubs, a third of 16-block cells fallen logs. Every
 *     candidate is checked against the world: its biome, water under and beside its trunk, the
 *     local slope, cave openings under it, waterfalls near its crown.</li>
 * </ol>
 * A tree is a {@link TreeSpec}; its shape is {@link TreeShapes#shape}. Everything is
 * deterministic and order-independent: candidates depend only on their cell, and acceptance only
 * on the candidates around, never on what was generated before. Caches are per thread and keyed
 * by exact grid coordinates.
 */
public final class ForestModel {
    /** Biome categories returned by {@link Biomes#category}. */
    public static final int NONE = -1;
    /** River biomes: neither forest nor an obstacle (a riverbank takes the woods around). */
    public static final int TRANSPARENT = -2;

    /** The biome of a column at the surface, as a forest category. Thread-safe. */
    public interface Biomes {
        /** {@link ForestType} ordinal, {@link #NONE} or {@link #TRANSPARENT}. */
        int category(int x, int y, int z);

        /**
         * 0..1: how well moss blends into the biome's grass at a block (see
         * {@code GrassColors.mossBlend}: about 1 under plains, forest and birch forest grass, 0 under
         * the dark grass of dark forests and swamps). 1 where unknown.
         */
        default double mossBlend(int x, int y, int z) {
            return 1.0;
        }
    }

    /** What the forests read from the world: the other systems' models. Thread-safe. */
    public interface Land {
        /**
         * The ground surface as generated (top solid block {@code ceil(surface) - 1}), except the
         * caves' roofs over gorges: river water lies under those, so no tree stands there anyway.
         */
        double surface(int x, int z);

        /**
         * The terrain model's column at a point (height before rivers, the geography's climate and
         * wind), uncached: the forests read it on their 16-block grid only.
         */
        TerrainColumn sample(int x, int z);

        WaterColumn water(int x, int z);

        void channels(int x, int z, double reach, HydrologyModel.ChannelVisitor visitor);

        /** Falls of 6+ blocks near a point: x, z, upper and lower water top. */
        List<double[]> falls(int x, int z);

        boolean isCave(int x, int y, int z);

        /**
         * False where a natural formation (a boulder, an outcrop, a pillar, driftwood on a shore)
         * keeps a tree away: no trunk within {@code trunk} blocks of its footprint, and for tall
         * formations no crown of radius {@code crown} over it. True where there is none (and in
         * worlds without natural formations).
         */
        default boolean clearOfFormations(int x, int z, double trunk, double crown) {
            return true;
        }
    }

    static final int CELL = 4;
    static final int SHRUB_CELL = 3;
    static final int LOG_CELL = 16;
    static final int GRID = 16;
    private static final int MAIN = 0;
    private static final int SHRUB = 1;
    private static final int FALLEN = 2;
    /** Share of the lattice filled at density 1. */
    private static final double FILL = 0.92;
    /** Temperature lost per block of height (the geography's lapse rate). */
    private static final double LAPSE = com.sincerelyuq.vastlands.worldgen.geography.GeographySettings.DEFAULT.lapseRate();
    /** The forest temperature of polar lowland (taiga up to ~40 blocks above the sea in the coldest climates). */
    private static final double POLAR_FLOOR = -0.7;
    /** How far a tree's shape can reach from its root. */
    public static final int REACH = TreeShape.REACH;

    private final long seed;
    private final Land land;
    private final Biomes biomes;
    private final ForestSettings settings;
    private final int seaLevel;
    private final SimplexNoise warpA, warpB, warpC, warpD, cover, patch, clear, meadow, wetMeadow, gaps, age, micro, line, fungi;
    private final SimplexNoise[] species = new SimplexNoise[Wood.VALUES.length + 1];

    private final ThreadLocal<Caches> caches = ThreadLocal.withInitial(Caches::new);

    private static final class Caches {
        final ForestCache<Ground> grounds = new ForestCache<>(8192);
        final ForestCache<Node> nodes = new ForestCache<>(4096);
        final ForestCache<float[]> weights = new ForestCache<>(4096);
        final ForestCache<Cand> cands = new ForestCache<>(16384);
        final ForestCache<List<double[]>> falls = new ForestCache<>(64);
        final ForestCache<TreeShape> shapes = new ForestCache<>(2048);
        final float[] mix = new float[ForestType.COUNT + 1];
    }

    public ForestModel(long seed, Land land, Biomes biomes, ForestSettings settings, int seaLevel) {
        this.seed = seed;
        this.land = land;
        this.biomes = biomes;
        this.settings = settings;
        this.seaLevel = seaLevel;
        this.warpA = new SimplexNoise(seed + 1);
        this.warpB = new SimplexNoise(seed + 2);
        this.warpC = new SimplexNoise(seed + 3);
        this.warpD = new SimplexNoise(seed + 4);
        this.cover = new SimplexNoise(seed + 5);
        this.patch = new SimplexNoise(seed + 6);
        this.clear = new SimplexNoise(seed + 7);
        this.meadow = new SimplexNoise(seed + 8);
        this.wetMeadow = new SimplexNoise(seed + 9);
        this.gaps = new SimplexNoise(seed + 10);
        this.age = new SimplexNoise(seed + 11);
        this.micro = new SimplexNoise(seed + 12);
        this.line = new SimplexNoise(seed + 13);
        this.fungi = new SimplexNoise(seed + 14);
        for (int i = 0; i < this.species.length; i++) {
            this.species[i] = new SimplexNoise(seed + 40 + i);
        }
    }

    public ForestSettings settings() {
        return this.settings;
    }

    // ------------------------------------------------------------------ the 16-block grid

    /** A point of the 16-block grid: the terrain's height and the climate there. */
    private static final class Ground {
        final double height;
        final double temperature;
        final double seaLevelTemperature;
        final double moisture;
        final double windX;
        final double windZ;

        Ground(TerrainColumn c) {
            GeographySample g = c.geography();
            this.height = c.height();
            this.temperature = g.temperature();
            this.seaLevelTemperature = g.seaLevelTemperature();
            this.moisture = g.moisture();
            this.windX = g.windX();
            this.windZ = g.windZ();
        }
    }

    private Ground ground(long i, long j) {
        return this.caches.get().grounds.get(ForestCache.key(6, i, j), k -> new Ground(this.land.sample((int) (i * GRID), (int) (j * GRID))));
    }

    /** Grid offsets of the rings ~32 and ~75 blocks around a node, for the topographic position. */
    private static final int[][] RING_NEAR = {{2, 0}, {1, 2}, {-1, 2}, {-2, 0}, {-1, -2}, {1, -2}};
    private static final int[][] RING_FAR = {{4, 2}, {0, 5}, {-4, 2}, {-4, -2}, {0, -5}, {4, -2}};

    /** A node of the 16-block grid: the biome category and the terrain's position around it. */
    private static final class Node {
        int category;
        /** Topographic position: the ground minus the mean ground ~32 and ~75 blocks around. */
        double tpi;
        /** 0..1: the sea within ~32..75 blocks. */
        double coast;
    }

    private Node node(long i, long j) {
        return this.caches.get().nodes.get(ForestCache.key(0, i, j), k -> this.buildNode(i, j));
    }

    private Node buildNode(long i, long j) {
        Node n = new Node();
        int x = (int) (i * GRID);
        int z = (int) (j * GRID);
        double ground = this.land.surface(x, z);
        n.category = this.biomes.category(x, (int) Math.ceil(ground) - 1, z);
        double sum1 = 0.0;
        double sum2 = 0.0;
        int sea1 = 0;
        int sea2 = 0;
        for (int k = 0; k < 6; k++) {
            double h1 = this.ground(i + RING_NEAR[k][0], j + RING_NEAR[k][1]).height;
            double h2 = this.ground(i + RING_FAR[k][0], j + RING_FAR[k][1]).height;
            sum1 += Math.max(h1, this.seaLevel - 1);
            sum2 += Math.max(h2, this.seaLevel - 1);
            sea1 += h1 < this.seaLevel - 1 ? 1 : 0;
            sea2 += h2 < this.seaLevel - 1 ? 1 : 0;
        }
        n.tpi = ground - 0.5 * (sum1 / 6.0 + sum2 / 6.0);
        n.coast = clamp01(sea1 / 3.0 + sea2 / 9.0);
        return n;
    }

    /** Community weights of a grid node blended over the nodes within 2.5 nodes (~40 blocks). */
    private float[] weights(long i, long j) {
        return this.caches.get().weights.get(ForestCache.key(1, i, j), k -> {
            float[] w = new float[ForestType.COUNT + 1];
            double total = 0.0;
            for (int di = -2; di <= 2; di++) {
                for (int dj = -2; dj <= 2; dj++) {
                    double d2 = (di * di + dj * dj) / 6.25;
                    if (d2 >= 1.0) {
                        continue;
                    }
                    double kw = (1.0 - d2) * (1.0 - d2);
                    int c = this.node(i + di, j + dj).category;
                    if (c == TRANSPARENT) {
                        continue;
                    }
                    w[c == NONE ? ForestType.COUNT : c] += (float) kw;
                    total += kw;
                }
            }
            if (total <= 0.0) {
                w[ForestType.COUNT] = 1.0F;
            } else {
                for (int t = 0; t < w.length; t++) {
                    w[t] /= (float) total;
                }
            }
            return w;
        });
    }

    /** Community weights at a point (bilinear between grid nodes), into {@code out}. */
    private void weightsAt(double x, double z, float[] out) {
        double fx = x / GRID;
        double fz = z / GRID;
        long i = (long) Math.floor(fx);
        long j = (long) Math.floor(fz);
        float tx = (float) (fx - i);
        float tz = (float) (fz - j);
        float[] a = this.weights(i, j);
        float[] b = this.weights(i + 1, j);
        float[] c = this.weights(i, j + 1);
        float[] d = this.weights(i + 1, j + 1);
        for (int t = 0; t < out.length; t++) {
            out[t] = (a[t] * (1 - tx) + b[t] * tx) * (1 - tz) + (c[t] * (1 - tx) + d[t] * tx) * tz;
        }
    }

    private double nodeField(double x, double z, boolean coast) {
        double fx = x / GRID;
        double fz = z / GRID;
        long i = (long) Math.floor(fx);
        long j = (long) Math.floor(fz);
        double tx = fx - i;
        double tz = fz - j;
        Node a = this.node(i, j);
        Node b = this.node(i + 1, j);
        Node c = this.node(i, j + 1);
        Node d = this.node(i + 1, j + 1);
        double va = coast ? a.coast : a.tpi;
        double vb = coast ? b.coast : b.tpi;
        double vc = coast ? c.coast : c.tpi;
        double vd = coast ? d.coast : d.tpi;
        return (va * (1 - tx) + vb * tx) * (1 - tz) + (vc * (1 - tx) + vd * tx) * tz;
    }

    // ------------------------------------------------------------------ the forest at a point

    /** Everything the density and the choice of a tree depend on at one point. */
    static final class Point {
        final float[] weights = new float[ForestType.COUNT + 1];
        double share;
        ForestType dominant;
        /** The prevailing wind (unit vector). */
        double windX;
        double windZ;
        /** The climate's temperature (vanilla's scale, clamped like the biomes'). */
        double temperature;
        /** Unclamped temperature at the ground for the trees: lapse rate from the local height, polar lowlands kept wooded. */
        double forestTemperature;
        double moisture;
        double density;
        double base;
        double age;
        double old;
        double young;
        double clearing;
        double riparian;
        double waterDistance = Double.POSITIVE_INFINITY;
        double waterDirX;
        double waterDirZ;
        double treeline;
        double exposure;
        double valley;
        double coast;
        double slope;
        double size;
        double groves;
    }

    /** Warped coordinates: noise fields meander instead of showing their lattice. */
    private double wx(double x, double z) {
        return x + 38.0 * this.warpA.sample(x / 420.0, z / 420.0) + 7.0 * this.warpC.sample(x / 90.0, z / 90.0);
    }

    private double wz(double x, double z) {
        return z + 38.0 * this.warpB.sample(x / 420.0, z / 420.0) + 7.0 * this.warpD.sample(x / 90.0, z / 90.0);
    }

    /** The forest at a point. {@code exact} also reads the water and the local slope there. */
    private void evaluate(double x, double z, Point p, boolean water) {
        this.weightsAt(x, z, p.weights);
        double share = 0.0;
        double best = 0.0;
        ForestType dominant = null;
        double capacity = 0.0;
        double groves = 0.0;
        double maturity = 0.0;
        // Climate and height from the 16-block grid (bilinear): both change slowly, and the grid
        // costs one terrain query per 256 columns.
        double fx = x / GRID;
        double fz = z / GRID;
        long gi = (long) Math.floor(fx);
        long gj = (long) Math.floor(fz);
        double tx = fx - gi;
        double tz = fz - gj;
        Ground ga = this.ground(gi, gj);
        Ground gb = this.ground(gi + 1, gj);
        Ground gc = this.ground(gi, gj + 1);
        Ground gd = this.ground(gi + 1, gj + 1);
        double wa = (1 - tx) * (1 - tz);
        double wb = tx * (1 - tz);
        double wc = (1 - tx) * tz;
        double wd = tx * tz;
        double height = wa * ga.height + wb * gb.height + wc * gc.height + wd * gd.height;
        double seaLevelTemperature = wa * ga.seaLevelTemperature + wb * gb.seaLevelTemperature + wc * gc.seaLevelTemperature + wd * gd.seaLevelTemperature;
        p.temperature = wa * ga.temperature + wb * gb.temperature + wc * gc.temperature + wd * gd.temperature;
        p.moisture = wa * ga.moisture + wb * gb.moisture + wc * gc.moisture + wd * gd.moisture;
        double windX = wa * ga.windX + wb * gb.windX + wc * gc.windX + wd * gd.windX;
        double windZ = wa * ga.windZ + wb * gb.windZ + wc * gc.windZ + wd * gd.windZ;
        double wind = Math.hypot(windX, windZ);
        p.windX = wind > 1e-9 ? windX / wind : 1.0;
        p.windZ = wind > 1e-9 ? windZ / wind : 0.0;
        // The geography clamps its temperature at -1, so high ground all reads the same: the trees use
        // the sea-level temperature cooled by the local height above the sea, which keeps falling.
        double altitude = Math.max(0.0, height - this.seaLevel - 12.0);
        p.forestTemperature = Math.max(seaLevelTemperature - LAPSE * altitude, POLAR_FLOOR - LAPSE * Math.max(0.0, altitude - 40.0));
        double moist = smoothstep(-0.55, 0.45, p.moisture);
        for (int t = 0; t < ForestType.COUNT; t++) {
            double w = p.weights[t];
            if (w <= 0.0) {
                continue;
            }
            ForestType type = ForestType.VALUES[t];
            share += w;
            if (w > best) {
                best = w;
                dominant = type;
            }
            double open = type.closed() ? 0.72 + 0.4 * moist : 0.55 + 0.9 * moist;
            capacity += w * Math.min(1.0, type.capacity * open);
            groves += w * type.groves;
            maturity += w * type.maturity;
        }
        p.share = share;
        p.dominant = dominant;
        if (share <= 0.0) {
            p.density = 0.0;
            p.base = 0.0;
            return;
        }
        groves /= share;
        maturity /= share;
        p.groves = groves;
        double qx = this.wx(x, z);
        double qz = this.wz(x, z);

        // Regional structure.
        double reg = 0.55 + 0.5 * smoothstep(-0.6, 0.6, NoiseMath.fbm(this.cover, x / 1700.0, z / 1700.0, 2, 0.5));
        double patchRaw = NoiseMath.fbm(this.patch, qx / 190.0, qz / 190.0, 2, 0.45);
        double grove = smoothstep(0.36, 0.7, patchRaw);
        double d = capacity * (1.0 - 0.65 * groves) * (0.85 + 0.3 * (0.5 + 0.5 * patchRaw)) + groves * 0.45 * grove * share;
        d *= Math.min(1.0, reg + 0.25 * groves);

        double ageRaw = NoiseMath.fbm(this.age, qx / 950.0, qz / 950.0, 2, 0.5) + (maturity - 0.5) * 0.8 + this.settings.age();
        p.age = smoothstep(-0.65, 0.65, ageRaw);
        p.old = smoothstep(0.6, 0.92, p.age);
        p.young = 1.0 - smoothstep(0.1, 0.4, p.age);
        d *= 1.0 + 0.12 * p.young - 0.1 * p.old;

        // Clearings: small openings, large meadows, gaps where old trees fell.
        double k1 = smoothstep(0.48, 0.62, NoiseMath.fbm(this.clear, qx / 115.0, qz / 115.0, 2, 0.45));
        double k2 = smoothstep(0.52, 0.66, NoiseMath.fbm(this.meadow, qx / 480.0, qz / 480.0, 2, 0.5));
        // Gaps where old trees fell: only the noise's peaks, so they open as round patches.
        double k3 = smoothstep(0.55, 0.78, this.gaps.sample(qx / 30.0, qz / 30.0)) * p.old * 0.8;
        double strength = 1.0 - 0.5 * groves;
        p.clearing = Math.max(Math.max(k1, k2), k3) * strength * this.settings.clearings();
        d *= 1.0 - 0.96 * p.clearing;

        // Terrain: the treeline, exposed crests, sheltered valleys, the coast.
        double cold = this.line.sample(x / 320.0, z / 320.0) * 0.1;
        p.treeline = smoothstep(-1.15, -0.78, p.forestTemperature + cold);
        double tpi = this.nodeField(x, z, false);
        double coldness = smoothstep(-0.2, -0.9, p.forestTemperature);
        p.exposure = smoothstep(8.0, 30.0, tpi);
        p.valley = smoothstep(-6.0, -24.0, tpi);
        p.coast = this.nodeField(x, z, true);
        d *= p.treeline;
        d *= 1.0 - 0.5 * p.exposure * (0.3 + 0.7 * coldness);
        d *= 1.0 + 0.15 * p.valley;
        d *= 1.0 - 0.55 * p.coast;

        // Water: riparian woods, wet meadows on some floodplains.
        p.riparian = 0.0;
        p.waterDistance = Double.POSITIVE_INFINITY;
        if (water) {
            this.water(x, z, p);
            if (p.waterDistance < 18.0) {
                p.riparian = 1.0 - smoothstep(2.5, 18.0, p.waterDistance);
                double boost = (groves > 0.5 ? 0.6 : 0.3) * this.settings.riparian();
                double wet = p.riparian * smoothstep(0.45, 0.7, this.wetMeadow.sample(qx / 260.0, qz / 260.0));
                d = d + (share * boost * p.riparian) * (1.0 - d);
                d *= 1.0 - 0.8 * wet;
            }
        }
        d *= 0.88 + 0.24 * (0.5 + 0.5 * this.micro.sample(x / 19.0, z / 19.0));
        d *= this.settings.density();
        p.base = d;
        p.density = clamp01(d);
        // Size: colder, drier, more exposed and steeper ground grows smaller trees.
        p.size = (0.55 + 0.45 * smoothstep(-1.05, -0.5, p.forestTemperature)) * (0.82 + 0.18 * moist) * (1.0 - 0.25 * p.exposure * (0.4 + 0.6 * coldness))
                * (1.0 - 0.25 * p.coast) * (0.85 + 0.25 * p.age) * this.settings.treeSize();
    }

    /** Distance to the nearest river or lake edge within 24 blocks and the direction to it. */
    private void water(double x, double z, Point p) {
        final double[] best = {Double.POSITIVE_INFINITY, 0, 0};
        this.land.channels((int) Math.floor(x), (int) Math.floor(z), 24.0, (id, ax, az, bx, bz, halfWidth, bank, waterTop, lake, distance) -> {
            double edge = distance - halfWidth;
            if (edge < best[0]) {
                double dx = bx - ax;
                double dz = bz - az;
                double len2 = dx * dx + dz * dz;
                double t = len2 > 1e-9 ? ((x - ax) * dx + (z - az) * dz) / len2 : 0.0;
                t = Math.max(0.0, Math.min(1.0, t));
                best[0] = edge;
                best[1] = ax + t * dx - x;
                best[2] = az + t * dz - z;
            }
        });
        p.waterDistance = Math.max(0.0, best[0]);
        double n = Math.hypot(best[1], best[2]);
        p.waterDirX = n > 1e-6 ? best[1] / n : 0.0;
        p.waterDirZ = n > 1e-6 ? best[2] / n : 0.0;
    }

    /** The forest at a column (for the F3 screen, previews and other systems). */
    public ForestSample sample(int x, int z) {
        return this.sample(x, z, true);
    }

    /** The forest at a column; {@code slope} also measures the local slope (four more surface queries). */
    public ForestSample sample(int x, int z, boolean slope) {
        Point p = new Point();
        this.evaluate(x + 0.5, z + 0.5, p, true);
        p.slope = slope ? this.localSlope(x, z, 2) : Double.NaN;
        return new ForestSample(p.dominant, p.share, p.density, p.age, p.clearing, p.riparian, p.treeline, p.exposure, p.slope, p.temperature,
                p.moisture, p.old);
    }

    /** 0..1: how much of the smoothed biome neighbourhood of a column belongs to forest communities. */
    public double share(int x, int z) {
        float[] w = this.caches.get().mix;
        this.weightsAt(x + 0.5, z + 0.5, w);
        double share = 0.0;
        for (int t = 0; t < ForestType.COUNT; t++) {
            share += w[t];
        }
        return share;
    }

    /** 0..1: how well moss blends into the biome's grass at a block (see {@link Biomes#mossBlend}). */
    public double mossBlend(int x, int y, int z) {
        return this.biomes.mossBlend(x, y, z);
    }

    /** 0..1: how mossy a community's floor grows where it is wet and old (old growth taiga most, open land not at all). */
    public static double mossEcology(ForestType type) {
        return type == null ? 0.0 : Choices.mossEcology(type);
    }

    /** The forest category of the surface biome of a column ({@link ForestType} ordinal, {@link #NONE}, {@link #TRANSPARENT}). */
    public int category(int x, int y, int z) {
        return this.biomes.category(x, y, z);
    }

    /** Tree density at a column, 0..1 ({@code ForestSystem.treeDensity}). */
    public double density(int x, int z) {
        Point p = new Point();
        this.evaluate(x + 0.5, z + 0.5, p, true);
        return p.density;
    }

    private double localSlope(int x, int z, int r) {
        double ex = this.land.surface(x + r, z) - this.land.surface(x - r, z);
        double ez = this.land.surface(x, z + r) - this.land.surface(x, z - r);
        return Math.sqrt(ex * ex + ez * ez) / (2.0 * r);
    }

    // ------------------------------------------------------------------ candidates

    /** One lattice candidate: its position, the tree it would be, and whether it stands. */
    private static final class Cand {
        int layer;
        long i;
        long j;
        int x;
        int z;
        boolean pass;
        double priority;
        TreeForm form;
        /** The form's spacing, wider for big mushroom caps. */
        double spacing;
        Wood wood;
        ForestType type;
        double height;
        double crown;
        double vines;
        int flags;
        double leanX;
        double leanZ;
        double windX;
        double windZ;
        long shapeSeed;
        /** -1 unknown, 0 rejected by the world, 1 fits. */
        int site = -1;
        int y;
        TreeSpec spec;
        boolean decided;
    }

    private static int cellSize(int layer) {
        return layer == MAIN ? CELL : (layer == SHRUB ? SHRUB_CELL : LOG_CELL);
    }

    private Cand cand(int layer, long i, long j) {
        return this.caches.get().cands.get(ForestCache.key(2 + layer, i, j), k -> this.buildCand(layer, i, j));
    }

    private long cellSeed(int layer, long i, long j) {
        return SimplexNoise.mix(this.seed ^ (layer + 1) * 0x632BE59BD9B4E019L ^ i * 0x9E3779B97F4A7C15L ^ j * 0xC2B2AE3D27D4EB4FL);
    }

    private Cand buildCand(int layer, long i, long j) {
        Cand c = new Cand();
        c.layer = layer;
        c.i = i;
        c.j = j;
        int size = cellSize(layer);
        TreeRandom r = new TreeRandom(this.cellSeed(layer, i, j));
        c.x = (int) (i * size + Math.floor(r.next() * size));
        c.z = (int) (j * size + Math.floor(r.next() * size));
        double u = r.next();
        Point p = new Point();
        this.evaluate(c.x + 0.5, c.z + 0.5, p, layer != FALLEN);
        if (p.share <= 0.0 || p.density <= 0.0) {
            return c;
        }
        double chance;
        if (layer == MAIN) {
            chance = p.density * FILL;
        } else if (layer == SHRUB) {
            chance = this.shrubChance(p);
        } else {
            chance = this.fallenChance(p);
        }
        if (u >= chance) {
            return c;
        }
        ForestType type = this.pickType(p, r);
        if (type == null) {
            return c;
        }
        c.type = type;
        c.shapeSeed = r.nextLong();
        if (layer == MAIN) {
            Choices.main(this, c, type, p, r);
        } else if (layer == SHRUB) {
            Choices.shrub(c, type, p, r);
        } else {
            int ground = groundTop(this.land.surface(c.x, c.z));
            Choices.fallen(c, type, p, r, this.biomes.mossBlend(c.x, ground, c.z));
        }
        if (c.form == null) {
            return c;
        }
        c.windX = p.windX;
        c.windZ = p.windZ;
        // Lean: riparian trees towards the water, others a little at random (mushroom stems more).
        double lean = r.range(-0.6, 0.6) * (c.form.mushroom() ? 2.4 : 1.0);
        double la = r.next() * Math.PI * 2;
        c.leanX = Math.cos(la) * lean;
        c.leanZ = Math.sin(la) * lean;
        if (layer == MAIN && p.waterDistance < 7.0 && p.waterDistance >= 1.0 && leans(c.form)) {
            double amount = r.range(0.8, 2.6) * (1.0 - p.waterDistance / 9.0) * (c.height / 10.0);
            c.leanX += p.waterDirX * amount;
            c.leanZ += p.waterDirZ * amount;
        }
        c.pass = true;
        c.spacing = c.form.mushroom() ? c.form.spacing + Math.max(0.0, c.crown - 3.4) : c.form.spacing;
        c.priority = c.spacing + r.next() * 0.999;
        return c;
    }

    private static boolean leans(TreeForm form) {
        return form == TreeForm.BROADLEAF || form == TreeForm.BIRCH || form == TreeForm.SWAMP || form == TreeForm.TALL_JUNGLE;
    }

    private double shrubChance(Point p) {
        double rate = 0.0;
        for (int t = 0; t < ForestType.COUNT; t++) {
            if (p.weights[t] > 0.0) {
                rate += p.weights[t] * Choices.shrubRate(ForestType.VALUES[t]);
            }
        }
        rate /= Math.max(1e-6, p.share);
        // Shrubs gather at edges and in clearings' margins, thin out in deep shade.
        double edge = smoothstep(0.04, 0.25, p.density) * (1.0 - smoothstep(0.45, 0.85, p.density));
        return rate * (0.35 + 2.4 * edge + 0.5 * p.riparian) * p.treeline * this.settings.shrubs();
    }

    private double fallenChance(Point p) {
        double rate = 0.0;
        for (int t = 0; t < ForestType.COUNT; t++) {
            if (p.weights[t] > 0.0) {
                ForestType type = ForestType.VALUES[t];
                rate += p.weights[t] * (type.closed() ? 0.4 : (type == ForestType.SWAMP ? 0.3 : 0.08));
            }
        }
        rate /= Math.max(1e-6, p.share);
        return rate * p.density * (0.25 + 1.1 * p.old) * this.settings.deadwood();
    }

    private ForestType pickType(Point p, TreeRandom r) {
        double u = r.next() * p.share;
        for (int t = 0; t < ForestType.COUNT; t++) {
            u -= p.weights[t];
            if (u < 0.0 && p.weights[t] > 0.0) {
                return ForestType.VALUES[t];
            }
        }
        return p.dominant;
    }

    /** 0.35..1.65: patches of one wood within mixed stands. */
    double speciesPatch(Wood wood, double x, double z) {
        double qx = this.wx(x, z);
        double qz = this.wz(x, z);
        return 0.35 + 1.3 * smoothstep(-0.5, 0.5, this.species[wood.ordinal()].sample(qx / 95.0, qz / 95.0));
    }

    /** 0.25..1.75 (mean about 1): the patches of huge mushrooms in dark forests (~70 blocks), groups of caps among the dark oaks. */
    double mushroomPatch(double x, double z) {
        double qx = this.wx(x, z);
        double qz = this.wz(x, z);
        return 0.25 + 1.5 * smoothstep(-0.35, 0.55, NoiseMath.fbm(this.fungi, qx / 70.0, qz / 70.0, 2, 0.5));
    }

    /** 0.35..1.65: patches of pines among spruces. */
    double pinePatch(double x, double z) {
        double qx = this.wx(x, z);
        double qz = this.wz(x, z);
        return 0.35 + 1.3 * smoothstep(-0.5, 0.5, this.species[Wood.VALUES.length].sample(qx / 110.0, qz / 110.0));
    }

    // ------------------------------------------------------------------ the world under a tree

    /** Whether the world has room for a candidate, and the Y of its root. Cached in the candidate. */
    private boolean site(Cand c) {
        if (c.site >= 0) {
            return c.site == 1;
        }
        c.site = this.checkSite(c) ? 1 : 0;
        return c.site == 1;
    }

    private static int groundTop(double surface) {
        return (int) Math.ceil(surface) - 1;
    }

    private boolean checkSite(Cand c) {
        return this.siteProblem(c) == null;
    }

    /** Null if the world has room for the candidate (and sets its root Y), else what is in the way. */
    private String siteProblem(Cand c) {
        TreeForm form = c.form;
        int w = form.wide() ? 2 : 1;
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        boolean swamp = c.type == ForestType.SWAMP;
        int rootTop = Integer.MIN_VALUE;
        for (int a = 0; a < w; a++) {
            for (int b = 0; b < w; b++) {
                int x = c.x + a;
                int z = c.z + b;
                WaterColumn water = this.land.water(x, z);
                int top;
                if (water.kind() == WaterColumn.Kind.DRY) {
                    top = groundTop(this.land.surface(x, z));
                } else if (swamp && water.kind() == WaterColumn.Kind.SEA && water.waterTop() - water.groundTop() <= 1
                        && form != TreeForm.UPROOTED && form != TreeForm.DEBRIS) {
                    // Swamp oaks stand in shallow water, logs and stumps lie in it.
                    top = water.groundTop();
                } else {
                    return "water at the trunk";
                }
                if (a == 0 && b == 0) {
                    rootTop = top;
                }
                lowest = Math.min(lowest, top);
                highest = Math.max(highest, top);
            }
        }
        if (highest - lowest > 1 || lowest < this.seaLevel - 3) {
            return "uneven trunk ground or below sea level";
        }
        int category = this.biomes.category(c.x, rootTop, c.z);
        if (category == NONE) {
            return "unmanaged biome";
        }
        // Slope: from the ground two blocks around the trunk.
        double slope = this.localSlope(c.x, c.z, 2);
        double limit = switch (form) {
            case ANCIENT, MEGA_CONIFER, MEGA_PINE, MEGA_JUNGLE -> 0.45;
            case DARK_OAK, FALLEN, UPROOTED, DEBRIS, HUGE_BROWN_MUSHROOM, HUGE_RED_MUSHROOM -> 0.55;
            case GIANT_MUSHROOM -> 0.45;
            case BUSH, KRUMMHOLZ -> 1.25;
            default -> 0.95;
        };
        if (slope > limit) {
            return "slope";
        }
        // Not on an eroded bank: water beside the trunk must lie below its ground.
        for (int k = 0; k < 4; k++) {
            int x = c.x + (k == 0 ? -1 : (k == 1 ? w : 0));
            int z = c.z + (k == 2 ? -1 : (k == 3 ? w : 0));
            WaterColumn beside = this.land.water(x, z);
            if (beside.wet() && !swamp && beside.waterTop() >= lowest) {
                return "eroded bank";
            }
        }
        // Natural formations (boulders, outcrops, pillars, driftwood) keep trunks, logs and crowns away.
        double trunkKeep = form.deadwood() ? (form == TreeForm.STUMP ? 1.2 : c.height + 2.5) : 0.5 * w + 1.0;
        if (!this.land.clearOfFormations(c.x, c.z, trunkKeep, form.lowGrowth() ? 0.0 : c.crown)) {
            return "natural formation";
        }
        // Caves: nothing open under the trunk, and big trees not at the lip of an opening.
        for (int a = 0; a < w; a++) {
            for (int b = 0; b < w; b++) {
                for (int dy : new int[] {0, -1, -3, -6}) {
                    if (this.land.isCave(c.x + a, lowest + dy, c.z + b)) {
                        return "cave under the trunk";
                    }
                }
            }
        }
        boolean lying = form.deadwood() && form != TreeForm.STUMP;
        if (c.crown >= 4.5 || lying) {
            double ring = lying ? 2.0 : 3.0;
            for (int k = 0; k < 6; k++) {
                double a = k * (Math.PI / 3);
                int x = c.x + (int) Math.round(Math.cos(a) * ring);
                int z = c.z + (int) Math.round(Math.sin(a) * ring);
                int top = groundTop(this.land.surface(x, z));
                if (this.land.isCave(x, top - 1, z) || this.land.isCave(x, top - 4, z)) {
                    return "cave at the lip";
                }
            }
        }
        // Waterfalls: keep crowns away from curtains and their pools.
        if (!form.deadwood() && form != TreeForm.BUSH) {
            double keep = c.crown + 8.0;
            // The falls of every 512-block region the kept distance reaches.
            for (int rx = Math.floorDiv(c.x - (int) keep, 512); rx <= Math.floorDiv(c.x + (int) keep, 512); rx++) {
                for (int rz = Math.floorDiv(c.z - (int) keep, 512); rz <= Math.floorDiv(c.z + (int) keep, 512); rz++) {
                    for (double[] f : this.falls(rx * 512, rz * 512)) {
                        if (Math.abs(f[0] - c.x) < keep && Math.abs(f[1] - c.z) < keep && Math.hypot(f[0] - c.x, f[1] - c.z) < keep) {
                            return "waterfall";
                        }
                    }
                }
            }
        }
        if (lying && !this.fallenFits(c, lowest)) {
            return "fallen log ground";
        }
        c.y = lowest + 1;
        return null;
    }

    /**
     * Lying deadwood needs level dry ground under every block of its lowest layer (its shape decides
     * the direction), swamp logs also shallow water. A broken log's far piece may rest one block up
     * or down: the ground under it decides its step ({@link TreeSpec#step}).
     */
    private boolean fallenFits(Cand c, int top) {
        TreeShape shape = this.shapeOf(c, top + 1);
        boolean broken = c.form == TreeForm.FALLEN && (c.flags >>> TreeSpec.STYLE_SHIFT & 15) == TreeShapes.LOG_BROKEN;
        int split = broken ? TreeShapes.breakIndex(c.height, c.shapeSeed) : Integer.MAX_VALUE;
        boolean swamp = c.type == ForestType.SWAMP && c.form == TreeForm.FALLEN;
        int far = Integer.MIN_VALUE;
        for (int k = 0; k < shape.size(); k++) {
            int pos = shape.positions[k];
            if (TreeShape.dy(pos) != 0) {
                continue;
            }
            int x = c.x + TreeShape.dx(pos);
            int z = c.z + TreeShape.dz(pos);
            WaterColumn water = this.land.water(x, z);
            int g;
            if (water.kind() == WaterColumn.Kind.DRY) {
                g = groundTop(this.land.surface(x, z));
            } else if (swamp && water.kind() == WaterColumn.Kind.SEA && water.waterTop() - water.groundTop() <= 1) {
                g = water.groundTop();
            } else {
                return false;
            }
            if (Math.abs(TreeShape.dx(pos)) + Math.abs(TreeShape.dz(pos)) > split) {
                if (far == Integer.MIN_VALUE) {
                    far = g;
                } else if (g != far) {
                    return false;
                }
            } else if (g != top) {
                // Level ground under every block, so the log never bridges a dip.
                return false;
            }
            if (this.land.isCave(x, g, z) || this.land.isCave(x, g - 2, z)) {
                return false;
            }
        }
        if (broken && far != Integer.MIN_VALUE) {
            int step = far - top;
            if (Math.abs(step) > 1) {
                return false;
            }
            c.flags |= (step == 1 ? 1 : (step == -1 ? 2 : 0)) << TreeSpec.STEP_SHIFT;
        }
        return true;
    }

    private List<double[]> falls(int x, int z) {
        int rx = Math.floorDiv(x, 512);
        int rz = Math.floorDiv(z, 512);
        return this.caches.get().falls.get(ForestCache.key(5, rx, rz), k -> this.land.falls(rx * 512 + 256, rz * 512 + 256));
    }

    // ------------------------------------------------------------------ acceptance

    /** The candidate's tree if it stands, else null. */
    private TreeSpec accepted(Cand c) {
        if (c.decided) {
            return c.spec;
        }
        c.decided = true;
        if (!c.pass || !this.site(c)) {
            return null;
        }
        if (c.layer == MAIN) {
            // Yield to a larger neighbour closer than the mean of the two spacings.
            for (long di = -2; di <= 2; di++) {
                for (long dj = -2; dj <= 2; dj++) {
                    if (di == 0 && dj == 0) {
                        continue;
                    }
                    Cand n = this.cand(MAIN, c.i + di, c.j + dj);
                    if (!n.pass || n.priority <= c.priority) {
                        continue;
                    }
                    double keep = 0.5 * (c.spacing + n.spacing);
                    double dx = n.x - c.x;
                    double dz = n.z - c.z;
                    if (dx * dx + dz * dz < keep * keep && this.site(n)) {
                        return null;
                    }
                }
            }
        } else if (c.layer == SHRUB) {
            // Shrubs keep clear of trunks and of each other.
            if (this.nearTrunk(c.x, c.z, 1.0, 1.0)) {
                return null;
            }
            for (long di = -1; di <= 1; di++) {
                for (long dj = -1; dj <= 1; dj++) {
                    if (di == 0 && dj == 0) {
                        continue;
                    }
                    Cand n = this.cand(SHRUB, c.i + di, c.j + dj);
                    if (n.pass && n.priority > c.priority && Math.hypot(n.x - c.x, n.z - c.z) < 2.6 && this.site(n)) {
                        return null;
                    }
                }
            }
        } else {
            // Fallen logs lie between trunks.
            TreeShape shape = this.shapeOf(c, c.y);
            for (int k = 0; k < shape.size(); k++) {
                int pos = shape.positions[k];
                if (TreeShape.dy(pos) == 0 && this.nearTrunk(c.x + TreeShape.dx(pos), c.z + TreeShape.dz(pos), 1.6, 0.0)) {
                    return null;
                }
            }
        }
        c.spec = new TreeSpec(ForestCache.key(2 + c.layer, c.i, c.j), c.x, c.z, c.y, c.form, c.wood, c.height, c.crown, c.leanX, c.leanZ,
                c.windX, c.windZ, c.vines, c.flags, c.type, c.shapeSeed);
        return c.spec;
    }

    /** True if an accepted lattice tree's trunk stands within {@code extra} + half its spacing × {@code share} of a column. */
    private boolean nearTrunk(int x, int z, double extra, double share) {
        long i0 = Math.floorDiv(x - 5, CELL);
        long i1 = Math.floorDiv(x + 5, CELL);
        long j0 = Math.floorDiv(z - 5, CELL);
        long j1 = Math.floorDiv(z + 5, CELL);
        for (long i = i0; i <= i1; i++) {
            for (long j = j0; j <= j1; j++) {
                Cand n = this.cand(MAIN, i, j);
                if (!n.pass) {
                    continue;
                }
                double keep = extra + share * 0.5 * n.spacing + (n.form.wide() ? 0.7 : 0.0);
                double cx = n.x + (n.form.wide() ? 0.5 : 0.0);
                double cz = n.z + (n.form.wide() ? 0.5 : 0.0);
                if (Math.abs(cx - x) <= keep + 0.5 && Math.abs(cz - z) <= keep + 0.5 && Math.hypot(cx - x, cz - z) <= keep + 0.5
                        && this.accepted(n) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ queries

    /** Why a lattice candidate stands or not (diagnostics). */
    public String explain(int layer, long i, long j) {
        Cand c = this.cand(layer, i, j);
        StringBuilder out = new StringBuilder(String.format(java.util.Locale.ROOT, "cell %d %d %d at %d %d: pass %s form %s %s h %.2f r %.2f prio %.3f",
                layer, i, j, c.x, c.z, c.pass, c.form, c.wood, c.height, c.crown, c.priority));
        if (c.pass) {
            out.append(" site ").append(this.site(c)).append(" (").append(this.siteProblem(c)).append(") y ").append(c.y);
            if (layer == MAIN) {
                for (long di = -2; di <= 2; di++) {
                    for (long dj = -2; dj <= 2; dj++) {
                        Cand n = this.cand(MAIN, i + di, j + dj);
                        if ((di != 0 || dj != 0) && n.pass && n.priority > c.priority) {
                            double keep = 0.5 * (c.spacing + n.spacing);
                            double d = Math.hypot(n.x - c.x, n.z - c.z);
                            if (d < keep) {
                                out.append(String.format(java.util.Locale.ROOT, "%n  yields? to %d %d at %d %d (%s, d %.1f < %.1f): site %s (%s)", i + di, j + dj, n.x, n.z, n.form, d, keep, this.site(n), this.siteProblem(n)));
                            }
                        }
                    }
                }
            }
            out.append(" accepted ").append(this.accepted(c) != null);
        }
        return out.toString();
    }

    /**
     * Every tree, shrub and fallen log whose shape can reach into the box (inclusive), in a fixed
     * order (layer, then cells by x, then z).
     */
    public void trees(int minX, int minZ, int maxX, int maxZ, Consumer<TreeSpec> out) {
        for (int layer = MAIN; layer <= FALLEN; layer++) {
            int size = cellSize(layer);
            int reach = layerReach(layer);
            long i0 = Math.floorDiv(minX - reach, size);
            long i1 = Math.floorDiv(maxX + reach, size);
            long j0 = Math.floorDiv(minZ - reach, size);
            long j1 = Math.floorDiv(maxZ + reach, size);
            for (long i = i0; i <= i1; i++) {
                for (long j = j0; j <= j1; j++) {
                    Cand c = this.cand(layer, i, j);
                    if (!c.pass) {
                        continue;
                    }
                    // Only trees whose form can reach the box (most of the cells around hold smaller ones).
                    int r = Math.min(c.form.reach, reach);
                    if (c.x < minX - r || c.x > maxX + r || c.z < minZ - r || c.z > maxZ + r) {
                        continue;
                    }
                    TreeSpec spec = this.accepted(c);
                    if (spec != null) {
                        out.accept(spec);
                    }
                }
            }
        }
    }

    /** How far from their roots the trees of a lattice may reach: what {@link #trees} looks around a box. */
    private static int layerReach(int layer) {
        return layer == MAIN ? REACH : (layer == SHRUB ? 4 : TreeForm.FALLEN.reach);
    }

    /** How far the blocks of a tree may lie from its root: its form's reach, within its lattice's. */
    static int reach(TreeSpec spec) {
        int layer = (int) (spec.key() >>> 58) - 2;
        int reach = spec.form().reach;
        return layer >= MAIN && layer <= FALLEN ? Math.min(reach, layerReach(layer)) : reach;
    }

    /** The shape of a tree (cached per thread by the tree's key). */
    public TreeShape shape(TreeSpec spec) {
        // The flags take part in the key: a broken log's shape is built once before its step is known.
        return this.caches.get().shapes.get(spec.key() ^ SimplexNoise.mix(spec.flags() + 0x5348415045L), k -> TreeShapes.shape(spec));
    }

    private TreeShape shapeOf(Cand c, int y) {
        return this.shape(new TreeSpec(ForestCache.key(2 + c.layer, c.i, c.j), c.x, c.z, y, c.form, c.wood, c.height, c.crown, c.leanX, c.leanZ,
                c.windX, c.windZ, c.vines, c.flags, c.type, c.shapeSeed));
    }

    // ------------------------------------------------------------------ what grows where

    /** Sets a candidate's tree. */
    static void set(Cand c, TreeForm form, Wood wood, double height, double crown) {
        c.form = form;
        c.wood = wood;
        c.height = Math.max(1.0, Math.min(height, 56.0));
        c.crown = Math.max(0.8, Math.min(crown, REACH - 2.0));
    }

    /** The species, forms and sizes of every community. */
    private static final class Choices {
        static double shrubRate(ForestType type) {
            return switch (type) {
                case JUNGLE -> 0.34;
                case SPARSE_JUNGLE -> 0.22;
                case TEMPERATE, BIRCH, OLD_BIRCH -> 0.05;
                case FLOWER, HIGHLAND, SAVANNA -> 0.05;
                case DARK, SWAMP -> 0.03;
                case BOREAL, OLD_SPRUCE, OLD_PINE, GROVE -> 0.045;
                case DRY -> 0.08;
                case GRASSLAND -> 0.012;
                case ALPINE -> 0.04;
            };
        }

        static void main(ForestModel m, Cand c, ForestType type, Point p, TreeRandom r) {
            // Forest edges: where the density is low, and where the forest meets open land, the sea
            // or biomes it does not manage, trees are smaller and shrubs commoner.
            double margin = 1.0 - smoothstep(0.55, 0.92, p.share);
            double edge = Math.max(1.0 - smoothstep(0.12, 0.55, p.density), margin);
            double s = p.size * (1.0 - 0.3 * margin);
            double old = p.old;
            double young = p.young;
            double t = p.forestTemperature;
            boolean snowy = p.temperature < -0.45;
            double x = c.x;
            double z = c.z;
            switch (type) {
                case TEMPERATE -> {
                    double cold = smoothstep(-0.1, -0.42, t);
                    double oak = 0.58 * m.speciesPatch(Wood.OAK, x, z);
                    double birch = 0.3 * m.speciesPatch(Wood.BIRCH, x, z) * (1.0 + 0.6 * p.riparian);
                    double spruce = 0.8 * cold * m.speciesPatch(Wood.SPRUCE, x, z);
                    double dark = 0.06 * smoothstep(0.1, 0.5, p.moisture) * (1.0 - cold) * m.speciesPatch(Wood.DARK_OAK, x, z);
                    Wood wood = pick(r, new Wood[] {Wood.OAK, Wood.BIRCH, Wood.SPRUCE, Wood.DARK_OAK}, oak, birch, spruce, dark);
                    if (r.chance(0.04 + 0.3 * edge)) {
                        bush(c, wood == Wood.DARK_OAK ? Wood.OAK : wood, r, s);
                    } else if (old > 0.2 && r.chance(0.03 * old)) {
                        set(c, TreeForm.SNAG, wood == Wood.SPRUCE ? Wood.SPRUCE : Wood.OAK, r.range(4, 9) * s, 1);
                    } else {
                        switch (wood) {
                            case BIRCH -> birch(c, r, s, old, young);
                            case SPRUCE -> spruce(c, r, s, old, young);
                            case DARK_OAK -> set(c, TreeForm.DARK_OAK, Wood.DARK_OAK, r.range(7, 10.5) * s, r.range(3.8, 5.0));
                            default -> oak(c, r, s, old, young, edge, p.riparian, 0.02);
                        }
                    }
                    bees(c, r, 0.004);
                }
                case FLOWER -> {
                    Wood wood = r.chance(0.55) ? Wood.OAK : Wood.BIRCH;
                    if (r.chance(0.12 + 0.2 * edge)) {
                        bush(c, wood, r, s);
                    } else if (wood == Wood.OAK) {
                        set(c, TreeForm.BROADLEAF, Wood.OAK, r.range(5.5, 9) * s, r.range(2.6, 4.0));
                    } else {
                        set(c, TreeForm.BIRCH, Wood.BIRCH, r.range(6, 10) * s, r.range(1.6, 2.3));
                    }
                    bees(c, r, 0.03);
                }
                case BIRCH -> {
                    if (r.chance(0.04 + 0.25 * edge)) {
                        bush(c, Wood.BIRCH, r, s);
                    } else if (r.chance(0.1 * m.speciesPatch(Wood.OAK, x, z))) {
                        oak(c, r, s, old, young, edge, p.riparian, 0.0);
                    } else {
                        birch(c, r, s, old, young);
                    }
                    bees(c, r, 0.004);
                }
                case OLD_BIRCH -> {
                    if (r.chance(0.03 + 0.2 * edge)) {
                        bush(c, Wood.BIRCH, r, s);
                    } else if (r.chance(0.025 + 0.03 * old)) {
                        set(c, TreeForm.SNAG, Wood.BIRCH, r.range(5, 11) * s, 1);
                    } else if (r.chance(0.06)) {
                        oak(c, r, s, old, young, edge, p.riparian, 0.02);
                    } else if (r.chance(0.85)) {
                        set(c, TreeForm.BIRCH, Wood.BIRCH, r.range(13, 20) * s, r.range(1.9, 2.7));
                    } else {
                        birch(c, r, s, old, young);
                    }
                    bees(c, r, 0.004);
                }
                case DARK -> {
                    double u = r.next();
                    // Huge mushrooms gather in patches: groups of caps of many sizes among the dark oaks.
                    double fungi = 0.06 * m.mushroomPatch(x, z) * m.settings.mushrooms();
                    double v = u - fungi;
                    if (u < fungi) {
                        mushroom(c, r, old);
                    } else if (v < 0.03) {
                        bush(c, Wood.DARK_OAK, r, s);
                    } else if (v < 0.15) {
                        oak(c, r, s, old, young, edge, p.riparian, 0.03);
                    } else if (v < 0.20) {
                        birch(c, r, s, old, young);
                    } else if (old > 0.2 && r.chance(0.5 * old)) {
                        set(c, TreeForm.DARK_OAK, Wood.DARK_OAK, r.range(10, 15) * s, r.range(5.0, 6.6));
                    } else {
                        set(c, TreeForm.DARK_OAK, Wood.DARK_OAK, r.range(6.5, 10.5) * s, r.range(3.6, 5.0));
                    }
                }
                case BOREAL -> {
                    double pine = 0.18 * m.pinePatch(x, z);
                    double birch = 0.13 * (1.0 + young) * (snowy ? 0.5 : 1.0) * m.speciesPatch(Wood.BIRCH, x, z) * (1.0 + 0.8 * p.riparian);
                    double spruce = 0.7 * m.speciesPatch(Wood.SPRUCE, x, z);
                    int k = pickIndex(r, spruce, pine, birch);
                    if (r.chance(0.05 + 0.3 * edge)) {
                        if (k == 2) {
                            bush(c, Wood.BIRCH, r, s);
                        } else {
                            set(c, TreeForm.CONIFER, Wood.SPRUCE, r.range(2.8, 4.8), r.range(1.2, 1.9));
                        }
                    } else if (old > 0.3 && r.chance(0.025 * old)) {
                        set(c, TreeForm.SNAG, Wood.SPRUCE, r.range(5, 10) * s, 1);
                    } else if (k == 0) {
                        if (old > 0.4 && r.chance(0.02 * old) && s > 0.8) {
                            set(c, TreeForm.MEGA_CONIFER, Wood.SPRUCE, r.range(20, 28) * s, r.range(4.2, 5.4));
                            c.flags |= TreeSpec.PODZOL;
                        } else {
                            spruce(c, r, s, old, young);
                        }
                    } else if (k == 1) {
                        set(c, TreeForm.PINE, Wood.SPRUCE, r.range(11, 17) * s, r.range(2.0, 3.0));
                    } else {
                        birch(c, r, s, old, young);
                    }
                }
                case OLD_SPRUCE, OLD_PINE -> {
                    boolean pines = type == ForestType.OLD_PINE;
                    double u = r.next();
                    if (u < 0.04 + 0.2 * edge) {
                        set(c, TreeForm.CONIFER, Wood.SPRUCE, r.range(3, 5.5), r.range(1.3, 2.0));
                    } else if (u < 0.07) {
                        set(c, TreeForm.SNAG, Wood.SPRUCE, r.range(6, 12) * s, 1);
                    } else if (u < 0.07 + 0.3 * (0.5 + old) && s > 0.7) {
                        if (pines) {
                            set(c, TreeForm.MEGA_PINE, Wood.SPRUCE, r.range(22, 32) * s, r.range(3.8, 5.0));
                        } else {
                            set(c, TreeForm.MEGA_CONIFER, Wood.SPRUCE, r.range(24, 34) * s, r.range(4.8, 6.4));
                        }
                        c.flags |= TreeSpec.PODZOL | TreeSpec.MOSS;
                    } else if (pines ? r.chance(0.55) : r.chance(0.12)) {
                        set(c, TreeForm.PINE, Wood.SPRUCE, r.range(12, 19) * s, r.range(2.0, 3.2));
                    } else {
                        set(c, TreeForm.CONIFER, Wood.SPRUCE, r.range(10, 18) * s, r.range(2.6, 3.9));
                    }
                }
                case GRASSLAND -> {
                    if (snowy) {
                        if (r.chance(0.75)) {
                            set(c, TreeForm.CONIFER, Wood.SPRUCE, r.range(5, 11) * s, r.range(1.9, 3.0));
                        } else {
                            birch(c, r, s, 0, young);
                        }
                    } else if (r.chance(0.14 + 0.1 * edge)) {
                        bush(c, Wood.OAK, r, s);
                    } else if (r.chance(0.22)) {
                        birch(c, r, s, old, young);
                    } else if (p.density < 0.08 && old > 0.3 && r.chance(0.05)) {
                        set(c, TreeForm.ANCIENT, Wood.OAK, r.range(17, 23) * s, r.range(8.5, 11.5));
                        c.flags |= TreeSpec.ROOTS;
                    } else {
                        // Open-grown oaks: short trunks and wide crowns.
                        set(c, TreeForm.BROADLEAF, Wood.OAK, r.range(6.5, 11.5) * s, r.range(3.6, 5.6));
                    }
                    if (c.form != TreeForm.BUSH) {
                        bees(c, r, 0.05);
                    }
                }
                case SAVANNA -> {
                    double u = r.next();
                    if (u < 0.1 + 0.1 * edge) {
                        bush(c, Wood.ACACIA, r, s);
                    } else if (u < 0.1 + 0.14 * (1.0 + p.riparian)) {
                        set(c, TreeForm.BROADLEAF, Wood.OAK, r.range(5, 8) * s, r.range(2.4, 3.6));
                    } else if (r.chance(0.16)) {
                        set(c, TreeForm.ACACIA, Wood.ACACIA, r.range(8.5, 11.5) * s, r.range(4.3, 5.4));
                    } else {
                        set(c, TreeForm.ACACIA, Wood.ACACIA, r.range(6, 9.5) * s, r.range(2.9, 4.3));
                    }
                }
                case JUNGLE, SPARSE_JUNGLE -> {
                    boolean dense = type == ForestType.JUNGLE;
                    double u = r.next();
                    double mega = dense ? 0.14 * (0.4 + p.age) : 0.0;
                    if (u < (dense ? 0.36 : 0.32)) {
                        bush(c, Wood.JUNGLE, r, s);
                    } else if (u < (dense ? 0.36 : 0.32) + mega && s > 0.75) {
                        set(c, TreeForm.MEGA_JUNGLE, Wood.JUNGLE, r.range(20, 31) * s, r.range(5.8, 7.8));
                        c.vines = r.range(0.6, 1.0);
                    } else if (r.chance(dense ? 0.12 : 0.2)) {
                        set(c, TreeForm.BROADLEAF, Wood.OAK, r.range(8, 12) * s, r.range(3.2, 4.6));
                        c.vines = r.range(0.3, 0.7);
                    } else {
                        set(c, TreeForm.TALL_JUNGLE, Wood.JUNGLE, r.range(dense ? 11 : 9, dense ? 20 : 15) * s, r.range(2.3, 3.2));
                        c.vines = r.range(0.35, 0.8);
                        if (r.chance(0.25)) {
                            c.flags |= TreeSpec.COCOA;
                        }
                    }
                }
                case SWAMP -> {
                    if (r.chance(0.05 + 0.1 * edge)) {
                        bush(c, Wood.OAK, r, s);
                    } else {
                        set(c, TreeForm.SWAMP, Wood.OAK, r.range(5.5, 9) * s, r.range(3.2, 4.6));
                        c.vines = r.range(0.55, 0.95);
                    }
                }
                case HIGHLAND, GROVE, ALPINE -> {
                    double cold = smoothstep(-0.55, -0.95, t);
                    double exposed = p.exposure;
                    double krumm = type == ForestType.ALPINE ? 0.75 : clamp01(0.08 + 0.7 * cold + 0.5 * exposed * cold);
                    if (r.chance(krumm)) {
                        set(c, TreeForm.KRUMMHOLZ, Wood.SPRUCE, r.range(2, 4.2), r.range(1.6, 2.8));
                    } else if (type == ForestType.HIGHLAND && !snowy && r.chance(0.35)) {
                        if (r.chance(0.3)) {
                            bush(c, Wood.OAK, r, s);
                        } else {
                            set(c, TreeForm.BROADLEAF, Wood.OAK, r.range(5, 9) * s, r.range(2.4, 3.8));
                        }
                    } else if (r.chance(type == ForestType.GROVE ? 0.15 : 0.08)) {
                        set(c, TreeForm.PINE, Wood.SPRUCE, r.range(9, 14) * s, r.range(1.8, 2.6));
                    } else {
                        set(c, TreeForm.CONIFER, Wood.SPRUCE, r.range(6, 13) * s, r.range(2.0, 3.2));
                    }
                }
                case DRY -> {
                    if (r.chance(0.3)) {
                        bush(c, Wood.OAK, r, s);
                    } else {
                        set(c, TreeForm.BROADLEAF, Wood.OAK, r.range(4, 6.5) * s, r.range(2.0, 3.0));
                    }
                }
            }
            // Riparian broadleaves carry vines.
            if (p.riparian > 0.3 && (c.form == TreeForm.BROADLEAF) && c.vines == 0.0 && r.chance(0.5)) {
                c.vines = 0.35 * p.riparian;
            }
            if (p.old > 0.5 && p.moisture > 0.0 && (c.form == TreeForm.ANCIENT || c.form == TreeForm.MEGA_CONIFER)) {
                c.flags |= TreeSpec.MOSS;
            }
        }

        static void shrub(Cand c, ForestType type, Point p, TreeRandom r) {
            double s = Math.max(0.7, p.size);
            switch (type) {
                case JUNGLE, SPARSE_JUNGLE -> bush(c, Wood.JUNGLE, r, s);
                case DARK -> bush(c, Wood.DARK_OAK, r, s);
                case BIRCH, OLD_BIRCH -> bush(c, r.chance(0.6) ? Wood.BIRCH : Wood.OAK, r, s);
                case BOREAL, OLD_SPRUCE, OLD_PINE, GROVE, ALPINE -> {
                    if (r.chance(0.7)) {
                        set(c, TreeForm.CONIFER, Wood.SPRUCE, r.range(2.5, 4.5), r.range(1.1, 1.7));
                    } else {
                        bush(c, Wood.SPRUCE, r, s);
                    }
                }
                case SAVANNA -> bush(c, r.chance(0.7) ? Wood.ACACIA : Wood.OAK, r, s);
                default -> bush(c, Wood.OAK, r, s);
            }
        }

        /**
         * The floor lattice: mostly fallen logs (plain, broken, rotting, and giant ones in old
         * growth), then stumps, woody debris and root plates of wind-thrown trees. Moss grows on
         * wet ground where it suits the biome ({@code blend}: moss against the biome's grass), rot
         * (holes, mushrooms) on wet shaded ground.
         */
        static void fallen(Cand c, ForestType type, Point p, TreeRandom r, double blend) {
            Wood wood = switch (type) {
                case BOREAL, OLD_SPRUCE, OLD_PINE, GROVE, HIGHLAND, ALPINE -> Wood.SPRUCE;
                case BIRCH, OLD_BIRCH -> Wood.BIRCH;
                case DARK -> r.chance(0.6) ? Wood.DARK_OAK : Wood.OAK;
                case JUNGLE, SPARSE_JUNGLE -> Wood.JUNGLE;
                case SAVANNA -> Wood.ACACIA;
                default -> r.chance(0.7) ? Wood.OAK : Wood.BIRCH;
            };
            double wet = smoothstep(-0.35, 0.45, p.moisture);
            double u = r.next();
            int style = TreeShapes.LOG_PLAIN;
            if (u < 0.17) {
                set(c, TreeForm.STUMP, wood, r.between(1, 2) + (r.chance(0.3 * p.old) ? 1 : 0), 1);
            } else if (u < 0.29) {
                set(c, TreeForm.DEBRIS, wood, 2, 2);
            } else if (u < 0.29 + 0.07 * (0.4 + p.old) && type.closed()) {
                set(c, TreeForm.UPROOTED, wood, Math.min(8, r.between(4, 6) + (int) Math.round(2.0 * p.old)), 3);
            } else {
                boolean big = type == ForestType.OLD_SPRUCE || type == ForestType.OLD_PINE || type == ForestType.DARK || type == ForestType.TEMPERATE;
                if (big && p.old > 0.5 && r.chance(0.2 * p.old)) {
                    style = TreeShapes.LOG_GIANT;
                    set(c, TreeForm.FALLEN, wood, r.between(6, 8), 1);
                } else {
                    double v = r.next();
                    style = v < 0.22 ? TreeShapes.LOG_BROKEN : (v < 0.22 + 0.3 * wet * (0.4 + p.old) ? TreeShapes.LOG_ROTTEN : TreeShapes.LOG_PLAIN);
                    set(c, TreeForm.FALLEN, wood, r.between(3, 5) + (int) Math.round(3.0 * p.old), 1);
                }
            }
            c.flags |= style << TreeSpec.STYLE_SHIFT;
            double moss = mossEcology(type) * wet * (0.5 + 0.5 * p.old) * Math.max(blend, 0.45);
            if (r.chance(Math.min(1.0, 1.6 * moss))) {
                c.flags |= TreeSpec.MOSS;
            }
            if (wet > 0.3 && (type.closed() || type == ForestType.SWAMP) && r.chance(0.3 + 0.4 * p.old)) {
                c.flags |= TreeSpec.ROT;
            }
        }

        /** 0..1: how mossy a community's floor grows where it is wet and old (old growth taiga most, open land not at all). */
        static double mossEcology(ForestType type) {
            return switch (type) {
                case OLD_SPRUCE, OLD_PINE -> 1.0;
                case SWAMP -> 0.55;
                case BOREAL -> 0.45;
                case JUNGLE, SPARSE_JUNGLE -> 0.35;
                case TEMPERATE, BIRCH, OLD_BIRCH, FLOWER, DARK -> 0.3;
                case HIGHLAND, GROVE -> 0.2;
                default -> 0.0;
            };
        }

        /**
         * A huge mushroom of the dark forest. Most keep vanilla's familiar shapes (a flat brown cap,
         * a round red dome) at varied sizes; the rest are umbrellas, wide shelves on short stems,
         * tiered and lopsided brown caps, tall narrow, flattened and lobed red ones; in old stands a
         * rare giant of either colour.
         */
        static void mushroom(Cand c, TreeRandom r, double old) {
            if (old > 0.25 && r.chance(0.045 + 0.05 * old)) {
                boolean red = r.chance(0.45);
                if (red) {
                    c.flags |= TreeSpec.RED | (r.chance(0.7) ? TreeShapes.RED_BELL : TreeShapes.RED_DOME) << TreeSpec.STYLE_SHIFT;
                    set(c, TreeForm.GIANT_MUSHROOM, Wood.MUSHROOM, r.range(10, 15), r.range(4.6, 6.4));
                } else {
                    c.flags |= (r.chance(0.55) ? TreeShapes.BROWN_TIERED : TreeShapes.BROWN_UMBRELLA) << TreeSpec.STYLE_SHIFT;
                    set(c, TreeForm.GIANT_MUSHROOM, Wood.MUSHROOM, r.range(10, 16), r.range(5.2, 7.0));
                }
                return;
            }
            double u = r.next();
            if (r.chance(0.55)) {
                int style;
                double height;
                double crown;
                if (u < 0.5) {
                    style = TreeShapes.BROWN_FLAT;
                    height = r.between(5, 8);
                    crown = r.range(2.6, 3.4);
                } else if (u < 0.68) {
                    style = TreeShapes.BROWN_UMBRELLA;
                    height = r.between(6, 9);
                    crown = r.range(3.2, 4.2);
                } else if (u < 0.8) {
                    style = TreeShapes.BROWN_WIDE;
                    height = r.between(4, 6);
                    crown = r.range(4.2, 5.6);
                } else if (u < 0.9) {
                    style = TreeShapes.BROWN_TIERED;
                    height = r.between(8, 11);
                    crown = r.range(3.4, 4.6);
                } else {
                    style = TreeShapes.BROWN_LOPSIDED;
                    height = r.between(5, 8);
                    crown = r.range(3.0, 4.2);
                }
                c.flags |= style << TreeSpec.STYLE_SHIFT;
                set(c, TreeForm.HUGE_BROWN_MUSHROOM, Wood.MUSHROOM, height, crown);
            } else {
                int style;
                double height;
                double crown;
                if (u < 0.5) {
                    style = TreeShapes.RED_DOME;
                    height = r.between(5, 8);
                    crown = r.range(2.0, 2.6);
                } else if (u < 0.67) {
                    style = TreeShapes.RED_TALL;
                    height = r.between(9, 13);
                    crown = r.range(1.6, 2.2);
                } else if (u < 0.82) {
                    style = TreeShapes.RED_FLATTENED;
                    height = r.between(5, 8);
                    crown = r.range(3.2, 4.4);
                } else {
                    style = TreeShapes.RED_LOBED;
                    height = r.between(6, 9);
                    crown = r.range(2.6, 3.4);
                }
                c.flags |= style << TreeSpec.STYLE_SHIFT;
                set(c, TreeForm.HUGE_RED_MUSHROOM, Wood.MUSHROOM, height, crown);
            }
        }

        static void bush(Cand c, Wood wood, TreeRandom r, double s) {
            set(c, TreeForm.BUSH, wood, r.chance(0.3) ? 3 : 2, r.range(1.3, 2.3) * Math.min(1.1, Math.max(0.8, s)));
        }

        static void oak(Cand c, TreeRandom r, double s, double old, double young, double edge, double riparian, double ancient) {
            if (old > 0.3 && s > 0.85 && edge < 0.4 && r.chance(ancient * old)) {
                set(c, TreeForm.ANCIENT, Wood.OAK, r.range(19, 27) * s, r.range(9.5, 12.5));
                c.flags |= TreeSpec.ROOTS;
            } else if (r.chance(0.12 + 0.3 * (old + 0.3 * (1 - young)))) {
                set(c, TreeForm.BROADLEAF, Wood.OAK, r.range(11, 15) * s, r.range(4.6, 6.2));
            } else if (r.chance(0.3 * young + 0.15 * edge)) {
                set(c, TreeForm.BROADLEAF, Wood.OAK, r.range(4.5, 6.5) * s, r.range(2.0, 2.8));
            } else {
                set(c, TreeForm.BROADLEAF, Wood.OAK, r.range(7, 10.5) * s, r.range(3.0, 4.4));
            }
        }

        static void birch(Cand c, TreeRandom r, double s, double old, double young) {
            if (r.chance(0.08 + 0.35 * old)) {
                set(c, TreeForm.BIRCH, Wood.BIRCH, r.range(12, 17) * s, r.range(2.0, 2.7));
            } else if (r.chance(0.3 * young)) {
                set(c, TreeForm.BIRCH, Wood.BIRCH, r.range(5, 7) * s, r.range(1.5, 2.0));
            } else {
                set(c, TreeForm.BIRCH, Wood.BIRCH, r.range(7, 12) * s, r.range(1.8, 2.5));
            }
        }

        static void spruce(Cand c, TreeRandom r, double s, double old, double young) {
            if (r.chance(0.1 + 0.3 * old)) {
                set(c, TreeForm.CONIFER, Wood.SPRUCE, r.range(14, 20) * s, r.range(3.2, 4.3));
            } else if (r.chance(0.3 * young)) {
                set(c, TreeForm.CONIFER, Wood.SPRUCE, r.range(5, 8) * s, r.range(1.8, 2.5));
            } else {
                set(c, TreeForm.CONIFER, Wood.SPRUCE, r.range(8, 14) * s, r.range(2.3, 3.5));
            }
        }

        static void bees(Cand c, TreeRandom r, double chance) {
            if ((c.wood == Wood.OAK || c.wood == Wood.BIRCH) && (c.form == TreeForm.BROADLEAF || c.form == TreeForm.BIRCH || c.form == TreeForm.ANCIENT)
                    && r.chance(chance)) {
                c.flags |= TreeSpec.BEES;
            }
        }

        static Wood pick(TreeRandom r, Wood[] woods, double... weights) {
            return woods[pickIndex(r, weights)];
        }

        static int pickIndex(TreeRandom r, double... weights) {
            double total = 0.0;
            for (double w : weights) {
                total += w;
            }
            double u = r.next() * total;
            for (int k = 0; k < weights.length; k++) {
                u -= weights[k];
                if (u < 0.0) {
                    return k;
                }
            }
            return 0;
        }
    }
}
