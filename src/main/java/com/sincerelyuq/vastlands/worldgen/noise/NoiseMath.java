package com.sincerelyuq.vastlands.worldgen.noise;

/**
 * Shared noise helpers of the geography and terrain models: fractal sums of {@link SimplexNoise},
 * smooth steps and a monotone spline. Pure Java, stateless.
 */
public final class NoiseMath {
    private static final double ROTATE_COS = Math.cos(0.9);
    private static final double ROTATE_SIN = Math.sin(0.9);

    private NoiseMath() {
    }

    /** Fractal Brownian motion, normalised to about [-1, 1]; each octave is rotated to hide lattice axes. */
    public static double fbm(SimplexNoise noise, double x, double z, int octaves, double gain) {
        double sum = 0.0;
        double amplitude = 1.0;
        double norm = 0.0;
        for (int i = 0; i < octaves; i++) {
            sum += amplitude * noise.sample(x, z);
            norm += amplitude;
            amplitude *= gain;
            double nx = (x * ROTATE_COS - z * ROTATE_SIN) * 2.0 + 31.7;
            double nz = (x * ROTATE_SIN + z * ROTATE_COS) * 2.0 - 12.3;
            x = nx;
            z = nz;
        }
        return sum / norm;
    }

    /** Ridged multifractal (Musgrave) in [0, 1]: sharp connected crests, detail concentrated on them. */
    public static double ridged(SimplexNoise noise, double x, double z, int octaves) {
        double sum = 0.0;
        double amplitude = 1.0;
        double norm = 0.0;
        double weight = 1.0;
        for (int i = 0; i < octaves; i++) {
            double signal = 1.0 - Math.abs(noise.sample(x, z));
            signal *= signal;
            signal *= weight;
            weight = clamp01(signal * 2.0);
            sum += signal * amplitude;
            norm += amplitude;
            amplitude *= 0.5;
            double nx = (x * ROTATE_COS - z * ROTATE_SIN) * 2.0 + 31.7;
            double nz = (x * ROTATE_SIN + z * ROTATE_COS) * 2.0 - 12.3;
            x = nx;
            z = nz;
        }
        return sum / norm;
    }

    /** Hermite step from 0 at {@code edge0} to 1 at {@code edge1}; {@code edge0 > edge1} gives a falling step. */
    public static double smoothstep(double edge0, double edge1, double v) {
        double t = clamp01((v - edge0) / (edge1 - edge0));
        return t * t * (3.0 - 2.0 * t);
    }

    public static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    public static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }

    /**
     * Smooth maximum of two non-negative values: rounds the crease where they meet (by up to k / 4),
     * and equals max(a, b) exactly when either is 0 or they differ by more than k, so a term that
     * fades to 0 can be switched off without a step.
     */
    public static double smoothMax(double a, double b, double k) {
        double kk = k * clamp01(Math.min(a, b) / k);
        if (kk <= 0.0) {
            return Math.max(a, b);
        }
        double h = clamp01(0.5 + 0.5 * (a - b) / kk);
        return b + (a - b) * h + kk * h * (1.0 - h);
    }

    /** Monotone cubic (Fritsch-Carlson) interpolation through fixed points; clamps outside them. */
    public static final class Spline {
        private final double[] xs, ys, tangents;

        public Spline(double[] xs, double[] ys) {
            this.xs = xs;
            this.ys = ys;
            int n = xs.length;
            double[] secants = new double[n - 1];
            for (int i = 0; i < n - 1; i++) {
                secants[i] = (ys[i + 1] - ys[i]) / (xs[i + 1] - xs[i]);
            }
            this.tangents = new double[n];
            this.tangents[0] = secants[0];
            this.tangents[n - 1] = secants[n - 2];
            for (int i = 1; i < n - 1; i++) {
                this.tangents[i] = secants[i - 1] * secants[i] <= 0.0 ? 0.0 : (secants[i - 1] + secants[i]) / 2.0;
            }
            for (int i = 0; i < n - 1; i++) {
                if (secants[i] == 0.0) {
                    this.tangents[i] = 0.0;
                    this.tangents[i + 1] = 0.0;
                    continue;
                }
                double a = this.tangents[i] / secants[i];
                double b = this.tangents[i + 1] / secants[i];
                double h = a * a + b * b;
                if (h > 9.0) {
                    double t = 3.0 / Math.sqrt(h);
                    this.tangents[i] = t * a * secants[i];
                    this.tangents[i + 1] = t * b * secants[i];
                }
            }
        }

        public double at(double x) {
            if (x <= this.xs[0]) {
                return this.ys[0];
            }
            int n = this.xs.length;
            if (x >= this.xs[n - 1]) {
                return this.ys[n - 1];
            }
            int i = 0;
            while (x > this.xs[i + 1]) {
                i++;
            }
            double h = this.xs[i + 1] - this.xs[i];
            double t = (x - this.xs[i]) / h;
            double t2 = t * t;
            double t3 = t2 * t;
            return (2 * t3 - 3 * t2 + 1) * this.ys[i] + (t3 - 2 * t2 + t) * h * this.tangents[i]
                    + (-2 * t3 + 3 * t2) * this.ys[i + 1] + (t3 - t2) * h * this.tangents[i + 1];
        }
    }
}
