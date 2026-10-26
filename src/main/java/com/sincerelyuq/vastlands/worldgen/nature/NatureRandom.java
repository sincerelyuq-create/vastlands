package com.sincerelyuq.vastlands.worldgen.nature;

import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;

/**
 * A small random sequence for one candidate or one shape (SplitMix64): each builds its own from
 * its seed, so nothing depends on what was generated before. Not thread-safe; never shared.
 */
final class NatureRandom {
    private long state;

    NatureRandom(long seed) {
        this.state = SimplexNoise.mix(seed ^ 0x4E61747572654CL);
    }

    long nextLong() {
        long z = (this.state += 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Uniform in [0, 1). */
    double next() {
        return (this.nextLong() >>> 11) * 0x1.0p-53;
    }

    double range(double min, double max) {
        return min + (max - min) * this.next();
    }

    /** Uniform integer in [min, max] (inclusive). */
    int between(int min, int max) {
        return min + (int) ((this.nextLong() >>> 33) % (max - min + 1));
    }

    boolean chance(double p) {
        return this.next() < p;
    }

    /** Skewed towards small values: {@code min + (max - min) * u^power}. */
    double skewed(double min, double max, double power) {
        return min + (max - min) * Math.pow(this.next(), power);
    }

    /** A hash of a position and a channel in [0, 1) (no state). */
    static double hash(long seed, long x, long y, long z) {
        long h = SimplexNoise.mix(seed ^ x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L);
        return (h >>> 11) * 0x1.0p-53;
    }

    /** Smooth 3D value noise in about [0, 1) at a scale in blocks. */
    static double value(long seed, double x, double y, double z, double scale) {
        double fx = x / scale;
        double fy = y / scale;
        double fz = z / scale;
        long ix = (long) Math.floor(fx);
        long iy = (long) Math.floor(fy);
        long iz = (long) Math.floor(fz);
        double tx = smooth(fx - ix);
        double ty = smooth(fy - iy);
        double tz = smooth(fz - iz);
        double v = 0.0;
        for (int k = 0; k < 8; k++) {
            int ox = k & 1;
            int oy = (k >> 1) & 1;
            int oz = (k >> 2) & 1;
            double w = (ox == 1 ? tx : 1 - tx) * (oy == 1 ? ty : 1 - ty) * (oz == 1 ? tz : 1 - tz);
            v += w * hash(seed, ix + ox, iy + oy, iz + oz);
        }
        return v;
    }

    private static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }
}
