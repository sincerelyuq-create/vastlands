package com.sincerelyuq.vastlands.worldgen.forest;

import com.sincerelyuq.vastlands.Vastlands;
import com.sincerelyuq.vastlands.worldgen.biome.BiomeColors;
import com.sincerelyuq.vastlands.worldgen.context.GenerationContext;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;

/**
 * The world's surface biomes as forest categories: the generator's biome source at the ground,
 * mapped through the biome tags {@code #vastlands:forest/<community>} (and
 * {@code #vastlands:forest/transparent} for river biomes). A biome in none of them is
 * {@link ForestModel#NONE}: Vastlands leaves its vegetation alone. Biomes of other mods take part
 * only if a tag lists them (optional entries need no dependency). Thread-safe.
 */
public final class ForestBiomes implements ForestModel.Biomes {
    private static final TagKey<Biome>[] TAGS = tags();
    private static final TagKey<Biome> TRANSPARENT = TagKey.create(Registries.BIOME, Vastlands.id("forest/transparent"));

    private final BiomeSource source;
    private final Climate.Sampler sampler;
    private final Map<Holder<Biome>, Integer> categories = new ConcurrentHashMap<>();
    private final Map<Holder<Biome>, Double> blends = new ConcurrentHashMap<>();

    public ForestBiomes(BiomeSource source, Climate.Sampler sampler) {
        this.source = source;
        this.sampler = sampler;
    }

    @SuppressWarnings("unchecked")
    private static TagKey<Biome>[] tags() {
        TagKey<Biome>[] tags = new TagKey[ForestType.COUNT];
        for (ForestType type : ForestType.VALUES) {
            tags[type.ordinal()] = TagKey.create(Registries.BIOME, Vastlands.id("forest/" + type.tagPath()));
        }
        return tags;
    }

    /** The forest category of a biome (first community tag in {@link ForestType} order). */
    public static int category(Holder<Biome> biome) {
        for (int t = 0; t < TAGS.length; t++) {
            if (biome.is(TAGS[t])) {
                return t;
            }
        }
        return biome.is(TRANSPARENT) ? ForestModel.TRANSPARENT : ForestModel.NONE;
    }

    /**
     * The surface biome of a block as the forests see it: the biome source's answer with exact
     * climate searches (see {@link ExactClimateSearch}), so the same in every chunk and thread.
     */
    public Holder<Biome> biome(int x, int y, int z) {
        return ExactClimateSearch.call(() -> this.source.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), this.sampler));
    }

    @Override
    public int category(int x, int y, int z) {
        return this.categories.computeIfAbsent(this.biome(x, y, z), ForestBiomes::category);
    }

    /** How well moss blends into the grass of the biome at a block: from the biome's grass colour (see {@link BiomeColors}). */
    @Override
    public double mossBlend(int x, int y, int z) {
        return this.blends.computeIfAbsent(this.biome(x, y, z), h -> BiomeColors.mossBlend(h.value()));
    }

    /**
     * The world's forests, bound to its biomes on first use, or empty outside Vastlands worlds
     * (or where another system replaced the forests).
     */
    public static Optional<VastlandsForests> forests(WorldGenLevel level, ChunkGenerator generator) {
        Optional<GenerationContext> context = GenerationContext.of(level.getLevel());
        if (context.isEmpty() || !(context.get().systems().forests() instanceof VastlandsForests forests)) {
            return Optional.empty();
        }
        BiomeSource source = generator.getBiomeSource();
        // The trees keep clear of the natural formations: bind those to the world first.
        com.sincerelyuq.vastlands.worldgen.nature.NatureBiomes.nature(level, generator);
        if (forests.model() == null || forests.boundTo() != source) {
            forests.bind(source, new ForestBiomes(source, level.getLevel().getChunkSource().randomState().sampler()));
        }
        return Optional.of(forests);
    }
}
