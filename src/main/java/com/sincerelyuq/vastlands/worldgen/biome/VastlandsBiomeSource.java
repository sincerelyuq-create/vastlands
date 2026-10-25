package com.sincerelyuq.vastlands.worldgen.biome;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sincerelyuq.vastlands.Vastlands;
import com.sincerelyuq.vastlands.worldgen.caves.CaveBiome;
import com.sincerelyuq.vastlands.worldgen.caves.VastlandsCaves;
import com.sincerelyuq.vastlands.worldgen.context.GenerationContext;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * Biome source of the Vastlands overworld ({@code "type": "vastlands:caves"}): the surface biomes
 * of a delegate (vanilla's multi-noise overworld table on the terrain's climate parameters) and,
 * below the ground, the cave biomes the caves decide (see {@code CaveBiomes}): provinces of lush
 * caves, dripstone, the deep dark, crystal and hot caves, and plain rock under the surface biome.
 *
 * <p>Each cave slot is a biome list, normally a tag ({@code #vastlands:cave/lush} and so on), so a
 * slot can hold biomes of other mods as optional tag entries: present when the mod is installed,
 * silently absent otherwise. A slot whose list is empty falls back to plain rock. Several biomes in
 * one slot share its provinces by regions of about 512 blocks.
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
                    Biome.LIST_CODEC.optionalFieldOf("other", HolderSet.empty()).forGetter(s -> s.slots.get(CaveBiome.OTHER))
            ).apply(instance, VastlandsBiomeSource::new)
    );

    private final BiomeSource surface;
    private final Map<CaveBiome, HolderSet<Biome>> slots = new EnumMap<>(CaveBiome.class);
    /** Resolved on first use (tags are bound after the dimension is decoded): the biomes of each slot after fallbacks. */
    private volatile Map<CaveBiome, List<Holder<Biome>>> resolved;

    public VastlandsBiomeSource(BiomeSource surface, HolderSet<Biome> lush, HolderSet<Biome> dripstone, HolderSet<Biome> deepDark,
                                HolderSet<Biome> crystal, HolderSet<Biome> volcanic, HolderSet<Biome> other) {
        this.surface = surface;
        this.slots.put(CaveBiome.LUSH, lush);
        this.slots.put(CaveBiome.DRIPSTONE, dripstone);
        this.slots.put(CaveBiome.DEEP_DARK, deepDark);
        this.slots.put(CaveBiome.CRYSTAL, crystal);
        this.slots.put(CaveBiome.VOLCANIC, volcanic);
        this.slots.put(CaveBiome.OTHER, other);
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
        return Stream.concat(this.surface.possibleBiomes().stream(), this.slots.values().stream().flatMap(HolderSet::stream));
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
            Vastlands.LOGGER.info("Vastlands cave biomes: {}", map.entrySet().stream()
                    .filter(e -> e.getKey() != CaveBiome.SURFACE && e.getKey() != CaveBiome.NONE)
                    .map(e -> e.getKey().name().toLowerCase(java.util.Locale.ROOT) + " " + e.getValue().stream()
                            .map(h -> h.unwrapKey().map(k -> k.location().toString()).orElse("?")).collect(Collectors.toList()))
                    .collect(Collectors.joining(", ")));
        }
        return map;
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
            return this.surface.getNoiseBiome(x, y, z, sampler);
        }
        List<Holder<Biome>> biomes = this.slots().get(slot);
        if (!biomes.isEmpty()) {
            if (biomes.size() == 1) {
                return biomes.get(0);
            }
            // Several biomes share a slot by region (about 512 blocks), the same for every height.
            long h = com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise.mix((long) (bx >> 9) * 0x9E3779B97F4A7C15L ^ (long) (bz >> 9) * 0xC2B2AE3D27D4EB4FL ^ slot.ordinal());
            return biomes.get((int) Math.floorMod(h, (long) biomes.size()));
        }
        // Plain rock: the biome of the surface above.
        int top = (int) Math.floor(context.systems().surfaceHeight(bx, bz)) - 2;
        return this.surface.getNoiseBiome(x, QuartPos.fromBlock(top), z, sampler);
    }

    @Override
    public void addDebugInfo(List<String> info, BlockPos pos, Climate.Sampler sampler) {
        this.surface.addDebugInfo(info, pos, sampler);
        GenerationContext context = GenerationContext.of(sampler);
        if (context != null && context.systems().caves() instanceof VastlandsCaves caves) {
            info.add("Vastlands cave biome: " + caves.caveBiome(pos.getX(), pos.getY(), pos.getZ()).name().toLowerCase(java.util.Locale.ROOT));
        }
    }
}
