package com.sincerelyuq.vastlands.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sincerelyuq.vastlands.Vastlands;
import com.sincerelyuq.vastlands.worldgen.context.GenerationContext;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSystems;
import com.sincerelyuq.vastlands.worldgen.system.Passthrough;
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
     * While every density stage is a passthrough, the Vastlands router must reproduce vanilla's
     * overworld router exactly. Skipped automatically once a system is implemented.
     */
    @Test
    void passthroughRouterReproducesVanillaDensity(MinecraftServer server) {
        RegistryAccess registries = server.registryAccess();
        var noises = registries.lookupOrThrow(Registries.NOISE);
        var settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS);
        RandomState vanilla = RandomState.create(settings.getOrThrow(NoiseGeneratorSettings.OVERWORLD).value(), noises, SEED);
        RandomState vastlands = RandomState.create(settings.getOrThrow(NOISE).value(), noises, SEED);

        assertTrue(GenerationContext.of(vanilla).isEmpty());
        GenerationSystems systems = GenerationContext.of(vastlands).orElseThrow().systems();
        assumeTrue(systems.terrain() == Passthrough.INSTANCE
                && systems.hydrology() == Passthrough.INSTANCE
                && systems.caves() == Passthrough.INSTANCE, "a density stage is implemented");

        List<Function<NoiseRouter, DensityFunction>> outputs = List.of(
                NoiseRouter::finalDensity, NoiseRouter::initialDensityWithoutJaggedness, NoiseRouter::depth,
                NoiseRouter::continents, NoiseRouter::erosion, NoiseRouter::ridges,
                NoiseRouter::temperature, NoiseRouter::vegetation);
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
}
