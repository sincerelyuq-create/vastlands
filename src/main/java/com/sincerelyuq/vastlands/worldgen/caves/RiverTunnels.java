package com.sincerelyuq.vastlands.worldgen.caves;

import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.smoothstep;

import com.sincerelyuq.vastlands.worldgen.geography.GeographyModel;
import com.sincerelyuq.vastlands.worldgen.hydrology.HydrologyModel;
import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;

/**
 * Natural tunnels: where a river of the hydrology runs in a deep gorge through high ground, the
 * caves give the gorge back its rock and leave a vaulted tunnel over the water, so the river
 * disappears into a cave mouth on one side of the hill and comes out on the other.
 *
 * <p>The river itself is untouched: its channel, water levels, falls, boulders and the block step
 * that places its water are the hydrology's. The caves only add rock <em>above</em> the carved
 * ground, never below the top of any water near it:
 * <ul>
 *     <li>A segment of a river is roofed by a strength {@code b} (0..1): the gorge must be deep (the
 *     regional ground ~30 blocks to either side stands 24..40+ blocks above the water), the river
 *     narrow enough to span (half width under ~20), and a gate noise along the river (700 blocks)
 *     open; tunnels fade out before lakes. {@code b} varies smoothly along the river, so a tunnel
 *     starts as a gorge that narrows into a slot and closes into an arch (its mouth).</li>
 *     <li>Rock comes back up to {@code carved + b × (terrain - carved)}: the hill over a tunnel is
 *     the terrain as it was before the gorge was cut.</li>
 *     <li>Over the water a vault stays open: as wide as the channel, its bank and three blocks
 *     more, 7..20 blocks high above the highest water within reach (so a fall inside a tunnel
 *     has the vault rising over it), arched across.</li>
 *     <li>Where the rock over the vault would be thinner than 7 blocks the vault stays open to the
 *     sky: skylights, often over falls, and the slot gorges at the mouths.</li>
 * </ul>
 */
final class RiverTunnels {
    static final double REACH = 80.0;
    private static final double MIN_ROOF = 7.0;

    /** The roof of one column. */
    static final class Roof {
        boolean active;
        /** Rock is restored from the carved ground up to this height... */
        double surface;
        /** ...except inside the vault, up to its ceiling, where the column lies inside it. */
        boolean vault;
        double ceiling;
        double strength;
    }

    interface Rivers {
        /** The hydrology's channel segments near a column (see {@link HydrologyModel#channels}). */
        void channels(int x, int z, double reach, HydrologyModel.ChannelVisitor visitor);
    }

    private final GeographyModel geography;
    private final Rivers rivers;
    private final CaveSettings settings;
    private final SimplexNoise gate;
    private final SimplexNoise heights;
    private final ThreadLocal<Scan> scans = ThreadLocal.withInitial(Scan::new);
    private final ThreadLocal<StrengthCache> strengths = ThreadLocal.withInitial(StrengthCache::new);

    RiverTunnels(long seed, GeographyModel geography, Rivers rivers, CaveSettings settings) {
        this.geography = geography;
        this.rivers = rivers;
        this.settings = settings;
        this.gate = new SimplexNoise(seed + 601);
        this.heights = new SimplexNoise(seed + 602);
    }

    /** Nearest open channel and highest water around a column. */
    private static final class Scan implements HydrologyModel.ChannelVisitor {
        long id;
        double ax, az, bx, bz, halfWidth, bank, distance;
        int waterTop;
        int highest;
        double lakeDistance;

        void reset() {
            this.distance = Double.POSITIVE_INFINITY;
            this.highest = Integer.MIN_VALUE;
            this.lakeDistance = Double.POSITIVE_INFINITY;
        }

        @Override
        public void segment(long id, double ax, double az, double bx, double bz, double halfWidth, double bank, int waterTop, boolean lake, double distance) {
            if (lake) {
                this.lakeDistance = Math.min(this.lakeDistance, distance - halfWidth);
                return;
            }
            if (distance <= halfWidth + bank + 18.0) {
                this.highest = Math.max(this.highest, waterTop);
            }
            if (distance < this.distance) {
                this.id = id;
                this.ax = ax;
                this.az = az;
                this.bx = bx;
                this.bz = bz;
                this.halfWidth = halfWidth;
                this.bank = bank;
                this.waterTop = waterTop;
                this.distance = distance;
            }
        }
    }

    /**
     * The roof of a column, given the carved ground (hydrology surface) and the terrain before
     * carving there.
     */
    void roof(int x, int z, double carved, double terrain, Roof out) {
        out.active = false;
        if (this.settings.riverTunnels() <= 0.0 || terrain - carved < 4.0) {
            return;
        }
        Scan scan = this.scans.get();
        scan.reset();
        this.rivers.channels(x, z, REACH, scan);
        if (scan.distance == Double.POSITIVE_INFINITY) {
            return;
        }
        double b = this.strengths.get().get(this, scan);
        double edge = scan.halfWidth + scan.bank;
        if (scan.lakeDistance < Double.POSITIVE_INFINITY) {
            b *= smoothstep(edge + 20.0, edge + 60.0, scan.lakeDistance);
        }
        if (b <= 0.01) {
            return;
        }
        double surface = carved + b * (terrain - carved);
        if (surface <= carved + 0.5) {
            return;
        }
        double radius = edge + 3.0 + 2.0 * b;
        if (scan.distance < radius) {
            double height = Math.min(20.0, Math.max(7.0, 5.0 + 0.7 * scan.halfWidth))
                    * (0.85 + 0.15 * this.heights.sample(x / 40.0, z / 40.0));
            double u = scan.distance / radius;
            double ceiling = Math.max(scan.highest, scan.waterTop) + 1.0 + height * Math.pow(1.0 - u * u, 0.6);
            if (surface - ceiling < MIN_ROOF) {
                return;
            }
            out.vault = true;
            out.ceiling = ceiling;
        } else {
            out.vault = false;
            out.ceiling = Double.NEGATIVE_INFINITY;
        }
        out.active = true;
        out.surface = surface;
        out.strength = b;
    }

    /** Roof strength of a river segment (cached per thread by segment identity). */
    private double strength(Scan s) {
        double dx = s.bx - s.ax;
        double dz = s.bz - s.az;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-6) {
            return 0.0;
        }
        double nx = -dz / len;
        double nz = dx / len;
        double mx = 0.5 * (s.ax + s.bx);
        double mz = 0.5 * (s.az + s.bz);
        double off = s.halfWidth + s.bank + 28.0;
        double left = this.geography.smoothElevation(mx + nx * off, mz + nz * off);
        double right = this.geography.smoothElevation(mx - nx * off, mz - nz * off);
        double depth = Math.min(left, right) - s.waterTop;
        double threshold = 0.55 - 0.7 * this.settings.riverTunnels();
        double open = smoothstep(threshold, threshold + 0.15, this.gate.sample(mx / 700.0, mz / 700.0));
        return smoothstep(24.0, 40.0, depth) * open * smoothstep(24.0, 15.0, s.halfWidth);
    }

    private static final class StrengthCache {
        private static final int SIZE = 4096;
        private final long[] ids = new long[SIZE];
        private final double[] values = new double[SIZE];
        private final boolean[] filled = new boolean[SIZE];

        double get(RiverTunnels tunnels, Scan scan) {
            int slot = (int) (SimplexNoise.mix(scan.id) & (SIZE - 1));
            if (this.filled[slot] && this.ids[slot] == scan.id) {
                return this.values[slot];
            }
            double value = tunnels.strength(scan);
            this.ids[slot] = scan.id;
            this.values[slot] = value;
            this.filled[slot] = true;
            return value;
        }
    }
}
