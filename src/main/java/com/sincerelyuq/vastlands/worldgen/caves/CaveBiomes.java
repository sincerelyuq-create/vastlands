package com.sincerelyuq.vastlands.worldgen.caves;

import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.smoothstep;

import com.sincerelyuq.vastlands.worldgen.geography.GeographyModel;
import com.sincerelyuq.vastlands.worldgen.geography.GeographySample;
import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;

/**
 * Where the cave biomes are: provinces of a warped 2D Voronoi diagram, one diagram per layer, so a
 * cave biome fills a contiguous region hundreds of blocks across and a few hundred blocks deep:
 * <ul>
 *     <li>the shallow layer, from {@value #SURFACE_DEPTH} to about 90 blocks under the ground (cells
 *     of ~{@value #SHALLOW_CELL} blocks): lush caves under moist, mild climates, dripstone, crystal
 *     caves;</li>
 *     <li>the middle layer, from there down to the aquitard (~{@value #MIDDLE_CELL}): dripstone,
 *     lush caves in wet regions, the deep dark under mountains (it holds Y -27, where ancient cities
 *     start), crystal and hot caves;</li>
 *     <li>the deep layer below the aquitard (~{@value #DEEP_CELL}): dripstone, crystal caves, the
 *     deep dark, hot caves in volcanic rock.</li>
 * </ul>
 * The deep dark of provinces keeps to Y {@value #DEEP_DARK_BOTTOM}..{@value #DEEP_DARK_TOP}: at most
 * ~150 blocks thick, however high the ground (megacaves of the deep dark fill their whole vault),
 * and stays out of flooded rock (ancient cities start in it at Y -27 and would stand in cave
 * water); deep dark megacaves are dry ones.
 * A province's slot comes from the climate and rock at its site, a share of the provinces
 * ({@code CaveSettings.caveBiomes}) holds no cave biome (plain rock under the surface biome), and
 * the layer boundaries wander by ±15 blocks. Megacaves carry a theme of their own over their whole
 * volume (a lush lake cavern, a dripstone hall, a deep dark abyss, a lava cavern of hot rock), so
 * the largest spaces show their biomes.
 *
 * <p>Pure function of the seed and the position; province slots are cached per thread.
 */
final class CaveBiomes {
    /** Within this many blocks of the ground the surface biome stays. */
    static final int SURFACE_DEPTH = 16;
    static final double SHALLOW_CELL = 420.0;
    static final double MIDDLE_CELL = 520.0;
    static final double DEEP_CELL = 600.0;
    private static final double SHALLOW_DEPTH = 90.0;
    /** Deep dark provinces fill only Y {@value #DEEP_DARK_BOTTOM}..{@value #DEEP_DARK_TOP} (ancient cities start at -27). */
    static final int DEEP_DARK_TOP = 0;
    static final int DEEP_DARK_BOTTOM = -150;
    private static final CaveBiome[] SLOTS = CaveBiome.values();

    private final long seed;
    private final GeographyModel geography;
    private final CaveGeology geology;
    private final FluidDomains domains;
    private final Megacaves megacaves;
    private final CaveModel.Ground ground;
    private final int seaTop;
    private final CaveSettings settings;
    private final ThreadLocal<double[]> scratch = ThreadLocal.withInitial(() -> new double[2]);
    private final SimplexNoise warpX, warpZ, layers;
    /** Province codes: the slot's ordinal, plus {@link #LUMINOUS} where a lumen biome may take its place. */
    private final ThreadLocal<KeyedCache<Integer>> provinces = ThreadLocal.withInitial(() -> new KeyedCache<>(512));
    private static final int LUMINOUS = 0x100;
    /**
     * Share of the eligible (warm, humid, middle and deep) lush and dripstone provinces that are
     * luminous, and of the eligible megacaves; each rises by its {@code _EXPANDED} amount with
     * {@code CaveSettings.luminousCaves} (Agent 8: the bioluminescent depths spread further).
     */
    private static final double LUMINOUS_SHARE = 0.5;
    private static final double LUMINOUS_SHARE_EXPANDED = 0.2;
    private static final double LUMINOUS_MEGACAVES = 0.4;
    private static final double LUMINOUS_MEGACAVES_EXPANDED = 0.25;

    CaveBiomes(long seed, GeographyModel geography, CaveGeology geology, FluidDomains domains, Megacaves megacaves, CaveModel.Ground ground,
               int seaTop, CaveSettings settings) {
        this.seed = SimplexNoise.mix(seed ^ 0x42494F4D4553L);
        this.geography = geography;
        this.geology = geology;
        this.domains = domains;
        this.megacaves = megacaves;
        this.ground = ground;
        this.seaTop = seaTop;
        this.settings = settings;
        this.warpX = new SimplexNoise(seed + 901);
        this.warpZ = new SimplexNoise(seed + 902);
        this.layers = new SimplexNoise(seed + 903);
    }

    /**
     * The slot of a block. Cheap for any position (no cave column is built): the ground, the
     * aquitard's mean height, the megacave whose footprint holds the column and the province.
     */
    CaveBiome at(int x, int y, int z, double surface) {
        return CaveBiome.VALUES[this.code(x, y, z, surface) & 0xFF];
    }

    /**
     * True if the block lies in a luminous cave province or megacave: a lush or dripstone one of
     * the middle or deep layer under a warm, humid climate (half of them), where a luminous cave
     * biome (Sincerelyuq's Biomes' bioluminescent depths, the {@code luminous} tag) takes the
     * place of the slot's biome. Without such a biome nothing changes: the slot stays as it was.
     */
    boolean luminous(int x, int y, int z, double surface) {
        return (this.code(x, y, z, surface) & LUMINOUS) != 0;
    }

    /** The slot ordinal of a block, with {@link #LUMINOUS} set for luminous provinces and megacaves. */
    private int code(int x, int y, int z, double surface) {
        if (y > surface - SURFACE_DEPTH) {
            return CaveBiome.SURFACE.ordinal();
        }
        boolean deep = y < this.domains.aquitard(x, z);
        double[] q = this.scratch.get();
        Megacaves.Megacave m = this.megacaves.covering(x, z, deep ? Megacaves.DEEP : Megacaves.UPPER, q);
        if (m != null && q[0] < 1.0 && y >= m.bottom() - 10.0 && y <= m.top() + 10.0) {
            CaveBiome theme = this.theme(m);
            if (theme == CaveBiome.LUSH) {
                CaveBiome slot = this.plausible(theme, x, y, z, surface, deep);
                return slot.ordinal() | (slot == CaveBiome.LUSH && this.luminous(m) ? LUMINOUS : 0);
            }
            if (theme != null) {
                return theme.ordinal() | (theme == CaveBiome.DRIPSTONE && this.luminous(m) ? LUMINOUS : 0);
            }
        }
        int layer;
        if (deep) {
            layer = 2;
        } else {
            double shallow = surface - SHALLOW_DEPTH - 15.0 * this.layers.sample(x / 140.0, z / 140.0);
            layer = y > shallow ? 0 : 1;
        }
        int province = this.province(x, z, layer);
        CaveBiome slot = this.plausible(CaveBiome.VALUES[province & 0xFF], x, y, z, surface, deep);
        return slot.ordinal() | (slot.ordinal() == (province & 0xFF) ? province & LUMINOUS : 0);
    }

    /** A lush or dripstone megacave under a warm, humid climate is luminous for 40% of them (65% expanded). */
    private boolean luminous(Megacaves.Megacave m) {
        return m.temperature > -0.1 && m.wetness > 0.45 && m.volcanic < 0.5
                && (SimplexNoise.mix(m.seed ^ 0x4C756D656EL) >>> 11) * 0x1.0p-53 < LUMINOUS_MEGACAVES + LUMINOUS_MEGACAVES_EXPANDED * this.settings.luminousCaves();
    }

    /** Checks a slot against the column itself: no lush caves under cold ground or the sea, no deep dark out of its band or in flooded rock. */
    private CaveBiome plausible(CaveBiome slot, int x, int y, int z, double surface, boolean deep) {
        if (slot == CaveBiome.LUSH) {
            // Provinces and megacaves take the climate of their site; the column's own decides too.
            if (surface < this.seaTop - 2 || this.ground.geography(x, z).temperature() < -0.3) {
                return CaveBiome.NONE;
            }
        }
        if (slot == CaveBiome.DEEP_DARK) {
            if (y > DEEP_DARK_TOP || y < DEEP_DARK_BOTTOM) {
                // The deep dark keeps to a band (vanilla's own is Y -64..0): its sculk features cost
                // generation time in proportion to its volume, and under high ground a province
                // would otherwise be hundreds of blocks thick.
                return CaveBiome.NONE;
            }
            FluidDomains.Domain domain = this.domains.owner(x, z, deep);
            if (domain.fluid != CaveFluid.AIR && domain.level >= y) {
                // Not in flooded rock: ancient cities (which start in the deep dark at Y -27) would
                // stand in the water of the caves around them.
                return CaveBiome.NONE;
            }
        }
        return slot;
    }

    /** The theme of a megacave over its whole volume, or null for the province's. */
    private CaveBiome theme(Megacaves.Megacave m) {
        double t = m.theme;
        boolean sea = m.domain.sea;
        if (m.zone == Megacaves.UPPER) {
            if (!sea && t < 0.4 && m.wetness > 0.35 && m.temperature > -0.35) {
                return CaveBiome.LUSH;
            }
            if (t < 0.62) {
                return CaveBiome.DRIPSTONE;
            }
            return t < 0.75 ? CaveBiome.CRYSTAL : null;
        }
        if (m.domain.fluid == CaveFluid.LAVA || m.volcanic > 0.5 && t < 0.5) {
            return CaveBiome.VOLCANIC;
        }
        if (t < 0.22 && m.volcanic < 0.3 && m.domain.fluid == CaveFluid.AIR) {
            return CaveBiome.DEEP_DARK;
        }
        if (t < 0.45) {
            return CaveBiome.DRIPSTONE;
        }
        return t < 0.6 ? CaveBiome.CRYSTAL : null;
    }

    private int province(int x, int z, int layer) {
        double cell = layer == 0 ? SHALLOW_CELL : (layer == 1 ? MIDDLE_CELL : DEEP_CELL);
        double scale = 0.9 * cell;
        double wx = x + 0.3 * cell * this.warpX.sample(x / scale + 13.0 * layer, z / scale);
        double wz = z + 0.3 * cell * this.warpZ.sample(x / scale, z / scale - 13.0 * layer);
        long ci = (long) Math.floor(wx / cell);
        long cj = (long) Math.floor(wz / cell);
        long best = 0;
        long bi = 0;
        long bj = 0;
        double bestD = Double.POSITIVE_INFINITY;
        for (long j = cj - 1; j <= cj + 1; j++) {
            for (long i = ci - 1; i <= ci + 1; i++) {
                long h = this.hash(i, j, layer);
                double sx = (i + 0.15 + 0.7 * FluidDomains.unit(h, 0)) * cell;
                double sz = (j + 0.15 + 0.7 * FluidDomains.unit(h, 1)) * cell;
                double d = (wx - sx) * (wx - sx) + (wz - sz) * (wz - sz);
                if (d < bestD) {
                    bestD = d;
                    best = h;
                    bi = i;
                    bj = j;
                }
            }
        }
        long key = (bi << 32 ^ (bj & 0xFFFFFFFFL)) * 3 + layer;
        long site = best;
        long fi = bi;
        long fj = bj;
        return this.provinces.get().get(key, k -> {
            CaveBiome slot = this.slot(site, fi, fj, cell, layer);
            return slot.ordinal() | (this.luminous(slot, site, fi, fj, cell, layer) ? LUMINOUS : 0);
        });
    }

    /**
     * Whether a province of the middle or deep layer is luminous: lush and dripstone provinces
     * under a warm, humid climate off volcanic rock, a share {@value #LUMINOUS_SHARE} of them (an
     * own draw of the site, so the slot itself is the same as without it).
     */
    private boolean luminous(CaveBiome slot, long h, long i, long j, double cell, int layer) {
        if (layer == 0 || slot != CaveBiome.LUSH && slot != CaveBiome.DRIPSTONE
                || FluidDomains.unit(h, 7) >= LUMINOUS_SHARE + LUMINOUS_SHARE_EXPANDED * this.settings.luminousCaves()) {
            return false;
        }
        double sx = (i + 0.15 + 0.7 * FluidDomains.unit(h, 0)) * cell;
        double sz = (j + 0.15 + 0.7 * FluidDomains.unit(h, 1)) * cell;
        GeographySample g = this.geography.sample(sx, sz);
        CaveGeology.Geology geo = this.geology.at(sx, sz, g);
        return g.continentalness() > 0.0 && g.temperature() > -0.05 && geo.wetness() > 0.4 && geo.volcanic() < 0.4;
    }

    /** The slot of a province, from the climate and the rock at its site. */
    private CaveBiome slot(long h, long i, long j, double cell, int layer) {
        double sx = (i + 0.15 + 0.7 * FluidDomains.unit(h, 0)) * cell;
        double sz = (j + 0.15 + 0.7 * FluidDomains.unit(h, 1)) * cell;
        GeographySample g = this.geography.sample(sx, sz);
        CaveGeology.Geology geo = this.geology.at(sx, sz, g);
        boolean land = g.continentalness() > 0.0;
        double mild = smoothstep(-0.45, -0.2, g.temperature());
        double mountains = smoothstep(0.15, 0.6, g.mountain());
        double[] w = new double[SLOTS.length];
        switch (layer) {
            case 0 -> {
                w[CaveBiome.LUSH.ordinal()] = land ? mild * (0.25 + 2.0 * geo.wetness()) : 0.0;
                w[CaveBiome.DRIPSTONE.ordinal()] = 0.35 + 0.4 * geo.karst() + 0.2 * geo.fracture();
                w[CaveBiome.CRYSTAL.ordinal()] = 0.2 + 0.15 * geo.fracture();
                w[CaveBiome.OTHER.ordinal()] = 0.05;
            }
            case 1 -> {
                w[CaveBiome.LUSH.ordinal()] = land ? mild * (0.1 + 1.2 * geo.wetness()) : 0.0;
                w[CaveBiome.DRIPSTONE.ordinal()] = 0.45 + 0.4 * geo.karst();
                w[CaveBiome.DEEP_DARK.ordinal()] = land ? (0.25 + 0.8 * mountains) * (1.0 - geo.volcanic()) : 0.0;
                w[CaveBiome.CRYSTAL.ordinal()] = 0.25;
                w[CaveBiome.VOLCANIC.ordinal()] = 0.5 * geo.volcanic();
                w[CaveBiome.OTHER.ordinal()] = 0.05;
            }
            default -> {
                w[CaveBiome.DRIPSTONE.ordinal()] = 0.35 + 0.2 * geo.karst();
                w[CaveBiome.DEEP_DARK.ordinal()] = (0.1 + 0.25 * mountains) * (1.0 - geo.volcanic());
                w[CaveBiome.CRYSTAL.ordinal()] = 0.35;
                w[CaveBiome.VOLCANIC.ordinal()] = 0.12 + 1.4 * geo.volcanic();
                w[CaveBiome.OTHER.ordinal()] = 0.05;
            }
        }
        double total = 0.0;
        for (double v : w) {
            total += v;
        }
        // Plain rock provinces: the share the settings leave without a cave biome.
        double share = Math.max(0.01, Math.min(0.99, this.settings.caveBiomes()));
        if (FluidDomains.unit(h, 5) >= share || total <= 0.0) {
            return CaveBiome.NONE;
        }
        double r = FluidDomains.unit(h, 6) * total;
        for (int k = 0; k < w.length; k++) {
            r -= w[k];
            if (r < 0.0 && w[k] > 0.0) {
                return SLOTS[k];
            }
        }
        return CaveBiome.NONE;
    }

    private long hash(long i, long j, int layer) {
        return SimplexNoise.mix(this.seed ^ i * 0x9E3779B97F4A7C15L ^ j * 0xC2B2AE3D27D4EB4FL ^ (layer + 1) * 0x165667B19E3779F9L);
    }
}
