package com.sincerelyuq.vastlands.worldgen.caves;

import com.sincerelyuq.vastlands.worldgen.geography.GeographyModel;
import com.sincerelyuq.vastlands.worldgen.geography.GeographySample;
import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;

/**
 * Hydrogeology of the caves: where the cave space is dry, flooded or full of lava, and how high.
 *
 * <p>The rock is divided into fluid domains: cells of a warped 2D Voronoi diagram, in two zones
 * stacked one above the other. The upper zone (from the surface down to the aquitard, a cave-free
 * rock band around Y -66) has cells of about {@value #UPPER_CELL} blocks, the deep zone (from the
 * aquitard down to bedrock) cells of about {@value #DEEP_CELL}. Every domain holds one fluid up to
 * one level and air above it:
 * <ul>
 *     <li>upper domains near or under the sea are flooded to sea level (sea caves, flooded coastal
 *     karst, sea-connected networks); inland domains hold a water table a little below their
 *     regional base level (where rivers run: the local base level of erosion) or are dry, by the
 *     climate (wet regions, karst) and chance;</li>
 *     <li>deep domains hold lava lakes (Y -236 up to about -150 in volcanic regions), are flooded
 *     (rare ancient lakes; completely under the open ocean) or dry.</li>
 * </ul>
 * Inside a domain every open cave block below the level is fluid and every one above it air, so a
 * domain's water or lava always stands level and is held by rock wherever the cave ends. Where two
 * domains meet with a different fluid at some height, the caves get a rock dam along the Voronoi
 * edge over exactly those heights (and two blocks more): flooded and dry caves, or water and lava,
 * never touch, and a dam crest stands flush with or above the higher water. The aquitard band
 * closes the same way where the zones above and below it differ.
 *
 * <p>Pure function of the seed and the position; domain properties come from the geography at the
 * domain's site, so every chunk sees the same domains.
 */
final class FluidDomains {
    static final double UPPER_CELL = 320.0;
    static final double DEEP_CELL = 448.0;
    /** Neighbours farther than this from the edge do not matter (blocks). */
    static final double EDGE_REACH = 12.0;
    /** Mean height and half thickness of the aquitard band. */
    static final double AQUITARD_Y = -66.0;
    static final double AQUITARD_HALF = 4.0;
    /** Dry domains: no fluid. */
    static final int NO_LEVEL = Integer.MIN_VALUE / 2;

    /** One fluid domain. */
    static final class Domain {
        final long key;
        final double siteX;
        final double siteZ;
        final CaveFluid fluid;
        /** Y of the top fluid block, or {@link #NO_LEVEL}. */
        final int level;
        final boolean sea;

        Domain(long key, double siteX, double siteZ, CaveFluid fluid, int level, boolean sea) {
            this.key = key;
            this.siteX = siteX;
            this.siteZ = siteZ;
            this.fluid = fluid;
            this.level = fluid == CaveFluid.AIR ? NO_LEVEL : level;
            this.sea = sea;
        }

        CaveFluid at(int y) {
            return y <= this.level ? this.fluid : CaveFluid.AIR;
        }

        @Override
        public String toString() {
            return this.fluid == CaveFluid.AIR ? "dry" : (this.fluid == CaveFluid.WATER ? (this.sea ? "sea " : "water ") : "lava ") + this.level;
        }
    }

    /** The domain owning a point and the neighbours whose edge lies within {@link #EDGE_REACH}. */
    static final class Hit {
        Domain owner;
        int count;
        final Domain[] neighbours = new Domain[4];
        /** Distance from the point to the Voronoi edge with each neighbour (warped space ≈ blocks). */
        final double[] edge = new double[4];
        /** Position along that edge, the same seen from both sides. */
        final double[] along = new double[4];
    }

    private final GeographyModel geography;
    private final CaveGeology geology;
    private final CaveSettings settings;
    private final int seaTop;
    private final long seed;
    private final SimplexNoise upperWarpX, upperWarpZ, deepWarpX, deepWarpZ;
    private final SimplexNoise aquitardBroad, aquitardFine;
    private final ThreadLocal<Domain[]> upperCache = ThreadLocal.withInitial(() -> new Domain[256]);
    private final ThreadLocal<Domain[]> deepCache = ThreadLocal.withInitial(() -> new Domain[256]);

    FluidDomains(long seed, GeographyModel geography, CaveGeology geology, CaveSettings settings, int seaTop) {
        this.geography = geography;
        this.geology = geology;
        this.settings = settings;
        this.seaTop = seaTop;
        this.seed = SimplexNoise.mix(seed ^ 0x464C554944L);
        this.upperWarpX = new SimplexNoise(seed + 201);
        this.upperWarpZ = new SimplexNoise(seed + 202);
        this.deepWarpX = new SimplexNoise(seed + 203);
        this.deepWarpZ = new SimplexNoise(seed + 204);
        this.aquitardBroad = new SimplexNoise(seed + 205);
        this.aquitardFine = new SimplexNoise(seed + 206);
    }

    /** Middle of the aquitard band at a column; the band is {@link #AQUITARD_HALF} blocks either side. */
    double aquitard(double x, double z) {
        return AQUITARD_Y + 9.0 * this.aquitardBroad.sample(x / 800.0, z / 800.0) + 3.0 * this.aquitardFine.sample(x / 170.0, z / 170.0);
    }

    void locate(double x, double z, boolean deep, Hit out) {
        double cell = deep ? DEEP_CELL : UPPER_CELL;
        SimplexNoise warpX = deep ? this.deepWarpX : this.upperWarpX;
        SimplexNoise warpZ = deep ? this.deepWarpZ : this.upperWarpZ;
        double warpScale = deep ? 650.0 : 480.0;
        double wx = x + 70.0 * warpX.sample(x / warpScale, z / warpScale);
        double wz = z + 70.0 * warpZ.sample(x / warpScale, z / warpScale);
        long ci = (long) Math.floor(wx / cell);
        long cj = (long) Math.floor(wz / cell);
        double[] sx = new double[9];
        double[] sz = new double[9];
        long[] keys = new long[9];
        int best = 0;
        double bestD = Double.POSITIVE_INFINITY;
        int n = 0;
        for (long j = cj - 1; j <= cj + 1; j++) {
            for (long i = ci - 1; i <= ci + 1; i++) {
                long key = i * 0x1F1F1F1FL ^ j * 0x3C6EF372FE94F82BL ^ (deep ? 0x5DEECE66DL : 0L);
                long h = SimplexNoise.mix(this.seed ^ key);
                sx[n] = (i + 0.25 + 0.5 * unit(h, 0)) * cell;
                sz[n] = (j + 0.25 + 0.5 * unit(h, 1)) * cell;
                keys[n] = key;
                double d = (wx - sx[n]) * (wx - sx[n]) + (wz - sz[n]) * (wz - sz[n]);
                if (d < bestD) {
                    bestD = d;
                    best = n;
                }
                n++;
            }
        }
        out.owner = this.domain(keys[best], sx[best], sz[best], deep);
        out.count = 0;
        for (int k = 0; k < 9; k++) {
            if (k == best) {
                continue;
            }
            double ex = sx[k] - sx[best];
            double ez = sz[k] - sz[best];
            double len = Math.sqrt(ex * ex + ez * ez);
            double d = (wx - sx[k]) * (wx - sx[k]) + (wz - sz[k]) * (wz - sz[k]);
            double e = (d - bestD) / (2.0 * len);
            if (e >= EDGE_REACH) {
                continue;
            }
            // Along the edge, oriented the same way from both sides (by key order).
            double tx = -ez / len;
            double tz = ex / len;
            if (keys[best] > keys[k]) {
                tx = -tx;
                tz = -tz;
            }
            double mx = 0.5 * (sx[k] + sx[best]);
            double mz = 0.5 * (sz[k] + sz[best]);
            double along = (wx - mx) * tx + (wz - mz) * tz;
            int slot = out.count;
            if (slot == out.neighbours.length) {
                // Keep the nearest edges.
                slot = 0;
                for (int q = 1; q < out.count; q++) {
                    if (out.edge[q] > out.edge[slot]) {
                        slot = q;
                    }
                }
                if (out.edge[slot] <= e) {
                    continue;
                }
            } else {
                out.count++;
            }
            out.neighbours[slot] = this.domain(keys[k], sx[k], sz[k], deep);
            out.edge[slot] = e;
            out.along[slot] = along;
        }
    }

    private Domain domain(long key, double x, double z, boolean deep) {
        Domain[] cache = (deep ? this.deepCache : this.upperCache).get();
        int slot = (int) (SimplexNoise.mix(key) & (cache.length - 1));
        Domain domain = cache[slot];
        if (domain != null && domain.key == key) {
            return domain;
        }
        domain = deep ? this.deepDomain(key, x, z) : this.upperDomain(key, x, z);
        cache[slot] = domain;
        return domain;
    }

    private Domain upperDomain(long key, double x, double z) {
        GeographySample g = this.geography.sample(x, z);
        CaveGeology.Geology geo = this.geology.at(x, z, g);
        long h = SimplexNoise.mix(this.seed ^ key ^ 0x55505045L);
        if (g.continentalness() < 0.08 || g.elevation() < this.seaTop + 4) {
            return new Domain(key, x, z, CaveFluid.WATER, this.seaTop, true);
        }
        double wet = Math.max(0.05, Math.min(0.85, 0.15 + 0.6 * geo.wetness() + 0.12 * geo.karst()));
        if (unit(h, 2) < wet) {
            int level = (int) Math.floor(g.baseHeight() - 8.0 - 30.0 * unit(h, 3));
            level = Math.min(Math.max(level, (int) AQUITARD_Y + 30), CaveModel.MAX_LEVEL);
            return new Domain(key, x, z, CaveFluid.WATER, level, false);
        }
        return new Domain(key, x, z, CaveFluid.AIR, NO_LEVEL, false);
    }

    private Domain deepDomain(long key, double x, double z) {
        GeographySample g = this.geography.sample(x, z);
        CaveGeology.Geology geo = this.geology.at(x, z, g);
        long h = SimplexNoise.mix(this.seed ^ key ^ 0x44454550L);
        double u = unit(h, 2);
        if (g.continentalness() < -0.12) {
            // Under the open ocean the deep caves are part of the sea or dry behind rock.
            return u < 0.75
                    ? new Domain(key, x, z, CaveFluid.WATER, this.seaTop, true)
                    : new Domain(key, x, z, CaveFluid.AIR, NO_LEVEL, false);
        }
        double lava = (0.32 + 0.45 * geo.volcanic()) * this.settings.lava();
        double water = 0.16 * (1.0 - geo.volcanic());
        if (u < lava) {
            int level = (int) Math.floor(-236.0 + 30.0 * unit(h, 3) + 80.0 * geo.volcanic());
            return new Domain(key, x, z, CaveFluid.LAVA, Math.min(level, -150), false);
        }
        if (u < lava + water) {
            int level = (int) Math.floor(-226.0 + 100.0 * unit(h, 3));
            return new Domain(key, x, z, CaveFluid.WATER, Math.min(level, -110), false);
        }
        return new Domain(key, x, z, CaveFluid.AIR, NO_LEVEL, false);
    }

    static double unit(long h, int channel) {
        long m = SimplexNoise.mix(h + channel * 0x9E3779B97F4A7C15L);
        return (m >>> 11) * 0x1.0p-53;
    }
}
