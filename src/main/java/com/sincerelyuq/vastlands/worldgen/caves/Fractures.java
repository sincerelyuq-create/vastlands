package com.sincerelyuq.vastlands.worldgen.caves;

import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.smoothstep;

import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;

/**
 * Chasms: tall, narrow cracks along the joints of the rock.
 *
 * <p>Joint sets are the zero lines of a noise stretched about six times along a strike direction
 * that turns slowly over ~7 000 blocks, so cracks within a region run roughly parallel, about 75
 * blocks apart, curving gently. A gate noise opens them in stretches of tens to hundreds of
 * blocks, tapering at both ends; fractured rock (mountains, escarpments) opens most of its joints,
 * plains almost none. Each crack is a lens in section: widest (up to ~6 blocks either side of the
 * line) at a middle height, closing above and below, up to ~120 blocks tall in mountains. A rare
 * ravine set reaches the surface, V-shaped, widest at the top. A second set cuts the deep zone.
 * The chasm module returns the crack of each set at a column; the column turns it into cave space.
 */
final class Fractures {
    /** A crack at one column. */
    static final class Crack {
        /** Horizontal distance from the column to the crack line. */
        double distance;
        /** Largest half width, at {@link #middle}. */
        double width;
        double middle;
        /** Half height of the lens; for ravines the depth below the surface. */
        double half;
        boolean ravine;
        double ravineTop;

        boolean open() {
            return this.width > 0.0;
        }

        /** Signed distance to the crack's wall at a height (negative inside). */
        double sdf(double y) {
            if (this.ravine) {
                if (y > this.ravineTop || y < this.ravineTop - this.half) {
                    return Double.POSITIVE_INFINITY;
                }
                double t = (y - (this.ravineTop - this.half)) / this.half;
                return this.distance - this.width * (0.2 + 0.8 * t);
            }
            double t = (y - this.middle) / this.half;
            double q = 1.0 - t * t;
            return q <= 0.0 ? Double.POSITIVE_INFINITY : this.distance - this.width * Math.sqrt(q);
        }

        double low() {
            return this.ravine ? this.ravineTop - this.half : this.middle - this.half;
        }

        double high() {
            return this.ravine ? this.ravineTop : this.middle + this.half;
        }
    }

    /** Joint frames are anchored on a grid of this size (blocks). */
    private static final double FRAME = 2400.0;
    private final SimplexNoise strike, line, gate, widthNoise, heightNoise, middleNoise, ravines;
    private final SimplexNoise deepStrike, deepLine, deepGate;
    private final double scale;

    Fractures(long seed, CaveSettings settings) {
        this.strike = new SimplexNoise(seed + 401);
        this.line = new SimplexNoise(seed + 402);
        this.gate = new SimplexNoise(seed + 403);
        this.widthNoise = new SimplexNoise(seed + 404);
        this.heightNoise = new SimplexNoise(seed + 405);
        this.middleNoise = new SimplexNoise(seed + 406);
        this.ravines = new SimplexNoise(seed + 407);
        this.deepStrike = new SimplexNoise(seed + 408);
        this.deepLine = new SimplexNoise(seed + 409);
        this.deepGate = new SimplexNoise(seed + 410);
        this.scale = settings.vertical();
    }

    /**
     * The upper-zone crack at a column, between the aquitard and the cover of rock below the
     * surface (ravines reach the surface).
     */
    void upper(double x, double z, double surface, double floor, CaveGeology.Geology geo, int seaTop, Crack out) {
        out.width = 0.0;
        double fracture = geo.fracture();
        double distance = this.distance(this.strike, this.line, x, z, 0.0);
        if (distance > 13.0) {
            return;
        }
        double threshold = 0.8 - 0.65 * Math.min(1.0, (0.3 * geo.vertical() + fracture) * this.scale);
        double open = smoothstep(threshold, threshold + 0.18, this.gate.sample(x / 200.0, z / 200.0));
        if (open <= 0.0) {
            return;
        }
        out.distance = distance;
        boolean ravine = fracture > 0.35 && surface > seaTop + 3 && this.ravines.sample(x / 900.0, z / 900.0) > 0.55;
        if (ravine) {
            out.ravine = true;
            out.ravineTop = surface + 6.0;
            out.half = 6.0 + (22.0 + 50.0 * fracture) * open;
            out.width = (1.5 + 4.0 * fracture) * open;
            return;
        }
        double top = surface - CaveNetwork.COVER - 3.0;
        double bottom = floor;
        if (top - bottom < 16.0) {
            return;
        }
        out.ravine = false;
        out.middle = bottom + (top - bottom) * (0.35 + 0.2 * (1.0 + this.middleNoise.sample(x / 600.0, z / 600.0)));
        double half = (12.0 + 110.0 * fracture * (0.5 + 0.5 * this.heightNoise.sample(x / 350.0, z / 350.0))) * open;
        out.half = Math.min(half, Math.min(top - out.middle, out.middle - bottom));
        out.width = out.half < 6.0 ? 0.0 : open * (1.2 + 4.8 * fracture * (0.5 + 0.5 * this.widthNoise.sample(x / 110.0, z / 110.0)));
    }

    /** The deep-zone crack at a column, between bedrock and the aquitard. */
    void deep(double x, double z, double ceiling, CaveGeology.Geology geo, Crack out) {
        out.width = 0.0;
        out.ravine = false;
        double strength = Math.min(1.0, (0.35 + 0.4 * geo.fracture() + 0.4 * geo.volcanic()) * this.scale);
        double distance = this.distance(this.deepStrike, this.deepLine, x, z, 0.5);
        if (distance > 13.0) {
            return;
        }
        double threshold = 0.8 - 0.5 * strength;
        double open = smoothstep(threshold, threshold + 0.2, this.deepGate.sample(x / 260.0, z / 260.0));
        if (open <= 0.0) {
            return;
        }
        double bottom = -238.0;
        double top = ceiling;
        if (top - bottom < 20.0) {
            return;
        }
        out.distance = distance;
        out.middle = bottom + (top - bottom) * (0.3 + 0.2 * (1.0 + this.middleNoise.sample(x / 700.0 + 40.0, z / 700.0)));
        double half = (20.0 + 70.0 * strength * (0.5 + 0.5 * this.heightNoise.sample(x / 420.0 + 40.0, z / 420.0))) * open;
        out.half = Math.min(half, Math.min(top - out.middle, out.middle - bottom));
        out.width = out.half < 8.0 ? 0.0 : open * (1.8 + 5.0 * strength * (0.5 + 0.5 * this.widthNoise.sample(x / 130.0 + 40.0, z / 130.0)));
    }

    /**
     * Distance to the nearest zero line of a joint set: a noise stretched along the strike. The
     * strike turns over ~7 000 blocks, but each frame is anchored at a corner of a
     * {@value #FRAME}-block grid (its own strike, its own line pattern), and the four frames
     * around a column are blended by distance weights (a frame's lines thin out and vanish away
     * from its corner). Rotating about the world's origin instead (Agent 4) shifted the stretched
     * coordinate by |r| × the turn per block: far from the origin the lines broke up into scattered
     * single-block holes.
     */
    private double distance(SimplexNoise strike, SimplexNoise line, double x, double z, double offset) {
        long fi = (long) Math.floor(x / FRAME);
        long fj = (long) Math.floor(z / FRAME);
        double tx = smoothstep(0.0, 1.0, x / FRAME - fi);
        double tz = smoothstep(0.0, 1.0, z / FRAME - fj);
        double[] gradient = new double[2];
        double best = Double.POSITIVE_INFINITY;
        for (int b = 0; b <= 1; b++) {
            for (int a = 0; a <= 1; a++) {
                double w = (a == 0 ? 1.0 - tx : tx) * (b == 0 ? 1.0 - tz : tz);
                if (w < 0.02) {
                    continue;
                }
                double ax = (fi + a) * FRAME;
                double az = (fj + b) * FRAME;
                double angle = Math.PI * strike.sample(ax / 7000.0 + offset, az / 7000.0);
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);
                double u = (x - ax) * cos + (z - az) * sin;
                double v = -(x - ax) * sin + (z - az) * cos;
                long h = SimplexNoise.mix((fi + a) * 0x9E3779B97F4A7C15L ^ (fj + b) * 0xC2B2AE3D27D4EB4FL ^ Double.doubleToLongBits(offset));
                double value = line.sample(u / 75.0 + 1000.0 * FluidDomains.unit(h, 0), v / 430.0 + 1000.0 * FluidDomains.unit(h, 1), gradient);
                double gu = gradient[0] / 75.0;
                double gv = gradient[1] / 430.0;
                double slope = Math.sqrt(gu * gu + gv * gv);
                best = Math.min(best, Math.abs(value) / Math.max(slope, 1e-4) / Math.sqrt(w));
            }
        }
        return best;
    }
}
