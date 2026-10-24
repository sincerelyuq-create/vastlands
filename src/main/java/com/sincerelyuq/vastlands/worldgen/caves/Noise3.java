package com.sincerelyuq.vastlands.worldgen.caves;

import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;

/**
 * Seeded 3D gradient noise (improved Perlin: quintic fade, 12 edge gradients) in about [-1, 1].
 * Lattice corners are hashed from the seed and their coordinates, so there are no permutation
 * tables and the noise never repeats. Immutable, thread-safe, about 30 ns per sample.
 */
final class Noise3 {
    private static final double[] GX = {1, -1, 1, -1, 1, -1, 1, -1, 0, 0, 0, 0, 1, -1, 0, 0};
    private static final double[] GY = {1, 1, -1, -1, 0, 0, 0, 0, 1, -1, 1, -1, 1, 1, -1, -1};
    private static final double[] GZ = {0, 0, 0, 0, 1, 1, -1, -1, 1, 1, -1, -1, 0, 0, 1, -1};

    private final long seed;

    Noise3(long seed) {
        this.seed = SimplexNoise.mix(seed ^ 0x3D3D3D3D5EEDL);
    }

    double sample(double x, double y, double z) {
        long xi = (long) Math.floor(x);
        long yi = (long) Math.floor(y);
        long zi = (long) Math.floor(z);
        double fx = x - xi;
        double fy = y - yi;
        double fz = z - zi;
        double u = fade(fx);
        double v = fade(fy);
        double w = fade(fz);
        long hx0 = xi * 0x9E3779B97F4A7C15L;
        long hx1 = hx0 + 0x9E3779B97F4A7C15L;
        long hy0 = yi * 0xC2B2AE3D27D4EB4FL;
        long hy1 = hy0 + 0xC2B2AE3D27D4EB4FL;
        long hz0 = zi * 0x165667B19E3779F9L;
        long hz1 = hz0 + 0x165667B19E3779F9L;
        double c000 = this.grad(hx0 ^ hy0 ^ hz0, fx, fy, fz);
        double c100 = this.grad(hx1 ^ hy0 ^ hz0, fx - 1, fy, fz);
        double c010 = this.grad(hx0 ^ hy1 ^ hz0, fx, fy - 1, fz);
        double c110 = this.grad(hx1 ^ hy1 ^ hz0, fx - 1, fy - 1, fz);
        double c001 = this.grad(hx0 ^ hy0 ^ hz1, fx, fy, fz - 1);
        double c101 = this.grad(hx1 ^ hy0 ^ hz1, fx - 1, fy, fz - 1);
        double c011 = this.grad(hx0 ^ hy1 ^ hz1, fx, fy - 1, fz - 1);
        double c111 = this.grad(hx1 ^ hy1 ^ hz1, fx - 1, fy - 1, fz - 1);
        double x00 = c000 + u * (c100 - c000);
        double x10 = c010 + u * (c110 - c010);
        double x01 = c001 + u * (c101 - c001);
        double x11 = c011 + u * (c111 - c011);
        double y0 = x00 + v * (x10 - x00);
        double y1 = x01 + v * (x11 - x01);
        return y0 + w * (y1 - y0);
    }

    private double grad(long corner, double dx, double dy, double dz) {
        long h = (corner ^ this.seed) * 0xD6E8FEB86659FD93L;
        int g = (int) (h >>> 60);
        return GX[g] * dx + GY[g] * dy + GZ[g] * dz;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }
}
