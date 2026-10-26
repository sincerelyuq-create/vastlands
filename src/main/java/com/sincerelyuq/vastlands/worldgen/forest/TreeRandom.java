package com.sincerelyuq.vastlands.worldgen.forest;

import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;

/**
 * A small, fast random sequence for one tree or one candidate (SplitMix64). Each tree builds its
 * own from its seed, so its shape depends only on the seed and never on what else was generated
 * before it. Not thread-safe; never shared.
 */
final class TreeRandom {
    private long state;

    TreeRandom(long seed) {
        this.state = SimplexNoise.mix(seed ^ 0x7A3C91E2D5B4F061L);
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

    /** Uniform in [min, max). */
    double range(double min, double max) {
        return min + (max - min) * this.next();
    }

    /** Uniform integer in [0, bound). */
    int nextInt(int bound) {
        return (int) ((this.nextLong() >>> 33) % bound);
    }

    /** Uniform integer in [min, max] (inclusive). */
    int between(int min, int max) {
        return min + this.nextInt(max - min + 1);
    }

    boolean chance(double p) {
        return this.next() < p;
    }

    /** Approximately normal (mean 0, deviation 1): a sum of three uniforms. */
    double gaussian() {
        return (this.next() + this.next() + this.next() - 1.5) * 2.0;
    }
}
