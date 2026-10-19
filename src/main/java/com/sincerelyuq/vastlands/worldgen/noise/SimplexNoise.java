package com.sincerelyuq.vastlands.worldgen.noise;

/**
 * Seeded 2D simplex noise with analytic gradient, in [-1, 1].
 *
 * <p>Lattice corners are hashed from the seed and their integer coordinates instead of a
 * permutation table, so the noise never repeats. 64 evenly spaced unit gradients avoid the
 * axis-aligned ridges of small gradient sets, which matters for ridged noise.
 *
 * <p>Immutable and thread-safe. Pure Java, no game classes, so the terrain model can be run and
 * rendered outside the game.
 */
public final class SimplexNoise {
    private static final double F2 = 0.5 * (Math.sqrt(3.0) - 1.0);
    private static final double G2 = (3.0 - Math.sqrt(3.0)) / 6.0;
    private static final int GRADIENT_COUNT = 64;
    private static final double[] GRADIENT_X = new double[GRADIENT_COUNT];
    private static final double[] GRADIENT_Y = new double[GRADIENT_COUNT];
    /** Brings the sum of the three corner contributions to about [-1, 1] for unit gradients. */
    private static final double SCALE = 99.204;

    static {
        for (int i = 0; i < GRADIENT_COUNT; i++) {
            double angle = (i + 0.5) * (2.0 * Math.PI / GRADIENT_COUNT);
            GRADIENT_X[i] = Math.cos(angle);
            GRADIENT_Y[i] = Math.sin(angle);
        }
    }

    private final long seed;

    public SimplexNoise(long seed) {
        this.seed = mix(seed ^ 0x5DEECE66DL);
    }

    /** Noise value at (x, y). */
    public double sample(double x, double y) {
        return this.sample(x, y, null);
    }

    /**
     * Noise value at (x, y). If {@code gradient} is not null, its first two elements receive the
     * partial derivatives d/dx and d/dy.
     */
    public double sample(double x, double y, double[] gradient) {
        double s = (x + y) * F2;
        long i = (long) Math.floor(x + s);
        long j = (long) Math.floor(y + s);
        double t = (i + j) * G2;
        double x0 = x - (i - t);
        double y0 = y - (j - t);
        int i1 = x0 > y0 ? 1 : 0;
        int j1 = 1 - i1;
        double x1 = x0 - i1 + G2;
        double y1 = y0 - j1 + G2;
        double x2 = x0 - 1.0 + 2.0 * G2;
        double y2 = y0 - 1.0 + 2.0 * G2;

        double value = 0.0;
        double dx = 0.0;
        double dy = 0.0;

        double t0 = 0.5 - x0 * x0 - y0 * y0;
        if (t0 > 0.0) {
            int g = this.gradient(i, j);
            double dot = GRADIENT_X[g] * x0 + GRADIENT_Y[g] * y0;
            double t02 = t0 * t0;
            double t04 = t02 * t02;
            value += t04 * dot;
            double k = -8.0 * t02 * t0 * dot;
            dx += k * x0 + t04 * GRADIENT_X[g];
            dy += k * y0 + t04 * GRADIENT_Y[g];
        }
        double t1 = 0.5 - x1 * x1 - y1 * y1;
        if (t1 > 0.0) {
            int g = this.gradient(i + i1, j + j1);
            double dot = GRADIENT_X[g] * x1 + GRADIENT_Y[g] * y1;
            double t12 = t1 * t1;
            double t14 = t12 * t12;
            value += t14 * dot;
            double k = -8.0 * t12 * t1 * dot;
            dx += k * x1 + t14 * GRADIENT_X[g];
            dy += k * y1 + t14 * GRADIENT_Y[g];
        }
        double t2 = 0.5 - x2 * x2 - y2 * y2;
        if (t2 > 0.0) {
            int g = this.gradient(i + 1, j + 1);
            double dot = GRADIENT_X[g] * x2 + GRADIENT_Y[g] * y2;
            double t22 = t2 * t2;
            double t24 = t22 * t22;
            value += t24 * dot;
            double k = -8.0 * t22 * t2 * dot;
            dx += k * x2 + t24 * GRADIENT_X[g];
            dy += k * y2 + t24 * GRADIENT_Y[g];
        }
        if (gradient != null) {
            gradient[0] = dx * SCALE;
            gradient[1] = dy * SCALE;
        }
        return value * SCALE;
    }

    private int gradient(long i, long j) {
        return (int) (mix(this.seed ^ i * 0x9E3779B97F4A7C15L ^ j * 0xC2B2AE3D27D4EB4FL) >>> 58);
    }

    /** 64 well-mixed bits for an integer lattice point. */
    public long hash(long i, long j) {
        return mix(this.seed ^ i * 0x9E3779B97F4A7C15L ^ j * 0xC2B2AE3D27D4EB4FL ^ 0x2545F4914F6CDD1DL);
    }

    /** Uniform value in [0, 1) for an integer lattice point and a channel; used for jittered cells. */
    public double hash01(long i, long j, int channel) {
        long h = mix(this.seed ^ i * 0x9E3779B97F4A7C15L ^ j * 0xC2B2AE3D27D4EB4FL ^ channel * 0x165667B19E3779F9L);
        return (h >>> 11) * 0x1.0p-53;
    }

    /** MurmurHash3 64-bit finalizer. */
    public static long mix(long h) {
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h;
    }
}
