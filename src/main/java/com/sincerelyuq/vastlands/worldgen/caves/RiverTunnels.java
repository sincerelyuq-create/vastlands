package com.sincerelyuq.vastlands.worldgen.caves;

import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.smoothstep;

import com.sincerelyuq.vastlands.worldgen.geography.GeographyModel;
import com.sincerelyuq.vastlands.worldgen.hydrology.HydrologyModel;
import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;

/**
 * Rivers running through rock: natural tunnels and natural bridges over the rivers of the
 * hydrology.
 *
 * <p>The rivers themselves are untouched: channel, water levels, falls, boulders and the block step
 * that places their water are the hydrology's. The caves only add rock <em>above</em> the carved
 * ground, never below the top of any water near it, and always leave a vault over the water:
 * <ul>
 *     <li>Natural tunnels: where a river runs in a deep gorge through high ground (the regional
 *     ground ~30 blocks to either side 24..40+ blocks above the water), the river is narrow enough
 *     to span (half width under ~20) and a gate noise along the river (700 blocks) is open, the
 *     gorge gets its rock back up to {@code carved + b × (terrain - carved)}: the hill over the
 *     tunnel is the terrain as it was before the gorge was cut. {@code b} (0..1) varies smoothly
 *     along the river, so a tunnel starts as a gorge narrowing into a slot that closes into an arch
 *     (its mouth); tunnels fade out before lakes.</li>
 *     <li>Natural bridges: on rivers in valleys at least 13 blocks deep (more for wide rivers; no
 *     fall or lake within 40 blocks), a fin of rock 18..36 blocks thick spans the valley from wall
 *     to wall, its crest rounded a little below the valley's rim, its ends steep and ragged, a deck
 *     of at least 6 blocks over a low vault.</li>
 *     <li>The vault over the water: as wide as the channel, its bank and a few blocks more,
 *     arched, 7..20 blocks high above the highest water within reach (a fall inside a tunnel has
 *     the vault rising over it). Where the rock over it would be thinner than 7 blocks it stays
 *     open to the sky: skylights over falls, the slot gorges at tunnel mouths, the portals of
 *     bridges.</li>
 *     <li>Swallets: rarely, where the cave domain under a river is flooded at least 14 blocks
 *     below the river and no dam crosses it, a shaft opens in the bank beside the channel and a
 *     notch one block wide at the river's water level joins the two. The cave block step (after
 *     the hydrology's last water step) fills the notch with source water and hangs a curtain in
 *     the shaft: a flowing block at the river's water top and falling water down to the cave
 *     water, the states vanilla computes for them (like the hydrology's falls), so the river pours
 *     into the cave system for ever without a fluid tick and without draining.</li>
 *     <li>Grottoes behind falls: behind most falls of 19+ blocks a dry grotto is cut into the cliff
 *     under the lip, its floor one block above the plunge pool and its ceiling at least 15 blocks
 *     below the upper river's water (under its bed), opening onto the gorge behind the curtain,
 *     so from inside the falling water is a wall. The cliff face is found exactly from the
 *     hydrology's water columns (where the upper water top begins).</li>
 * </ul>
 */
final class RiverTunnels {
    static final double REACH = 80.0;
    private static final double MIN_ROOF = 7.0;
    /** Chance that a river segment (4 blocks of river) is the middle of a natural bridge, at the default setting. */
    private static final double BRIDGE_CHANCE = 0.01;
    /** Rock left over the vault of a natural bridge (a thinner deck than over a tunnel). */
    private static final double MIN_DECK = 6.0;
    private static final int MAX_BRIDGES = 4;
    /** Chance that a river segment is the site of a swallet, at the default setting. */
    private static final double SWALLET_CHANCE = 0.004;
    static final double SHAFT_RADIUS = 2.2;

    /** What a swallet makes of a column. */
    enum SwalletPart {
        NONE,
        /** Notch: open at the river's water top (source water) and the block above. */
        NOTCH,
        /** Shaft: open from below the cave water up to the sky. */
        SHAFT,
        /** Shaft column beside the notch: the curtain hangs here. */
        LIP
    }

    /** A swallet, rasterized: its cells and levels. */
    record Swallet(boolean exists, int water, int bottom, long[] notch, long[] shaft, long[] lips) {
        static final Swallet NONE = new Swallet(false, 0, 0, new long[0], new long[0], new long[0]);

        /** A shaft cell, for diagnostics. */
        long anyShaftCell() {
            return this.shaft.length > 0 ? this.shaft[0] : 0L;
        }

        SwalletPart part(int x, int z) {
            long key = cell(x, z);
            for (long c : this.lips) {
                if (c == key) {
                    return SwalletPart.LIP;
                }
            }
            for (long c : this.shaft) {
                if (c == key) {
                    return SwalletPart.SHAFT;
                }
            }
            for (long c : this.notch) {
                if (c == key) {
                    return SwalletPart.NOTCH;
                }
            }
            return SwalletPart.NONE;
        }
    }

    static long cell(int x, int z) {
        return (long) x << 32 | (z & 0xFFFFFFFFL);
    }

    /** The roof of one column. */
    static final class Roof {
        boolean active;
        /** Rock is restored from the carved ground up to this height... */
        double surface;
        /** ...except inside the vault, up to its ceiling, where the column lies inside it. */
        boolean vault;
        double ceiling;
        /** Gorge roof strength (0..1), or 1 under a natural bridge. */
        double strength;
        boolean bridge;
        /** The swallet the column belongs to, if any, and which part of it. */
        Swallet swallet = Swallet.NONE;
        SwalletPart part = SwalletPart.NONE;
        /** Open interval of a grotto behind a fall in this column, or {@code grottoLo > grottoHi}. */
        int grottoLo = 1;
        int grottoHi = 0;
    }

    interface Rivers {
        /** The hydrology's channel segments near a column (see {@link HydrologyModel#channels}). */
        void channels(int x, int z, double reach, HydrologyModel.ChannelVisitor visitor);

        /** Falls of 6+ blocks near a column: x, z, upper and lower water top (see {@link HydrologyModel#falls}). */
        java.util.List<double[]> falls(int x, int z);
    }

    /** A grotto behind a fall. */
    private record Grotto(boolean exists, double x, double z, double tx, double tz, double depth, double reach, double halfWidth, int floor, int ceiling) {
        static final Grotto NONE = new Grotto(false, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private record Bridge(boolean exists, double x, double z, double tx, double tz, double half, double top, int water,
                          double vaultRadius, double vaultHeight, long seed) {
        static final Bridge NONE = new Bridge(false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private final long seed;
    private final GeographyModel geography;
    private final CaveNetwork.Ground ground;
    private final Rivers rivers;
    private final CaveModel.Waters waters;
    private final FluidDomains domains;
    private final CaveSettings settings;
    private final int seaTop;
    private final SimplexNoise gate;
    private final SimplexNoise heights;
    private final SimplexNoise ragged;
    private final ThreadLocal<Scan> scans = ThreadLocal.withInitial(Scan::new);
    private final ThreadLocal<Cache> caches = ThreadLocal.withInitial(Cache::new);

    RiverTunnels(long seed, GeographyModel geography, CaveNetwork.Ground ground, Rivers rivers, CaveModel.Waters waters, FluidDomains domains,
                 CaveSettings settings, int seaTop) {
        this.seed = SimplexNoise.mix(seed ^ 0x54554E4E454CL);
        this.geography = geography;
        this.ground = ground;
        this.rivers = rivers;
        this.waters = waters;
        this.domains = domains;
        this.settings = settings;
        this.seaTop = seaTop;
        this.gate = new SimplexNoise(seed + 601);
        this.heights = new SimplexNoise(seed + 602);
        this.ragged = new SimplexNoise(seed + 603);
    }

    /** Nearest open channel, highest water and natural bridge middles around a column. */
    private final class Scan implements HydrologyModel.ChannelVisitor {
        long id;
        double ax, az, bx, bz, halfWidth, bank, distance;
        int waterTop;
        int highest;
        double lakeDistance;
        int candidates;
        final long[] ids = new long[MAX_BRIDGES];
        final double[][] lines = new double[MAX_BRIDGES][6];

        void reset() {
            this.distance = Double.POSITIVE_INFINITY;
            this.highest = Integer.MIN_VALUE;
            this.lakeDistance = Double.POSITIVE_INFINITY;
            this.candidates = 0;
            this.swallets = 0;
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
            if (this.swallets < MAX_BRIDGES && distance < halfWidth + bank + 12.0 && RiverTunnels.this.swalletCandidate(id)) {
                int i = this.swallets++;
                this.swalletIds[i] = id;
                double[] line = this.swalletLines[i];
                line[0] = ax;
                line[1] = az;
                line[2] = bx;
                line[3] = bz;
                line[4] = halfWidth;
                line[5] = bank;
                this.swalletTops[i] = waterTop;
            }
            if (this.candidates < MAX_BRIDGES && RiverTunnels.this.bridgeCandidate(id)) {
                int i = this.candidates++;
                this.ids[i] = id;
                double[] line = this.lines[i];
                line[0] = ax;
                line[1] = az;
                line[2] = bx;
                line[3] = bz;
                line[4] = halfWidth;
                line[5] = bank;
                this.lineTops[i] = waterTop;
            }
        }

        final int[] lineTops = new int[MAX_BRIDGES];
        int swallets;
        final long[] swalletIds = new long[MAX_BRIDGES];
        final double[][] swalletLines = new double[MAX_BRIDGES][6];
        final int[] swalletTops = new int[MAX_BRIDGES];
    }

    /** Collects whether any river segment near a point has another water top or lies in a lake. */
    private static final class Uniform implements HydrologyModel.ChannelVisitor {
        int water;
        boolean mixed;

        @Override
        public void segment(long id, double ax, double az, double bx, double bz, double halfWidth, double bank, int waterTop, boolean lake, double distance) {
            if (lake || waterTop != this.water) {
                this.mixed = true;
            }
        }
    }

    private boolean swalletCandidate(long id) {
        long h = SimplexNoise.mix(this.seed ^ id ^ 0x5357414CL);
        return (h >>> 11) * 0x1.0p-53 < SWALLET_CHANCE * this.settings.riverTunnels() / 0.5;
    }

    private boolean bridgeCandidate(long id) {
        long h = SimplexNoise.mix(this.seed ^ id);
        return (h >>> 11) * 0x1.0p-53 < BRIDGE_CHANCE * this.settings.riverTunnels() / 0.5;
    }

    /** Grottoes behind the falls near a column: face x, z, floor and ceiling (for previews and tests). */
    java.util.List<int[]> grottoesNear(int x, int z) {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        Cache cache = this.caches.get();
        for (double[] fall : cache.falls(this, x, z)) {
            Grotto g = cache.grotto(this, fall);
            if (g.exists) {
                out.add(new int[] {(int) Math.floor(g.x), (int) Math.floor(g.z), g.floor, g.ceiling});
            }
        }
        return out;
    }

    /** Swallets whose site lies near a column (for previews and tests). */
    java.util.List<Swallet> swalletsNear(int x, int z) {
        java.util.List<Swallet> out = new java.util.ArrayList<>();
        Scan scan = this.scans.get();
        scan.reset();
        this.rivers.channels(x, z, REACH, scan);
        Cache cache = this.caches.get();
        for (int i = 0; i < scan.swallets; i++) {
            Swallet swallet = cache.swallet(this, scan.swalletIds[i], scan.swalletLines[i], scan.swalletTops[i]);
            if (swallet.exists) {
                out.add(swallet);
            }
        }
        return out;
    }

    /**
     * The roof of a column, given the carved ground (hydrology surface) and the terrain before
     * carving there.
     */
    void roof(int x, int z, double carved, double terrain, Roof out) {
        out.active = false;
        out.swallet = Swallet.NONE;
        out.part = SwalletPart.NONE;
        out.grottoLo = 1;
        out.grottoHi = 0;
        if (this.settings.riverTunnels() <= 0.0) {
            return;
        }
        this.grotto(x, z, out);
        Scan scan = this.scans.get();
        scan.reset();
        this.rivers.channels(x, z, REACH, scan);
        if (scan.distance == Double.POSITIVE_INFINITY) {
            return;
        }
        Cache cache = this.caches.get();
        for (int i = 0; i < scan.swallets; i++) {
            Swallet swallet = cache.swallet(this, scan.swalletIds[i], scan.swalletLines[i], scan.swalletTops[i]);
            if (swallet.exists) {
                SwalletPart part = swallet.part(x, z);
                if (part != SwalletPart.NONE) {
                    out.swallet = swallet;
                    out.part = part;
                    break;
                }
            }
        }
        double surface = Double.NEGATIVE_INFINITY;
        double strength = 0.0;
        boolean bridge = false;
        // Gorge tunnel.
        if (terrain - carved >= 4.0) {
            double b = cache.strength(this, scan);
            double edge = scan.halfWidth + scan.bank;
            if (scan.lakeDistance < Double.POSITIVE_INFINITY) {
                b *= smoothstep(edge + 20.0, edge + 60.0, scan.lakeDistance);
            }
            if (b > 0.01) {
                surface = carved + b * (terrain - carved);
                strength = b;
            }
        }
        // Natural bridges.
        double vaultHeight = 0.0;
        double vaultRadius = 0.0;
        double flare = 0.0;
        for (int i = 0; i < scan.candidates; i++) {
            Bridge br = cache.bridge(this, scan.ids[i], scan.lines[i], scan.lineTops[i]);
            if (!br.exists) {
                continue;
            }
            double dx = x - br.x;
            double dz = z - br.z;
            double along = Math.abs(dx * br.tx + dz * br.tz);
            double half = br.half * (0.85 + 0.15 * (1.0 + this.ragged.sample(x / 14.0, z / 14.0)));
            if (along > half + 8.0) {
                continue;
            }
            // A rounded crest, then steep ragged faces at both ends of the fin.
            double t = Math.min(1.0, along / half);
            double top = br.top + 0.8 * this.ragged.sample(x / 16.0 + 50.0, z / 16.0) - 2.0 * t * t
                    - 2.5 * Math.max(0.0, along - half);
            if (top > surface) {
                surface = top;
                strength = 1.0;
                bridge = true;
                vaultHeight = br.vaultHeight;
                vaultRadius = br.vaultRadius;
                // The vault rises towards both portals: an arched opening, not a box.
                double end = Math.min(1.0, along / half);
                flare = 0.6 * br.vaultHeight * end * end * end;
            }
        }
        if (surface <= carved + 0.5) {
            return;
        }
        // The vault over the water.
        double edge = scan.halfWidth + scan.bank;
        double radius = bridge ? vaultRadius : edge + 3.0 + 2.0 * strength;
        out.vault = false;
        out.ceiling = Double.NEGATIVE_INFINITY;
        if (scan.distance < radius) {
            double height = bridge ? vaultHeight : Math.min(20.0, Math.max(7.0, 5.0 + 0.7 * scan.halfWidth));
            height *= 0.85 + 0.15 * this.heights.sample(x / 40.0, z / 40.0);
            double u = scan.distance / radius;
            double ceiling = Math.max(scan.highest, scan.waterTop) + 1.0 + (height + flare) * Math.pow(1.0 - u * u, 0.6);
            if (surface - ceiling < (bridge ? MIN_DECK : MIN_ROOF)) {
                return;
            }
            out.vault = true;
            out.ceiling = ceiling;
        }
        out.active = true;
        out.surface = surface;
        out.strength = strength;
        out.bridge = bridge;
    }

    /** Fills the grotto interval of a column if a grotto behind a fall reaches it. */
    private void grotto(int x, int z, Roof out) {
        Cache cache = this.caches.get();
        for (double[] fall : cache.falls(this, x, z)) {
            if (Math.abs(fall[0] - x) > 40.0 || Math.abs(fall[1] - z) > 40.0) {
                continue;
            }
            Grotto g = cache.grotto(this, fall);
            if (!g.exists) {
                continue;
            }
            double dx = x + 0.5 - g.x;
            double dz = z + 0.5 - g.z;
            double along = dx * g.tx + dz * g.tz;
            double across = dz * g.tx - dx * g.tz;
            // An elliptical room reaching `depth` into the cliff upstream of the face, and its mouth
            // running on downstream (same section) through the rocky rim to the plunge pool.
            double a = along > -0.5 * g.depth ? 0.0 : (along + 0.5 * g.depth) / (0.5 * g.depth + 1.0);
            // The mouth is wider than the room: the curtain curves round the lip of a tall fall.
            double b = across / (along > 0.0 ? 1.3 * g.halfWidth : g.halfWidth);
            double q = a * a + b * b;
            if (along > g.reach || q >= 1.0) {
                continue;
            }
            int top = this.underWater(x, z, g.floor, g.floor + (int) Math.floor((g.ceiling - g.floor) * Math.pow(1.0 - q, 0.4)));
            if (top >= g.floor + 2) {
                out.grottoLo = g.floor;
                out.grottoHi = top;
                return;
            }
        }
    }

    /**
     * Lowers the top of a grotto interval {@code lo..hi} so that rock stays under the river water
     * of the column (the curtain of the fall hangs over the cliff face) and beside the water of its
     * four neighbours, with a block to spare. The plunge pool's water lies below {@code lo}.
     *
     * @return the new top, below {@code lo} if the column must stay closed
     */
    private int underWater(int x, int z, int lo, int hi) {
        for (int k = 0; k < 5; k++) {
            int cx = x + (k == 1 ? 1 : k == 2 ? -1 : 0);
            int cz = z + (k == 3 ? 1 : k == 4 ? -1 : 0);
            int top = this.waters.placedTop(cx, cz);
            if (top == Integer.MIN_VALUE || top < lo) {
                continue;
            }
            int bottom = (int) Math.ceil(this.ground.surface(cx, cz)) - (k == 0 ? 2 : 1);
            if (bottom <= lo) {
                return lo - 1;
            }
            hi = Math.min(hi, bottom - 1);
        }
        return hi;
    }

    /** A grotto behind a fall, if the fall suits one. */
    private Grotto grottoFor(double[] fall) {
        int upper = (int) fall[2];
        int lower = (int) fall[3];
        int drop = upper - lower;
        if (drop < 19) {
            return Grotto.NONE;
        }
        long h = SimplexNoise.mix(this.seed ^ Double.doubleToLongBits(fall[0]) * 31 ^ Double.doubleToLongBits(fall[1]) ^ 0x47524F54L);
        if (FluidDomains.unit(h, 0) >= 0.7 * this.settings.riverTunnels() / 0.5) {
            return Grotto.NONE;
        }
        // The river's downstream direction at the fall.
        Scan scan = new Scan();
        scan.reset();
        this.rivers.channels((int) Math.floor(fall[0]), (int) Math.floor(fall[1]), 12.0, scan);
        if (scan.distance == Double.POSITIVE_INFINITY) {
            return Grotto.NONE;
        }
        double dx = scan.bx - scan.ax;
        double dz = scan.bz - scan.az;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-6) {
            return Grotto.NONE;
        }
        double tx = dx / len;
        double tz = dz / len;
        // The cliff face: where the upper water begins, walking upstream from below the fall.
        double face = Double.NaN;
        for (int s = 12; s >= -24; s--) {
            int top = this.waters.placedTop((int) Math.floor(fall[0] + tx * s), (int) Math.floor(fall[1] + tz * s));
            if (top == upper) {
                face = s + 0.5;
                break;
            }
        }
        if (face != face) {
            return Grotto.NONE;
        }
        // The mouth reaches the first column of the plunge pool downstream of the face.
        double reach = Double.NaN;
        for (int s = (int) Math.floor(face) + 1; s <= (int) Math.floor(face) + 14; s++) {
            int top = this.waters.placedTop((int) Math.floor(fall[0] + tx * s), (int) Math.floor(fall[1] + tz * s));
            if (top == lower) {
                reach = s + 3.5 - face;
                break;
            }
        }
        if (reach != reach) {
            return Grotto.NONE;
        }
        double px = fall[0] + tx * face;
        double pz = fall[1] + tz * face;
        int floor = lower + 1;
        int ceiling = floor + Math.min(9, drop - 15);
        double depth = 6.0 + 6.0 * FluidDomains.unit(h, 1);
        double halfWidth = Math.min(9.0, Math.max(4.0, scan.halfWidth + 1.0));
        // Dry rock around it: the cave domain holds no water at the grotto, and no dam is near.
        FluidDomains.Hit hit = new FluidDomains.Hit();
        this.domains.locate(px - tx * depth * 0.5, pz - tz * depth * 0.5, false, hit);
        if (hit.owner.level >= floor - 2) {
            return Grotto.NONE;
        }
        for (int k = 0; k < hit.count; k++) {
            if (hit.edge[k] < 12.0) {
                return Grotto.NONE;
            }
        }
        return new Grotto(true, px, pz, tx, tz, depth, reach, halfWidth, floor, ceiling);
    }

    /** Roof strength of a river segment for gorge tunnels. */
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

    /** A natural bridge centred on a river segment, if the valley there suits one. */
    private Bridge bridge(long id, double[] line, int water) {
        double halfWidth = line[4];
        double bank = line[5];
        if (halfWidth > 22.0 || water < this.seaTop + 2) {
            return Bridge.NONE;
        }
        double dx = line[2] - line[0];
        double dz = line[3] - line[1];
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-6) {
            return Bridge.NONE;
        }
        double tx = dx / len;
        double tz = dz / len;
        double cx = 0.5 * (line[0] + line[2]);
        double cz = 0.5 * (line[1] + line[3]);
        // No fall, ledge or lake within 40 blocks: one flat pool under the bridge.
        Uniform uniform = new Uniform();
        uniform.water = water;
        this.rivers.channels((int) Math.floor(cx), (int) Math.floor(cz), 40.0, uniform);
        if (uniform.mixed) {
            return Bridge.NONE;
        }
        // The valley rim on both sides: the highest carved ground within 40 blocks of the bank.
        double rim = Double.POSITIVE_INFINITY;
        for (int side = -1; side <= 1; side += 2) {
            double highest = Double.NEGATIVE_INFINITY;
            for (int k = 1; k <= 5; k++) {
                double d = halfWidth + bank + 8.0 * k;
                highest = Math.max(highest, this.ground.surface(cx - side * tz * d, cz + side * tx * d));
            }
            rim = Math.min(rim, highest);
        }
        double depth = rim - water;
        if (depth < 13.0 + 0.3 * halfWidth || depth > 70.0) {
            return Bridge.NONE;
        }
        long h = SimplexNoise.mix(this.seed ^ id ^ 0x425249444745L);
        double top = rim - 1.0 - 2.0 * FluidDomains.unit(h, 0);
        double vaultHeight = Math.min(top - water - 3.0 - MIN_DECK, 4.0 + 0.5 * halfWidth + 2.0 * FluidDomains.unit(h, 1));
        if (vaultHeight < 3.5) {
            return Bridge.NONE;
        }
        double half = 9.0 + 9.0 * FluidDomains.unit(h, 2);
        return new Bridge(true, cx, cz, tx, tz, half, top, water, halfWidth + bank + 2.0, vaultHeight, h);
    }

    /**
     * A swallet beside a river segment, if the site suits one: a shaft in the bank down into the
     * flooded cave zone and a notch from the water to it, rasterized into cells and checked so the
     * placed water stays exactly where it is.
     */
    private Swallet swallet(long id, double[] line, int water) {
        double halfWidth = line[4];
        double bank = line[5];
        if (halfWidth > 14.0 || water < this.seaTop + 4) {
            return Swallet.NONE;
        }
        double dx = line[2] - line[0];
        double dz = line[3] - line[1];
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-6) {
            return Swallet.NONE;
        }
        long h = SimplexNoise.mix(this.seed ^ id ^ 0x53484146L);
        double side = FluidDomains.unit(h, 0) < 0.5 ? -1.0 : 1.0;
        double nx = -dz / len * side;
        double nz = dx / len * side;
        double mx = 0.5 * (line[0] + line[2]);
        double mz = 0.5 * (line[1] + line[3]);
        Uniform uniform = new Uniform();
        uniform.water = water;
        this.rivers.channels((int) Math.floor(mx), (int) Math.floor(mz), 24.0, uniform);
        if (uniform.mixed) {
            return Swallet.NONE;
        }
        double sx = mx + nx * (halfWidth + bank + 4.0);
        double sz = mz + nz * (halfWidth + bank + 4.0);
        // The cave water under the shaft: one flooded domain, well below the river, no dam near.
        FluidDomains.Hit hit = new FluidDomains.Hit();
        this.domains.locate(sx, sz, false, hit);
        FluidDomains.Domain domain = hit.owner;
        if (domain.fluid != CaveFluid.WATER || domain.level > water - 14
                || domain.level - 6 < this.domains.aquitard(sx, sz) + FluidDomains.AQUITARD_HALF + 3.0) {
            return Swallet.NONE;
        }
        for (int k = 0; k < hit.count; k++) {
            if (hit.edge[k] < 9.0) {
                return Swallet.NONE;
            }
        }
        // Rasterize: shaft cells around the shaft centre, notch cells along the line from the water.
        java.util.List<Long> shaft = new java.util.ArrayList<>();
        java.util.List<Long> notch = new java.util.ArrayList<>();
        int r = (int) Math.ceil(SHAFT_RADIUS) + 1;
        for (int cz = (int) Math.floor(sz) - r; cz <= (int) Math.floor(sz) + r; cz++) {
            for (int cx = (int) Math.floor(sx) - r; cx <= (int) Math.floor(sx) + r; cx++) {
                if (Math.hypot(cx + 0.5 - sx, cz + 0.5 - sz) <= SHAFT_RADIUS) {
                    if (this.waters.placedTop(cx, cz) != Integer.MIN_VALUE) {
                        return Swallet.NONE;
                    }
                    shaft.add(cell(cx, cz));
                }
            }
        }
        // The notch: a 4-connected staircase of cells from the last water cell to the shaft.
        double fx = mx + nx * (halfWidth - 1.0);
        double fz = mz + nz * (halfWidth - 1.0);
        int cx = (int) Math.floor(fx);
        int cz = (int) Math.floor(fz);
        int tx = (int) Math.floor(sx);
        int tz = (int) Math.floor(sz);
        if (this.waters.placedTop(cx, cz) != water) {
            return Swallet.NONE;
        }
        for (int step = 0; step < 24; step++) {
            long key = cell(cx, cz);
            if (shaft.contains(key)) {
                break;
            }
            if (this.waters.placedTop(cx, cz) != water) {
                notch.add(key);
            } else if (!notch.isEmpty()) {
                return Swallet.NONE;
            }
            int ex = tx - cx;
            int ez = tz - cz;
            if (Math.abs(ex) >= Math.abs(ez)) {
                cx += Integer.signum(ex);
            } else {
                cz += Integer.signum(ez);
            }
        }
        if (notch.isEmpty() || notch.size() > 8 || !shaft.contains(cell(cx, cz))) {
            return Swallet.NONE;
        }
        // The rock beside the notch must hold its water: solid at the water top.
        java.util.Set<Long> open = new java.util.HashSet<>(shaft);
        open.addAll(notch);
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (long key : notch) {
            int nxc = (int) (key >> 32);
            int nzc = (int) key;
            for (int[] offset : sides) {
                int ax = nxc + offset[0];
                int az = nzc + offset[1];
                if (open.contains(cell(ax, az)) || this.waters.placedTop(ax, az) == water) {
                    continue;
                }
                if (this.ground.surface(ax, az) <= water + 0.05 || this.waters.placedTop(ax, az) != Integer.MIN_VALUE) {
                    return Swallet.NONE;
                }
            }
        }
        java.util.List<Long> lips = new java.util.ArrayList<>();
        for (long key : shaft) {
            int lx = (int) (key >> 32);
            int lz = (int) key;
            for (long n : notch) {
                if (Math.abs((int) (n >> 32) - lx) + Math.abs((int) n - lz) == 1) {
                    lips.add(key);
                    break;
                }
            }
        }
        if (lips.isEmpty()) {
            return Swallet.NONE;
        }
        // The shaft's own ground must be dry land above the water.
        if (this.ground.surface(sx, sz) < water + 1.5) {
            return Swallet.NONE;
        }
        return new Swallet(true, water, domain.level - 3, toArray(notch), toArray(shaft), toArray(lips));
    }

    private static long[] toArray(java.util.List<Long> values) {
        long[] out = new long[values.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = values.get(i);
        }
        return out;
    }

    /** Per-thread caches of gorge strengths and bridges by segment identity. */
    private static final class Cache {
        private static final int SIZE = 4096;
        private final long[] strengthIds = new long[SIZE];
        private final double[] strengthAx = new double[SIZE];
        private final double[] strengthAz = new double[SIZE];
        private final double[] strengths = new double[SIZE];
        private final boolean[] strengthFilled = new boolean[SIZE];
        private final long[] bridgeIds = new long[256];
        private final double[][] bridgeStarts = new double[256][2];
        private final Bridge[] bridges = new Bridge[256];
        private final long[] swalletIds = new long[256];
        private final double[][] swalletStarts = new double[256][2];
        private final Swallet[] swallets = new Swallet[256];
        private final long[] fallRegions = new long[16];
        @SuppressWarnings("unchecked")
        private final java.util.List<double[]>[] fallLists = new java.util.List[16];
        private final java.util.Map<java.util.List<Double>, Grotto> grottoes = new java.util.LinkedHashMap<>(64, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(java.util.Map.Entry<java.util.List<Double>, Grotto> eldest) {
                return this.size() > 512;
            }
        };

        /** Falls near a 512-block region (cached per region). */
        java.util.List<double[]> falls(RiverTunnels tunnels, int x, int z) {
            int rx = Math.floorDiv(x, 512);
            int rz = Math.floorDiv(z, 512);
            long key = (long) rx << 32 | (rz & 0xFFFFFFFFL);
            int slot = (rx & 3) << 2 | (rz & 3);
            if (this.fallLists[slot] != null && this.fallRegions[slot] == key) {
                return this.fallLists[slot];
            }
            java.util.List<double[]> near = new java.util.ArrayList<>();
            for (double[] fall : tunnels.rivers.falls(x, z)) {
                if (fall[0] >= rx * 512 - 64 && fall[0] < rx * 512 + 576 && fall[1] >= rz * 512 - 64 && fall[1] < rz * 512 + 576) {
                    near.add(fall);
                }
            }
            this.fallRegions[slot] = key;
            this.fallLists[slot] = near;
            return near;
        }

        Grotto grotto(RiverTunnels tunnels, double[] fall) {
            java.util.List<Double> key = java.util.List.of(fall[0], fall[1]);
            Grotto g = this.grottoes.get(key);
            if (g == null) {
                g = tunnels.grottoFor(fall);
                this.grottoes.put(key, g);
            }
            return g;
        }

        Swallet swallet(RiverTunnels tunnels, long id, double[] line, int water) {
            int slot = (int) (SimplexNoise.mix(id ^ 0x5357L) & 255);
            Swallet swallet = this.swallets[slot];
            if (swallet != null && this.swalletIds[slot] == id && this.swalletStarts[slot][0] == line[0] && this.swalletStarts[slot][1] == line[1]) {
                return swallet;
            }
            swallet = tunnels.swallet(id, line, water);
            this.swalletIds[slot] = id;
            this.swalletStarts[slot][0] = line[0];
            this.swalletStarts[slot][1] = line[1];
            this.swallets[slot] = swallet;
            return swallet;
        }

        double strength(RiverTunnels tunnels, Scan scan) {
            int slot = (int) (SimplexNoise.mix(scan.id) & (SIZE - 1));
            // The identity is a hash of the segment: compare its start too, so a collision can
            // never hand one segment another's value.
            if (this.strengthFilled[slot] && this.strengthIds[slot] == scan.id && this.strengthAx[slot] == scan.ax && this.strengthAz[slot] == scan.az) {
                return this.strengths[slot];
            }
            double value = tunnels.strength(scan);
            this.strengthIds[slot] = scan.id;
            this.strengthAx[slot] = scan.ax;
            this.strengthAz[slot] = scan.az;
            this.strengths[slot] = value;
            this.strengthFilled[slot] = true;
            return value;
        }

        Bridge bridge(RiverTunnels tunnels, long id, double[] line, int water) {
            int slot = (int) (SimplexNoise.mix(id) & 255);
            Bridge bridge = this.bridges[slot];
            if (bridge != null && this.bridgeIds[slot] == id && this.bridgeStarts[slot][0] == line[0] && this.bridgeStarts[slot][1] == line[1]) {
                return bridge;
            }
            bridge = tunnels.bridge(id, line, water);
            this.bridgeIds[slot] = id;
            this.bridgeStarts[slot][0] = line[0];
            this.bridgeStarts[slot][1] = line[1];
            this.bridges[slot] = bridge;
            return bridge;
        }
    }
}
