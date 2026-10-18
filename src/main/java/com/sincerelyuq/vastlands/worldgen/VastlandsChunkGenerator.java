package com.sincerelyuq.vastlands.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sincerelyuq.vastlands.worldgen.context.GenerationContext;
import com.sincerelyuq.vastlands.worldgen.terrain.TerrainColumn;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Chunk generator of the Vastlands overworld ({@code "type": "vastlands:noise"}).
 *
 * <p>Behaves exactly like {@code minecraft:noise}: terrain comes from the noise router of its
 * settings, where the Vastlands systems are bound as density stages. It exists so that the world
 * preset references a Vastlands generator type from the first release, giving later systems a place
 * for generation steps that cannot be expressed as density (for example, river water above sea
 * level) without changing the generator type of existing worlds.
 *
 * <p>It must stay a {@link NoiseBasedChunkGenerator}: {@code ChunkMap} only builds a level's
 * {@code RandomState} from the generator's noise settings (and thereby binds the Vastlands stages)
 * when the generator is an instance of that class.
 */
public class VastlandsChunkGenerator extends NoiseBasedChunkGenerator {
    public static final MapCodec<VastlandsChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
                            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(NoiseBasedChunkGenerator::generatorSettings)
                    )
                    .apply(instance, instance.stable(VastlandsChunkGenerator::new))
    );

    public VastlandsChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings) {
        super(biomeSource, settings);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        super.addDebugScreenInfo(info, random, pos);
        GenerationContext.of(random).ifPresentOrElse(context -> {
            info.add("Vastlands: " + context.systems().describe());
            TerrainColumn column = context.systems().terrain().column(pos.getX(), pos.getZ());
            info.add(String.format(Locale.ROOT, "Vastlands terrain: surface %.1f base %.1f mountain %.2f plateau %.2f slope %.2f gully %.1f",
                    context.systems().surfaceHeight(pos.getX(), pos.getZ()), column.baseHeight(), column.mountain(),
                    column.plateau(), column.slope(), column.gullyDepth()));
        }, () -> info.add("Vastlands: no generation context (noise settings contain no Vastlands stage)"));
    }
}
