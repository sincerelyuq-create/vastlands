package com.sincerelyuq.vastlands.worldgen.forest;

import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;

/**
 * The shapes of Vastlands trees: one generator per {@link TreeForm}, building logs, leaves and
 * decorations into a {@link TreeShape.Builder} from a {@link TreeSpec}. Everything is drawn from
 * the spec's own random sequence, so a tree is the same whichever chunk asks for it.
 *
 * <p>Shapes follow vanilla's look (log axes along branches, leaves in clusters, vanilla blocks
 * only) with more structure: trunks that lean and curve a little, branches spread by the golden
 * angle and bent upward, crowns made of lumpy clusters at the branch ends, conifers in whorls,
 * giants with flared roots and heavy limbs. Every block stays within {@link TreeShape#REACH} of
 * the root horizontally.
 */
public final class TreeShapes {
    private static final double GOLDEN = 2.399963229728653;
    private static final int MAX = TreeShape.REACH - 1;

    private TreeShapes() {
    }

    private static final ThreadLocal<TreeShape.Builder> BUILDERS = ThreadLocal.withInitial(TreeShape.Builder::new);

    /** The shape of a tree. */
    public static TreeShape shape(TreeSpec spec) {
        TreeShape.Builder b = BUILDERS.get();
        b.clear(ForestModel.reach(spec));
        Ctx c = new Ctx(b, spec);
        switch (spec.form()) {
            case BUSH -> bush(c);
            case BROADLEAF -> broadleaf(c);
            case ANCIENT -> ancient(c);
            case BIRCH -> birch(c);
            case CONIFER -> conifer(c, false, false);
            case PINE -> conifer(c, true, false);
            case MEGA_CONIFER -> conifer(c, false, true);
            case MEGA_PINE -> conifer(c, true, true);
            case KRUMMHOLZ -> krummholz(c);
            case DARK_OAK -> darkOak(c);
            case TALL_JUNGLE -> tallJungle(c);
            case MEGA_JUNGLE -> megaJungle(c);
            case ACACIA -> acacia(c);
            case SWAMP -> swamp(c);
            case SNAG -> snag(c);
            case FALLEN -> fallen(c);
            case HUGE_BROWN_MUSHROOM -> hugeMushroom(c, true);
            case HUGE_RED_MUSHROOM -> hugeMushroom(c, false);
        }
        return b.build();
    }

    /** Drawing state of one tree. */
    private static final class Ctx {
        final TreeShape.Builder b;
        final TreeSpec spec;
        final TreeRandom r;
        final Wood wood;
        final long noiseSeed;
        final int log;
        final int leaves;

        Ctx(TreeShape.Builder b, TreeSpec spec) {
            this.b = b;
            this.spec = spec;
            this.r = new TreeRandom(spec.seed());
            this.wood = spec.wood();
            this.noiseSeed = SimplexNoise.mix(spec.seed() ^ 0x4C3A5F0E1D2B6987L);
            this.log = TreeBlocks.code(TreeBlocks.LOG, this.wood, TreeBlocks.AXIS_Y);
            this.leaves = TreeBlocks.code(TreeBlocks.LEAVES, this.wood, 0);
        }

        int log(int axis) {
            return TreeBlocks.code(TreeBlocks.LOG, this.wood, axis);
        }

        int barkWood() {
            return TreeBlocks.code(TreeBlocks.WOOD, this.wood, 0);
        }

        /** Smooth value noise in about [0, 1] at a 2.2-block scale: lumps on crowns. */
        double lump(double x, double y, double z) {
            double fx = x / 2.2;
            double fy = y / 2.2;
            double fz = z / 2.2;
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
                v += w * this.hash(ix + ox, iy + oy, iz + oz);
            }
            return v;
        }

        double hash(long x, long y, long z) {
            long h = SimplexNoise.mix(this.noiseSeed ^ x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L);
            return (h >>> 11) * 0x1.0p-53;
        }
    }

    private static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private static int clampReach(double v) {
        return (int) Math.round(Math.max(-MAX, Math.min(MAX, v)));
    }

    // ---------------------------------------------------------------- primitives

    /**
     * Logs along a segment, face to face where the line steps sideways, each with the axis the
     * segment runs along (like vanilla's branches).
     */
    private static void logLine(Ctx c, double x0, double y0, double z0, double x1, double y1, double z1) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        double adx = Math.abs(dx);
        double ady = Math.abs(dy);
        double adz = Math.abs(dz);
        int axis = ady >= Math.max(adx, adz) ? TreeBlocks.AXIS_Y : (adx >= adz ? TreeBlocks.AXIS_X : TreeBlocks.AXIS_Z);
        int code = c.log(axis);
        int steps = Math.max(1, (int) Math.ceil(Math.max(adx, Math.max(ady, adz)) * 2.0));
        int px = (int) Math.round(x0);
        int py = (int) Math.round(y0);
        int pz = (int) Math.round(z0);
        c.b.log(px, py, pz, code);
        for (int s = 1; s <= steps; s++) {
            double t = s / (double) steps;
            int nx = (int) Math.round(x0 + dx * t);
            int ny = (int) Math.round(y0 + dy * t);
            int nz = (int) Math.round(z0 + dz * t);
            // Face-connected: move one axis at a time (the segment's own axis last).
            if (ny != py && (nx != px || nz != pz)) {
                if (axis == TreeBlocks.AXIS_Y) {
                    c.b.log(nx, py, nz, code);
                } else {
                    c.b.log(px, ny, pz, code);
                }
            }
            if (nx != px && nz != pz) {
                c.b.log(nx, ny, pz, code);
            }
            c.b.log(nx, ny, nz, code);
            px = nx;
            py = ny;
            pz = nz;
        }
    }

    /** A thick limb: logs filling a tube of radius r0 tapering to r1 (a plain line where thin). */
    private static void thickLine(Ctx c, double x0, double y0, double z0, double x1, double y1, double z1, double r0, double r1) {
        logLine(c, x0, y0, z0, x1, y1, z1);
        double len = Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (z1 - z0) * (z1 - z0));
        if (Math.max(r0, r1) < 0.75) {
            return;
        }
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        int axis = Math.abs(dy) >= Math.max(Math.abs(dx), Math.abs(dz)) ? TreeBlocks.AXIS_Y
                : (Math.abs(dx) >= Math.abs(dz) ? TreeBlocks.AXIS_X : TreeBlocks.AXIS_Z);
        int code = c.log(axis);
        int steps = Math.max(1, (int) Math.ceil(len * 2.0));
        for (int s = 0; s <= steps; s++) {
            double t = s / (double) steps;
            double r = r0 + (r1 - r0) * t;
            if (r < 0.75) {
                continue;
            }
            double cx = x0 + dx * t;
            double cy = y0 + dy * t;
            double cz = z0 + dz * t;
            int ri = (int) Math.ceil(r);
            for (int ox = -ri; ox <= ri; ox++) {
                for (int oy = -ri; oy <= ri; oy++) {
                    for (int oz = -ri; oz <= ri; oz++) {
                        int bx = (int) Math.round(cx) + ox;
                        int by = (int) Math.round(cy) + oy;
                        int bz = (int) Math.round(cz) + oz;
                        double ex = bx - cx;
                        double ey = by - cy;
                        double ez = bz - cz;
                        // Distance from the segment's axis at this sample.
                        double along = (ex * dx + ey * dy + ez * dz) / Math.max(1e-6, len);
                        double perp2 = ex * ex + ey * ey + ez * ez - along * along;
                        if (perp2 <= r * r * 0.82 && Math.abs(along) <= 0.6) {
                            c.b.log(bx, by, bz, code);
                        }
                    }
                }
            }
        }
    }

    /**
     * A lumpy cluster of leaves: an ellipsoid whose surface is pushed in and out by value noise,
     * with a ragged outer shell. Only fills empty cells.
     */
    private static void blob(Ctx c, double cx, double cy, double cz, double rx, double ry, double rz, double rough) {
        int x0 = (int) Math.floor(cx - rx - 1);
        int x1 = (int) Math.ceil(cx + rx + 1);
        int y0 = (int) Math.floor(cy - ry - 1);
        int y1 = (int) Math.ceil(cy + ry + 1);
        int z0 = (int) Math.floor(cz - rz - 1);
        int z1 = (int) Math.ceil(cz + rz + 1);
        // The lumps move the surface by at most this much (lump is in [0, 1)): skip the noise outside.
        double outer = 1.0 + Math.abs(rough) * 0.8 + 1e-9;
        for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    double ex = (x - cx) / rx;
                    double ey = (y - cy) / ry;
                    double ez = (z - cz) / rz;
                    double d = ex * ex + ey * ey + ez * ez;
                    if (d > outer) {
                        continue;
                    }
                    double limit = 1.0 + rough * (c.lump(x, y, z) - 0.5) * 1.6;
                    if (d > limit) {
                        continue;
                    }
                    // Ragged shell: some outer leaves are missing.
                    if (d > 0.62 * limit && c.hash(x, y + 977, z) < 0.22 * rough + 0.05) {
                        continue;
                    }
                    c.b.soft(x, y, z, c.leaves);
                }
            }
        }
    }

    /** A flat irregular disc of leaves (one layer): conifer whorls, acacia and dark oak canopies. */
    private static void disc(Ctx c, double cx, int y, double cz, double radius, double rough) {
        int ri = (int) Math.ceil(radius + 1);
        double outer = radius * (1.0 + Math.abs(rough) * 0.55) + 1e-9;
        for (int z = (int) Math.floor(cz) - ri; z <= (int) Math.ceil(cz) + ri; z++) {
            for (int x = (int) Math.floor(cx) - ri; x <= (int) Math.ceil(cx) + ri; x++) {
                double ex = x - cx;
                double ez = z - cz;
                double d = Math.sqrt(ex * ex + ez * ez);
                if (d > outer) {
                    continue;
                }
                double limit = radius * (1.0 + rough * (c.lump(x, y * 1.7, z) - 0.5) * 1.1);
                if (d <= limit && !(d > limit - 0.9 && c.hash(x, y + 311, z) < 0.3 * rough)) {
                    c.b.soft(x, y, z, c.leaves);
                }
            }
        }
    }

    /** Vine clinging to the face {@code f} (0 north, 1 east, 2 south, 3 west) of the block at x, y, z, hanging down up to {@code length}. */
    private static void vine(Ctx c, int x, int y, int z, int f, int length) {
        int vx = x - TreeShape.Builder.FACE_X[f];
        int vz = z - TreeShape.Builder.FACE_Z[f];
        int code = TreeBlocks.code(TreeBlocks.VINE, c.wood, 1 << f);
        for (int k = 0; k < length; k++) {
            int vy = y - k;
            if (vy < 1 || !c.b.isEmpty(vx, vy, vz)) {
                return;
            }
            c.b.soft(vx, vy, vz, code);
        }
    }

    /** Vines on the trunk columns and hanging from the lower crown, by {@code spec.vines}. */
    private static void vines(Ctx c, int trunkTop, int crownBottom) {
        double amount = c.spec.vines();
        if (amount <= 0.0) {
            return;
        }
        int r = (int) Math.ceil(c.spec.crown()) + 3;
        int top = (int) Math.ceil(c.spec.height()) + 2;
        for (int y = top; y >= 1; y--) {
            for (int z = -r; z <= r; z++) {
                for (int x = -r; x <= r; x++) {
                    int code = c.b.get(x, y, z);
                    int kind = TreeBlocks.kind(code);
                    if (kind == TreeBlocks.LOG && y <= trunkTop && TreeBlocks.extra(code) == TreeBlocks.AXIS_Y) {
                        for (int f = 0; f < 4; f++) {
                            if (c.hash(x * 4 + f, y, z) < 0.55 * amount) {
                                vine(c, x, y, z, f, 1);
                            }
                        }
                    } else if (kind == TreeBlocks.LEAVES && y >= crownBottom - 1 && c.b.isEmpty(x, y - 1, z)) {
                        for (int f = 0; f < 4; f++) {
                            int nx = x - TreeShape.Builder.FACE_X[f];
                            int nz = z - TreeShape.Builder.FACE_Z[f];
                            if (c.b.isEmpty(nx, y, nz) && c.hash(x * 4 + f, y + 401, z) < 0.16 * amount) {
                                vine(c, x, y, z, f, 1 + (int) (c.hash(x, y + 73, z * 4 + f) * (2 + 5 * amount)));
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * A bee nest on the trunk just below the crown, as vanilla places them: on the east, south or
     * west side, facing south, with room in front.
     */
    private static void beeNest(Ctx c, int trunkX, int trunkZ, int crownBottom) {
        if (!c.spec.has(TreeSpec.BEES)) {
            return;
        }
        int f = 1 + c.r.nextInt(3);
        int y = Math.max(2, crownBottom - 1);
        for (int k = 0; k < 3; k++) {
            int side = 1 + (f - 1 + k) % 3;
            int nx = trunkX + TreeShape.Builder.FACE_X[side];
            int nz = trunkZ + TreeShape.Builder.FACE_Z[side];
            if (c.b.isEmpty(nx, y, nz) && c.b.isEmpty(nx, y, nz + 1) && TreeBlocks.isLog(c.b.get(trunkX, y, trunkZ))) {
                c.b.soft(nx, y, nz, TreeBlocks.code(TreeBlocks.BEE_NEST, c.wood, 2));
                return;
            }
        }
    }

    /** Ground around a flared or giant trunk. */
    private static void groundAround(Ctx c, double cx, double cz, double radius, int code, double share) {
        int ri = (int) Math.ceil(radius);
        for (int z = (int) Math.floor(cz) - ri; z <= (int) Math.ceil(cz) + ri; z++) {
            for (int x = (int) Math.floor(cx) - ri; x <= (int) Math.ceil(cx) + ri; x++) {
                double d = Math.hypot(x - cx, z - cz);
                double limit = radius * (0.7 + 0.5 * c.lump(x * 1.6, 0, z * 1.6));
                if (d <= limit && c.hash(x, -50, z) < share) {
                    c.b.ground(x, z, code);
                }
            }
        }
    }

    /** Trunk position at height h: lean grows with height (bent towards the top), plus a gentle curve. */
    private static double[] trunkAt(Ctx c, double h, double top, double curveX, double curveZ) {
        double t = top <= 0 ? 0 : Math.max(0.0, Math.min(1.0, h / top));
        double lean = Math.pow(t, 1.7);
        double bend = Math.sin(Math.PI * t);
        return new double[] {c.spec.leanX() * lean + curveX * bend, c.spec.leanZ() * lean + curveZ * bend};
    }

    /** A 1 x 1 trunk from the root up to {@code top}, leaning and curving; returns the top's position. */
    private static double[] trunk(Ctx c, int top, double curveX, double curveZ) {
        double[] p = new double[] {0, 0};
        for (int y = 0; y <= top; y++) {
            p = trunkAt(c, y, top, curveX, curveZ);
            c.b.log(clampReach(p[0]), y, clampReach(p[1]), c.log);
        }
        return p;
    }

    /** A 2 x 2 trunk (root at its north-west column) up to {@code top}, with an optional lean. */
    private static double[] wideTrunk(Ctx c, int top, double curveX, double curveZ) {
        double[] p = new double[] {0, 0};
        // Only ancient trunks bend: a shifted 2 x 2 column elsewhere reads as a broken trunk.
        boolean bends = c.spec.form() == TreeForm.ANCIENT;
        for (int y = 0; y <= top; y++) {
            p = bends ? trunkAt(c, y, top, curveX, curveZ) : new double[] {0, 0};
            int px = clampReach(p[0]);
            int pz = clampReach(p[1]);
            c.b.log(px, y, pz, c.log);
            c.b.log(px + 1, y, pz, c.log);
            c.b.log(px, y, pz + 1, c.log);
            c.b.log(px + 1, y, pz + 1, c.log);
        }
        return p;
    }

    /** Flared roots of a 2 x 2 trunk: bark blocks spreading at the base and along the ground. */
    private static void flare(Ctx c, int roots, double length) {
        int wood = c.barkWood();
        double phase = c.r.next() * Math.PI * 2;
        for (int k = 0; k < roots; k++) {
            double a = phase + k * (Math.PI * 2 / roots) + c.r.range(-0.35, 0.35);
            double len = length * c.r.range(0.6, 1.15);
            double sx = 0.5 + Math.cos(a) * 1.2;
            double sz = 0.5 + Math.sin(a) * 1.2;
            int steps = (int) Math.ceil(len * 2);
            for (int s = 0; s <= steps; s++) {
                double t = s / (double) steps;
                double x = sx + Math.cos(a) * len * t;
                double z = sz + Math.sin(a) * len * t;
                int y = t < 0.3 ? 1 : 0;
                c.b.log((int) Math.round(x), 0, (int) Math.round(z), wood);
                if (y == 1 && t < 0.15) {
                    c.b.log((int) Math.round(x), 1, (int) Math.round(z), wood);
                }
            }
        }
    }

    // ---------------------------------------------------------------- forms

    private static void bush(Ctx c) {
        double r = c.spec.crown();
        int h = (int) Math.round(c.spec.height());
        c.b.log(0, 0, 0, c.log);
        if (h >= 3) {
            c.b.log(0, 1, 0, c.log);
        }
        double cy = Math.max(0.6, h * 0.45);
        double ox = c.r.range(-0.5, 0.5);
        double oz = c.r.range(-0.5, 0.5);
        blob(c, ox, cy, oz, r * c.r.range(0.9, 1.15), Math.max(1.0, h * 0.55), r * c.r.range(0.9, 1.15), 0.55);
        if (r > 1.6) {
            double a = c.r.next() * Math.PI * 2;
            blob(c, ox + Math.cos(a) * r * 0.6, cy - 0.3, oz + Math.sin(a) * r * 0.6, r * 0.65, Math.max(0.9, h * 0.4), r * 0.65, 0.5);
        }
    }

    private static void broadleaf(Ctx c) {
        double height = c.spec.height();
        double crown = c.spec.crown();
        int trunkTop = (int) Math.round(height - c.r.range(1.6, 2.6));
        double base = height * c.r.range(0.34, 0.48);
        double curveX = c.r.range(-0.7, 0.7) * (height / 10.0);
        double curveZ = c.r.range(-0.7, 0.7) * (height / 10.0);
        trunk(c, trunkTop, curveX, curveZ);
        // Crown asymmetry: the whole crown leans a little to one side and is wider one way.
        double skewA = c.r.next() * Math.PI * 2;
        double skew = crown * c.r.range(0.0, 0.18);
        double sx = Math.cos(skewA) * skew;
        double sz = Math.sin(skewA) * skew;
        int branches = Math.max(2, (int) Math.round(1.2 + crown * 0.75 + c.r.range(-0.6, 0.9)));
        double phase = c.r.next() * Math.PI * 2;
        double clusterR = 1.45 + 0.26 * crown;
        for (int k = 0; k < branches; k++) {
            double rel = (k + c.r.range(0.15, 0.85)) / branches;
            double hs = base + (trunkTop - 1 - base) * rel;
            double[] p = trunkAt(c, hs, trunkTop, curveX, curveZ);
            double a = phase + k * GOLDEN + c.r.range(-0.35, 0.35);
            double up = 0.25 + 0.75 * rel + c.r.range(-0.12, 0.12);
            double len = crown * (0.95 - 0.4 * rel) * c.r.range(0.82, 1.08);
            double ex = p[0] + Math.cos(a) * len + sx * rel;
            double ez = p[1] + Math.sin(a) * len + sz * rel;
            double ey = hs + up * len;
            ex = Math.max(-MAX + clusterR, Math.min(MAX - clusterR, ex));
            ez = Math.max(-MAX + clusterR, Math.min(MAX - clusterR, ez));
            logLine(c, p[0], hs, p[1], ex, ey, ez);
            double cr = clusterR * c.r.range(0.85, 1.15);
            blob(c, ex, ey + 0.7, ez, cr, cr * 0.8, cr, 0.55);
            if (crown >= 4.0 && c.r.chance(0.6)) {
                // A side shoot from the middle of the branch with its own cluster.
                double t = c.r.range(0.45, 0.7);
                double mx = p[0] + (ex - p[0]) * t;
                double my = hs + (ey - hs) * t;
                double mz = p[1] + (ez - p[1]) * t;
                double b2 = a + (c.r.chance(0.5) ? 0.75 : -0.75);
                double l2 = len * c.r.range(0.35, 0.5);
                double fx = Math.max(-MAX + 2, Math.min(MAX - 2, mx + Math.cos(b2) * l2));
                double fz = Math.max(-MAX + 2, Math.min(MAX - 2, mz + Math.sin(b2) * l2));
                logLine(c, mx, my, mz, fx, my + l2 * 0.45, fz);
                blob(c, fx, my + l2 * 0.45 + 0.6, fz, cr * 0.8, cr * 0.6, cr * 0.8, 0.6);
            }
        }
        double[] top = trunkAt(c, trunkTop, trunkTop, curveX, curveZ);
        double tr = clusterR * c.r.range(1.05, 1.3);
        blob(c, top[0] + sx * 0.5, trunkTop + 1.0, top[1] + sz * 0.5, tr, tr * 0.8, tr, 0.55);
        if (crown >= 3.5) {
            // Fill between the clusters, so a big crown reads as one canopy with lobes.
            double mid = base + (height - base) * 0.62;
            double[] q = trunkAt(c, mid, trunkTop, curveX, curveZ);
            blob(c, q[0] + sx * 0.6, mid, q[1] + sz * 0.6, crown * 0.72, (height - base) * 0.32, crown * 0.72, 0.7);
        }
        vines(c, trunkTop, (int) base);
        beeNest(c, 0, 0, (int) Math.floor(base));
    }

    private static void ancient(Ctx c) {
        double height = c.spec.height();
        double crown = Math.min(c.spec.crown(), MAX - 3.5);
        int split = (int) Math.round(height * c.r.range(0.42, 0.55));
        double curveX = c.r.range(-1.0, 1.0);
        double curveZ = c.r.range(-1.0, 1.0);
        wideTrunk(c, split + 1, curveX, curveZ);
        flare(c, c.r.between(4, 7), c.r.range(2.2, 3.6));
        double[] fork = trunkAt(c, split, split + 1, curveX, curveZ);
        double cx = fork[0] + 0.5;
        double cz = fork[1] + 0.5;
        int limbs = c.r.between(4, 6);
        double phase = c.r.next() * Math.PI * 2;
        for (int k = 0; k < limbs; k++) {
            double a = phase + k * (Math.PI * 2 / limbs) + c.r.range(-0.3, 0.3);
            double reach = crown * c.r.range(0.55, 0.8);
            // Limb: out and up to an elbow, then out and only a little up to the tip.
            double ex = cx + Math.cos(a) * reach * 0.45;
            double ez = cz + Math.sin(a) * reach * 0.45;
            double ey = split + (height - split) * c.r.range(0.28, 0.45);
            double tx = cx + Math.cos(a + c.r.range(-0.25, 0.25)) * reach;
            double tz = cz + Math.sin(a + c.r.range(-0.25, 0.25)) * reach;
            double ty = ey + (height - ey) * c.r.range(0.18, 0.4);
            thickLine(c, cx, split, cz, ex, ey, ez, 1.1, 0.8);
            thickLine(c, ex, ey, ez, tx, ty, tz, 0.8, 0.0);
            double cr = c.r.range(3.0, 3.9);
            blob(c, tx, ty + 1.0, tz, cr, cr * 0.72, cr, 0.6);
            // Secondary branches off the limb, each with a cluster.
            int twigs = c.r.between(2, 4);
            for (int s = 0; s < twigs; s++) {
                double t = c.r.range(0.25, 0.85);
                double mx = ex + (tx - ex) * t;
                double my = ey + (ty - ey) * t;
                double mz = ez + (tz - ez) * t;
                double b2 = a + (s % 2 == 0 ? 1 : -1) * c.r.range(0.6, 1.2);
                double len = crown * c.r.range(0.25, 0.42);
                double fx = Math.max(-MAX + 3, Math.min(MAX - 3, mx + Math.cos(b2) * len));
                double fz = Math.max(-MAX + 3, Math.min(MAX - 3, mz + Math.sin(b2) * len));
                double fy = my + len * c.r.range(0.3, 0.7);
                logLine(c, mx, my, mz, fx, fy, fz);
                double r2 = c.r.range(2.5, 3.4);
                blob(c, fx, fy + 0.8, fz, r2, r2 * 0.72, r2, 0.6);
            }
        }
        // One or two heavy low limbs spreading almost level: the silhouette of an old open-grown oak.
        int low = c.r.between(0, 2);
        for (int k = 0; k < low; k++) {
            double a = phase + GOLDEN * (k + 0.5) + c.r.range(-0.3, 0.3);
            double hs = split * c.r.range(0.55, 0.8);
            double len = crown * c.r.range(0.6, 0.85);
            double tx = cx + Math.cos(a) * len;
            double tz = cz + Math.sin(a) * len;
            double ty = hs + len * c.r.range(0.15, 0.35);
            thickLine(c, cx, hs, cz, tx, ty, tz, 0.85, 0.0);
            double cr = c.r.range(2.6, 3.4);
            blob(c, tx, ty + 0.9, tz, cr, cr * 0.6, cr, 0.65);
        }
        // The dome over the fork.
        double dr = c.r.range(4.2, 5.4);
        logLine(c, cx, split, cz, cx + c.r.range(-1, 1), height - 3, cz + c.r.range(-1, 1));
        blob(c, cx, height - 2.4, cz, dr, dr * 0.65, dr, 0.6);
        if (c.spec.has(TreeSpec.ROOTS)) {
            groundAround(c, 0.5, 0.5, 4.2, TreeBlocks.GROUND_ROOTED_DIRT, 0.35);
            groundAround(c, 0.5, 0.5, 5.5, TreeBlocks.GROUND_COARSE_DIRT, 0.25);
        }
        if (c.spec.has(TreeSpec.MOSS)) {
            groundAround(c, 0.5, 0.5, 6.0, TreeBlocks.GROUND_MOSS, 0.3);
        }
        vines(c, split, split);
        beeNest(c, 0, 0, split);
    }

    private static void birch(Ctx c) {
        double height = c.spec.height();
        double crown = c.spec.crown();
        int trunkTop = (int) Math.round(height - 1.0);
        double curveX = c.r.range(-0.5, 0.5) * height / 10.0;
        double curveZ = c.r.range(-0.5, 0.5) * height / 10.0;
        trunk(c, trunkTop, curveX, curveZ);
        double base = height * c.r.range(0.38, 0.5);
        double span = height + 1.0 - base;
        double ox = 0.0;
        double oz = 0.0;
        for (int y = (int) Math.round(base); y <= (int) Math.round(height) + 1; y++) {
            double rel = (y - base) / span;
            double profile = Math.pow(Math.sin(Math.PI * Math.min(1.0, 0.16 + rel * 0.9)), 0.55);
            double rr = Math.max(1.45, (crown + 0.45) * profile + c.r.range(-0.2, 0.3));
            double[] p = trunkAt(c, Math.min(y, trunkTop), trunkTop, curveX, curveZ);
            ox = Math.max(-0.7, Math.min(0.7, ox + c.r.range(-0.35, 0.35)));
            oz = Math.max(-0.7, Math.min(0.7, oz + c.r.range(-0.35, 0.35)));
            disc(c, p[0] + ox, y, p[1] + oz, rr, 0.4);
        }
        // Twigs: short side branches with small clusters.
        int twigs = c.r.between(1, 3);
        for (int k = 0; k < twigs; k++) {
            double hs = base + span * c.r.range(0.1, 0.6);
            double[] p = trunkAt(c, hs, trunkTop, curveX, curveZ);
            double a = c.r.next() * Math.PI * 2;
            double len = c.r.range(1.4, 2.4);
            double ex = p[0] + Math.cos(a) * len;
            double ez = p[1] + Math.sin(a) * len;
            logLine(c, p[0], hs, p[1], ex, hs + len * 0.6, ez);
            blob(c, ex, hs + len * 0.6 + 0.5, ez, 1.4, 1.0, 1.4, 0.5);
        }
        double[] top = trunkAt(c, trunkTop, trunkTop, curveX, curveZ);
        c.b.soft(clampReach(top[0]), trunkTop + 1, clampReach(top[1]), c.leaves);
        c.b.soft(clampReach(top[0]), trunkTop + 2, clampReach(top[1]), c.leaves);
        beeNest(c, 0, 0, (int) Math.floor(base));
    }

    /**
     * Conifers: spruce (a cone of whorls down low) or pine (a long bare trunk under a rounded crown),
     * on a 1 x 1 or 2 x 2 trunk. Every whorl has radial branch logs, so the leaves at its edge stay
     * within reach of a log.
     */
    private static void conifer(Ctx c, boolean pine, boolean wide) {
        double height = c.spec.height();
        double crown = c.spec.crown();
        int trunkTop = (int) Math.round(height - (pine ? 1.0 : 1.5));
        double curve = pine ? 0.6 : 0.25;
        double curveX = c.r.range(-curve, curve) * height / 12.0;
        double curveZ = c.r.range(-curve, curve) * height / 12.0;
        if (wide) {
            wideTrunk(c, trunkTop, curveX, curveZ);
        } else {
            trunk(c, trunkTop, curveX, curveZ);
        }
        double off = wide ? 0.5 : 0.0;
        double bottom = pine ? height * (wide ? c.r.range(0.5, 0.6) : c.r.range(0.58, 0.72))
                : (wide ? height * c.r.range(0.28, 0.45) : (height < 6.5 ? c.r.range(0.5, 1.2) : c.r.range(1.0, 3.2) + height * 0.06));
        int period = c.r.between(2, 3);
        double phase = c.r.next() * Math.PI * 2;
        int layer = 0;
        for (int y = (int) Math.ceil(bottom); y <= height; y++) {
            double rel = (y - bottom) / Math.max(1.0, height - bottom);
            double r;
            if (pine) {
                // Rounded crown: widest a third of the way up, tapering to a blunt top.
                r = crown * Math.pow(Math.sin(Math.PI * Math.min(1.0, 0.18 + rel * 0.82)), 0.6) * (1.0 - 0.2 * rel) + 0.4;
            } else {
                r = crown * Math.pow(1.0 - rel, 0.9) + 0.6;
            }
            boolean whorl = (y - (int) Math.ceil(bottom)) % period == 0;
            r += whorl ? 0.55 : -0.45;
            if (rel > 0.92) {
                r = Math.min(r, 1.0);
            }
            double[] p = trunkAt(c, Math.min(y, trunkTop), trunkTop, curveX, curveZ);
            double px = p[0] + off;
            double pz = p[1] + off;
            if (r >= 0.8) {
                disc(c, px, y, pz, Math.min(r, MAX - 1.5), pine ? 0.4 : 0.28);
            } else {
                c.b.soft(clampReach(p[0]), y, clampReach(p[1]), c.leaves);
            }
            if (whorl && r > 2.8 && y <= trunkTop) {
                int arms = wide ? 8 : (r > 3.4 ? 6 : 4);
                double start = phase + layer * 0.7;
                // Branches end well inside the whorl: no end grain showing through the needles.
                double len = Math.min(r - 2.2, MAX - 2.0);
                for (int k = 0; k < arms; k++) {
                    double a = start + k * (Math.PI * 2 / arms);
                    double droop = pine ? 0.08 : 0.0;
                    logLine(c, px, y, pz, px + Math.cos(a) * len, y + droop * len, pz + Math.sin(a) * len);
                }
                layer++;
            }
        }
        double[] top = trunkAt(c, trunkTop, trunkTop, curveX, curveZ);
        int tx = clampReach(top[0]);
        int tz = clampReach(top[1]);
        for (int y = trunkTop + 1; y <= height + 1; y++) {
            c.b.soft(tx, y, tz, c.leaves);
            if (wide) {
                c.b.soft(tx + 1, y, tz, c.leaves);
                c.b.soft(tx, y, tz + 1, c.leaves);
                c.b.soft(tx + 1, y, tz + 1, c.leaves);
            }
        }
        if (!wide) {
            c.b.soft(tx, (int) Math.round(height) + 1, tz, c.leaves);
        }
        if (c.spec.has(TreeSpec.PODZOL)) {
            groundAround(c, off, off, crown * 0.75 + (wide ? 2.5 : 1.0), TreeBlocks.GROUND_PODZOL, 0.85);
        }
        if (c.spec.has(TreeSpec.MOSS) && wide) {
            groundAround(c, off, off, crown * 0.6, TreeBlocks.GROUND_MOSS, 0.18);
        }
    }

    private static void krummholz(Ctx c) {
        double wx = c.spec.windX();
        double wz = c.spec.windZ();
        int h = Math.max(1, (int) Math.round(c.spec.height() - 1.0));
        double crown = c.spec.crown();
        // The trunk bends downwind; the crown is a low mat stretched along the wind.
        for (int y = 0; y < h; y++) {
            double t = y / (double) Math.max(1, h);
            c.b.log((int) Math.round(wx * t * 1.2), y, (int) Math.round(wz * t * 1.2), c.log);
        }
        double cx = wx * crown * 0.55;
        double cz = wz * crown * 0.55;
        double along = crown * c.r.range(1.2, 1.45);
        double across = crown * c.r.range(0.75, 0.95);
        int ri = (int) Math.ceil(along + 1);
        for (int y = 0; y <= h + 1; y++) {
            double ry = 1.0 + 0.5 * h;
            for (int z = -ri; z <= ri; z++) {
                for (int x = -ri; x <= ri; x++) {
                    double ex = x - cx;
                    double ez = z - cz;
                    double a = (ex * wx + ez * wz) / along;
                    double b = (-ex * wz + ez * wx) / across;
                    double e = (y - h * 0.6) / ry;
                    double d = a * a + b * b + e * e;
                    if (d <= 1.0 + 0.5 * (c.lump(x, y, z) - 0.5) && !(d > 0.6 && c.hash(x, y, z) < 0.2)) {
                        c.b.soft(x, y, z, c.leaves);
                    }
                }
            }
        }
    }

    private static void darkOak(Ctx c) {
        double height = c.spec.height();
        double crown = c.spec.crown();
        int split = (int) Math.round(height * c.r.range(0.48, 0.62));
        double curveX = c.r.range(-0.6, 0.6);
        double curveZ = c.r.range(-0.6, 0.6);
        double[] p = wideTrunk(c, split, curveX, curveZ);
        double cx = p[0] + 0.5;
        double cz = p[1] + 0.5;
        if (c.r.chance(0.45)) {
            flare(c, c.r.between(2, 4), c.r.range(1.0, 1.8));
        }
        int limbs = c.r.between(3, 4);
        double phase = c.r.next() * Math.PI * 2;
        double canopy = height - 1.0;
        double sumX = 0;
        double sumZ = 0;
        for (int k = 0; k < limbs; k++) {
            double a = phase + k * (Math.PI * 2 / limbs) + c.r.range(-0.4, 0.4);
            double len = crown * c.r.range(0.45, 0.65);
            double tx = cx + Math.cos(a) * len;
            double tz = cz + Math.sin(a) * len;
            double ty = canopy - c.r.range(0.5, 1.5);
            thickLine(c, cx, split, cz, tx, ty, tz, crown > 5 ? 0.8 : 0.0, 0.0);
            double cr = c.r.range(2.6, 3.3);
            blob(c, tx, ty + 0.6, tz, cr, cr * 0.55, cr, 0.55);
            sumX += tx;
            sumZ += tz;
            // Short arms under the canopy keep its edge within reach of a log.
            double b = a + c.r.range(-0.8, 0.8);
            logLine(c, tx, ty, tz, tx + Math.cos(b) * 1.6, ty, tz + Math.sin(b) * 1.6);
        }
        double mx = sumX / limbs;
        double mz = sumZ / limbs;
        logLine(c, cx, split, cz, mx, canopy, mz);
        disc(c, mx, (int) Math.round(canopy), mz, crown, 0.5);
        disc(c, mx, (int) Math.round(canopy) + 1, mz, crown - 1.0, 0.55);
        disc(c, mx + c.r.range(-1, 1), (int) Math.round(canopy) + 2, mz + c.r.range(-1, 1), Math.max(1.2, crown - 2.6), 0.6);
        vines(c, split, (int) canopy - 1);
    }

    private static void tallJungle(Ctx c) {
        double height = c.spec.height();
        double crown = c.spec.crown();
        int trunkTop = (int) Math.round(height - 1.5);
        double curveX = c.r.range(-0.8, 0.8);
        double curveZ = c.r.range(-0.8, 0.8);
        trunk(c, trunkTop, curveX, curveZ);
        double[] top = trunkAt(c, trunkTop, trunkTop, curveX, curveZ);
        int side = c.r.between(1, 3);
        for (int k = 0; k < side; k++) {
            double hs = trunkTop - c.r.range(1.0, 4.0);
            double[] p = trunkAt(c, hs, trunkTop, curveX, curveZ);
            double a = c.r.next() * Math.PI * 2;
            double len = crown * c.r.range(0.6, 0.9);
            double ex = p[0] + Math.cos(a) * len;
            double ez = p[1] + Math.sin(a) * len;
            logLine(c, p[0], hs, p[1], ex, hs + len * 0.5, ez);
            blob(c, ex, hs + len * 0.5 + 0.6, ez, 2.0, 1.2, 2.0, 0.5);
        }
        blob(c, top[0], trunkTop + 0.6, top[1], crown + 0.6, 1.9, crown + 0.6, 0.5);
        blob(c, top[0] + c.r.range(-0.6, 0.6), trunkTop + 2.0, top[1] + c.r.range(-0.6, 0.6), crown * 0.6, 1.1, crown * 0.6, 0.5);
        vines(c, trunkTop, trunkTop - 2);
        if (c.spec.has(TreeSpec.COCOA)) {
            int pods = c.r.between(1, 3);
            for (int k = 0; k < pods; k++) {
                int y = c.r.between(2, Math.max(2, trunkTop - 4));
                int f = c.r.nextInt(4);
                double[] p = trunkAt(c, y, trunkTop, curveX, curveZ);
                int lx = clampReach(p[0]);
                int lz = clampReach(p[1]);
                int px = lx - TreeShape.Builder.FACE_X[f];
                int pz = lz - TreeShape.Builder.FACE_Z[f];
                if (TreeBlocks.isLog(c.b.get(lx, y, lz))) {
                    c.b.soft(px, y, pz, TreeBlocks.code(TreeBlocks.COCOA, c.wood, f | c.r.nextInt(3) << 2));
                }
            }
        }
    }

    private static void megaJungle(Ctx c) {
        double height = c.spec.height();
        double crown = c.spec.crown();
        int trunkTop = (int) Math.round(height - 2.0);
        double curveX = c.r.range(-0.8, 0.8);
        double curveZ = c.r.range(-0.8, 0.8);
        wideTrunk(c, trunkTop, curveX, curveZ);
        if (c.r.chance(0.6)) {
            flare(c, c.r.between(3, 5), c.r.range(1.5, 2.6));
        }
        double phase = c.r.next() * Math.PI * 2;
        int k = 0;
        for (double y = height * c.r.range(0.4, 0.5); y < trunkTop - 2; y += c.r.range(2.5, 4.5)) {
            double[] p = trunkAt(c, y, trunkTop, curveX, curveZ);
            double a = phase + k++ * GOLDEN;
            double len = c.r.range(2.5, Math.min(5.5, crown));
            double ex = p[0] + 0.5 + Math.cos(a) * len;
            double ez = p[1] + 0.5 + Math.sin(a) * len;
            logLine(c, p[0] + 0.5, y, p[1] + 0.5, ex, y + len * 0.45, ez);
            double cr = c.r.range(2.0, 2.8);
            blob(c, ex, y + len * 0.45 + 0.7, ez, cr, cr * 0.55, cr, 0.55);
        }
        double[] top = trunkAt(c, trunkTop, trunkTop, curveX, curveZ);
        double tx = top[0] + 0.5;
        double tz = top[1] + 0.5;
        for (int a = 0; a < 6; a++) {
            double ang = phase + a * (Math.PI / 3);
            double len = crown * 0.55;
            logLine(c, tx, trunkTop, tz, tx + Math.cos(ang) * len, trunkTop + 0.8, tz + Math.sin(ang) * len);
        }
        blob(c, tx, trunkTop + 1.0, tz, crown, 2.2, crown, 0.55);
        blob(c, tx + c.r.range(-1, 1), trunkTop + 2.6, tz + c.r.range(-1, 1), crown * 0.6, 1.4, crown * 0.6, 0.55);
        vines(c, trunkTop, trunkTop - 3);
    }

    private static void acacia(Ctx c) {
        double height = c.spec.height();
        double crown = c.spec.crown();
        int straight = Math.max(1, (int) Math.round(height * c.r.range(0.3, 0.5)));
        for (int y = 0; y <= straight; y++) {
            c.b.log(0, y, 0, c.log);
        }
        int forks = c.r.chance(0.65) ? 2 : 1;
        double a0 = c.r.next() * Math.PI * 2;
        for (int f = 0; f < forks; f++) {
            double a = f == 0 ? a0 : a0 + Math.PI + c.r.range(-0.7, 0.7);
            int from = f == 0 ? straight : Math.max(1, straight - c.r.between(0, 1));
            int top = (int) Math.round(height - (f == 0 ? 1 : c.r.range(1.5, 3.0)));
            double x = 0;
            double z = 0;
            double lean = c.r.range(0.55, 0.95);
            int px = 0;
            int pz = 0;
            for (int y = from + 1; y <= top; y++) {
                x += Math.cos(a) * lean;
                z += Math.sin(a) * lean;
                int nx = clampReach(x);
                int nz = clampReach(z);
                c.b.log(nx, y, nz, c.log);
                px = nx;
                pz = nz;
            }
            double cr = crown * (f == 0 ? 1.0 : c.r.range(0.65, 0.85));
            // Flat umbrella: a wide layer with a smaller one on top, arms under the edge.
            for (int k = 0; k < 4; k++) {
                double b = a + k * (Math.PI / 2) + 0.4;
                logLine(c, px, top, pz, px + Math.cos(b) * (cr - 1.6), top, pz + Math.sin(b) * (cr - 1.6));
            }
            disc(c, px, top + 1, pz, Math.min(cr, MAX - 1.0), 0.45);
            disc(c, px + c.r.range(-0.6, 0.6), top + 2, pz + c.r.range(-0.6, 0.6), Math.max(1.2, cr - 1.6), 0.5);
        }
    }

    private static void swamp(Ctx c) {
        double height = c.spec.height();
        double crown = c.spec.crown();
        int trunkTop = (int) Math.round(height - 1.5);
        double curveX = c.r.range(-0.6, 0.6);
        double curveZ = c.r.range(-0.6, 0.6);
        trunk(c, trunkTop, curveX, curveZ);
        double[] top = trunkAt(c, trunkTop, trunkTop, curveX, curveZ);
        double phase = c.r.next() * Math.PI * 2;
        for (int k = 0; k < 4; k++) {
            double a = phase + k * (Math.PI / 2) + c.r.range(-0.3, 0.3);
            double len = crown * c.r.range(0.45, 0.65);
            logLine(c, top[0], trunkTop - 1, top[1], top[0] + Math.cos(a) * len, trunkTop - 0.4, top[1] + Math.sin(a) * len);
        }
        blob(c, top[0], trunkTop - 0.2, top[1], crown, 1.5, crown, 0.5);
        blob(c, top[0] + c.r.range(-0.8, 0.8), trunkTop + 1.0, top[1] + c.r.range(-0.8, 0.8), crown * 0.6, 1.0, crown * 0.6, 0.5);
        vines(c, trunkTop, trunkTop - 2);
    }

    private static void snag(Ctx c) {
        double height = c.spec.height();
        int top = (int) Math.round(height);
        double curveX = c.r.range(-0.5, 0.5);
        double curveZ = c.r.range(-0.5, 0.5);
        trunk(c, top, curveX, curveZ);
        int stubs = c.r.between(0, 2);
        for (int k = 0; k < stubs; k++) {
            double hs = top * c.r.range(0.45, 0.85);
            double[] p = trunkAt(c, hs, top, curveX, curveZ);
            double a = c.r.next() * Math.PI * 2;
            double len = c.r.range(1.0, 2.2);
            logLine(c, p[0], hs, p[1], p[0] + Math.cos(a) * len, hs + len * 0.5, p[1] + Math.sin(a) * len);
        }
    }

    private static void fallen(Ctx c) {
        boolean alongX = c.r.chance(0.5);
        int sign = c.r.chance(0.5) ? 1 : -1;
        int length = (int) Math.round(c.spec.height());
        int start = 1;
        if (c.r.chance(0.6)) {
            // Stump with a gap, like vanilla's fallen trees.
            int stump = c.r.between(1, 2);
            for (int y = 0; y < stump; y++) {
                c.b.log(0, y, 0, c.log);
            }
            start = 2;
        }
        int axis = alongX ? TreeBlocks.AXIS_X : TreeBlocks.AXIS_Z;
        int code = c.log(axis);
        int moss = TreeBlocks.code(TreeBlocks.MOSS_CARPET, c.wood, 0);
        for (int k = start; k < start + length; k++) {
            int x = alongX ? k * sign : 0;
            int z = alongX ? 0 : k * sign;
            c.b.log(x, 0, z, code);
            if (c.spec.has(TreeSpec.MOSS) && c.r.chance(0.45)) {
                c.b.soft(x, 1, z, moss);
            }
        }
    }

    /** Huge mushrooms exactly as vanilla builds them (stem, flat brown cap or red dome). */
    private static void hugeMushroom(Ctx c, boolean brown) {
        int height = Math.max(4, (int) Math.round(c.spec.height()));
        int stem = TreeBlocks.code(TreeBlocks.STEM, Wood.MUSHROOM, 0);
        for (int y = 0; y < height; y++) {
            c.b.log(0, y, 0, stem);
        }
        int kind = brown ? TreeBlocks.BROWN_CAP : TreeBlocks.RED_CAP;
        if (brown) {
            int r = 3;
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    boolean west = x == -r;
                    boolean east = x == r;
                    boolean north = z == -r;
                    boolean south = z == r;
                    boolean edgeX = west || east;
                    boolean edgeZ = north || south;
                    if (edgeX && edgeZ) {
                        continue;
                    }
                    boolean w = west || edgeZ && x == 1 - r;
                    boolean e = east || edgeZ && x == r - 1;
                    boolean n = north || edgeX && z == 1 - r;
                    boolean s = south || edgeX && z == r - 1;
                    int faces = (n ? 1 : 0) | (e ? 2 : 0) | (s ? 4 : 0) | (w ? 8 : 0) | 16;
                    c.b.force(x, height, z, TreeBlocks.code(kind, Wood.MUSHROOM, faces));
                }
            }
        } else {
            int radius = 2;
            for (int y = height - 3; y <= height; y++) {
                int j = y < height ? radius : radius - 1;
                int k = radius - 2;
                for (int x = -j; x <= j; x++) {
                    for (int z = -j; z <= j; z++) {
                        boolean edgeX = x == -j || x == j;
                        boolean edgeZ = z == -j || z == j;
                        if (y >= height || edgeX != edgeZ) {
                            boolean up = y >= height - 1;
                            int faces = (z < -k ? 1 : 0) | (x > k ? 2 : 0) | (z > k ? 4 : 0) | (x < -k ? 8 : 0) | (up ? 16 : 0);
                            c.b.force(x, y, z, TreeBlocks.code(kind, Wood.MUSHROOM, faces));
                        }
                    }
                }
            }
        }
    }
}
