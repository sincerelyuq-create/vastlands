package com.sincerelyuq.vastlands.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sincerelyuq.vastlands.worldgen.context.GenerationContext;
import com.sincerelyuq.vastlands.worldgen.geography.GeographySample;
import com.sincerelyuq.vastlands.worldgen.hydrology.HydrologyModel;
import com.sincerelyuq.vastlands.worldgen.hydrology.RiverWater;
import com.sincerelyuq.vastlands.worldgen.hydrology.VastlandsHydrology;
import com.sincerelyuq.vastlands.worldgen.hydrology.WaterColumn;
import com.sincerelyuq.vastlands.worldgen.ocean.OceanSurface;
import com.sincerelyuq.vastlands.worldgen.terrain.TerrainColumn;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/**
 * Chunk generator of the Vastlands overworld ({@code "type": "vastlands:noise"}).
 *
 * <p>Terrain comes from the noise router of its settings, where the Vastlands systems are bound as
 * density stages, exactly like {@code minecraft:noise}. On top of that it runs the generation steps
 * that cannot be expressed as density (see {@link OceanSurface} and {@link RiverWater}):
 * <ul>
 *     <li>after noise filling and again after carving, water instead of lava (and the aquifer's
 *     rock lid above it) in the open water of abyssal trenches below Y -54;</li>
 *     <li>after noise filling (before surface rules) and again after carving, the water of rivers
 *     and lakes above sea level, their sealed beds and banks, and the curtains of waterfalls;</li>
 *     <li>after feature decoration, the fractured ice fields of polar seas.</li>
 * </ul>
 * Heightmap queries made before the chunk exists (structure placement) include river and lake
 * water, as vanilla's include the sea.
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
            RiverWater.place(result, context.get().systems().hydrology(), false);
            return result;
        });
    }

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk) {
        super.applyCarvers(level, seed, random, biomeManager, structureManager, chunk);
        // Carvers cut through water too, and below Y -54 their tunnels fill with lava: reopen trenches.
        // ...and through river beds and banks: refill and reseal rivers and lakes.
        GenerationContext.of(random).ifPresent(context -> {
            OceanSurface.fillDeepWater(chunk, context.systems());
            RiverWater.place(chunk, context.systems().hydrology(), true);
        });
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        int base = super.getBaseHeight(x, z, type, level, random);
        Optional<GenerationContext> context = GenerationContext.of(random);
        if (context.isEmpty() || !type.isOpaque().test(Blocks.WATER.defaultBlockState())) {
            return base;
        }
        WaterColumn water = context.get().systems().hydrology().water(x, z);
        return water.placed() ? Math.max(base, water.waterTop() + 1) : base;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        NoiseColumn column = super.getBaseColumn(x, z, height, random);
        GenerationContext.of(random).ifPresent(context -> {
            WaterColumn water = context.systems().hydrology().water(x, z);
            if (water.placed()) {
                BlockState state = Blocks.WATER.defaultBlockState();
                for (int y = Math.max(height.getMinY(), water.groundTop() + 1); y <= Math.min(height.getMaxY(), water.waterTop()); y++) {
                    column.setBlock(y, state);
                }
                for (int y = water.waterTop() + 1; y <= Math.min(height.getMaxY(), water.surfaceTop()); y++) {
                    column.setBlock(y, Blocks.AIR.defaultBlockState());
                }
            }
        });
        return column;
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
            if (context.systems().hydrology() instanceof VastlandsHydrology hydrology) {
                WaterColumn water = hydrology.water(pos.getX(), pos.getZ());
                HydrologyModel.Column river = hydrology.describe(pos.getX(), pos.getZ());
                info.add(String.format(Locale.ROOT, "Vastlands hydrology: %s%s ground %d, nearest river %s",
                        water.kind(), water.wet() ? " top " + water.waterTop() : "", water.groundTop(),
                        river.riverEdge < 400.0 ? String.format(Locale.ROOT, "%.0f wide, top %d, %.0f blocks away", river.riverWidth, river.riverTop, Math.max(0.0, river.riverEdge)) : "none"));
            }
        }, () -> info.add("Vastlands: no generation context (noise settings contain no Vastlands stage)"));
    }
}
