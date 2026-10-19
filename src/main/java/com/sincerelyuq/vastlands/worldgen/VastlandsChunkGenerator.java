package com.sincerelyuq.vastlands.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sincerelyuq.vastlands.worldgen.context.GenerationContext;
import com.sincerelyuq.vastlands.worldgen.geography.GeographySample;
import com.sincerelyuq.vastlands.worldgen.ocean.OceanSurface;
import com.sincerelyuq.vastlands.worldgen.terrain.TerrainColumn;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/**
 * Chunk generator of the Vastlands overworld ({@code "type": "vastlands:noise"}).
 *
 * <p>Terrain comes from the noise router of its settings, where the Vastlands systems are bound as
 * density stages, exactly like {@code minecraft:noise}. On top of that it runs the generation steps
 * that cannot be expressed as density (see {@link OceanSurface}):
 * <ul>
 *     <li>after noise filling and again after carving, water instead of lava (and the aquifer's
 *     rock lid above it) in the open water of abyssal trenches below Y -54;</li>
 *     <li>after feature decoration, the fractured ice fields of polar seas.</li>
 * </ul>
 * Later systems add their own steps here (for example, river water above sea level).
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
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        CompletableFuture<ChunkAccess> filled = super.fillFromNoise(blender, randomState, structureManager, chunk);
        Optional<GenerationContext> context = GenerationContext.of(randomState);
        if (context.isEmpty()) {
            return filled;
        }
        return filled.thenApply(result -> {
            OceanSurface.fillDeepWater(result, context.get().systems());
            return result;
        });
    }

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk) {
        super.applyCarvers(level, seed, random, biomeManager, structureManager, chunk);
        // Carvers cut through water too, and below Y -54 their tunnels fill with lava: reopen trenches.
        GenerationContext.of(random).ifPresent(context -> OceanSurface.fillDeepWater(chunk, context.systems()));
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        super.applyBiomeDecoration(level, chunk, structureManager);
        // After this chunk's features, so vanilla's freeze_top_layer cannot close the cracks again.
        GenerationContext.of(level.getLevel()).ifPresent(context -> OceanSurface.placeIceFields(chunk, context.systems(), this.getSeaLevel()));
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
            GeographySample geography = column.geography();
            info.add(String.format(Locale.ROOT, "Vastlands geography: %s temperature %.2f (sea %.2f) moisture %.2f rain shadow %.2f dry %.2f plains %.2f ice %.2f",
                    geography.landform(), geography.temperature(), geography.seaLevelTemperature(), geography.moisture(),
                    geography.rainShadow(), geography.dryness(), geography.plains(), geography.iceCover()));
        }, () -> info.add("Vastlands: no generation context (noise settings contain no Vastlands stage)"));
    }
}
