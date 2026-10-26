package com.sincerelyuq.vastlands.worldgen.nature;

import com.sincerelyuq.vastlands.Vastlands;
import com.sincerelyuq.vastlands.worldgen.biome.BiomeColors;
import com.sincerelyuq.vastlands.worldgen.biome.VastlandsBiomeSource;
import com.sincerelyuq.vastlands.worldgen.caves.CaveBiome;
import com.sincerelyuq.vastlands.worldgen.caves.VastlandsCaves;
import com.sincerelyuq.vastlands.worldgen.context.GenerationContext;
import com.sincerelyuq.vastlands.worldgen.forest.ExactClimateSearch;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;

/**
 * The world's biomes as decoration styles: the generator's biome source (exact climate searches,
 * like the forests', so every chunk and thread gets the same biome), mapped through the biome tags
 * {@code #vastlands:nature/surface/<style>} and {@code #vastlands:nature/cave/<profile>}.
 *
 * <p>A surface biome in no style tag is judged by vanilla's convention tags and its own climate
 * (oceans, rivers, beaches, mountains; cold, hot and dry, hot and wet, else temperate), so other
 * mods' biomes get fitting formations without a list. Underground, plain rock (the surface biome
 * below the ground) is {@link CaveProfile#PLAIN}; a cave biome in no profile tag takes its cave
 * slot's profile (lush caves, dripstone, deep dark, crystal, volcanic) and {@link CaveProfile#OTHER}
 * in the {@code other} slot. Sincerelyuq's Biomes' cave biomes (Agent 7) sit in the slot tags too,
 * so their own profiles ({@link CaveProfile#FIRST}) are checked first. Thread-safe.
 */
public final class NatureBiomes implements NatureModel.Biomes {
    private static final TagKey<Biome>[] SURFACE = surfaceTags();
    private static final TagKey<Biome>[] CAVE = caveTags();

    private final BiomeSource source;
    private final Climate.Sampler sampler;
    @Nullable
    private final VastlandsCaves caves;
    private final Map<Holder<Biome>, Integer> styles = new ConcurrentHashMap<>();
    private final Map<Holder<Biome>, Integer> profiles = new ConcurrentHashMap<>();
    private final Map<Holder<Biome>, Double> blends = new ConcurrentHashMap<>();

    public NatureBiomes(BiomeSource source, Climate.Sampler sampler, @Nullable VastlandsCaves caves) {
        this.source = source;
        this.sampler = sampler;
        this.caves = caves;
    }

    @SuppressWarnings("unchecked")
    private static TagKey<Biome>[] surfaceTags() {
        TagKey<Biome>[] tags = new TagKey[SurfaceStyle.VALUES.length];
        for (SurfaceStyle style : SurfaceStyle.VALUES) {
            tags[style.ordinal()] = TagKey.create(Registries.BIOME, Vastlands.id("nature/surface/" + style.tagPath()));
        }
        return tags;
    }

    @SuppressWarnings("unchecked")
    private static TagKey<Biome>[] caveTags() {
        TagKey<Biome>[] tags = new TagKey[CaveProfile.VALUES.length];
        for (CaveProfile profile : CaveProfile.VALUES) {
            tags[profile.ordinal()] = TagKey.create(Registries.BIOME, Vastlands.id("nature/cave/" + profile.tagPath()));
        }
        return tags;
    }

    /** The biome at a block as the world has it (exact climate searches). */
    public Holder<Biome> biome(int x, int y, int z) {
        return ExactClimateSearch.call(() -> this.source.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), this.sampler));
    }

    /** The decoration style of a surface biome: its tag, else vanilla's convention tags and its climate. */
    public static SurfaceStyle style(Holder<Biome> biome) {
        for (int t = 0; t < SURFACE.length; t++) {
            if (biome.is(SURFACE[t])) {
                return SurfaceStyle.VALUES[t];
            }
        }
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN)) {
            return SurfaceStyle.OCEAN;
        }
        if (biome.is(BiomeTags.IS_RIVER)) {
            return SurfaceStyle.RIVER;
        }
        if (biome.is(BiomeTags.IS_BEACH)) {
            return SurfaceStyle.BEACH;
        }
        if (biome.is(BiomeTags.IS_BADLANDS)) {
            return SurfaceStyle.BADLANDS;
        }
        if (biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL)) {
            return SurfaceStyle.MOUNTAIN;
        }
        Biome.ClimateSettings climate = biome.value().getModifiedClimateSettings();
        if (climate.temperature() < 0.15F) {
            return SurfaceStyle.SNOWY;
        }
        if (climate.temperature() >= 1.5F && climate.downfall() < 0.15F) {
            return SurfaceStyle.DESERT;
        }
        if (climate.temperature() >= 0.9F && climate.downfall() >= 0.85F) {
            return SurfaceStyle.JUNGLE;
        }
        if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA)) {
            return climate.temperature() < 0.4F ? SurfaceStyle.BOREAL : SurfaceStyle.TEMPERATE;
        }
        return SurfaceStyle.TEMPERATE;
    }

    /**
     * The decoration profile of the biome of a cave block: its tag; else, for a cave biome of a
     * slot ({@code caveBiome}), the slot's; else (plain rock under a surface biome) plain.
     */
    public static CaveProfile profile(Holder<Biome> biome, CaveBiome slot, boolean caveBiome) {
        for (CaveProfile first : CaveProfile.FIRST) {
            if (biome.is(CAVE[first.ordinal()])) {
                return first;
            }
        }
        for (int t = 0; t < CAVE.length; t++) {
            if (biome.is(CAVE[t])) {
                return CaveProfile.VALUES[t];
            }
        }
        if (!caveBiome) {
            return CaveProfile.PLAIN;
        }
        return switch (slot) {
            case LUSH -> CaveProfile.LUSH;
            case DRIPSTONE -> CaveProfile.DRIPSTONE;
            case DEEP_DARK -> CaveProfile.DEEP_DARK;
            case CRYSTAL -> CaveProfile.CRYSTAL;
            case VOLCANIC -> CaveProfile.VOLCANIC;
            case OTHER -> CaveProfile.OTHER;
            default -> CaveProfile.PLAIN;
        };
    }

    @Override
    public int surfaceStyle(int x, int y, int z) {
        return this.styles.computeIfAbsent(this.biome(x, y, z), h -> style(h).ordinal());
    }

    @Override
    public int caveProfile(int x, int y, int z) {
        CaveBiome slot = this.caves != null ? this.caves.caveBiome(x, y, z) : CaveBiome.NONE;
        if (slot == CaveBiome.SURFACE || slot == CaveBiome.NONE) {
            return CaveProfile.PLAIN.ordinal();
        }
        Holder<Biome> biome = this.biome(x, y, z);
        // A slot without biomes falls back (to plain rock under the surface biome): only the slot's own biomes take its profile.
        boolean cave = this.source instanceof VastlandsBiomeSource vastlands && vastlands.slots().get(slot).contains(biome);
        return this.profiles.computeIfAbsent(biome, h -> profile(h, slot, cave).ordinal());
    }

    @Override
    public double mossBlend(int x, int y, int z) {
        return this.blends.computeIfAbsent(this.biome(x, y, z), h -> BiomeColors.mossBlend(h.value()));
    }

    /**
     * The world's natural formations, bound to its biomes on first use, or empty outside Vastlands
     * worlds.
     */
    public static Optional<VastlandsNature> nature(WorldGenLevel level, ChunkGenerator generator) {
        Optional<GenerationContext> context = GenerationContext.of(level.getLevel());
        if (context.isEmpty() || !(context.get().systems().nature() instanceof VastlandsNature nature)) {
            return Optional.empty();
        }
        BiomeSource source = generator.getBiomeSource();
        if (nature.model() == null || nature.boundTo() != source) {
            VastlandsCaves caves = context.get().systems().caves() instanceof VastlandsCaves vc ? vc : null;
            nature.bind(source, new NatureBiomes(source, level.getLevel().getChunkSource().randomState().sampler(), caves));
        }
        return Optional.of(nature);
    }
}
