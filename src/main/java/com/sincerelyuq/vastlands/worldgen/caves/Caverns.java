package com.sincerelyuq.vastlands.worldgen.caves;

import com.sincerelyuq.vastlands.worldgen.geography.GeographyModel;
import com.sincerelyuq.vastlands.worldgen.geography.GeographySample;
import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;

/**
 * Enormous caverns: at most one per {@value #CELL}-block cell, about a third of the cells. Most
 * lie in the deep zone (centres Y -200..-130, under the aquitard), a few in the upper zone under
 * high ground. A cavern is 45..110 blocks in radius and 20..52 high: a flattened dome over a
 * breakdown floor of rubble mounds, its outline lobed by noise, its walls stepping back in
 * terraces, and pillars (wider at floor and ceiling) standing in it. Their fluid comes from the
 * domains like everything else: in a flooded domain the floor holds an underground lake, in a lava
 * domain a lava lake, and tunnels of the levels they cut through open into them.
 */
final class Caverns {
    static final double CELL = 640.0;
    private static final double PILLAR_CELL = 26.0;

    static final class Cavern {
        final long key;
        final double x, y, z, rh, rv, floorDepth;
        final long seed;
        final boolean exists;

        Cavern(long key, double x, double y, double z, double rh, double rv, double floorDepth, long seed, boolean exists) {
            this.key = key;
            this.x = x;
            this.y = y;
            this.z = z;
            this.rh = rh;
            this.rv = rv;
            this.floorDepth = floorDepth;
            this.seed = seed;
            this.exists = exists;
        }
    }

    /** A cavern's section at one column. */
    static final class Section {
        Cavern cavern;
        double dh;
        double lobes;
        double rubble;
        double pillarDistance;
        double pillarRadius;

        boolean present() {
            return this.cavern != null;
        }

        /** Signed distance to the cavern's rock at a height (negative inside), terraces and pillars included. */
        double sdf(double y) {
            Cavern c = this.cavern;
            long band = (long) Math.floor((y + 1000.0) / 6.0);
            double terrace = 1.0 + 0.07 * (FluidDomains.unit(c.seed ^ band, 3) - 0.5);
            double rh = c.rh * this.lobes * terrace;
            double q = this.dh / rh;
            if (q >= 1.0) {
                return (q - 1.0) * rh;
            }
            double s = Math.sqrt(1.0 - q * q);
            double ceiling = c.y + c.rv * Math.pow(s, 0.8);
            double floor = c.y - c.rv * c.floorDepth * Math.sqrt(s) + this.rubble;
            double sdf = Math.max(floor - y, y - ceiling);
            if (this.pillarRadius > 0.0 && ceiling > floor) {
                double mid = 0.5 * (ceiling + floor);
                double half = 0.5 * (ceiling - floor);
                double t = (y - mid) / half;
                double r = this.pillarRadius * (1.0 + 1.4 * t * t);
                sdf = Math.max(sdf, r - this.pillarDistance);
            }
            return sdf;
        }

        double low() {
            return this.cavern.y - this.cavern.rv * this.cavern.floorDepth - Math.abs(this.rubble) - 2.0;
        }

        double high() {
            return this.cavern.y + this.cavern.rv + 2.0;
        }
    }

    private final long seed;
    private final GeographyModel geography;
    private final CaveGeology geology;
    private final FluidDomains domains;
    private final CaveNetwork.Ground ground;
    private final CaveSettings settings;
    private final SimplexNoise outline, rubble;
    private final ThreadLocal<Cavern[]> cache = ThreadLocal.withInitial(() -> new Cavern[64]);

    Caverns(long seed, GeographyModel geography, CaveGeology geology, FluidDomains domains, CaveNetwork.Ground ground, CaveSettings settings) {
        this.seed = SimplexNoise.mix(seed ^ 0x434156455253L);
        this.geography = geography;
        this.geology = geology;
        this.domains = domains;
        this.ground = ground;
        this.settings = settings;
        this.outline = new SimplexNoise(seed + 501);
        this.rubble = new SimplexNoise(seed + 502);
    }

    /** The cavern section at a column, if any cavern reaches it. */
    void section(double x, double z, Section out) {
        out.cavern = null;
        long ci = (long) Math.floor(x / CELL);
        long cj = (long) Math.floor(z / CELL);
        for (long j = cj - 1; j <= cj + 1; j++) {
            for (long i = ci - 1; i <= ci + 1; i++) {
                Cavern c = this.cavern(i, j);
                if (!c.exists) {
                    continue;
                }
                double dx = x - c.x;
                double dz = z - c.z;
                double dh = Math.sqrt(dx * dx + dz * dz);
                if (dh > c.rh * 1.25) {
                    continue;
                }
                double lobes = 0.78 + 0.22 * (1.0 + this.outline.sample(x / 75.0 + c.seed % 97, z / 75.0));
                if (dh > c.rh * lobes * 1.08) {
                    continue;
                }
                out.cavern = c;
                out.dh = dh;
                out.lobes = lobes;
                out.rubble = 0.12 * c.rv * this.rubble.sample(x / 18.0, z / 18.0);
                // Pillars: the nearest site of a jittered grid, if it holds one.
                out.pillarRadius = 0.0;
                double best = Double.POSITIVE_INFINITY;
                long pi = (long) Math.floor(x / PILLAR_CELL);
                long pj = (long) Math.floor(z / PILLAR_CELL);
                for (long b = pj - 1; b <= pj + 1; b++) {
                    for (long a = pi - 1; a <= pi + 1; a++) {
                        long h = SimplexNoise.mix(c.seed ^ a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL);
                        double sx = (a + 0.2 + 0.6 * FluidDomains.unit(h, 0)) * PILLAR_CELL;
                        double sz = (b + 0.2 + 0.6 * FluidDomains.unit(h, 1)) * PILLAR_CELL;
                        double d = Math.hypot(x - sx, z - sz);
                        if (d < best) {
                            best = d;
                            double fromCentre = Math.hypot(sx - c.x, sz - c.z) / c.rh;
                            out.pillarRadius = FluidDomains.unit(h, 2) < 0.45 && fromCentre < 0.85 ? 2.5 + 5.5 * FluidDomains.unit(h, 3) : 0.0;
                        }
                    }
                }
                out.pillarDistance = best;
                return;
            }
        }
    }

    private Cavern cavern(long i, long j) {
        Cavern[] cache = this.cache.get();
        long key = i << 32 ^ (j & 0xFFFFFFFFL);
        int slot = (int) (SimplexNoise.mix(key) & (cache.length - 1));
        Cavern c = cache[slot];
        if (c != null && c.key == key) {
            return c;
        }
        c = this.build(key, i, j);
        cache[slot] = c;
        return c;
    }

    private Cavern build(long key, long i, long j) {
        long h = SimplexNoise.mix(this.seed ^ key);
        double x = (i + 0.3 + 0.4 * FluidDomains.unit(h, 0)) * CELL;
        double z = (j + 0.3 + 0.4 * FluidDomains.unit(h, 1)) * CELL;
        GeographySample g = this.geography.sample(x, z);
        CaveGeology.Geology geo = this.geology.at(x, z, g);
        double chance = this.settings.giantCaverns() * (0.6 + 0.8 * Math.min(1.0, geo.abundance()));
        if (FluidDomains.unit(h, 2) >= chance) {
            return new Cavern(key, x, 0, z, 0, 0, 0, h, false);
        }
        double surface = this.ground.surface(x, z);
        double aquitard = this.domains.aquitard(x, z);
        double u = FluidDomains.unit(h, 3);
        double rh = 45.0 + 65.0 * u * u;
        double floorDepth = 0.5 + 0.15 * FluidDomains.unit(h, 4);
        boolean upper = FluidDomains.unit(h, 5) < 0.3 && surface - aquitard > 200.0;
        double y;
        double rv;
        if (upper) {
            y = Math.max(aquitard + 55.0, g.baseHeight() - 25.0);
            rv = Math.min(20.0 + 25.0 * FluidDomains.unit(h, 6), surface - 45.0 - y);
            rh = Math.min(rh, 80.0);
        } else {
            y = -200.0 + 70.0 * FluidDomains.unit(h, 7);
            rv = Math.min(20.0 + 32.0 * FluidDomains.unit(h, 6), aquitard - FluidDomains.AQUITARD_HALF - 6.0 - y);
            rv = Math.min(rv, (y + 240.0) / floorDepth);
            if (surface < y + rv + 30.0) {
                rv = 0.0;
            }
        }
        if (rv < 15.0) {
            return new Cavern(key, x, 0, z, 0, 0, 0, h, false);
        }
        return new Cavern(key, x, y, z, rh, rv, floorDepth, h, true);
    }
}
