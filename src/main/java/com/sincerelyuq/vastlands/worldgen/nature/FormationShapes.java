package com.sincerelyuq.vastlands.worldgen.nature;

import static com.sincerelyuq.vastlands.worldgen.nature.FormationBlocks.EMBED;
import static com.sincerelyuq.vastlands.worldgen.nature.FormationBlocks.SNAP_CEILING;
import static com.sincerelyuq.vastlands.worldgen.nature.FormationBlocks.SNAP_FLOOR;
import static com.sincerelyuq.vastlands.worldgen.nature.FormationBlocks.WET;

import com.sincerelyuq.vastlands.worldgen.compat.ModBlock;
import net.minecraft.core.Direction;

import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;

/**
 * The shapes of natural formations: one generator per {@link FormationKind}, drawing rock, wood and
 * plants into a {@link FormationShape.Builder} from a {@link FormationSpec}. A shape is a pure
 * function of its spec and of the model's world ({@link World}: the ground of the surface, the
 * open space of the caves), read only through pure queries, so every chunk a formation reaches
 * builds the same shape.
 *
 * <p>Rock is drawn as volumes with noisy surfaces (superellipsoids, discs stacked into cones and
 * pillars, tubes swept along arcs), sunk into the ground or the cave floor ({@code EMBED} cells
 * replace the soil or rock under them) and footed: the lowest block of every column is extended
 * down to the ground when written, the highest of a hanging formation up to the ceiling, so
 * nothing floats over uneven ground. Plants are snapped stacks that find the floor or ceiling of
 * their own column.
 */
final class FormationShapes {
    private FormationShapes() {
    }

    /** What the shapes read of the world: pure model queries. */
    interface World {
        /** The top solid block of the ground (the bed under water). */
        int groundTop(int x, int z);

        /** The top water block over a column, or {@link Integer#MIN_VALUE} where it is dry. */
        int waterTop(int x, int z);

        /** True if the block is open cave space. */
        boolean isCave(int x, int y, int z);
    }

    private static final ThreadLocal<FormationShape.Builder> BUILDERS = ThreadLocal.withInitial(FormationShape.Builder::new);

    static FormationShape shape(FormationSpec spec, World world) {
        FormationShape.Builder b = BUILDERS.get();
        b.clear(spec.kind().reach);
        if (!spec.kind().surface && spec.has(FormationSpec.FLOODED)) {
            // A stalagmite or column rising out of a cave pool: its body below the water stays.
            b.wetSolids();
        }
        Ctx c = new Ctx(b, spec, world);
        switch (spec.kind()) {
            case BOULDER -> boulder(c, true);
            case ROCK_CLUSTER -> cluster(c);
            case TALUS -> talus(c);
            case OUTCROP -> outcrop(c);
            case SPIRE -> spire(c, spec.height(), spec.radius(), false);
            case ARCH, GREAT_ARCH -> arch(c);
            case DRIFTWOOD -> driftwood(c);
            case SHORE_STONES -> shoreStones(c);
            case ISLET -> islet(c);
            case BALANCED_ROCK -> balancedRock(c);
            case GREAT_SPIRE -> spire(c, spec.height(), spec.radius(), true);
            case STONE_FOREST -> stoneForest(c);
            case RUBBLE -> rubble(c);
            case CAVE_BOULDER -> boulder(c, false);
            case STALAGMITE -> dripstone(c, true, false);
            case STALACTITE -> dripstone(c, false, true);
            case COLUMN -> column(c, spec.radius(), false, 0.25, 0.5);
            case POINTED_GROUP -> pointedGroup(c);
            case PENDANT -> pendant(c);
            case ROCK_PILLAR, MOSSY_PILLAR, SCULK_PILLAR -> column(c, spec.radius(), spec.kind() == FormationKind.MOSSY_PILLAR, 0.55, 0.8);
            case COLLAPSE -> collapse(c);
            case ROOT_CURTAIN -> rootCurtain(c);
            case MOSS_GARDEN -> mossGarden(c);
            case SCULK_MOUND -> sculkMound(c);
            case SCULK_GROWTH -> sculkGrowth(c);
            case CRYSTALS -> crystals(c);
            case BASALT_COLUMNS -> basaltColumns(c);
            case SUBMERGED_ROCK -> submerged(c);
            case CAVE_ISLET -> caveIslet(c);
            case GIANT_DRIPSTONE -> giantDripstone(c);
            case SPIKE_FIELD -> spikeField(c);
            case GIANT_COLUMN -> column(c, spec.radius(), spec.has(FormationSpec.LUSH), 0.45, 0.75);
            case FISSURE -> fissure(c);
            case LUMEN_MUSHROOM -> lumenMushroom(c, false);
            case LUMEN_GIANT -> lumenMushroom(c, true);
            case LUMEN_COLONY -> lumenColony(c);
            case LUMEN_GROWTH -> lumenGrowth(c);
            case POOL -> pool(c);
            case CRYSTAL_GARDEN -> crystalGarden(c);
            case CRYSTAL_SPIRES -> crystalSpires(c);
            case GIANT_CRYSTAL -> giantCrystal(c);
            case MAGMA_VENT -> magmaVent(c);
            case GEOTHERMAL_FIELD -> geothermalField(c);
            case ASH_VENTS -> ashVents(c);
        }
        return b.build();
    }

    /** Drawing state of one formation. */
    private static final class Ctx {
        final FormationShape.Builder b;
        final FormationSpec spec;
        final World world;
        final NatureRandom r;
        final long noise;
        final int coat;
        final int x;
        final int y;
        final int z;

        Ctx(FormationShape.Builder b, FormationSpec spec, World world) {
            this.b = b;
            this.spec = spec;
            this.world = world;
            this.r = new NatureRandom(spec.seed());
            this.noise = SimplexNoise.mix(spec.seed() ^ 0x526F636B4E6F6973L);
            this.coat = (spec.has(FormationSpec.MOSSY) ? FormationBlocks.MOSSY : 0) | (spec.has(FormationSpec.SCULK) ? FormationBlocks.SCULKY : 0)
                    | (spec.has(FormationSpec.LUSH) ? FormationBlocks.LUSHY : 0);
            this.x = spec.x();
            this.y = spec.y();
            this.z = spec.z();
        }

        /** A rock cell of the formation's palette: the body, or an accent where a noise patch says so. */
        int rock(int dx, int dy, int dz) {
            double v = NatureRandom.value(this.noise, this.x + dx, this.y + dy, this.z + dz, 3.2);
            return FormationBlocks.rock(this.spec.rock(), v > 0.7 ? FormationBlocks.ACCENT : FormationBlocks.BODY, this.coat);
        }

        int cap() {
            return FormationBlocks.rock(this.spec.rock(), FormationBlocks.CAP, this.coat);
        }

        /** The ground top under a column, relative to the anchor (the root's ground top is -1). */
        int ground(int dx, int dz) {
            return this.world.groundTop(this.x + dx, this.z + dz) - this.y;
        }

        /** The water top over a column relative to the anchor, or {@link Integer#MIN_VALUE}. */
        int water(int dx, int dz) {
            int w = this.world.waterTop(this.x + dx, this.z + dz);
            return w == Integer.MIN_VALUE ? w : w - this.y;
        }

        boolean cave(int dx, int dy, int dz) {
            return this.world.isCave(this.x + dx, this.y + dy, this.z + dz);
        }

        /** Surface lumps in about [0, 1) at a 2.5-block scale. */
        double lump(double dx, double dy, double dz) {
            return NatureRandom.value(this.noise ^ 0x4C756D70L, this.x + dx, this.y + dy, this.z + dz, 2.5);
        }

        /**
         * The first open block above the cave floor near a height in a column (relative), searching
         * {@code range} blocks down through open space or up through rock; {@link Integer#MIN_VALUE}
         * if none.
         */
        int floor(int dx, int from, int dz, int range) {
            if (this.cave(dx, from, dz)) {
                for (int k = 0; k < range; k++) {
                    if (!this.cave(dx, from - k - 1, dz)) {
                        return from - k;
                    }
                }
                return Integer.MIN_VALUE;
            }
            for (int k = 1; k <= range; k++) {
                if (this.cave(dx, from + k, dz)) {
                    return from + k;
                }
            }
            return Integer.MIN_VALUE;
        }

        /** The last open block under the cave ceiling near a height in a column (relative), or {@link Integer#MIN_VALUE}. */
        int ceiling(int dx, int from, int dz, int range) {
            if (this.cave(dx, from, dz)) {
                for (int k = 0; k < range; k++) {
                    if (!this.cave(dx, from + k + 1, dz)) {
                        return from + k;
                    }
                }
                return Integer.MIN_VALUE;
            }
            for (int k = 1; k <= range; k++) {
                if (this.cave(dx, from - k, dz)) {
                    return from - k;
                }
            }
            return Integer.MIN_VALUE;
        }
    }

    // ---------------------------------------------------------------- primitives

    /**
     * A rock volume: a superellipsoid (power 2 round, higher boxier) of radii rx, ry, rz turned by
     * {@code angle} about the vertical, its surface pushed in and out by noise. Cells below the
     * ground of their column (surface) or below the anchor (caves) are sunk one or two blocks into
     * it ({@code EMBED}) and dropped deeper; every column it covers gets a foot.
     */
    private static void blob(Ctx c, double cx, double cy, double cz, double rx, double ry, double rz, double angle, double power, double rough,
                             int fixedCode, boolean surface, int footDepth) {
        int x0 = (int) Math.floor(cx - Math.max(rx, rz) - 1);
        int x1 = (int) Math.ceil(cx + Math.max(rx, rz) + 1);
        int z0 = (int) Math.floor(cz - Math.max(rx, rz) - 1);
        int z1 = (int) Math.ceil(cz + Math.max(rx, rz) + 1);
        int y0 = (int) Math.floor(cy - ry - 1);
        int y1 = (int) Math.ceil(cy + ry + 1);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double outer = 1.0 + 0.6 * rough + 1e-9;
        for (int dz = z0; dz <= z1; dz++) {
            for (int dx = x0; dx <= x1; dx++) {
                int ground = surface ? c.ground(dx, dz) : -1;
                boolean any = false;
                for (int dy = y0; dy <= y1; dy++) {
                    double ex = dx - cx;
                    double ez = dz - cz;
                    double u = Math.abs((ex * cos + ez * sin) / rx);
                    double v = Math.abs((-ex * sin + ez * cos) / rz);
                    double w = Math.abs((dy - cy) / ry);
                    double q = Math.pow(Math.pow(u, power) + Math.pow(v, power) + Math.pow(w, power), 1.0 / power);
                    if (q > outer) {
                        continue;
                    }
                    if (q > 1.0 + rough * (c.lump(dx, dy, dz) - 0.5) * 1.2) {
                        continue;
                    }
                    if (dy < ground - 1) {
                        continue;
                    }
                    int code = fixedCode != 0 ? fixedCode : c.rock(dx, dy, dz);
                    c.b.solid(dx, dy, dz, dy <= ground ? code | EMBED : code);
                    any = true;
                }
                if (any && footDepth > 0) {
                    c.b.foot(dx, dz, footDepth);
                }
            }
        }
    }

    /**
     * A disc of rock at one height (a layer of a cone or a pillar): cells within {@code radius} of
     * the centre, the edge pushed by noise; a radius under 0.5 still gives the centre cell.
     */
    private static void disc(Ctx c, double cx, int dy, double cz, double radius, double rough, int fixedCode, int flags) {
        int ri = (int) Math.ceil(radius + 1.0);
        int icx = (int) Math.round(cx);
        int icz = (int) Math.round(cz);
        boolean any = false;
        for (int dz = icz - ri; dz <= icz + ri; dz++) {
            for (int dx = icx - ri; dx <= icx + ri; dx++) {
                double d = Math.hypot(dx - cx, dz - cz);
                if (d > radius + 0.5 + 0.5 * rough) {
                    continue;
                }
                if (d > radius + 0.4 + rough * (c.lump(dx, dy * 0.6, dz) - 0.5)) {
                    continue;
                }
                c.b.solid(dx, dy, dz, (fixedCode != 0 ? fixedCode : c.rock(dx, dy, dz)) | flags);
                any = true;
            }
        }
        if (!any) {
            c.b.solid(icx, dy, icz, (fixedCode != 0 ? fixedCode : c.rock(icx, dy, icz)) | flags);
        }
    }

    /** Pointed dripstone of {@code length} blocks from a cell outward (up or down), with vanilla's thickness sequence. */
    private static void pointed(Ctx c, int dx, int dy, int dz, boolean up, int length, int flags) {
        int dir = up ? 1 : -1;
        for (int k = 0; k < length; k++) {
            int thickness;
            int fromTip = length - 1 - k;
            if (fromTip == 0) {
                thickness = 0;
            } else if (fromTip == 1) {
                thickness = 1;
            } else {
                thickness = k == 0 ? 3 : 2;
            }
            c.b.decor(dx, dy + dir * k, dz, FormationBlocks.code(FormationBlocks.POINTED, (up ? 1 : 0) | thickness << 1) | flags);
        }
    }

    /**
     * A cone of discs from a base (floor or ceiling) towards a tip: radius r0 × (1 - t)^k with
     * rings of noise, the axis leaning by (leanX, leanZ) at the tip; the base sinks two layers into
     * the rock and every base column gets a foot (hanger). Dripstone cones end in pointed dripstone.
     * Returns the cells' count.
     */
    private static void spike(Ctx c, double bx, int by, double bz, boolean up, double r0, double length, double k, double leanX, double leanZ,
                              int fixedCode, boolean tip, int extension) {
        int dir = up ? 1 : -1;
        int n = Math.max(1, (int) Math.round(length));
        double ringPhase = c.r.next() * 10.0;
        int lastX = (int) Math.round(bx);
        int lastZ = (int) Math.round(bz);
        int lastI = -1;
        for (int i = -2; i < n; i++) {
            double t = Math.max(0.0, i) / n;
            double rings = 1.0 + 0.12 * Math.sin(i * 0.9 + ringPhase) * (1.0 - t);
            double radius = r0 * Math.pow(1.0 - t, k) * rings;
            if (radius < 0.42 && i > 0) {
                break;
            }
            double cx = bx + leanX * Math.pow(t, 1.4);
            double cz = bz + leanZ * Math.pow(t, 1.4);
            int dy = by + dir * i;
            int icx = (int) Math.round(cx);
            int icz = (int) Math.round(cz);
            if (i > 0 && (icx != lastX || icz != lastZ)) {
                // Keep the axis face to face where it steps sideways.
                c.b.solid(icx, dy - dir, icz, fixedCode != 0 ? fixedCode : c.rock(icx, dy - dir, icz));
            }
            disc(c, cx, dy, cz, Math.max(0.45, radius), radius < 1.5 ? 0.12 : 0.3, fixedCode, i < 0 ? EMBED : 0);
            if (i <= 0) {
                int ri = (int) Math.ceil(radius + 1.0);
                for (int dz = icz - ri; dz <= icz + ri; dz++) {
                    for (int dx = icx - ri; dx <= icx + ri; dx++) {
                        if (c.b.isSolid(dx, dy, dz)) {
                            if (up) {
                                c.b.foot(dx, dz, extension);
                            } else {
                                c.b.hang(dx, dz, extension);
                            }
                        }
                    }
                }
            }
            lastX = icx;
            lastZ = icz;
            lastI = i;
        }
        if (tip && lastI >= 0) {
            int tipLength = Math.max(1, Math.min(4, (int) Math.round((n - lastI) * 0.6) + 1));
            pointed(c, lastX, by + dir * (lastI + 1), lastZ, up, tipLength, 0);
        }
    }

    /** Feet under every column of the shape so far (formations on uneven ground). */
    private static void footAll(Ctx c, int depth) {
        int r = c.b.reach();
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                c.b.foot(dx, dz, depth);
            }
        }
    }

    /** Moss and plants on the floor of a cave around a point: snapped stacks ({@code [floor block, plant]}). */
    private static void floorPlant(Ctx c, int dx, int dy, int dz, int ground, int plant) {
        c.b.decor(dx, dy - 1, dz, ground | SNAP_FLOOR);
        if (plant != 0) {
            c.b.decor(dx, dy, dz, plant | SNAP_FLOOR);
        }
    }

    // ---------------------------------------------------------------- surface

    /**
     * A boulder sunk into the ground (surface) or lying on a cave floor: round to angular, one or
     * two lobes, a split boulder now and then.
     */
    private static void boulder(Ctx c, boolean surface) {
        double r = c.spec.radius();
        double h = c.spec.height();
        double power = c.r.range(2.0, 3.4);
        double angle = c.spec.angle();
        double ratio = c.r.range(0.65, 1.0);
        double cy = h * c.r.range(0.25, 0.5) - 0.5;
        blob(c, 0, cy, 0, r, h, r * ratio, angle, power, 0.4, 0, surface, surface ? 4 : 5);
        if (r > 1.6 && c.r.chance(0.55)) {
            double a = angle + c.r.range(-1.2, 1.2);
            double d = r * c.r.range(0.6, 0.9);
            double r2 = r * c.r.range(0.45, 0.7);
            double ox = Math.cos(a) * d;
            double oz = Math.sin(a) * d;
            double base = surface ? c.ground((int) Math.round(ox), (int) Math.round(oz)) + 1 : 0;
            blob(c, ox, base + r2 * 0.4 - 0.5, oz, r2, r2 * c.r.range(0.6, 0.9), r2, a, power, 0.4, 0, surface, surface ? 4 : 5);
        }
    }

    /** A few rocks of different sizes lying together, each on its own ground. */
    private static void cluster(Ctx c) {
        int n = c.r.between(3, 6);
        double big = c.spec.radius();
        for (int k = 0; k < n; k++) {
            double r = k == 0 ? big : c.r.skewed(0.55, Math.max(0.7, big * 0.7), 1.6);
            double a = c.r.next() * Math.PI * 2;
            double d = k == 0 ? 0.0 : c.r.range(1.6, 5.0);
            double ox = Math.cos(a) * d;
            double oz = Math.sin(a) * d;
            double base = c.ground((int) Math.round(ox), (int) Math.round(oz)) + 1;
            double h = r * c.r.range(0.7, 1.0);
            blob(c, ox, base + h * 0.35 - 0.5, oz, r, h, r * c.r.range(0.7, 1.0), c.r.next() * Math.PI, c.r.range(2.0, 3.2), 0.4, 0, true, 3);
        }
    }

    /**
     * Scree on a steep slope: rocks scattered along the fall line below the root (the side where
     * the ground drops), each on its own ground, with gravel between them.
     */
    private static void talus(Ctx c) {
        double radius = c.spec.radius();
        // Down the slope: the direction of the lowest ground around the root.
        double best = Double.POSITIVE_INFINITY;
        double fall = 0.0;
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            int g = c.ground((int) Math.round(Math.cos(a) * 6), (int) Math.round(Math.sin(a) * 6));
            if (g < best) {
                best = g;
                fall = a;
            }
        }
        int n = c.r.between(9, 18);
        int gravel = FormationBlocks.code(FormationBlocks.GRAVEL, 0);
        for (int k = 0; k < n; k++) {
            double along = c.r.range(-0.3, 1.0) * radius;
            double across = c.r.range(-0.45, 0.45) * radius;
            double ox = Math.cos(fall) * along - Math.sin(fall) * across;
            double oz = Math.sin(fall) * along + Math.cos(fall) * across;
            // Bigger blocks roll farthest.
            double r = c.r.skewed(0.5, 1.4 + 1.1 * Math.max(0.0, along / radius), 2.0);
            int ix = (int) Math.round(ox);
            int iz = (int) Math.round(oz);
            if (Math.abs(ix) > c.b.reach() - 2 || Math.abs(iz) > c.b.reach() - 2) {
                continue;
            }
            double base = c.ground(ix, iz) + 1;
            blob(c, ox, base + r * 0.3 - 0.5, oz, r, r * c.r.range(0.6, 0.9), r * c.r.range(0.7, 1.0), c.r.next() * Math.PI, c.r.range(2.4, 3.6), 0.45, 0, true, 3);
            // Gravel spilled around it.
            for (int g = 0; g < 3; g++) {
                int gx = ix + c.r.between(-2, 2);
                int gz = iz + c.r.between(-2, 2);
                if (Math.abs(gx) <= c.b.reach() && Math.abs(gz) <= c.b.reach()) {
                    int top = c.ground(gx, gz);
                    c.b.solid(gx, top, gz, gravel | EMBED);
                }
            }
        }
    }

    /**
     * A tor: two to four blocks of rock stacked on a crest, each smaller and shifted a little,
     * angular (superellipse 3..4) and jointed (a narrower layer between blocks), with loose
     * boulders at its foot.
     */
    private static void outcrop(Ctx c) {
        double radius = c.spec.radius();
        double height = c.spec.height();
        int layers = c.r.between(2, 4);
        double angle = c.spec.angle();
        double power = c.r.range(3.0, 4.2);
        // The lowest ground under the footprint: the tor stands from there.
        int low = 0;
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            low = Math.min(low, c.ground((int) Math.round(Math.cos(a) * radius), (int) Math.round(Math.sin(a) * radius)) + 1);
        }
        double y = low - 1;
        double ox = 0.0;
        double oz = 0.0;
        double r = radius;
        double total = height - low;
        for (int l = 0; l < layers && r >= 1.0; l++) {
            double h = l == layers - 1 ? Math.max(1.5, total - (y - low)) : total / layers * c.r.range(0.8, 1.2);
            double rx = r;
            double rz = r * c.r.range(0.65, 0.95);
            double a = angle + c.r.range(-0.4, 0.4);
            for (int dy = (int) Math.floor(y); dy < (int) Math.ceil(y + h); dy++) {
                // Rounded top edges, an inset at the joint under each block.
                double t = (dy - y) / h;
                double shrink = (dy == (int) Math.floor(y) && l > 0) ? 0.82 : (t > 0.75 ? 1.0 - (t - 0.75) * 0.9 : 1.0);
                layer(c, ox, dy, oz, rx * shrink, rz * shrink, a, power, 0.45);
            }
            y += h;
            ox += c.r.range(-0.3, 0.3) * r;
            oz += c.r.range(-0.3, 0.3) * r;
            r *= c.r.range(0.6, 0.85);
        }
        footAll(c, 6);
        int loose = c.r.between(0, 3);
        for (int k = 0; k < loose; k++) {
            double a = c.r.next() * Math.PI * 2;
            double d = radius + c.r.range(1.0, 3.0);
            double bx = Math.cos(a) * d;
            double bz = Math.sin(a) * d;
            if (Math.abs(bx) > c.b.reach() - 2 || Math.abs(bz) > c.b.reach() - 2) {
                continue;
            }
            double rr = c.r.range(0.6, 1.4);
            blob(c, bx, c.ground((int) Math.round(bx), (int) Math.round(bz)) + 1 + rr * 0.3 - 0.5, bz, rr, rr * 0.8, rr, a, 2.6, 0.4, 0, true, 3);
        }
    }

    /** One horizontal layer of a superellipse block (tors, islets). */
    private static void layer(Ctx c, double cx, int dy, double cz, double rx, double rz, double angle, double power, double rough) {
        int ri = (int) Math.ceil(Math.max(rx, rz) + 1.5);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        for (int dz = (int) Math.floor(cz) - ri; dz <= (int) Math.ceil(cz) + ri; dz++) {
            for (int dx = (int) Math.floor(cx) - ri; dx <= (int) Math.ceil(cx) + ri; dx++) {
                double ex = dx - cx;
                double ez = dz - cz;
                double u = Math.abs((ex * cos + ez * sin) / Math.max(0.5, rx + 0.4));
                double v = Math.abs((-ex * sin + ez * cos) / Math.max(0.5, rz + 0.4));
                double q = Math.pow(Math.pow(u, power) + Math.pow(v, power), 1.0 / power);
                if (q <= 1.0 + rough * (c.lump(dx, dy, dz) - 0.5)) {
                    int ground = c.ground(dx, dz);
                    if (dy >= ground - 1) {
                        int code = c.rock(dx, dy, dz);
                        c.b.solid(dx, dy, dz, dy <= ground ? code | EMBED : code);
                    }
                }
            }
        }
    }

    /**
     * A rock spire from the lowest ground under its footprint (the sea bed for sea stacks): a column
     * narrowing to the top with ledges, leaning a little; hoodoos (terracotta, sandstone) carry a
     * wider cap rock, great spires stand in tiers.
     */
    private static void spire(Ctx c, double height, double radius, boolean great) {
        boolean hoodoo = c.spec.rock() == Rock.TERRACOTTA || c.spec.rock() == Rock.SANDSTONE;
        int low = 0;
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            low = Math.min(low, c.ground((int) Math.round(Math.cos(a) * radius), (int) Math.round(Math.sin(a) * radius)) + 1);
        }
        int bottom = low - 1;
        int top = (int) Math.round(height);
        double leanA = c.r.next() * Math.PI * 2;
        double lean = c.r.range(0.0, great ? 3.0 : 1.5);
        double k = hoodoo ? c.r.range(0.15, 0.35) : c.r.range(0.45, 0.9);
        double ledges = c.r.next() * 10.0;
        boolean wet = c.spec.has(FormationSpec.FLOODED);
        for (int dy = bottom; dy <= top; dy++) {
            double t = Math.max(0.0, (dy - bottom) / (double) Math.max(1, top - bottom));
            double rr = radius * Math.pow(1.0 - 0.85 * t, k);
            if (hoodoo) {
                // A waist under the cap.
                rr *= 1.0 - 0.3 * Math.exp(-Math.pow((t - 0.78) / 0.12, 2.0));
            }
            if (great) {
                // Tiers: ledges every ~10 blocks.
                rr *= 1.0 + 0.16 * Math.max(0.0, Math.sin((dy + ledges) * Math.PI / 10.0));
            }
            double cx = Math.cos(leanA) * lean * t * t;
            double cz = Math.sin(leanA) * lean * t * t;
            disc(c, cx, dy, cz, Math.max(0.5, rr), 0.6, 0, (dy <= 0 ? EMBED : 0) | (wet ? WET : 0));
        }
        if (hoodoo) {
            // The cap rock: harder, wider, flat.
            double cx = Math.cos(leanA) * lean;
            double cz = Math.sin(leanA) * lean;
            double cr = Math.max(1.4, radius * c.r.range(0.9, 1.3));
            for (int dy = top + 1; dy <= top + 2; dy++) {
                disc(c, cx, dy, cz, dy == top + 2 ? cr * 0.75 : cr, 0.4, c.cap(), 0);
            }
        } else {
            int tip = c.r.between(1, great ? 4 : 2);
            for (int dy = top + 1; dy <= top + tip; dy++) {
                c.b.solid((int) Math.round(Math.cos(leanA) * lean), dy, (int) Math.round(Math.sin(leanA) * lean), c.rock(0, dy, 0));
            }
        }
        footAll(c, 8);
    }

    /**
     * A natural arch: two legs on the ground {@code radius} apart along the spec's angle, joined by
     * a span bowed into a half ellipse {@code height} above the higher leg's ground. Every cell is
     * measured to the arch's centre line in its own plane (thick at the legs, thinner at the crown)
     * and across it (wider than thick), so the opening under it stays open; the legs go down to
     * their own ground.
     */
    private static void arch(Ctx c) {
        double half = c.spec.radius();
        double rise = c.spec.height();
        double a = c.spec.angle();
        double cos = Math.cos(a);
        double sin = Math.sin(a);
        boolean great = c.spec.kind() == FormationKind.GREAT_ARCH;
        // A slender rib (about a sixth of the span thick at the crest) on flared legs: thicker
        // ribs read as a dome with a hole in it.
        double thick = great ? c.r.range(2.0, 2.8) : c.r.range(1.2, 1.7);
        double wide = thick * c.r.range(0.95, 1.35);
        int legA = c.ground((int) Math.round(-cos * half), (int) Math.round(-sin * half)) + 1;
        int legB = c.ground((int) Math.round(cos * half), (int) Math.round(sin * half)) + 1;
        double base = Math.max(legA, legB);
        double reach = half + thick * 1.8 + 2.0;
        int r = (int) Math.ceil(Math.min(reach, c.b.reach()));
        int low = Math.min(legA, legB) - 2;
        int high = (int) Math.ceil(base + rise + thick * 1.6 + 1);
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                double u = dx * cos + dz * sin;
                double w = -dx * sin + dz * cos;
                if (Math.abs(w) > wide * 1.6 + 1.0) {
                    continue;
                }
                int ground = c.ground(dx, dz);
                for (int dy = Math.max(low, ground - 1); dy <= high; dy++) {
                    double v = dy - base;
                    double t;
                    double din;
                    if (v < 0.0) {
                        // Below the springing line: the legs, straight down.
                        t = 0.0;
                        din = Math.abs(Math.abs(u) - half);
                    } else {
                        // The nearest point of the half ellipse: from the angle, refined.
                        double t0 = Math.atan2(v / rise, -u / half);
                        t0 = Math.max(0.0, Math.min(Math.PI, t0));
                        din = Double.POSITIVE_INFINITY;
                        t = t0;
                        for (int k = -4; k <= 4; k++) {
                            double tt = Math.max(0.0, Math.min(Math.PI, t0 + k * 0.04));
                            double eu = u + Math.cos(tt) * half;
                            double ev = v - Math.sin(tt) * rise;
                            double d = Math.sqrt(eu * eu + ev * ev);
                            if (d < din) {
                                din = d;
                                t = tt;
                            }
                        }
                    }
                    // The legs flare towards the ground, the rib stays slender above.
                    double leg = 1.0 - Math.sin(t);
                    double tt = thick * (1.0 + 1.0 * leg * leg);
                    double ww = wide * (1.0 + 0.7 * leg * leg);
                    double q = (din / tt) * (din / tt) + (w / ww) * (w / ww);
                    if (q <= 1.0 + 0.4 * (c.lump(dx, dy, dz) - 0.5)) {
                        int code = c.rock(dx, dy, dz);
                        c.b.solid(dx, dy, dz, dy <= ground ? code | EMBED : code);
                    }
                }
            }
        }
        footAll(c, great ? 10 : 6);
    }

    /**
     * Logs washed up along a shore (the spec's angle runs along the water's edge): each block floats
     * at the water's top where the column is wet, lies on the ground where it is dry, and the log
     * ends where the two differ by more than a block. Bleached (stripped) in dry and cold climates.
     */
    private static void driftwood(Ctx c) {
        int wood = Math.max(0, c.spec.span());
        boolean bleached = c.spec.has(FormationSpec.DRY) || c.spec.has(FormationSpec.SNOWY) || c.r.chance(0.35);
        int logs = c.r.chance(0.3) ? 2 : 1;
        for (int l = 0; l < logs; l++) {
            double a = c.spec.angle() + (l == 0 ? 0.0 : c.r.range(-0.9, 0.9));
            boolean alongX = Math.abs(Math.cos(a)) >= Math.abs(Math.sin(a));
            int axis = alongX ? 1 : 2;
            int length = (int) Math.round(c.spec.height()) - (l == 0 ? 0 : 2);
            int sign = c.r.chance(0.5) ? 1 : -1;
            int ox = l == 0 ? 0 : c.r.between(-2, 2);
            int oz = l == 0 ? 0 : c.r.between(-2, 2);
            int code = FormationBlocks.code(FormationBlocks.LOG, wood | axis << 3 | (bleached && (l == 0 || c.r.chance(0.5)) ? 1 << 5 : 0));
            int previous = Integer.MIN_VALUE;
            for (int k = 0; k < length; k++) {
                int dx = ox + (alongX ? k * sign : 0);
                int dz = oz + (alongX ? 0 : k * sign);
                if (Math.abs(dx) > c.b.reach() || Math.abs(dz) > c.b.reach()) {
                    break;
                }
                int water = c.water(dx, dz);
                int ground = c.ground(dx, dz);
                int y;
                boolean wet = water != Integer.MIN_VALUE && water > ground;
                y = wet ? water : ground + 1;
                if (wet && water - ground > 3) {
                    break;
                }
                if (previous != Integer.MIN_VALUE && Math.abs(y - previous) > 1) {
                    break;
                }
                if (previous != Integer.MIN_VALUE && y != previous) {
                    // A step: keep the log face to face (it lies over the edge).
                    c.b.solid(dx, Math.max(y, previous), dz, code | (wet ? WET : 0));
                }
                c.b.solid(dx, y, dz, code | (wet ? WET : 0));
                previous = y;
            }
        }
    }

    /** Stones along the water's edge and on the shallow bed beside it: single blocks and small lumps. */
    private static void shoreStones(Ctx c) {
        int n = c.r.between(3, 8);
        double a = c.spec.angle();
        for (int k = 0; k < n; k++) {
            double along = c.r.range(-4.0, 4.0);
            double across = c.r.range(-1.6, 1.6);
            int dx = (int) Math.round(Math.cos(a) * along - Math.sin(a) * across);
            int dz = (int) Math.round(Math.sin(a) * along + Math.cos(a) * across);
            if (Math.abs(dx) > c.b.reach() - 1 || Math.abs(dz) > c.b.reach() - 1) {
                continue;
            }
            int ground = c.ground(dx, dz);
            int water = c.water(dx, dz);
            if (water != Integer.MIN_VALUE && water - ground > 4) {
                continue;
            }
            int code = c.r.chance(0.25) ? FormationBlocks.code(FormationBlocks.GRAVEL, 0) : c.rock(dx, ground + 1, dz);
            int wet = water != Integer.MIN_VALUE ? WET : 0;
            c.b.solid(dx, ground, dz, code | EMBED | wet);
            c.b.solid(dx, ground + 1, dz, code | wet);
            if (c.r.chance(0.35)) {
                int ex = dx + (c.r.chance(0.5) ? 1 : 0);
                int ez = dz + (c.r.chance(0.5) ? 0 : 1);
                if (c.ground(ex, ez) == ground) {
                    c.b.solid(ex, ground + 1, ez, c.rock(ex, ground + 1, ez) | wet);
                }
            }
        }
    }

    /** A rocky island: a mound from the bed rising one to three blocks above the water, gravel at its shore. */
    private static void islet(Ctx c) {
        double radius = c.spec.radius();
        int water = c.water(0, 0);
        if (water == Integer.MIN_VALUE) {
            return;
        }
        double above = c.spec.height();
        double angle = c.spec.angle();
        double ratio = c.r.range(0.6, 1.0);
        int ri = (int) Math.ceil(radius + 1.5);
        int gravel = FormationBlocks.code(FormationBlocks.GRAVEL, 0);
        for (int dz = -ri; dz <= ri; dz++) {
            for (int dx = -ri; dx <= ri; dx++) {
                double u = (dx * Math.cos(angle) + dz * Math.sin(angle)) / radius;
                double v = (-dx * Math.sin(angle) + dz * Math.cos(angle)) / (radius * ratio);
                double q = Math.sqrt(u * u + v * v) - 0.35 * (c.lump(dx, 0, dz) - 0.5);
                if (q > 1.0) {
                    continue;
                }
                int bed = c.ground(dx, dz);
                int top = water + (int) Math.round(above * (1.0 - q * q)) - (q > 0.8 ? 1 : 0);
                for (int dy = bed; dy <= top; dy++) {
                    int code = q > 0.75 && dy <= water ? gravel : c.rock(dx, dy, dz);
                    c.b.solid(dx, dy, dz, code | WET | (dy == bed ? EMBED : 0));
                }
            }
        }
    }

    /** A great boulder balanced on a narrow pedestal of the same rock, overhanging it on every side. */
    private static void balancedRock(Ctx c) {
        double r = c.spec.radius();
        int pedestal = (int) Math.round(c.spec.height());
        double pr = Math.max(0.9, r * c.r.range(0.25, 0.38));
        int ground = c.ground(0, 0);
        for (int dy = ground - 1; dy <= pedestal; dy++) {
            double t = Math.max(0.0, (dy - ground) / (double) Math.max(1, pedestal - ground));
            // Flared at the foot, narrowest under the boulder.
            disc(c, 0, dy, 0, pr * (1.0 + 0.6 * (1.0 - t) * (1.0 - t)), 0.3, 0, dy <= ground ? EMBED : 0);
        }
        double h = r * c.r.range(0.55, 0.75);
        blob(c, c.r.range(-0.5, 0.5), pedestal + 1.0 + h * 0.85, c.r.range(-0.5, 0.5), r, h, r * c.r.range(0.7, 0.95), c.r.next() * Math.PI,
                c.r.range(2.0, 2.8), 0.35, 0, false, 0);
        c.b.foot(0, 0, 3);
    }

    /** A stone forest: limestone pinnacles crowded together, fluted (grooved) and sharp, each on its own ground. */
    private static void stoneForest(Ctx c) {
        double radius = c.spec.radius();
        int n = c.r.between(14, 30);
        double[][] placed = new double[n][];
        int count = 0;
        for (int tries = 0; tries < n * 4 && count < n; tries++) {
            double a = c.r.next() * Math.PI * 2;
            double d = radius * Math.sqrt(c.r.next());
            double px = Math.cos(a) * d;
            double pz = Math.sin(a) * d;
            double pr = c.r.skewed(1.0, 3.2, 1.5);
            boolean ok = Math.abs(px) + pr < c.b.reach() - 1 && Math.abs(pz) + pr < c.b.reach() - 1;
            for (int k = 0; k < count && ok; k++) {
                ok = Math.hypot(placed[k][0] - px, placed[k][1] - pz) > placed[k][2] + pr + 0.5;
            }
            if (!ok) {
                continue;
            }
            placed[count++] = new double[] {px, pz, pr};
            int ground = c.ground((int) Math.round(px), (int) Math.round(pz));
            // Taller towards the middle of the forest.
            double h = (6.0 + 18.0 * (1.0 - d / radius) * c.r.range(0.6, 1.0)) * c.spec.height() / 24.0;
            double flutes = c.r.next() * Math.PI;
            for (int dy = ground - 1; dy <= ground + (int) h; dy++) {
                double t = (dy - ground) / Math.max(1.0, h);
                double rr = pr * Math.pow(Math.max(0.0, 1.0 - 0.9 * t), 0.7);
                int ri = (int) Math.ceil(rr + 1);
                for (int dz = (int) Math.floor(pz) - ri; dz <= (int) Math.ceil(pz) + ri; dz++) {
                    for (int dx = (int) Math.floor(px) - ri; dx <= (int) Math.ceil(px) + ri; dx++) {
                        double ex = dx - px;
                        double ez = dz - pz;
                        double groove = 1.0 - 0.18 * Math.max(0.0, Math.cos(5.0 * Math.atan2(ez, ex) + flutes));
                        if (Math.hypot(ex, ez) <= rr * groove + 0.35) {
                            int code = c.rock(dx, dy, dz);
                            c.b.solid(dx, dy, dz, dy <= ground ? code | EMBED : code);
                        }
                    }
                }
                if (rr < 0.4) {
                    break;
                }
            }
        }
        footAll(c, 6);
    }

    // ---------------------------------------------------------------- caves

    /** Rubble on a cave floor: a few lumps and blocks of fallen rock, each settled on the floor of its column. */
    private static void rubble(Ctx c) {
        int n = c.r.between(2, 5);
        for (int k = 0; k < n; k++) {
            int dx = c.r.between(-3, 3);
            int dz = c.r.between(-3, 3);
            int code = c.r.chance(0.3) ? FormationBlocks.code(FormationBlocks.GRAVEL, 0) : c.rock(dx, 0, dz);
            int wet = c.spec.has(FormationSpec.FLOODED) ? WET : 0;
            // [floor block, rock, sometimes a second on top]
            c.b.decor(dx, -1, dz, FormationBlocks.code(FormationBlocks.KEEP, 0) | SNAP_FLOOR);
            c.b.decor(dx, 0, dz, code | SNAP_FLOOR | wet);
            if (c.r.chance(0.25)) {
                c.b.decor(dx, 1, dz, c.rock(dx, 1, dz) | SNAP_FLOOR | wet);
            }
        }
    }

    /**
     * Dripstone formations: a stalagmite from the floor or a stalactite from the ceiling, of
     * dripstone blocks ending in pointed dripstone in dripstone caves, of rock elsewhere; organic
     * taper with rings, a lean, sometimes a small companion beside it.
     */
    private static void dripstone(Ctx c, boolean up, boolean fromCeiling) {
        boolean drip = c.spec.has(FormationSpec.DRIPSTONE);
        int body = drip ? FormationBlocks.code(FormationBlocks.DRIPSTONE, 0) : 0;
        double length = c.spec.height();
        double r0 = c.spec.radius();
        int anchor = fromCeiling ? c.spec.span() - 1 : 0;
        double k = c.r.range(0.8, 1.5);
        double la = c.r.next() * Math.PI * 2;
        double lean = c.r.range(0.0, 0.25) * length * 0.25;
        spike(c, 0, anchor, 0, up, r0, length, k, Math.cos(la) * lean, Math.sin(la) * lean, body, drip, 8);
        if (c.r.chance(0.45) && r0 >= 1.2) {
            double a = c.r.next() * Math.PI * 2;
            double d = r0 + c.r.range(1.0, 2.5);
            int ox = (int) Math.round(Math.cos(a) * d);
            int oz = (int) Math.round(Math.sin(a) * d);
            int base = fromCeiling ? c.ceiling(ox, anchor, oz, 6) : c.floor(ox, 0, oz, 6);
            if (base != Integer.MIN_VALUE) {
                spike(c, ox, base, oz, up, r0 * c.r.range(0.35, 0.6), length * c.r.range(0.3, 0.6), k, 0, 0, body, drip, 6);
            }
        }
    }

    /**
     * A column from floor to ceiling: a stalagmite and a stalactite grown together, radius the
     * larger of the two cones and a waist where they met; rock pillars, mossy pillars, sculk
     * pillars and giant columns are the same with their own rock and coat.
     */
    private static void column(Ctx c, double radius, boolean mossy, double waistLow, double waistHigh) {
        boolean drip = c.spec.has(FormationSpec.DRIPSTONE);
        int body = drip ? FormationBlocks.code(FormationBlocks.DRIPSTONE, 0) : 0;
        int span = c.spec.span();
        double meet = c.r.range(0.35, 0.65);
        double waist = Math.max(0.6, radius * c.r.range(waistLow, waistHigh));
        double k1 = c.r.range(0.7, 1.4);
        double k2 = c.r.range(0.7, 1.4);
        double r1 = radius;
        double r2 = radius * c.r.range(0.6, 1.0);
        double terraces = c.r.next() * 9.0;
        boolean giant = c.spec.kind() == FormationKind.GIANT_COLUMN || c.spec.kind() == FormationKind.GIANT_DRIPSTONE;
        double ox = c.r.range(-0.4, 0.4) * Math.min(4.0, span * 0.06);
        double oz = c.r.range(-0.4, 0.4) * Math.min(4.0, span * 0.06);
        for (int dy = -2; dy <= span + 1; dy++) {
            double t = Math.max(0.0, Math.min(1.0, dy / (double) Math.max(1, span - 1)));
            double low = r1 * Math.pow(Math.max(0.0, 1.0 - t / Math.max(0.05, meet + 0.25)), k1);
            double high = r2 * Math.pow(Math.max(0.0, 1.0 - (1.0 - t) / Math.max(0.05, 1.25 - meet)), k2);
            double rr = Math.max(waist, Math.max(low, high));
            if (giant) {
                rr *= 1.0 + 0.1 * Math.sin((dy + terraces) * Math.PI / 6.0);
            }
            double cx = ox * Math.sin(Math.PI * t);
            double cz = oz * Math.sin(Math.PI * t);
            int flags = dy < 0 || dy >= span ? EMBED : 0;
            disc(c, cx, dy, cz, rr, 0.55, body, flags);
        }
        // Feet and hangers for the ends.
        int r = (int) Math.ceil(Math.max(r1, r2) + 1.5);
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                c.b.foot(dx, dz, 6);
                c.b.hang(dx, dz, 6);
            }
        }
        if (mossy) {
            vinesAround(c, Math.max(r1, r2) + 1.0, span, c.r.between(3, 7));
        }
    }

    /** Glow berry vines hanging from the ceiling around a point, as snapped stacks. */
    private static void vinesAround(Ctx c, double radius, int span, int count) {
        for (int k = 0; k < count; k++) {
            double a = c.r.next() * Math.PI * 2;
            double d = radius + c.r.range(0.0, 2.0);
            int dx = (int) Math.round(Math.cos(a) * d);
            int dz = (int) Math.round(Math.sin(a) * d);
            vine(c, dx, span - 1, dz, Math.min(span - 2, c.r.between(2, Math.max(2, Math.min(14, span * 2 / 3)))));
        }
    }

    /** One cave vine hanging from the ceiling (snapped), berries on a share of its blocks. */
    private static void vine(Ctx c, int dx, int top, int dz, int length) {
        if (length < 1 || Math.abs(dx) > c.b.reach() || Math.abs(dz) > c.b.reach()) {
            return;
        }
        c.b.decor(dx, top + 1, dz, FormationBlocks.code(FormationBlocks.KEEP, 0) | SNAP_CEILING);
        for (int k = 0; k < length; k++) {
            boolean berries = c.r.chance(0.3);
            boolean tip = k == length - 1;
            c.b.decor(dx, top - k, dz, FormationBlocks.code(FormationBlocks.CAVE_VINES, (berries ? 1 : 0) | (tip ? 2 : 0)) | SNAP_CEILING);
        }
    }

    /** Pointed dripstone on the ceiling around the root and, under some of them, on the floor. */
    private static void pointedGroup(Ctx c) {
        int span = c.spec.span();
        int n = c.r.between(2, 6);
        int wet = c.spec.has(FormationSpec.FLOODED) ? WET : 0;
        for (int k = 0; k < n; k++) {
            int dx = k == 0 ? 0 : c.r.between(-2, 2);
            int dz = k == 0 ? 0 : c.r.between(-2, 2);
            int down = Math.max(1, Math.min(span / 3, c.r.between(1, 5)));
            if (!c.b.isEmpty(dx, span - 1, dz)) {
                continue;
            }
            c.b.decor(dx, span, dz, FormationBlocks.code(FormationBlocks.KEEP, 0) | SNAP_CEILING);
            pointed(c, dx, span - 1, dz, false, down, SNAP_CEILING);
            if (c.r.chance(0.55) && span - down > 3) {
                int up = Math.max(1, Math.min((span - down) / 3, c.r.between(1, 3)));
                c.b.decor(dx, -1, dz, FormationBlocks.code(FormationBlocks.KEEP, 0) | SNAP_FLOOR);
                pointed(c, dx, 0, dz, true, up, SNAP_FLOOR | wet);
            }
        }
    }

    /** A blunt pendant of rock hanging from the ceiling. */
    private static void pendant(Ctx c) {
        double r = c.spec.radius();
        double h = c.spec.height();
        int ceiling = c.spec.span() - 1;
        blob(c, 0, ceiling - h * 0.45 + 1.0, 0, r, h * 0.6, r * c.r.range(0.6, 1.0), c.spec.angle(), c.r.range(2.0, 2.8), 0.45, 0, false, 0);
        int ri = (int) Math.ceil(r + 1.5);
        for (int dz = -ri; dz <= ri; dz++) {
            for (int dx = -ri; dx <= ri; dx++) {
                c.b.hang(dx, dz, 6);
            }
        }
        // Sink the top into the ceiling.
        for (int dz = -ri; dz <= ri; dz++) {
            for (int dx = -ri; dx <= ri; dx++) {
                if (c.b.isSolid(dx, ceiling, dz)) {
                    c.b.solid(dx, ceiling + 1, dz, c.rock(dx, ceiling + 1, dz) | EMBED);
                }
            }
        }
    }

    /** A cone of breakdown under a high ceiling: rubble, gravel and boulders. */
    private static void collapse(Ctx c) {
        double radius = c.spec.radius();
        double height = c.spec.height();
        int ri = (int) Math.ceil(radius + 1.5);
        int gravel = FormationBlocks.code(FormationBlocks.GRAVEL, 0);
        for (int dz = -ri; dz <= ri; dz++) {
            for (int dx = -ri; dx <= ri; dx++) {
                double d = Math.hypot(dx, dz) / radius + 0.3 * (c.lump(dx, 0, dz) - 0.5);
                if (d >= 1.0) {
                    continue;
                }
                int h = (int) Math.round(height * (1.0 - Math.pow(d, 1.4)));
                for (int dy = -1; dy <= h; dy++) {
                    double v = NatureRandom.value(c.noise ^ 0x47726176L, c.x + dx, c.y + dy, c.z + dz, 2.0);
                    int code = v > 0.62 ? gravel : c.rock(dx, dy, dz);
                    c.b.solid(dx, dy, dz, dy < 0 ? code | EMBED : code);
                }
                c.b.foot(dx, dz, 5);
            }
        }
        int boulders = c.r.between(2, 5);
        for (int k = 0; k < boulders; k++) {
            double a = c.r.next() * Math.PI * 2;
            double d = c.r.range(0.2, 0.9) * radius;
            double bx = Math.cos(a) * d;
            double bz = Math.sin(a) * d;
            double surface = height * (1.0 - Math.pow(d / radius, 1.4));
            double r = c.r.range(0.8, Math.max(1.0, radius * 0.3));
            blob(c, bx, surface + r * 0.4, bz, r, r * 0.8, r, a, 2.6, 0.4, 0, false, 0);
        }
    }

    /**
     * Lush caves: a clump of rooted earth in the ceiling, hanging roots under it, glow berry vines
     * hanging from it and beside it.
     */
    private static void rootCurtain(Ctx c) {
        int span = c.spec.span();
        double r = c.spec.radius();
        int ceiling = span - 1;
        int rooted = FormationBlocks.code(FormationBlocks.ROOTED_DIRT, 0);
        int ri = (int) Math.ceil(r + 1);
        for (int dz = -ri; dz <= ri; dz++) {
            for (int dx = -ri; dx <= ri; dx++) {
                double d = Math.hypot(dx, dz) / r + 0.4 * (c.lump(dx, 0, dz) - 0.5);
                if (d > 1.0) {
                    continue;
                }
                int depth = d < 0.5 ? 2 : 1;
                // [plants hanging, earth, the ceiling block above]
                c.b.decor(dx, ceiling + 1, dz, rooted | SNAP_CEILING | EMBED);
                for (int k = 1; k < depth; k++) {
                    c.b.decor(dx, ceiling + 1 - k, dz, rooted | SNAP_CEILING);
                }
                int below = ceiling + 1 - depth;
                double u = c.r.next();
                if (u < 0.45) {
                    c.b.decor(dx, below, dz, FormationBlocks.code(FormationBlocks.HANGING_ROOTS, 0) | SNAP_CEILING);
                } else if (u < 0.75 && span > 4) {
                    int length = Math.min(span - 3, c.r.between(2, Math.max(2, Math.min(12, span / 2))));
                    for (int k = 0; k < length; k++) {
                        c.b.decor(dx, below - k, dz, FormationBlocks.code(FormationBlocks.CAVE_VINES, (c.r.chance(0.3) ? 1 : 0) | (k == length - 1 ? 2 : 0))
                                | SNAP_CEILING);
                    }
                }
            }
        }
    }

    /**
     * Lush caves: a moss terrace: the floor turned to moss over a patch, a low mossy mound in it,
     * azalea bushes and flowering azalea leaves on the mound, carpets around, a spore blossom above.
     */
    private static void mossGarden(Ctx c) {
        double r = c.spec.radius();
        int moss = FormationBlocks.code(FormationBlocks.MOSS_BLOCK, 0);
        int carpet = FormationBlocks.code(FormationBlocks.MOSS_CARPET, 0);
        int ri = (int) Math.ceil(r + 1);
        double mound = Math.max(1.2, r * c.r.range(0.3, 0.5));
        double mh = c.r.range(1.0, 2.2);
        blob(c, 0, mh * 0.3 - 0.5, 0, mound, mh, mound * c.r.range(0.7, 1.0), c.r.next() * Math.PI, 2.2, 0.4, moss, false, 4);
        for (int dz = -ri; dz <= ri; dz++) {
            for (int dx = -ri; dx <= ri; dx++) {
                double d = Math.hypot(dx, dz) / r + 0.45 * (c.lump(dx, 0, dz) - 0.5);
                if (d > 1.0 || c.b.isSolid(dx, 0, dz) || c.b.isSolid(dx, -1, dz)) {
                    continue;
                }
                double u = c.r.next();
                int plant = u < 0.35 ? carpet : (u < 0.43 ? FormationBlocks.code(FormationBlocks.AZALEA, c.r.chance(0.4) ? 1 : 0) : 0);
                floorPlant(c, dx, 0, dz, moss, plant);
            }
        }
        // Bushes on the mound.
        int bushes = c.r.between(1, 3);
        for (int k = 0; k < bushes; k++) {
            int bx = c.r.between(-1, 1);
            int bz = c.r.between(-1, 1);
            int top = -1;
            for (int dy = 4; dy >= -1; dy--) {
                if (c.b.isSolid(bx, dy, bz)) {
                    top = dy;
                    break;
                }
            }
            boolean flowering = c.r.chance(0.5);
            c.b.decor(bx, top + 1, bz, FormationBlocks.code(FormationBlocks.AZALEA, flowering ? 1 : 0));
            if (c.spec.span() > top + 4) {
                for (int f = 0; f < 4; f++) {
                    if (c.r.chance(0.5)) {
                        int lx = bx + (f == 0 ? 1 : (f == 1 ? -1 : 0));
                        int lz = bz + (f == 2 ? 1 : (f == 3 ? -1 : 0));
                        if (c.b.isSolid(lx, top, lz)) {
                            c.b.decor(lx, top + 1, lz, FormationBlocks.code(FormationBlocks.AZALEA_LEAVES, c.r.chance(0.4) ? 1 : 0));
                        }
                    }
                }
            }
        }
        if (c.r.chance(0.35) && c.spec.span() > 6) {
            int sx = c.r.between(-2, 2);
            int sz = c.r.between(-2, 2);
            c.b.decor(sx, c.spec.span(), sz, FormationBlocks.code(FormationBlocks.KEEP, 0) | SNAP_CEILING);
            c.b.decor(sx, c.spec.span() - 1, sz, FormationBlocks.code(FormationBlocks.SPORE_BLOSSOM, 0) | SNAP_CEILING);
        }
    }

    /** Deep dark: a mound of deepslate coated in sculk, veins spreading over the floor around it. */
    private static void sculkMound(Ctx c) {
        double r = c.spec.radius();
        double h = c.spec.height();
        blob(c, 0, h * 0.25 - 0.5, 0, r, h, r * c.r.range(0.65, 1.0), c.spec.angle(), c.r.range(2.0, 3.0), 0.5, 0, false, 5);
        veins(c, r + 3.0, 0.4);
    }

    /** Sculk veins on the floor (snapped) within a radius, thinning out to the edge. */
    private static void veins(Ctx c, double radius, double share) {
        int ri = (int) Math.ceil(radius);
        int vein = FormationBlocks.code(FormationBlocks.SCULK_VEIN, 1);
        int keep = FormationBlocks.code(FormationBlocks.KEEP, 0);
        for (int dz = -ri; dz <= ri; dz++) {
            for (int dx = -ri; dx <= ri; dx++) {
                double d = Math.hypot(dx, dz) / radius;
                if (d > 1.0 || c.b.isSolid(dx, 0, dz) || c.b.isSolid(dx, -1, dz)) {
                    continue;
                }
                if (c.lump(dx * 1.5, 0, dz * 1.5) < 1.0 - share * (1.0 - d * d)) {
                    continue;
                }
                c.b.decor(dx, -1, dz, keep | SNAP_FLOOR);
                c.b.decor(dx, 0, dz, vein | SNAP_FLOOR);
            }
        }
    }

    /** Deep dark: a patch of sculk on the floor with veins at its ragged edge. */
    private static void sculkGrowth(Ctx c) {
        double r = c.spec.radius();
        int ri = (int) Math.ceil(r + 2);
        int sculk = FormationBlocks.code(FormationBlocks.SCULK, 0);
        int vein = FormationBlocks.code(FormationBlocks.SCULK_VEIN, 1);
        int keep = FormationBlocks.code(FormationBlocks.KEEP, 0);
        for (int dz = -ri; dz <= ri; dz++) {
            for (int dx = -ri; dx <= ri; dx++) {
                double d = Math.hypot(dx, dz) / r + 0.6 * (c.lump(dx * 1.3, 0, dz * 1.3) - 0.5);
                if (d < 0.85) {
                    c.b.decor(dx, -1, dz, sculk | SNAP_FLOOR | EMBED);
                } else if (d < 1.25 && c.r.chance(0.55)) {
                    c.b.decor(dx, -1, dz, keep | SNAP_FLOOR);
                    c.b.decor(dx, 0, dz, vein | SNAP_FLOOR);
                }
            }
        }
    }

    /** Crystal caves: a calcite mound with amethyst in it and clusters growing out of its faces. */
    private static void crystals(Ctx c) {
        double r = c.spec.radius();
        double h = c.spec.height();
        int calcite = FormationBlocks.code(FormationBlocks.CALCITE, 0);
        int amethyst = FormationBlocks.code(FormationBlocks.AMETHYST_BLOCK, 0);
        blob(c, 0, h * 0.25 - 0.5, 0, r, h, r * c.r.range(0.7, 1.0), c.spec.angle(), 2.2, 0.5, calcite, false, 4);
        int ri = (int) Math.ceil(r + 1);
        int[][] dirs = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
        for (int dy = -2; dy <= (int) Math.ceil(h) + 1; dy++) {
            for (int dz = -ri; dz <= ri; dz++) {
                for (int dx = -ri; dx <= ri; dx++) {
                    if (!c.b.isSolid(dx, dy, dz)) {
                        continue;
                    }
                    if (NatureRandom.value(c.noise ^ 0x416D6574L, c.x + dx, c.y + dy, c.z + dz, 1.8) > 0.66) {
                        c.b.force(dx, dy, dz, amethyst | (c.b.get(dx, dy, dz) & EMBED));
                    }
                    for (int f = 1; f < 6; f++) {
                        int nx = dx + dirs[f][0];
                        int ny = dy + dirs[f][1];
                        int nz = dz + dirs[f][2];
                        if (c.b.isEmpty(nx, ny, nz) && c.r.chance(0.12)) {
                            int size = c.r.between(0, 3);
                            c.b.decor(nx, ny, nz, FormationBlocks.code(FormationBlocks.AMETHYST, f | size << 3));
                        }
                    }
                }
            }
        }
    }

    /** Volcanic caves: basalt columns of many heights packed together, rising out of lava too. */
    private static void basaltColumns(Ctx c) {
        int n = c.r.between(5, 14);
        int span = c.spec.span();
        boolean lava = c.spec.has(FormationSpec.LAVA);
        int basalt = FormationBlocks.code(FormationBlocks.BASALT, 0);
        int maxH = Math.max(2, Math.min(12, span - 3));
        double radius = c.spec.radius();
        for (int k = 0; k < n; k++) {
            int dx = (int) Math.round(c.r.range(-radius, radius));
            int dz = (int) Math.round(c.r.range(-radius, radius));
            int h = c.r.between(1, maxH);
            boolean wide = c.r.chance(0.25);
            int base = c.floor(dx, 0, dz, 5);
            if (base == Integer.MIN_VALUE) {
                continue;
            }
            for (int dy = base - 1; dy < base + h; dy++) {
                int flags = (dy < base ? EMBED : 0) | (lava ? WET : 0);
                c.b.solid(dx, dy, dz, basalt | flags);
                if (wide) {
                    c.b.solid(dx + 1, dy, dz, basalt | flags);
                    c.b.solid(dx, dy, dz + 1, basalt | flags);
                    c.b.solid(dx + 1, dy, dz + 1, basalt | flags);
                }
            }
            c.b.foot(dx, dz, 4);
        }
    }

    /** Flooded caves: a boulder or a pillar on the bed, sometimes rising through the water's surface. */
    private static void submerged(Ctx c) {
        double r = c.spec.radius();
        double h = c.spec.height();
        if (c.r.chance(0.5)) {
            blob(c, 0, h * 0.3 - 0.5, 0, r, Math.max(1.0, h * 0.6), r * c.r.range(0.7, 1.0), c.spec.angle(), c.r.range(2.0, 3.0), 0.45, 0, false, 5);
        } else {
            for (int dy = -2; dy <= (int) h; dy++) {
                double t = Math.max(0.0, dy / Math.max(1.0, h));
                disc(c, 0, dy, 0, Math.max(0.6, r * (1.0 - 0.45 * t)), 0.5, 0, dy < 0 ? EMBED : 0);
            }
            footAll(c, 5);
        }
        markWet(c);
    }

    /** Flooded caves: an island in an underground lake, gravel and clay on its shore, moss on its top in lush caves. */
    private static void caveIslet(Ctx c) {
        double r = c.spec.radius();
        int level = c.spec.span();
        double above = c.spec.height();
        int ri = (int) Math.ceil(r + 1.5);
        int gravel = FormationBlocks.code(FormationBlocks.GRAVEL, 0);
        int clay = FormationBlocks.code(FormationBlocks.CLAY, 0);
        boolean mossy = c.spec.has(FormationSpec.MOSSY);
        for (int dz = -ri; dz <= ri; dz++) {
            for (int dx = -ri; dx <= ri; dx++) {
                double q = Math.hypot(dx, dz) / r + 0.4 * (c.lump(dx, 0, dz) - 0.5);
                if (q > 1.0) {
                    continue;
                }
                int bed = c.floor(dx, 0, dz, 4);
                if (bed == Integer.MIN_VALUE) {
                    continue;
                }
                int top = level + (int) Math.round(above * (1.0 - q * q));
                for (int dy = bed - 1; dy <= top; dy++) {
                    int code;
                    if (dy == top && dy > level && mossy) {
                        code = FormationBlocks.code(FormationBlocks.MOSS_BLOCK, 0);
                    } else if (q > 0.7 && dy <= level + 1) {
                        code = c.r.chance(0.3) ? clay : gravel;
                    } else {
                        code = c.rock(dx, dy, dz);
                    }
                    c.b.solid(dx, dy, dz, code | WET | (dy < bed ? EMBED : 0));
                }
            }
        }
    }

    /**
     * Monumental dripstone in a tall cave: a giant column where floor and ceiling are within its
     * reach, else a giant stalactite over a giant stalagmite; a flowstone skirt at the foot,
     * smaller spikes crowding around it.
     */
    private static void giantDripstone(Ctx c) {
        int span = c.spec.span();
        double r = c.spec.radius();
        boolean drip = c.spec.has(FormationSpec.DRIPSTONE);
        int body = drip ? FormationBlocks.code(FormationBlocks.DRIPSTONE, 0) : 0;
        if (span <= c.spec.height()) {
            column(c, r, false, 0.25, 0.5);
        } else {
            double k = c.r.range(0.9, 1.4);
            double down = Math.min(span * c.r.range(0.3, 0.55), c.spec.height() * 0.6);
            double up = Math.min(span - down - 4, c.spec.height() * c.r.range(0.35, 0.55));
            spike(c, 0, span - 1, 0, false, r * c.r.range(0.7, 1.0), down, k, c.r.range(-1.5, 1.5), c.r.range(-1.5, 1.5), body, drip, 10);
            if (up > 3) {
                spike(c, c.r.range(-1.0, 1.0), 0, c.r.range(-1.0, 1.0), true, r, up, k, c.r.range(-1.5, 1.5), c.r.range(-1.5, 1.5), body, drip, 10);
            }
        }
        // Flowstone skirt: a low wide cone draped at the foot.
        double skirt = r * c.r.range(1.4, 1.8);
        int ri = (int) Math.ceil(skirt + 1);
        for (int dz = -ri; dz <= ri; dz++) {
            for (int dx = -ri; dx <= ri; dx++) {
                double d = Math.hypot(dx, dz) / skirt + 0.3 * (c.lump(dx, 0, dz) - 0.5);
                if (d >= 1.0 || Math.abs(dx) > c.b.reach() || Math.abs(dz) > c.b.reach()) {
                    continue;
                }
                int h = (int) Math.round(2.5 * (1.0 - d));
                for (int dy = -1; dy <= h; dy++) {
                    c.b.solid(dx, dy, dz, (body != 0 ? body : c.rock(dx, dy, dz)) | (dy < 0 ? EMBED : 0));
                }
                c.b.foot(dx, dz, 6);
            }
        }
        int around = c.r.between(3, 7);
        for (int k = 0; k < around; k++) {
            double a = c.r.next() * Math.PI * 2;
            double d = skirt + c.r.range(1.0, 4.0);
            int ox = (int) Math.round(Math.cos(a) * d);
            int oz = (int) Math.round(Math.sin(a) * d);
            if (Math.abs(ox) > c.b.reach() - 2 || Math.abs(oz) > c.b.reach() - 2) {
                continue;
            }
            boolean up = c.r.chance(0.5);
            int base = up ? c.floor(ox, 0, oz, 8) : c.ceiling(ox, span - 1, oz, 8);
            if (base == Integer.MIN_VALUE) {
                continue;
            }
            double len = c.r.skewed(3.0, Math.min(16.0, span * 0.3), 1.6);
            spike(c, ox, base, oz, up, Math.max(0.8, len * c.r.range(0.12, 0.2)), len, c.r.range(0.9, 1.4), 0, 0, body, drip, 6);
        }
    }

    /** Stalagmites and stalactites of many sizes crowding a tall cave, each on the floor or ceiling of its own column. */
    private static void spikeField(Ctx c) {
        int span = c.spec.span();
        boolean drip = c.spec.has(FormationSpec.DRIPSTONE);
        int body = drip ? FormationBlocks.code(FormationBlocks.DRIPSTONE, 0) : 0;
        double radius = c.spec.radius();
        int n = c.r.between(7, 16);
        for (int k = 0; k < n; k++) {
            double a = c.r.next() * Math.PI * 2;
            double d = radius * Math.sqrt(c.r.next());
            int ox = (int) Math.round(Math.cos(a) * d);
            int oz = (int) Math.round(Math.sin(a) * d);
            if (Math.abs(ox) > c.b.reach() - 2 || Math.abs(oz) > c.b.reach() - 2) {
                continue;
            }
            boolean up = c.r.chance(0.45);
            int base = up ? c.floor(ox, 0, oz, 8) : c.ceiling(ox, span - 1, oz, 8);
            if (base == Integer.MIN_VALUE) {
                continue;
            }
            int room = up ? c.ceiling(ox, base, oz, span + 8) - base : base - c.floor(ox, base, oz, span + 8);
            if (room < 4) {
                continue;
            }
            double len = c.r.skewed(2.0, Math.min(c.spec.height(), room * 0.6), 1.8);
            spike(c, ox, base, oz, up, Math.max(0.6, len * c.r.range(0.1, 0.18)), len, c.r.range(0.8, 1.5), 0, 0, body, drip, 6);
        }
    }

    /** Every solid cell takes water where it stands in it (submerged formations). */
    private static void markWet(Ctx c) {
        int r = c.b.reach();
        int top = (int) Math.ceil(c.spec.height()) + 3;
        for (int dy = -3; dy <= top; dy++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    int code = c.b.get(dx, dy, dz);
                    if (FormationBlocks.isSolid(code)) {
                        c.b.force(dx, dy, dz, code | WET);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- other mods' biomes (Agent 7)

    /** Lumen mushroom cap forms. */
    private static final int CAP_DOME = 0;
    private static final int CAP_UMBRELLA = 1;
    private static final int CAP_PLATE = 2;
    private static final int CAP_BELL = 3;
    private static final int CAP_LOBED = 4;

    /** Cells of a planned part, kept apart from the builder until the whole part fits its cave. */
    private static final class Plan {
        final it.unimi.dsi.fastutil.ints.IntArrayList cells = new it.unimi.dsi.fastutil.ints.IntArrayList();
        final it.unimi.dsi.fastutil.ints.IntArrayList codes = new it.unimi.dsi.fastutil.ints.IntArrayList();
        final it.unimi.dsi.fastutil.ints.IntArrayList feet = new it.unimi.dsi.fastutil.ints.IntArrayList();

        void add(int dx, int dy, int dz, int code) {
            this.cells.add(FormationShape.pack(dx, dy, dz));
            this.codes.add(code);
        }

        void clear() {
            this.cells.clear();
            this.codes.clear();
            this.feet.clear();
        }

        /** True if every cell lies in open cave space within the reach (an EMBED cell may sit in rock). */
        boolean fits(Ctx c) {
            int reach = c.b.reach();
            for (int k = 0; k < this.cells.size(); k++) {
                int p = this.cells.getInt(k);
                int dx = FormationShape.dx(p);
                int dz = FormationShape.dz(p);
                if (Math.abs(dx) > reach || Math.abs(dz) > reach) {
                    return false;
                }
                if (!FormationBlocks.has(this.codes.getInt(k), EMBED) && !c.cave(dx, FormationShape.dy(p), dz)) {
                    return false;
                }
            }
            return true;
        }

        /** True if no cell, or its neighbour sideways or above, holds a solid block the formation already has (a stem, another cap). */
        boolean clearOf(Ctx c) {
            for (int k = 0; k < this.cells.size(); k++) {
                int p = this.cells.getInt(k);
                int dx = FormationShape.dx(p);
                int dy = FormationShape.dy(p);
                int dz = FormationShape.dz(p);
                if (c.b.isSolid(dx, dy, dz) || c.b.isSolid(dx + 1, dy, dz) || c.b.isSolid(dx - 1, dy, dz) || c.b.isSolid(dx, dy, dz + 1)
                        || c.b.isSolid(dx, dy, dz - 1) || c.b.isSolid(dx, dy + 1, dz)) {
                    return false;
                }
            }
            return true;
        }

        void commit(Ctx c) {
            for (int k = 0; k < this.cells.size(); k++) {
                int p = this.cells.getInt(k);
                int code = this.codes.getInt(k);
                if (FormationBlocks.isSolid(code)) {
                    c.b.solid(FormationShape.dx(p), FormationShape.dy(p), FormationShape.dz(p), code);
                } else {
                    c.b.decor(FormationShape.dx(p), FormationShape.dy(p), FormationShape.dz(p), code);
                }
            }
            for (int k = 0; k < this.feet.size(); k++) {
                int p = this.feet.getInt(k);
                c.b.foot(FormationShape.dx(p), FormationShape.dz(p), 4);
            }
        }
    }

    /**
     * A lumen mushroom (bioluminescent depths): a stem of lumen stem blocks rising from the floor,
     * leaning and curving a little, thicker under bigger caps (a single column, a plus, a 3 x 3,
     * a broad round trunk for giants) with a flared foot; a cap of lumen cap blocks with lumen gills
     * on its underside and bulbs hanging from a few gills. Five cap forms (dome, umbrella with a
     * skirt, flat plate, tall bell with its rim reaching down, lobed and lopsided), chosen from the
     * mushroom's height against its width, so tall narrow ones carry bells and domes and low wide
     * ones umbrellas and plates. The whole mushroom must lie in the open cave (cap included):
     * otherwise it is tried smaller, three times, and left out (never cut by the ceiling or a
     * wall). Smaller mature companions of its group lean away from it (clusters of two to four
     * of different heights), young mushrooms at the foot of the bigger ones, glowing moss and
     * sprouts around the stem.
     */
    private static void lumenMushroom(Ctx c, boolean giant) {
        double height = c.spec.height();
        double radius = c.spec.radius();
        double ratio = height / Math.max(1.0, radius);
        double u = c.r.next();
        int form;
        if (ratio > 2.2) {
            form = u < 0.45 ? CAP_BELL : (u < 0.8 ? CAP_DOME : CAP_LOBED);
        } else if (ratio < 1.25) {
            form = u < 0.45 ? CAP_UMBRELLA : (u < 0.78 ? CAP_PLATE : CAP_LOBED);
        } else {
            form = u < 0.32 ? CAP_DOME : (u < 0.58 ? CAP_UMBRELLA : (u < 0.8 ? CAP_LOBED : CAP_BELL));
        }
        double leanAngle = c.r.next() * Math.PI * 2;
        double lean = c.r.range(0.0, giant ? 0.16 : 0.24);
        double bend = c.r.range(1.2, 2.2);
        long capSeed = c.r.nextLong();
        Plan plan = new Plan();
        double h = height;
        double r = radius;
        boolean built = false;
        for (int attempt = 0; attempt < 4 && h >= 3.0 && r >= 1.2; attempt++) {
            plan.clear();
            mushroomPlan(c, plan, 0, 0, 0, h, r, form, Math.cos(leanAngle) * lean * h, Math.sin(leanAngle) * lean * h, bend, capSeed, true);
            if (plan.fits(c)) {
                plan.commit(c);
                built = true;
                break;
            }
            h *= 0.8;
            r *= 0.8;
        }
        if (!built) {
            return;
        }
        // Companions: smaller mature mushrooms of its group, leaning away from it, so lumen
        // mushrooms stand in clusters of different heights; each only where it fits whole.
        int companions = c.r.between(giant ? 1 : 0, giant ? 3 : 2);
        for (int k = 0; k < companions; k++) {
            double a = c.r.next() * Math.PI * 2;
            double ch = h * c.r.range(giant ? 0.3 : 0.45, giant ? 0.6 : 0.75);
            double cr = Math.max(1.2, Math.min(r * 0.8, ch * c.r.range(0.35, 0.9)));
            double d = r + cr * c.r.range(0.4, 0.9) + 1.0;
            int ox = (int) Math.round(Math.cos(a) * d);
            int oz = (int) Math.round(Math.sin(a) * d);
            int base = c.floor(ox, 0, oz, 4);
            double outward = c.r.range(0.08, 0.22) * ch;
            long seed = c.r.nextLong();
            if (base == Integer.MIN_VALUE || ch < 3.0) {
                continue;
            }
            double cratio = ch / cr;
            int cform = cratio > 2.2 ? (c.r.chance(0.5) ? CAP_BELL : CAP_DOME) : (cratio < 1.25 ? (c.r.chance(0.55) ? CAP_UMBRELLA : CAP_PLATE) : CAP_DOME);
            plan.clear();
            mushroomPlan(c, plan, ox, base, oz, ch, cr, cform, Math.cos(a) * outward, Math.sin(a) * outward, 1.6, seed, true);
            if (plan.fits(c) && plan.clearOf(c)) {
                plan.commit(c);
            }
        }
        // Young mushrooms at its foot, out of the stem's way, each only where it fits.
        int young = c.r.between(giant ? 3 : 1, giant ? 7 : 4);
        for (int k = 0; k < young; k++) {
            double a = c.r.next() * Math.PI * 2;
            double d = c.r.range(Math.max(2.0, r * 0.45), Math.max(3.0, r * 1.25));
            int ox = (int) Math.round(Math.cos(a) * d);
            int oz = (int) Math.round(Math.sin(a) * d);
            int base = c.floor(ox, 0, oz, 4);
            if (base == Integer.MIN_VALUE) {
                continue;
            }
            double yh = c.r.range(2.0, Math.min(5.5, Math.max(2.5, h * 0.4)));
            plan.clear();
            mushroomPlan(c, plan, ox, base, oz, yh, c.r.range(1.0, 2.2), c.r.chance(0.6) ? CAP_DOME : CAP_UMBRELLA, c.r.range(-0.6, 0.6),
                    c.r.range(-0.6, 0.6), 1.5, c.r.nextLong(), false);
            if (plan.fits(c) && plan.clearOf(c)) {
                plan.commit(c);
            }
        }
        lumenFloor(c, Math.max(2.5, r * 0.75), 0.75, 0.22);
    }

    /**
     * The cells of one lumen mushroom into a plan: stem from (bx, by, bz) up to the cap's
     * underside {@code height} blocks up, its top offset by (topX, topZ) along a curve
     * (exponent {@code bend}), the cap of radius {@code radius} and form {@code form}.
     */
    private static void mushroomPlan(Ctx c, Plan plan, int bx, int by, int bz, double height, double radius, int form, double topX, double topZ,
                                     double bend, long seed, boolean flare) {
        int stem = FormationBlocks.mod(ModBlock.LUMEN_STEM, 0);
        int cap = FormationBlocks.mod(ModBlock.LUMEN_CAP);
        int gills = FormationBlocks.mod(ModBlock.LUMEN_GILLS);
        int bulb = FormationBlocks.mod(ModBlock.LUMEN_BULB);
        int top = Math.max(2, (int) Math.round(height));
        double sr = radius < 3.2 ? 0.0 : (radius < 5.5 ? 0.6 : (radius < 8.5 ? 1.05 : 1.6));
        NatureRandom r = new NatureRandom(seed);
        double cx = bx;
        double cz = bz;
        for (int dy = flare ? -1 : 0; dy < top; dy++) {
            double t = Math.max(0, dy) / (double) top;
            int lastX = (int) Math.round(cx);
            int lastZ = (int) Math.round(cz);
            cx = bx + topX * Math.pow(t, bend);
            cz = bz + topZ * Math.pow(t, bend);
            if (sr == 0.0) {
                int sx = (int) Math.round(cx);
                int sz = (int) Math.round(cz);
                if (dy > 0 && (sx != lastX || sz != lastZ)) {
                    // A thin stem stepping aside stays joined face to face (a diagonal step by way
                    // of the column beside both).
                    if (sx != lastX && sz != lastZ) {
                        plan.add(sx, by + dy - 1, lastZ, stem);
                    }
                    plan.add(sx, by + dy - 1, sz, stem);
                }
                // Its own cell at every height: the disc below can miss it when the curve runs between cells.
                plan.add(sx, by + dy, sz, dy < 0 ? stem | EMBED : stem);
            }
            // A flared foot sunk one block into the floor.
            double rr = sr + (flare && dy <= 1 && sr > 0.0 ? 0.8 - 0.35 * Math.max(0, dy) : 0.0) + (flare && dy <= 0 && sr == 0.0 ? 0.6 : 0.0);
            int ri = (int) Math.ceil(rr + 0.5);
            for (int dz = -ri; dz <= ri; dz++) {
                for (int dx = -ri; dx <= ri; dx++) {
                    int x = (int) Math.round(cx) + dx;
                    int z = (int) Math.round(cz) + dz;
                    if (Math.hypot(x - cx, z - cz) > rr + 0.5) {
                        continue;
                    }
                    plan.add(x, by + dy, z, dy < 0 ? stem | EMBED : stem);
                    if (dy == 0) {
                        plan.feet.add(FormationShape.pack(x, 0, z));
                    }
                }
            }
        }
        // The cap: an outline that wanders (lobes for the lopsided form), a profile by form.
        int y0 = by + top;
        double ccx = cx;
        double ccz = cz;
        double lobePhase = r.next() * Math.PI * 2;
        int lobes = r.between(2, 3);
        if (form == CAP_LOBED) {
            double a = r.next() * Math.PI * 2;
            ccx += Math.cos(a) * radius * 0.22;
            ccz += Math.sin(a) * radius * 0.22;
        }
        double capH = switch (form) {
            case CAP_DOME -> Math.max(1.6, radius * 0.55);
            case CAP_UMBRELLA -> Math.max(1.2, radius * 0.3);
            case CAP_PLATE -> 1.0;
            case CAP_BELL -> Math.max(2.0, radius * 1.05);
            default -> Math.max(1.4, radius * 0.42);
        };
        double skirt = form == CAP_BELL ? radius * 0.65 : (form == CAP_UMBRELLA ? 1.0 : 0.0);
        int ri = (int) Math.ceil(radius * 1.3 + 1);
        for (int dz = -ri; dz <= ri; dz++) {
            for (int dx = -ri; dx <= ri; dx++) {
                double ex = bx + dx + (Math.round(ccx) - bx) - ccx;
                double ez = bz + dz + (Math.round(ccz) - bz) - ccz;
                double angle = Math.atan2(ez, ex);
                double outline = radius * (1.0 + 0.08 * (r.next() - 0.5));
                if (form == CAP_LOBED) {
                    outline *= 0.8 + 0.3 * (0.5 + 0.5 * Math.cos(lobes * angle + lobePhase));
                }
                double q = Math.hypot(ex, ez) / outline;
                if (q > 1.0) {
                    continue;
                }
                int x = (int) Math.round(ccx) + dx;
                int z = (int) Math.round(ccz) + dz;
                int lower = switch (form) {
                    case CAP_BELL -> y0 - (int) Math.round(skirt * Math.pow(q, 3.0));
                    case CAP_UMBRELLA -> y0 - (q > 0.8 ? (int) Math.round(skirt) : 0);
                    case CAP_DOME -> y0 - (q > 0.75 ? 1 : 0);
                    default -> y0;
                };
                double rise = switch (form) {
                    case CAP_DOME, CAP_LOBED -> capH * Math.sqrt(Math.max(0.0, 1.0 - q * q));
                    case CAP_BELL -> capH * Math.sqrt(Math.max(0.0, 1.0 - Math.pow(q, 1.6)));
                    case CAP_UMBRELLA -> capH * (1.0 - q) + 0.6;
                    default -> q < 0.6 ? 1.0 : 0.4;
                };
                int upper = y0 + (int) Math.round(rise);
                for (int y = lower; y <= Math.max(lower, upper); y++) {
                    boolean underside = y == lower && q < 0.9;
                    plan.add(x, y, z, underside ? gills : cap);
                }
                if (lower <= y0 && q > 0.3 && q < 0.85 && r.chance(0.07)) {
                    plan.add(x, lower - 1, z, bulb);
                }
            }
        }
    }

    /** Glowing moss on the floor around a point (snapped: the floor block becomes moss), sprouts on part of it. */
    private static void lumenFloor(Ctx c, double radius, double cover, double sprouts) {
        int moss = FormationBlocks.mod(ModBlock.LUMEN_MOSS);
        int sprout = FormationBlocks.mod(ModBlock.LUMEN_SPROUTS);
        int ri = (int) Math.ceil(radius + 1);
        for (int dz = -ri; dz <= ri; dz++) {
            for (int dx = -ri; dx <= ri; dx++) {
                double d = Math.hypot(dx, dz) / radius + 0.45 * (c.lump(dx * 1.4, 0, dz * 1.4) - 0.5);
                if (d > 1.0 || c.b.isSolid(dx, 0, dz) || c.b.isSolid(dx, -1, dz) || c.r.next() > cover) {
                    continue;
                }
                floorPlant(c, dx, 0, dz, moss, c.r.chance(sprouts) ? sprout : 0);
            }
        }
    }

    /** Bioluminescent depths: a colony of young lumen mushrooms on moss. */
    private static void lumenColony(Ctx c) {
        double radius = c.spec.radius();
        int n = c.r.between(3, 7);
        Plan plan = new Plan();
        for (int k = 0; k < n; k++) {
            double a = c.r.next() * Math.PI * 2;
            double d = k == 0 ? 0.0 : radius * Math.sqrt(c.r.next());
            int ox = (int) Math.round(Math.cos(a) * d);
            int oz = (int) Math.round(Math.sin(a) * d);
            int base = c.floor(ox, 0, oz, 3);
            if (base == Integer.MIN_VALUE) {
                continue;
            }
            double h = c.r.range(2.0, Math.max(2.2, c.spec.height()));
            plan.clear();
            mushroomPlan(c, plan, ox, base, oz, h, c.r.range(0.8, 2.0), c.r.chance(0.55) ? CAP_DOME : (c.r.chance(0.6) ? CAP_UMBRELLA : CAP_BELL),
                    c.r.range(-0.7, 0.7), c.r.range(-0.7, 0.7), 1.4, c.r.nextLong(), false);
            if (plan.fits(c)) {
                plan.commit(c);
            }
        }
        lumenFloor(c, radius + 1.0, 0.7, 0.3);
    }

    /**
     * Bioluminescent depths: moss and sprouts on the floor, in the thick of a grove a small
     * mushroom of the understory in the middle (its height in the spec, 0 for none; built only
     * where it fits whole); on a ceiling up to 20 blocks above a patch of moss with bulbs hanging
     * from it.
     */
    private static void lumenGrowth(Ctx c) {
        double radius = c.spec.radius();
        if (c.spec.height() >= 2.0) {
            double h = c.spec.height();
            double r = Math.max(0.8, Math.min(2.0, h * c.r.range(0.35, 0.6)));
            Plan plan = new Plan();
            mushroomPlan(c, plan, 0, 0, 0, h, r, c.r.chance(0.6) ? CAP_DOME : (c.r.chance(0.5) ? CAP_UMBRELLA : CAP_BELL), c.r.range(-0.5, 0.5),
                    c.r.range(-0.5, 0.5), 1.4, c.r.nextLong(), true);
            if (plan.fits(c)) {
                plan.commit(c);
            }
        }
        lumenFloor(c, radius, 0.85, 0.35);
        int span = c.spec.span();
        if (span > 20) {
            return;
        }
        int moss = FormationBlocks.mod(ModBlock.LUMEN_MOSS);
        int bulb = FormationBlocks.mod(ModBlock.LUMEN_BULB);
        int ri = (int) Math.ceil(radius);
        for (int dz = -ri; dz <= ri; dz++) {
            for (int dx = -ri; dx <= ri; dx++) {
                if (Math.hypot(dx, dz) / radius + 0.5 * (c.lump(dx * 1.3, 7, dz * 1.3) - 0.5) > 0.9) {
                    continue;
                }
                c.b.decor(dx, span, dz, moss | SNAP_CEILING);
                if (c.r.chance(0.16)) {
                    c.b.decor(dx, span - 1, dz, bulb | SNAP_CEILING);
                }
            }
        }
    }

    /**
     * A pool dug into a level stretch of cave floor: a lumen pond (water over clay, sea pickles,
     * moss around) or a spinel pool (lavender spinel in a smooth basalt basin, amethyst buds at its
     * rim). Its cells are only the columns of the root's floor height whose neighbours hold rock
     * at the water's level (or more pool), checked against the caves, so the pool cannot spill; a
     * second layer in the middle where the rock under it and beside it holds. Writing only ever
     * replaces natural ground (never air), so the pool keeps that promise in the world.
     */
    private static void pool(Ctx c) {
        double radius = c.spec.radius();
        boolean spinel = c.spec.has(FormationSpec.SPINEL);
        int ri = (int) Math.ceil(radius + 1);
        int side = 2 * ri + 1;
        boolean[] in = new boolean[side * side];
        int count = 0;
        for (int dz = -ri; dz <= ri; dz++) {
            for (int dx = -ri; dx <= ri; dx++) {
                double q = Math.hypot(dx, dz) / radius + 0.4 * (c.lump(dx, 0, dz) - 0.5);
                if (q <= 1.0 && c.cave(dx, 0, dz) && !c.cave(dx, -1, dz) && !c.cave(dx, -2, dz)) {
                    in[(dz + ri) * side + dx + ri] = true;
                    count++;
                }
            }
        }
        int[][] n4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        boolean changed = true;
        while (changed && count > 0) {
            changed = false;
            for (int dz = -ri; dz <= ri; dz++) {
                for (int dx = -ri; dx <= ri; dx++) {
                    if (!in[(dz + ri) * side + dx + ri]) {
                        continue;
                    }
                    for (int[] n : n4) {
                        int nx = dx + n[0];
                        int nz = dz + n[1];
                        boolean neighbour = Math.abs(nx) <= ri && Math.abs(nz) <= ri && in[(nz + ri) * side + nx + ri];
                        if (!neighbour && c.cave(nx, -1, nz)) {
                            in[(dz + ri) * side + dx + ri] = false;
                            count--;
                            changed = true;
                            break;
                        }
                    }
                }
            }
        }
        if (count < 5) {
            return;
        }
        int depth = (int) Math.round(c.spec.height());
        int fluid = FormationBlocks.code(FormationBlocks.POOL, spinel ? 1 : 0);
        int bed = spinel ? FormationBlocks.code(FormationBlocks.SMOOTH_BASALT, 0) : FormationBlocks.code(FormationBlocks.CLAY, 0);
        for (int dz = -ri; dz <= ri; dz++) {
            for (int dx = -ri; dx <= ri; dx++) {
                if (!in[(dz + ri) * side + dx + ri]) {
                    continue;
                }
                boolean interior = true;
                for (int[] n : n4) {
                    int nx = dx + n[0];
                    int nz = dz + n[1];
                    interior &= Math.abs(nx) <= ri && Math.abs(nz) <= ri && in[(nz + ri) * side + nx + ri] && !c.cave(nx, -2, nz);
                }
                boolean deep = depth >= 2 && interior && !c.cave(dx, -3, dz);
                int lowest = deep ? -2 : -1;
                for (int dy = -1; dy >= lowest; dy--) {
                    int code = fluid;
                    if (!spinel && dy == lowest && c.r.chance(0.18)) {
                        // A sea pickle on the bed (a water source with pickles: the pool stays whole).
                        code = FormationBlocks.code(FormationBlocks.POOL, c.r.between(1, 4) << 1);
                    }
                    c.b.force(dx, dy, dz, code | EMBED);
                }
                c.b.force(dx, lowest - 1, dz, bed | EMBED);
            }
        }
        // The rim: basalt round spinel, moss and clay round a lumen pond; buds or sprouts on it.
        for (int dz = -ri - 1; dz <= ri + 1; dz++) {
            for (int dx = -ri - 1; dx <= ri + 1; dx++) {
                boolean inside = Math.abs(dx) <= ri && Math.abs(dz) <= ri && in[(dz + ri) * side + dx + ri];
                if (inside) {
                    continue;
                }
                boolean rim = false;
                for (int[] n : n4) {
                    int nx = dx + n[0];
                    int nz = dz + n[1];
                    rim |= Math.abs(nx) <= ri && Math.abs(nz) <= ri && in[(nz + ri) * side + nx + ri];
                }
                if (!rim || !c.cave(dx, 0, dz)) {
                    continue;
                }
                if (spinel) {
                    c.b.force(dx, -1, dz, FormationBlocks.code(FormationBlocks.SMOOTH_BASALT, 0) | EMBED);
                    if (c.r.chance(0.3)) {
                        c.b.decor(dx, 0, dz, FormationBlocks.code(FormationBlocks.AMETHYST, 1 | c.r.between(0, 3) << 3));
                    }
                } else {
                    int ground = c.r.chance(0.6) ? FormationBlocks.mod(ModBlock.LUMEN_MOSS) : FormationBlocks.code(FormationBlocks.CLAY, 0);
                    c.b.force(dx, -1, dz, ground | EMBED);
                    if (c.r.chance(0.3)) {
                        c.b.decor(dx, 0, dz, FormationBlocks.mod(ModBlock.LUMEN_SPROUTS));
                    }
                }
            }
        }
        if (!spinel) {
            lumenFloor(c, radius + 3.0, 0.4, 0.3);
        }
    }

    /**
     * Amethyst caves: a crystal garden: amethyst spikes of many lengths (the mod's spike blocks,
     * base to tip) standing on the floor or hanging from the ceiling, on amethyst and glowing
     * amethyst set into the rock, with buds between them.
     */
    private static void crystalGarden(Ctx c) {
        boolean ceiling = c.spec.has(FormationSpec.CEILING);
        int span = c.spec.span();
        double radius = c.spec.radius();
        int n = c.r.between(4, 10);
        int keep = FormationBlocks.code(FormationBlocks.KEEP, 0);
        int amethyst = FormationBlocks.code(FormationBlocks.AMETHYST_BLOCK, 0);
        int glowing = FormationBlocks.mod(ModBlock.GLOWING_AMETHYST);
        int snap = ceiling ? SNAP_CEILING : SNAP_FLOOR;
        int anchorY = ceiling ? span : -1;
        int dir = ceiling ? -1 : 1;
        for (int k = 0; k < n; k++) {
            double a = c.r.next() * Math.PI * 2;
            double d = k == 0 ? 0.0 : radius * Math.sqrt(c.r.next());
            int dx = (int) Math.round(Math.cos(a) * d);
            int dz = (int) Math.round(Math.sin(a) * d);
            if (!c.b.isEmpty(dx, anchorY + dir, dz)) {
                continue;
            }
            int length = Math.max(1, Math.min((int) Math.round(c.r.skewed(1.0, Math.max(1.0, c.spec.height()), 1.6)), Math.max(1, span / 2)));
            double u = c.r.next();
            c.b.decor(dx, anchorY, dz, (u < 0.45 ? amethyst : (u < 0.62 ? glowing : keep)) | snap);
            for (int i = 0; i < length; i++) {
                c.b.decor(dx, anchorY + dir * (i + 1), dz, FormationBlocks.mod(ModBlock.AMETHYST_SPIKE, NatureBlocks.spikeState(i, length, !ceiling)) | snap);
            }
        }
        // Buds and clusters on the rock between them.
        int ri = (int) Math.ceil(radius + 1);
        for (int dz = -ri; dz <= ri; dz++) {
            for (int dx = -ri; dx <= ri; dx++) {
                if (Math.hypot(dx, dz) > radius + 1 || !c.b.isEmpty(dx, anchorY + dir, dz) || !c.r.chance(0.2)) {
                    continue;
                }
                c.b.decor(dx, anchorY, dz, keep | snap);
                c.b.decor(dx, anchorY + dir, dz, FormationBlocks.code(FormationBlocks.AMETHYST, (ceiling ? 0 : 1) | c.r.between(0, 3) << 3) | snap);
            }
        }
    }

    /**
     * A crystal: a cone of amethyst rock (amethyst, calcite, glowing amethyst) from a base on the
     * floor or ceiling, leaning by (leanX, leanZ) at its tip, ending in amethyst spike segments.
     * Its axis is checked against the cave and the crystal shortened where it would run into rock.
     */
    private static void crystal(Ctx c, double bx, int by, double bz, boolean up, double r0, double length, double leanX, double leanZ) {
        int dir = up ? 1 : -1;
        // Shorten to the open cave along the axis.
        int n = Math.max(2, (int) Math.round(length));
        for (int i = 1; i <= n; i++) {
            double t = i / (double) n;
            int ax = (int) Math.round(bx + leanX * t);
            int az = (int) Math.round(bz + leanZ * t);
            if (!c.cave(ax, by + dir * i, az) || !c.cave(ax, by + dir * (i + 1), az)) {
                n = Math.max(1, i - 2);
                leanX *= n / length;
                leanZ *= n / length;
                break;
            }
        }
        if (n < 2) {
            return;
        }
        int lastX = (int) Math.round(bx);
        int lastZ = (int) Math.round(bz);
        int lastI = 0;
        for (int i = -1; i < n; i++) {
            double t = Math.max(0.0, i) / n;
            double rr = r0 * (1.0 - 0.85 * t);
            double cx = bx + leanX * t;
            double cz = bz + leanZ * t;
            int icx = (int) Math.round(cx);
            int icz = (int) Math.round(cz);
            if (i > 0 && (icx != lastX || icz != lastZ)) {
                c.b.solid(icx, by + dir * (i - 1), icz, c.rock(icx, by + dir * (i - 1), icz));
            }
            disc(c, cx, by + dir * i, cz, Math.max(0.45, rr), 0.15, 0, i < 0 ? EMBED : 0);
            if (i <= 0) {
                int rb = (int) Math.ceil(rr + 1.0);
                for (int dz = icz - rb; dz <= icz + rb; dz++) {
                    for (int dx = icx - rb; dx <= icx + rb; dx++) {
                        if (c.b.isSolid(dx, by, dz)) {
                            if (up) {
                                c.b.foot(dx, dz, 5);
                            } else {
                                c.b.hang(dx, dz, 5);
                            }
                        }
                    }
                }
            }
            lastX = icx;
            lastZ = icz;
            lastI = i;
            if (rr < 0.5) {
                break;
            }
        }
        int tip = 0;
        while (tip < Math.max(1, Math.min(3, (int) Math.round(r0))) && c.cave(lastX, by + dir * (lastI + 1 + tip), lastZ)) {
            tip++;
        }
        for (int k = 0; k < tip; k++) {
            c.b.decor(lastX, by + dir * (lastI + 1 + k), lastZ, FormationBlocks.mod(ModBlock.AMETHYST_SPIKE, NatureBlocks.spikeState(k, tip, up)));
        }
    }

    /**
     * Amethyst caves: a group of large crystals leaning out from a common root on the floor or the
     * ceiling, each its own length and angle, over a geode-like collar of calcite and smooth basalt
     * with buds.
     */
    private static void crystalSpires(Ctx c) {
        boolean ceiling = c.spec.has(FormationSpec.CEILING);
        int span = c.spec.span();
        int by = ceiling ? span - 1 : 0;
        double radius = c.spec.radius();
        int n = c.r.between(2, 6);
        for (int k = 0; k < n; k++) {
            double a = c.r.next() * Math.PI * 2;
            double d = k == 0 ? 0.0 : c.r.range(0.5, radius * 0.45);
            double bx = Math.cos(a) * d;
            double bz = Math.sin(a) * d;
            int base = ceiling ? c.ceiling((int) Math.round(bx), by, (int) Math.round(bz), 5) : c.floor((int) Math.round(bx), by, (int) Math.round(bz), 5);
            if (base == Integer.MIN_VALUE) {
                continue;
            }
            double length = c.spec.height() * (k == 0 ? 1.0 : c.r.range(0.35, 0.8));
            double r0 = Math.max(0.7, Math.min(2.4, length * c.r.range(0.12, 0.2)));
            double tilt = c.r.range(0.15, 0.6) * (k == 0 ? 0.5 : 1.0);
            double out = Math.min(radius * 0.8, length * tilt);
            crystal(c, bx, base, bz, !ceiling, r0, length, Math.cos(a) * out, Math.sin(a) * out);
        }
        geodeCollar(c, radius * 0.7, ceiling ? span : -1, ceiling);
    }

    /** Calcite and smooth basalt set into the floor (ceiling) around a crystal group, buds on it. */
    private static void geodeCollar(Ctx c, double radius, int anchorY, boolean ceiling) {
        int snap = ceiling ? SNAP_CEILING : SNAP_FLOOR;
        int dir = ceiling ? -1 : 1;
        int ri = (int) Math.ceil(radius + 1);
        for (int dz = -ri; dz <= ri; dz++) {
            for (int dx = -ri; dx <= ri; dx++) {
                double q = Math.hypot(dx, dz) / radius + 0.35 * (c.lump(dx, 3, dz) - 0.5);
                if (q > 1.0 || !c.b.isEmpty(dx, anchorY + dir, dz) || !c.b.isEmpty(dx, anchorY, dz)) {
                    continue;
                }
                int ground = q < 0.6 ? FormationBlocks.code(FormationBlocks.CALCITE, 0) : FormationBlocks.code(FormationBlocks.SMOOTH_BASALT, 0);
                c.b.decor(dx, anchorY, dz, ground | snap);
                if (c.r.chance(0.18)) {
                    c.b.decor(dx, anchorY + dir, dz, FormationBlocks.code(FormationBlocks.AMETHYST, (ceiling ? 0 : 1) | c.r.between(0, 3) << 3) | snap);
                }
            }
        }
    }

    /**
     * Amethyst caves: a giant crystal of a megacave: a broad faceted shaft of amethyst leaning out of
     * the floor tens of blocks long, glowing in patches, with smaller crystals round its foot and a
     * wide geode collar.
     */
    private static void giantCrystal(Ctx c) {
        double length = c.spec.height();
        double r0 = c.spec.radius();
        double a = c.r.next() * Math.PI * 2;
        double out = Math.min(c.b.reach() - r0 - 2.0, length * c.r.range(0.15, 0.4));
        crystal(c, 0, 0, 0, true, r0, length, Math.cos(a) * out, Math.sin(a) * out);
        int around = c.r.between(2, 5);
        for (int k = 0; k < around; k++) {
            double b = c.r.next() * Math.PI * 2;
            double d = r0 + c.r.range(1.5, 4.0);
            int ox = (int) Math.round(Math.cos(b) * d);
            int oz = (int) Math.round(Math.sin(b) * d);
            int base = c.floor(ox, 0, oz, 6);
            if (base == Integer.MIN_VALUE) {
                continue;
            }
            double len = c.r.range(4.0, Math.max(5.0, length * 0.3));
            double tilt = c.r.range(0.2, 0.7);
            crystal(c, ox, base, oz, true, Math.max(0.8, len * 0.14), len, Math.cos(b) * len * tilt * 0.6, Math.sin(b) * len * tilt * 0.6);
        }
        geodeCollar(c, r0 + 4.0, -1, false);
    }

    /**
     * Smoldering caves: a vent: a low cone of magma-veined rock with a crater at its top: a pit two
     * blocks deep over a floor of magma, the mod's smoldering magma glowing in the pit's walls under
     * the rim. Smoldering magma erupts by itself only when open above (an explosion, then lava), so
     * it is never placed open above: a vent stays quiet until a player breaks its rim. Hot ground
     * around it.
     */
    private static void magmaVent(Ctx c) {
        double radius = c.spec.radius();
        int h = Math.max(2, (int) Math.round(Math.max(2.0, c.spec.height())));
        int pit = radius >= 3.2 ? 1 : 0;
        cone(c, 0, 0, 0, h, radius, pit + 1.4);
        footAll(c, 4);
        crater(c, 0, h, 0, pit);
        hotGround(c, radius + 2.5, 0.55);
    }

    /**
     * A cone of rock standing on a floor (the first open layer {@code floor}, its foot sunk one block
     * into the ground) up to {@code top}, its radius narrowing to {@code topRadius}.
     */
    private static void cone(Ctx c, int x, int floor, int z, int top, double radius, double topRadius) {
        for (int dy = floor - 1; dy <= top; dy++) {
            double t = Math.max(0.0, dy - floor) / Math.max(1.0, top - floor);
            double rr = Math.max(topRadius, radius - (radius - topRadius) * t);
            disc(c, x, dy, z, rr, 0.5, 0, dy < floor ? EMBED : 0);
        }
    }

    /**
     * The crater of a vent whose top layer is {@code top}: a pit of radius {@code pit} (0: one
     * column, 1: three by three) two blocks deep, magma at its floor, smoldering magma (or magma) in
     * the cells of its walls one below the rim, each under a rim block, so none is open above.
     */
    private static void crater(Ctx c, int x, int top, int z, int pit) {
        int magma = FormationBlocks.code(FormationBlocks.MAGMA, 0);
        int smoldering = FormationBlocks.mod(ModBlock.SMOLDERING_MAGMA);
        for (int dz = -pit; dz <= pit; dz++) {
            for (int dx = -pit; dx <= pit; dx++) {
                c.b.remove(x + dx, top, z + dz);
                c.b.remove(x + dx, top - 1, z + dz);
                c.b.force(x + dx, top - 2, z + dz, magma);
            }
        }
        int placed = 0;
        for (int dz = -pit - 1; dz <= pit + 1; dz++) {
            for (int dx = -pit - 1; dx <= pit + 1; dx++) {
                boolean wall = (Math.abs(dx) == pit + 1) != (Math.abs(dz) == pit + 1);
                if (!wall || !c.b.isSolid(x + dx, top, z + dz) || !c.b.isSolid(x + dx, top - 1, z + dz)) {
                    continue;
                }
                boolean glow = placed == 0 || c.r.chance(0.55);
                c.b.force(x + dx, top - 1, z + dz, glow ? smoldering : magma);
                placed += glow ? 1 : 0;
            }
        }
    }

    /** Magma, blackstone and basalt set into the floor around a point (snapped), thinning to the edge. */
    private static void hotGround(Ctx c, double radius, double share) {
        int ri = (int) Math.ceil(radius + 1);
        for (int dz = -ri; dz <= ri; dz++) {
            for (int dx = -ri; dx <= ri; dx++) {
                double q = Math.hypot(dx, dz) / radius + 0.4 * (c.lump(dx * 1.2, 5, dz * 1.2) - 0.5);
                if (q > 1.0 || c.b.isSolid(dx, 0, dz) || c.b.isSolid(dx, -1, dz) || c.r.next() > share * (1.2 - q)) {
                    continue;
                }
                double v = c.lump(dx * 2.0, 9, dz * 2.0);
                int ground = q < 0.55 && v < 0.55 ? FormationBlocks.code(FormationBlocks.MAGMA, 0)
                        : (v < 0.7 ? FormationBlocks.code(FormationBlocks.BLACKSTONE, 0) : FormationBlocks.code(FormationBlocks.BASALT, 0));
                c.b.decor(dx, -1, dz, ground | SNAP_FLOOR);
            }
        }
    }

    /**
     * Smoldering caves: a geothermal field in a great hall: a broad floor of magma and hot rock,
     * vents of several sizes, magma-veined pillars where the vault is low enough.
     */
    private static void geothermalField(Ctx c) {
        double radius = c.spec.radius();
        int span = c.spec.span();
        hotGround(c, radius, 0.85);
        int vents = c.r.between(2, 4);
        for (int k = 0; k < vents; k++) {
            double a = c.r.next() * Math.PI * 2;
            double d = c.r.range(0.0, radius * 0.7);
            int ox = (int) Math.round(Math.cos(a) * d);
            int oz = (int) Math.round(Math.sin(a) * d);
            int base = c.floor(ox, 0, oz, 5);
            if (base == Integer.MIN_VALUE) {
                continue;
            }
            double vr = c.r.range(2.0, 3.6);
            int vh = Math.max(2, (int) Math.round(c.r.range(2.0, Math.min(6.0, Math.max(2.0, c.spec.height())))));
            int pit = vr >= 3.2 ? 1 : 0;
            cone(c, ox, base, oz, base + vh, vr, pit + 1.4);
            crater(c, ox, base + vh, oz, pit);
            for (int dz = -4; dz <= 4; dz++) {
                for (int dx = -4; dx <= 4; dx++) {
                    if (c.b.isSolid(ox + dx, base, oz + dz)) {
                        c.b.foot(ox + dx, oz + dz, 4);
                    }
                }
            }
        }
        if (span <= 48) {
            int pillars = c.r.between(1, 3);
            for (int k = 0; k < pillars; k++) {
                double a = c.r.next() * Math.PI * 2;
                double d = c.r.range(radius * 0.4, radius * 0.9);
                int ox = (int) Math.round(Math.cos(a) * d);
                int oz = (int) Math.round(Math.sin(a) * d);
                int base = c.floor(ox, 0, oz, 5);
                if (base == Integer.MIN_VALUE) {
                    continue;
                }
                int top = c.ceiling(ox, base, oz, span + 8);
                if (top == Integer.MIN_VALUE || top - base < 6) {
                    continue;
                }
                double pr = c.r.range(1.4, 2.6);
                for (int dy = base - 1; dy <= top + 1; dy++) {
                    double t = (dy - base) / (double) Math.max(1, top - base);
                    double rr = pr * (1.0 - 0.35 * Math.sin(Math.PI * Math.max(0.0, Math.min(1.0, t))));
                    disc(c, ox, dy, oz, rr, 0.5, 0, dy < base || dy > top ? EMBED : 0);
                }
            }
        }
    }

    /**
     * Ashen wastes: a volcanic fissure: a crack in the ground one to three blocks wide and a few deep,
     * tapering at both ends, its lips of blackstone and basalt, magma glowing at its bottom, and ash
     * vents (Sincerelyuq's Biomes' block: hot ash, damage) on a few spots of its lips.
     */
    private static void fissure(Ctx c) {
        double width = c.spec.span();
        double half = Math.min(c.spec.radius() * 0.6, c.b.reach() - width * 0.5 - 3.0);
        double depth = c.spec.height();
        double cos = Math.cos(c.spec.angle());
        double sin = Math.sin(c.spec.angle());
        int cut = FormationBlocks.code(FormationBlocks.CUT, 0);
        int magma = FormationBlocks.code(FormationBlocks.MAGMA, 0);
        int black = FormationBlocks.code(FormationBlocks.BLACKSTONE, 0);
        int basalt = FormationBlocks.code(FormationBlocks.BASALT, 0);
        double wobble = c.r.next() * 10.0;
        int ri = (int) Math.ceil(half + width + 2);
        for (int dz = -ri; dz <= ri; dz++) {
            for (int dx = -ri; dx <= ri; dx++) {
                double along = dx * cos + dz * sin;
                double across = -dx * sin + dz * cos - 1.2 * Math.sin(along * 0.35 + wobble);
                double t = Math.abs(along) / half;
                if (t > 1.0) {
                    continue;
                }
                double w = width * 0.5 * (1.0 - t * t) + 0.3;
                double a = Math.abs(across);
                int ground = c.ground(dx, dz);
                if (a <= w) {
                    int d = Math.max(1, (int) Math.round(depth * (1.0 - t * t) * (1.0 - 0.4 * a / Math.max(0.5, w))));
                    for (int k = 0; k < d; k++) {
                        c.b.force(dx, ground - k, dz, cut);
                    }
                    c.b.force(dx, ground - d, dz, (d >= 2 && t < 0.7 ? magma : black) | EMBED);
                } else if (a <= w + 1.3) {
                    c.b.force(dx, ground, dz, (c.lump(dx, 0, dz) < 0.5 ? black : basalt) | EMBED);
                }
            }
        }
        int vents = c.spec.span() >= 2 ? c.r.between(1, 3) : c.r.between(0, 1);
        int vent = FormationBlocks.mod(ModBlock.ASH_VENT);
        for (int k = 0; k < vents; k++) {
            // On a lip (the crack's own frame, wobble included), never over the crack itself.
            double along = c.r.range(-0.6, 0.6) * half;
            double t = Math.abs(along) / half;
            double side = (c.r.chance(0.5) ? 1 : -1) * (width * 0.5 * (1.0 - t * t) + 0.3 + 0.9);
            double shifted = side + 1.2 * Math.sin(along * 0.35 + wobble);
            int vx = (int) Math.round(along * cos - shifted * sin);
            int vz = (int) Math.round(along * sin + shifted * cos);
            double va = vx * cos + vz * sin;
            double vt = Math.abs(va) / half;
            double vacross = Math.abs(-vx * sin + vz * cos - 1.2 * Math.sin(va * 0.35 + wobble));
            double vw = width * 0.5 * (1.0 - vt * vt) + 0.3;
            if (vt > 1.0 || vacross <= vw || vacross > vw + 1.3) {
                continue;
            }
            int ground = c.ground(vx, vz);
            c.b.force(vx, ground, vz, vent | EMBED);
            c.b.force(vx, ground - 1, vz, magma | EMBED);
        }
    }

    /**
     * Ashen wastes: fumaroles: one to three of Sincerelyuq's Biomes' ash vents (hot ash, damage)
     * within a few blocks, each over magma in a ring of basalt and smooth basalt set into the ground
     * with a few raised blocks, as the mod builds them; each on its own column's ground.
     */
    private static void ashVents(Ctx c) {
        int n = Math.max(1, c.spec.span());
        double spread = c.spec.radius();
        int vent = FormationBlocks.mod(ModBlock.ASH_VENT);
        int magma = FormationBlocks.code(FormationBlocks.MAGMA, 0);
        int basalt = FormationBlocks.code(FormationBlocks.BASALT, 0);
        int smooth = FormationBlocks.code(FormationBlocks.SMOOTH_BASALT, 0);
        for (int k = 0; k < n; k++) {
            double a = c.r.next() * Math.PI * 2;
            double d = k == 0 ? 0.0 : c.r.range(1.5, spread + 1.0);
            int vx = (int) Math.round(Math.cos(a) * d);
            int vz = (int) Math.round(Math.sin(a) * d);
            int ground = c.ground(vx, vz);
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    int d2 = dx * dx + dz * dz;
                    int g = c.ground(vx + dx, vz + dz);
                    int there = c.b.get(vx + dx, g, vz + dz);
                    if (d2 == 0 || d2 > 5 || FormationBlocks.kind(there) == FormationBlocks.MOD && FormationBlocks.modBlock(there) == ModBlock.ASH_VENT) {
                        continue;
                    }
                    if (Math.abs(g - ground) > 1) {
                        continue;
                    }
                    c.b.force(vx + dx, g, vz + dz, (c.r.chance(0.33) ? smooth : basalt) | EMBED);
                    if (d2 <= 2 && c.r.chance(0.5)) {
                        c.b.solid(vx + dx, g + 1, vz + dz, basalt);
                    }
                }
            }
            c.b.force(vx, ground, vz, vent | EMBED);
            c.b.force(vx, ground - 1, vz, magma | EMBED);
        }
        footAll(c, 3);
    }
}
