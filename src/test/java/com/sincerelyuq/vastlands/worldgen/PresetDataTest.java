package com.sincerelyuq.vastlands.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sincerelyuq.vastlands.Vastlands;
import com.sincerelyuq.vastlands.worldgen.context.GenerationContext;
import com.sincerelyuq.vastlands.worldgen.system.HydrologySystem;
import com.sincerelyuq.vastlands.worldgen.system.TerrainParameter;
import com.sincerelyuq.vastlands.worldgen.system.TerrainSystem;
import com.sincerelyuq.vastlands.worldgen.terrain.TerrainColumn;
import com.sincerelyuq.vastlands.worldgen.terrain.VastlandsTerrain;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.WorldPresetTags;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Checks the worldgen data as the game actually loads it from the mod's data pack. */
@ExtendWith(EphemeralTestServerProvider.class)
class PresetDataTest {
    private static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET, Vastlands.id("vastlands"));
    private static final ResourceKey<NoiseGeneratorSettings> NOISE = ResourceKey.create(Registries.NOISE_SETTINGS, Vastlands.id("overworld"));
    private static final long SEED = 20261018L;

    @Test
    void presetIsListedWithTheNormalWorldTypes(MinecraftServer server) {
        Holder<WorldPreset> preset = server.registryAccess().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(PRESET);
        assertTrue(preset.is(WorldPresetTags.NORMAL), "the create-world screen lists presets from #minecraft:normal");
    }

    @Test
    void presetOverworldUsesVastlandsGeneratorAndHeight(MinecraftServer server) {
        LevelStem overworld = server.registryAccess().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(PRESET).value().overworld().orElseThrow();
        DimensionType type = overworld.type().value();
        assertEquals(WorldHeight.MIN_Y, type.minY());
        assertEquals(WorldHeight.HEIGHT, type.height());

        VastlandsChunkGenerator generator = assertInstanceOf(VastlandsChunkGenerator.class, overworld.generator());
        assertTrue(generator.generatorSettings().is(NOISE));
        assertEquals(WorldHeight.MIN_Y, generator.getMinY());
        assertEquals(WorldHeight.HEIGHT, generator.getGenDepth());
    }

    /**
     * The Vastlands router differs from vanilla only where the terrain owns it (landscape, slides,
     * continents, erosion, ridges, depth, initial density, temperature, vegetation). Aquifer and
     * ore vein outputs must stay vanilla bit for bit.
     */
    @Test
    void routerKeepsVanillaOutputsTheTerrainDoesNotOwn(MinecraftServer server) {
        RegistryAccess registries = server.registryAccess();
        var noises = registries.lookupOrThrow(Registries.NOISE);
        var settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS);
        RandomState vanilla = RandomState.create(settings.getOrThrow(NoiseGeneratorSettings.OVERWORLD).value(), noises, SEED);
        RandomState vastlands = RandomState.create(settings.getOrThrow(NOISE).value(), noises, SEED);

        assertTrue(GenerationContext.of(vanilla).isEmpty());
        assertTrue(GenerationContext.of(vastlands).isPresent());
        List<Function<NoiseRouter, DensityFunction>> outputs = List.of(
                NoiseRouter::barrierNoise,
                NoiseRouter::fluidLevelFloodednessNoise, NoiseRouter::fluidLevelSpreadNoise, NoiseRouter::lavaNoise,
                NoiseRouter::veinToggle, NoiseRouter::veinRidged, NoiseRouter::veinGap);
        for (int x = -20000; x <= 20000; x += 1733) {
            for (int z = -20000; z <= 20000; z += 1911) {
                for (int y = WorldHeight.MIN_Y; y <= WorldHeight.MAX_Y; y += 29) {
                    DensityFunction.SinglePointContext pos = new DensityFunction.SinglePointContext(x, y, z);
                    for (Function<NoiseRouter, DensityFunction> output : outputs) {
                        assertEquals(output.apply(vanilla.router()).compute(pos), output.apply(vastlands.router()).compute(pos), "at " + pos);
                    }
                }
            }
        }
    }

    /**
     * The terrain-owned router outputs read the terrain system of the world; weirdness, depth and
     * the initial density come through the hydrology (river biomes, carved floors).
     */
    @Test
    void terrainOwnedOutputsFollowTheTerrain(MinecraftServer server) {
        RandomState state = vastlandsState(server);
        TerrainSystem terrain = GenerationContext.of(state).orElseThrow().systems().terrain();
        HydrologySystem hydrology = GenerationContext.of(state).orElseThrow().systems().hydrology();
        NoiseRouter router = state.router();
        for (int x = -6000; x <= 6000; x += 999) {
            for (int z = -6000; z <= 6000; z += 1013) {
                TerrainColumn column = terrain.column(x & ~3, z & ~3);
                DensityFunction.SinglePointContext pos = new DensityFunction.SinglePointContext(x, 70, z);
                assertEquals(column.climateContinentalness(), router.continents().compute(pos));
                assertEquals(column.climateErosion(), router.erosion().compute(pos));
                assertEquals(hydrology.parameter(TerrainParameter.WEIRDNESS, x, 70, z), router.ridges().compute(pos));
                assertEquals(column.climateTemperature(), router.temperature().compute(pos));
                assertEquals(column.climateHumidity(), router.vegetation().compute(pos));
                assertEquals(hydrology.parameter(TerrainParameter.DEPTH, x, 70, z), router.depth().compute(pos));
                // The preliminary surface (initial density threshold 0.390625) never lies above the
                // generated surface, channels included.
                int above = (int) Math.ceil(hydrology.surfaceHeight(x & ~3, z & ~3));
                assertTrue(router.initialDensityWithoutJaggedness().compute(new DensityFunction.SinglePointContext(x & ~3, above, z & ~3)) <= 0.390625);
            }
        }
    }

    /**
     * Final density: solid at the bottom of the world, air at the top, and the landscape surface
     * where the terrain says it is (away from caves: checked at the surface and just above it).
     */
    @Test
    void finalDensityMatchesTheTerrainSurface(MinecraftServer server) {
        RandomState state = vastlandsState(server);
        HydrologySystem hydrology = GenerationContext.of(state).orElseThrow().systems().hydrology();
        DensityFunction finalDensity = state.router().finalDensity();
        int checked = 0;
        for (int x = -12000; x <= 12000; x += 1204) {
            for (int z = -12000; z <= 12000; z += 1416) {
                int bx = x & ~3;
                int bz = z & ~3;
                double surface = hydrology.surfaceHeight(bx, bz);
                assertTrue(finalDensity.compute(new DensityFunction.SinglePointContext(bx, WorldHeight.MIN_Y, bz)) > 0.0, "bottom must be solid");
                assertTrue(finalDensity.compute(new DensityFunction.SinglePointContext(bx, WorldHeight.MAX_Y, bz)) < 0.0, "top must be air");
                assertTrue(finalDensity.compute(new DensityFunction.SinglePointContext(bx, (int) Math.ceil(surface) + 4, bz)) < 0.0,
                        "air above the surface at " + bx + "," + bz);
                checked++;
            }
        }
        assertTrue(checked > 300);
    }

    /**
     * The bottom slide is vanilla's (-64..-40) under ordinary ground and moves down under abyssal
     * trenches, so their water reaches the floor: open above the floor, solid below it.
     */
    @Test
    void trenchesStayOpenBelowTheVanillaSlide(MinecraftServer server) {
        RandomState state = vastlandsState(server);
        TerrainSystem terrain = GenerationContext.of(state).orElseThrow().systems().terrain();
        DensityFunction finalDensity = state.router().finalDensity();
        for (int y = -80; y <= 0; y++) {
            assertEquals(Math.clamp((y + 64) / 24.0, 0.0, 1.0), VastlandsTerrain.bottomSlide(70.0, y), 1e-12);
        }
        int bestX = 0;
        int bestZ = 0;
        double lowest = Double.POSITIVE_INFINITY;
        for (int x = -200_000; x < 200_000; x += 512) {
            for (int z = -200_000; z < 200_000; z += 2048) {
                double h = terrain.geography().smoothElevation(x, z);
                if (h < lowest) {
                    lowest = h;
                    bestX = x & ~3;
                    bestZ = z & ~3;
                }
            }
        }
        double floor = terrain.column(bestX, bestZ).height();
        Vastlands.LOGGER.info("PresetDataTest: deepest trench sample Y {} at {}, {}", floor, bestX, bestZ);
        assertTrue(floor < -80.0, "no trench found below Y -80: " + floor);
        for (int y = (int) Math.ceil(floor) + 1; y < -30; y += 4) {
            assertTrue(finalDensity.compute(new DensityFunction.SinglePointContext(bestX, y, bestZ)) < 0.0, "trench water at Y " + y);
        }
        assertTrue(finalDensity.compute(new DensityFunction.SinglePointContext(bestX, (int) floor - 40, bestZ)) > 0.0, "rock below the trench floor");
    }

    private static RandomState vastlandsState(MinecraftServer server) {
        RegistryAccess registries = server.registryAccess();
        return RandomState.create(
                registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NOISE).value(),
                registries.lookupOrThrow(Registries.NOISE),
                SEED);
    }
}
