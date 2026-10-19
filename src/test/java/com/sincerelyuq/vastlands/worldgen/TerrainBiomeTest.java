package com.sincerelyuq.vastlands.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sincerelyuq.vastlands.Vastlands;
import com.sincerelyuq.vastlands.worldgen.context.GenerationContext;
import com.sincerelyuq.vastlands.worldgen.system.TerrainSystem;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Biomes are chosen by vanilla's overworld biome table from climate parameters the terrain
 * computes, so they must agree with the terrain: oceans under water, land biomes on land, mountain
 * biomes on high mountains, and no river biomes until Hydrology makes rivers.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class TerrainBiomeTest {
    private static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET, Vastlands.id("vastlands"));

    @Test
    void surfaceBiomesMatchTheTerrain(MinecraftServer server) {
        RegistryAccess registries = server.registryAccess();
        VastlandsChunkGenerator generator = (VastlandsChunkGenerator) registries.lookupOrThrow(Registries.WORLD_PRESET)
                .getOrThrow(PRESET).value().overworld().orElseThrow().generator();
        RandomState state = RandomState.create(generator.generatorSettings().value(), registries.lookupOrThrow(Registries.NOISE), 20261018L);
        TerrainSystem terrain = GenerationContext.of(state).orElseThrow().systems().terrain();
        BiomeSource biomes = generator.getBiomeSource();
        Climate.Sampler sampler = state.sampler();

        int underwater = 0, oceanUnderwater = 0;
        int dry = 0, oceanOnLand = 0;
        int high = 0, highland = 0;
        int rivers = 0;
        for (int x = -60000; x <= 60000; x += 400) {
            for (int z = -60000; z <= 60000; z += 400) {
                double height = terrain.column(x, z).height();
                int y = (int) Math.floor(height);
                Holder<Biome> biome = biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), sampler);
                boolean ocean = biome.is(BiomeTags.IS_OCEAN);
                rivers += biome.is(BiomeTags.IS_RIVER) ? 1 : 0;
                if (height < 58.0) {
                    underwater++;
                    oceanUnderwater += ocean ? 1 : 0;
                } else if (height > 66.0) {
                    dry++;
                    oceanOnLand += ocean ? 1 : 0;
                }
                if (height > 300.0) {
                    high++;
                    highland += biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_BADLANDS) || biome.is(BiomeTags.IS_HILL)
                            || biome.is(Biomes.GROVE) || biome.is(Biomes.SAVANNA_PLATEAU) ? 1 : 0;
                }
            }
        }
        Vastlands.LOGGER.info("TerrainBiomeTest: underwater {} (ocean {}), dry {} (ocean {}), above 300 {} (highland {}), rivers {}",
                underwater, oceanUnderwater, dry, oceanOnLand, high, highland, rivers);
        assertEquals(0, rivers, "river biomes without rivers");
        assertTrue(oceanUnderwater > 0.97 * underwater, "ocean biomes under water: " + oceanUnderwater + " of " + underwater);
        assertTrue(oceanOnLand < 0.005 * dry, "ocean biomes on land: " + oceanOnLand + " of " + dry);
        assertTrue(high > 50, "too few high columns to judge: " + high);
        assertTrue(highland > 0.85 * high, "mountain biomes above Y 300: " + highland + " of " + high);
    }
}
