package com.sincerelyuq.vastlands.worldgen.biome;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sincerelyuq.vastlands.Vastlands;
import com.sincerelyuq.vastlands.worldgen.caves.CaveBiome;
import com.sincerelyuq.vastlands.worldgen.caves.VastlandsCaves;
import com.sincerelyuq.vastlands.worldgen.context.GenerationContext;
import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;

/**
 * Biome source of the Vastlands overworld ({@code "type": "vastlands:caves"}): the surface biomes
 * of a delegate (vanilla's multi-noise overworld table on the terrain's climate parameters) and,
 * below the ground, the cave biomes the caves decide (see {@code CaveBiomes}): provinces of lush
 * caves, dripstone, the deep dark, crystal and hot caves, and plain rock under the surface biome.
 *
 * <p>Each cave slot is a biome list, normally a tag ({@code #vastlands:cave/lush} and so on), so a
 * slot can hold biomes of other mods as optional tag entries: present when the mod is installed,
 * silently absent otherwise. A slot whose list is empty falls back to another (crystal to dripstone,
 * the others to plain rock). Several biomes in one slot share its provinces by regions of about
 * 512 blocks. The {@code luminous} list (Agent 7) takes the place of the lush or dripstone slot in
 * the provinces the caves mark luminous (warm, humid, middle and deep caves); empty, nothing
 * changes.
 *
 * <p>Regional surface biomes (Agent 7): the lists {@code painted_range}, {@code volcanic_waste},
 * {@code dune_sea}, {@code wetland} and {@code dusk_wood} (tags {@code #vastlands:region/<name>},
 * other mods' biomes as optional entries) take the place of the delegate's land biome wherever
 * {@link RegionalBiomes} puts their region, so those biomes follow the Vastlands geography instead
 * of a climate table of their own. Rivers, oceans, beaches, mushroom fields and the biomes of
 * {@code #vastlands:region/keep} stay. Empty lists change nothing.
 *
 * <p>Underground, vanilla's own cave biomes of the delegate (which follow its depth parameter and
 * climate) are replaced: plain rock takes the biome of the surface above, cave biomes come only
 * from the Vastlands slots. Near the ground (16 blocks) the delegate decides as before. The cave
 * system is found through the climate sampler of the call ({@link GenerationContext#of(Climate.Sampler)});
 * a sampler without a Vastlands context gets the delegate's biome.
 */
public final class VastlandsBiomeSource extends BiomeSource {
    public static final MapCodec<VastlandsBiomeSource> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    BiomeSource.CODEC.fieldOf("surface").forGetter(s -> s.surface),
                    Biome.LIST_CODEC.optionalFieldOf("lush", HolderSet.empty()).forGetter(s -> s.slots.get(CaveBiome.LUSH)),
                    Biome.LIST_CODEC.optionalFieldOf("dripstone", HolderSet.empty()).forGetter(s -> s.slots.get(CaveBiome.DRIPSTONE)),
                    Biome.LIST_CODEC.optionalFieldOf("deep_dark", HolderSet.empty()).forGetter(s -> s.slots.get(CaveBiome.DEEP_DARK)),
                    Biome.LIST_CODEC.optionalFieldOf("crystal", HolderSet.empty()).forGetter(s -> s.slots.get(CaveBiome.CRYSTAL)),
                    Biome.LIST_CODEC.optionalFieldOf("volcanic", HolderSet.empty()).forGetter(s -> s.slots.get(CaveBiome.VOLCANIC)),
                    Biome.LIST_CODEC.optionalFieldOf("other", HolderSet.empty()).forGetter(s -> s.slots.get(CaveBiome.OTHER)),
                    Biome.LIST_CODEC.optionalFieldOf("luminous", HolderSet.empty()).forGetter(s -> s.luminous),
                    Biome.LIST_CODEC.optionalFieldOf("painted_range", HolderSet.empty()).forGetter(s -> s.regions.get(RegionalBiomes.Region.PAINTED_RANGE)),
                    Biome.LIST_CODEC.optionalFieldOf("volcanic_waste", HolderSet.empty()).forGetter(s -> s.regions.get(RegionalBiomes.Region.VOLCANIC_WASTE)),
                    Biome.LIST_CODEC.optionalFieldOf("dune_sea", HolderSet.empty()).forGetter(s -> s.regions.get(RegionalBiomes.Region.DUNE_SEA)),
                    Biome.LIST_CODEC.optionalFieldOf("wetland", HolderSet.empty()).forGetter(s -> s.regions.get(RegionalBiomes.Region.WETLAND)),
                    Biome.LIST_CODEC.optionalFieldOf("dusk_wood", HolderSet.empty()).forGetter(s -> s.regions.get(RegionalBiomes.Region.DUSK_WOOD))
            ).apply(instance, VastlandsBiomeSource::new)
    );
    /** Surface biomes a region never replaces (besides rivers, oceans, beaches and mushroom fields). */
    public static final TagKey<Biome> REGION_KEEP = TagKey.create(Registries.BIOME, Vastlands.id("region/keep"));

    private final BiomeSource surface;
    private final Map<CaveBiome, HolderSet<Biome>> slots = new EnumMap<>(CaveBiome.class);
    private final HolderSet<Biome> luminous;
    private final Map<RegionalBiomes.Region, HolderSet<Biome>> regions = new EnumMap<>(RegionalBiomes.Region.class);
    /** Resolved on first use (tags are bound after the dimension is decoded): the biomes of each slot after fallbacks. */
    private volatile Map<CaveBiome, List<Holder<Biome>>> resolved;
    private volatile List<Holder<Biome>> luminousResolved;
    private volatile Map<RegionalBiomes.Region, List<Holder<Biome>>> regionsResolved;
    /** Bit per region with biomes. */
    private volatile int regionMask = -1;
    private final Map<Holder<Biome>, Boolean> replaceable = new ConcurrentHashMap<>();
    /** The region of each quart column, per thread (a pure function of the column, cached). */
    private final ThreadLocal<long[]> regionKeys = ThreadLocal.withInitial(() -> {
        long[] keys = new long[4096];
        java.util.Arrays.fill(keys, Long.MIN_VALUE);
        return keys;
    });
    private final ThreadLocal<byte[]> regionValues = ThreadLocal.withInitial(() -> new byte[4096]);

    public VastlandsBiomeSource(BiomeSource surface, HolderSet<Biome> lush, HolderSet<Biome> dripstone, HolderSet<Biome> deepDark,
                                HolderSet<Biome> crystal, HolderSet<Biome> volcanic, HolderSet<Biome> other, HolderSet<Biome> luminous,
                                HolderSet<Biome> paintedRange, HolderSet<Biome> volcanicWaste, HolderSet<Biome> duneSea, HolderSet<Biome> wetland,
                                HolderSet<Biome> duskWood) {
        this.surface = surface;
        this.slots.put(CaveBiome.LUSH, lush);
        this.slots.put(CaveBiome.DRIPSTONE, dripstone);
        this.slots.put(CaveBiome.DEEP_DARK, deepDark);
        this.slots.put(CaveBiome.CRYSTAL, crystal);
        this.slots.put(CaveBiome.VOLCANIC, volcanic);
        this.slots.put(CaveBiome.OTHER, other);
        this.luminous = luminous;
        this.regions.put(RegionalBiomes.Region.PAINTED_RANGE, paintedRange);
        this.regions.put(RegionalBiomes.Region.VOLCANIC_WASTE, volcanicWaste);
        this.regions.put(RegionalBiomes.Region.DUNE_SEA, duneSea);
        this.regions.put(RegionalBiomes.Region.WETLAND, wetland);
        this.regions.put(RegionalBiomes.Region.DUSK_WOOD, duskWood);
    }

    /** Without the lists added by Agent 7 (tests and older code). */
    public VastlandsBiomeSource(BiomeSource surface, HolderSet<Biome> lush, HolderSet<Biome> dripstone, HolderSet<Biome> deepDark,
                                HolderSet<Biome> crystal, HolderSet<Biome> volcanic, HolderSet<Biome> other) {
        this(surface, lush, dripstone, deepDark, crystal, volcanic, other, HolderSet.empty(), HolderSet.empty(), HolderSet.empty(), HolderSet.empty(),
                HolderSet.empty(), HolderSet.empty());
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    public BiomeSource surface() {
        return this.surface;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.of(this.surface.possibleBiomes().stream(), this.slots.values().stream().flatMap(HolderSet::stream), this.luminous.stream(),
                this.regions.values().stream().flatMap(HolderSet::stream)).flatMap(s -> s).distinct();
    }

    /** The biomes of each slot after fallbacks (empty for SURFACE and NONE). */
    public Map<CaveBiome, List<Holder<Biome>>> slots() {
        Map<CaveBiome, List<Holder<Biome>>> map = this.resolved;
        if (map == null) {
            map = new EnumMap<>(CaveBiome.class);
            for (CaveBiome slot : CaveBiome.values()) {
                CaveBiome s = slot;
                List<Holder<Biome>> biomes = List.of();
                while (s != CaveBiome.SURFACE && s != CaveBiome.NONE) {
                    HolderSet<Biome> set = this.slots.get(s);
                    biomes = set == null ? List.of() : set.stream().toList();
                    if (!biomes.isEmpty()) {
                        break;
                    }
                    s = s.fallback();
                }
                map.put(slot, biomes);
            }
            this.resolved = map;
            Vastlands.LOGGER.info("Vastlands cave biomes: {}, luminous {}", map.entrySet().stream()
                    .filter(e -> e.getKey() != CaveBiome.SURFACE && e.getKey() != CaveBiome.NONE)
                    .map(e -> e.getKey().name().toLowerCase(Locale.ROOT) + " " + names(e.getValue()))
                    .collect(Collectors.joining(", ")), names(this.luminousBiomes()));
            Vastlands.LOGGER.info("Vastlands regional biomes: {}", this.regionBiomes().entrySet().stream()
                    .map(e -> e.getKey().tagPath() + " " + names(e.getValue())).collect(Collectors.joining(", ")));
        }
        return map;
    }

    private static List<String> names(List<Holder<Biome>> biomes) {
        return biomes.stream().map(h -> h.unwrapKey().map(k -> k.location().toString()).orElse("?")).collect(Collectors.toList());
    }

    /** The luminous cave biomes (empty without other mods). */
    public List<Holder<Biome>> luminousBiomes() {
        List<Holder<Biome>> list = this.luminousResolved;
        if (list == null) {
            list = this.luminous.stream().toList();
            this.luminousResolved = list;
        }
        return list;
    }

    /** The biomes of each surface region (empty lists without other mods). */
    public Map<RegionalBiomes.Region, List<Holder<Biome>>> regionBiomes() {
        Map<RegionalBiomes.Region, List<Holder<Biome>>> map = this.regionsResolved;
        if (map == null) {
            map = new EnumMap<>(RegionalBiomes.Region.class);
            for (RegionalBiomes.Region r : RegionalBiomes.Region.VALUES) {
                map.put(r, this.regions.get(r).stream().toList());
            }
            this.regionsResolved = map;
        }
        return map;
    }

    /** One bit per region that has biomes. */
    public int regionMask() {
        int mask = this.regionMask;
        if (mask < 0) {
            mask = 0;
            for (Map.Entry<RegionalBiomes.Region, List<Holder<Biome>>> e : this.regionBiomes().entrySet()) {
                if (!e.getValue().isEmpty()) {
                    mask |= 1 << e.getKey().ordinal();
                }
            }
            this.regionMask = mask;
        }
        return mask;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        GenerationContext context = GenerationContext.of(sampler);
        if (context == null || !(context.systems().caves() instanceof VastlandsCaves caves)) {
            return this.surface.getNoiseBiome(x, y, z, sampler);
        }
        int bx = QuartPos.toBlock(x);
        int by = QuartPos.toBlock(y);
        int bz = QuartPos.toBlock(z);
        CaveBiome slot = caves.caveBiome(bx, by, bz);
        if (slot == CaveBiome.SURFACE) {
            return this.surfaceBiome(x, y, z, sampler, context);
        }
        List<Holder<Biome>> biomes = this.slots().get(slot);
        if ((slot == CaveBiome.LUSH || slot == CaveBiome.DRIPSTONE) && !this.luminousBiomes().isEmpty() && caves.luminous(bx, by, bz)) {
            biomes = this.luminousBiomes();
        }
        if (!biomes.isEmpty()) {
            return pick(biomes, bx, bz, slot.ordinal());
        }
        // Plain rock: the biome of the surface above.
        int top = (int) Math.floor(context.systems().surfaceHeight(bx, bz)) - 2;
        return this.surfaceBiome(x, QuartPos.fromBlock(top), z, sampler, context);
    }

    /** Several biomes share a list by region (about 512 blocks), the same for every height. */
    private static Holder<Biome> pick(List<Holder<Biome>> biomes, int bx, int bz, int salt) {
        if (biomes.size() == 1) {
            return biomes.get(0);
        }
        long h = SimplexNoise.mix((long) (bx >> 9) * 0x9E3779B97F4A7C15L ^ (long) (bz >> 9) * 0xC2B2AE3D27D4EB4FL ^ salt);
        return biomes.get((int) Math.floorMod(h, (long) biomes.size()));
    }

    /** The delegate's surface biome, or a regional biome in its place. */
    private Holder<Biome> surfaceBiome(int x, int y, int z, Climate.Sampler sampler, GenerationContext context) {
        Holder<Biome> base = this.surface.getNoiseBiome(x, y, z, sampler);
        int mask = this.regionMask();
        if (mask == 0 || !this.replaceable.computeIfAbsent(base, VastlandsBiomeSource::replaceable)) {
            return base;
        }
        RegionalBiomes.Region region = this.region(x, z, mask, context);
        if (region == null) {
            return base;
        }
        return pick(this.regionBiomes().get(region), QuartPos.toBlock(x), QuartPos.toBlock(z), 64 + region.ordinal());
    }

    private static boolean replaceable(Holder<Biome> biome) {
        return !biome.is(BiomeTags.IS_OCEAN) && !biome.is(BiomeTags.IS_DEEP_OCEAN) && !biome.is(BiomeTags.IS_RIVER) && !biome.is(BiomeTags.IS_BEACH)
                && !biome.is(Biomes.MUSHROOM_FIELDS) && !biome.is(REGION_KEEP);
    }

    /** The region of a quart column (cached per thread; the same answer from every thread). */
    private RegionalBiomes.Region region(int qx, int qz, int mask, GenerationContext context) {
        long key = (long) qx << 32 ^ (qz & 0xFFFFFFFFL);
        int slot = (int) (SimplexNoise.mix(key) & 4095);
        long[] keys = this.regionKeys.get();
        byte[] values = this.regionValues.get();
        if (keys[slot] != key) {
            RegionalBiomes.Region r = context.regions().region(QuartPos.toBlock(qx), QuartPos.toBlock(qz), mask);
            values[slot] = (byte) (r == null ? -1 : r.ordinal());
            keys[slot] = key;
        }
        int v = values[slot];
        return v < 0 ? null : RegionalBiomes.Region.VALUES[v];
    }

    @Override
    public void addDebugInfo(List<String> info, BlockPos pos, Climate.Sampler sampler) {
        this.surface.addDebugInfo(info, pos, sampler);
        GenerationContext context = GenerationContext.of(sampler);
        if (context != null && context.systems().caves() instanceof VastlandsCaves caves) {
            info.add("Vastlands cave biome: " + caves.caveBiome(pos.getX(), pos.getY(), pos.getZ()).name().toLowerCase(Locale.ROOT)
                    + (caves.luminous(pos.getX(), pos.getY(), pos.getZ()) ? " (luminous)" : ""));
            StringBuilder regions = new StringBuilder();
            for (RegionalBiomes.Region r : RegionalBiomes.Region.VALUES) {
                double s = context.regions().strength(r, pos.getX(), pos.getZ());
                if (s > 0.05) {
                    regions.append(regions.length() == 0 ? "" : ", ").append(r.tagPath()).append(String.format(Locale.ROOT, " %.2f", s));
                }
            }
            info.add("Vastlands region: " + (regions.length() == 0 ? "none" : regions));
        }
    }
}
