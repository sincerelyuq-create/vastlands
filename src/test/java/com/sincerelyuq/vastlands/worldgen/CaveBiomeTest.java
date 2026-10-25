package com.sincerelyuq.vastlands.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sincerelyuq.vastlands.Vastlands;
import com.sincerelyuq.vastlands.worldgen.biome.VastlandsBiomeSource;
import com.sincerelyuq.vastlands.worldgen.caves.CaveBiome;
import com.sincerelyuq.vastlands.worldgen.caves.VastlandsCaves;
import com.sincerelyuq.vastlands.worldgen.context.GenerationContext;
import com.sincerelyuq.vastlands.worldgen.geography.GeographySample;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSystems;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * The Vastlands biome source on a datapack-loaded server: the cave slots resolve from their tags,
 * the surface biomes are the delegate's, and underground the cave biomes appear in contiguous
 * provinces with plausible climates (lush caves never under frozen ground or the sea), the deep
 * dark rare and present at Y -27 (ancient cities), every biome among the source's possible biomes.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class CaveBiomeTest {
    private static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET, Vastlands.id("vastlands"));
    private static final long SEED = 20261018L;

    @Test
    void caveBiomesFillContiguousPlausibleProvinces(MinecraftServer server) {
        RegistryAccess registries = server.registryAccess();
        VastlandsChunkGenerator generator = (VastlandsChunkGenerator) registries.lookupOrThrow(Registries.WORLD_PRESET)
                .getOrThrow(PRESET).value().overworld().orElseThrow().generator();
        VastlandsBiomeSource source = assertInstanceOf(VastlandsBiomeSource.class, generator.getBiomeSource());
        Set<Holder<Biome>> possible = source.possibleBiomes();
        for (ResourceKey<Biome> key : new ResourceKey[] {Biomes.LUSH_CAVES, Biomes.DRIPSTONE_CAVES, Biomes.DEEP_DARK}) {
            assertTrue(possible.stream().anyMatch(h -> h.is(key)), "possible biomes lack " + key.location());
        }
        // Without other mods the crystal and hot slots fall back to plain rock (no biome of their own).
        assertTrue(source.slots().get(CaveBiome.LUSH).stream().allMatch(h -> h.is(Biomes.LUSH_CAVES)), "lush slot");
        assertTrue(source.slots().get(CaveBiome.DEEP_DARK).stream().allMatch(h -> h.is(Biomes.DEEP_DARK)), "deep dark slot");

        RandomState state = RandomState.create(generator.generatorSettings().value(), registries.lookupOrThrow(Registries.NOISE), SEED);
        GenerationSystems systems = GenerationContext.of(state).orElseThrow().systems();
        VastlandsCaves caves = (VastlandsCaves) systems.caves();
        Climate.Sampler sampler = state.sampler();
        assertEquals(GenerationContext.of(state).orElseThrow(), GenerationContext.of(sampler), "the sampler must lead to its world's context");

        int surfaceChecked = 0;
        int surfaceDifferent = 0;
        Map<String, Integer> counts = new HashMap<>();
        int samples = 0;
        int lushCold = 0;
        int lushSea = 0;
        int deepDarkAtCities = 0;
        int citySamples = 0;
        for (int x = -24000; x <= 24000; x += 600) {
            for (int z = -24000; z <= 24000; z += 600) {
                double ground = systems.surfaceHeight(x, z);
                int top = (int) Math.floor(ground);
                // Near the ground: the delegate's surface biome, unchanged.
                Holder<Biome> near = source.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(top - 4), QuartPos.fromBlock(z), sampler);
                Holder<Biome> delegate = source.surface().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(top - 4), QuartPos.fromBlock(z), sampler);
                surfaceChecked++;
                surfaceDifferent += near.equals(delegate) ? 0 : 1;
                GeographySample g = systems.terrain().column(x, z).geography();
                for (int y = top - 24; y > -240; y -= 24) {
                    Holder<Biome> biome = source.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), sampler);
                    assertTrue(possible.contains(biome), "biome outside the possible biomes at " + x + " " + y + " " + z + ": " + biome);
                    String name = biome.unwrapKey().map(k -> k.location().getPath()).orElse("?");
                    counts.merge(name, 1, Integer::sum);
                    samples++;
                    if (biome.is(Biomes.LUSH_CAVES)) {
                        lushCold += g.temperature() < -0.5 ? 1 : 0;
                        lushSea += ground < 55 ? 1 : 0;
                    }
                }
                if (top > 0) {
                    citySamples++;
                    deepDarkAtCities += source.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(-27), QuartPos.fromBlock(z), sampler).is(Biomes.DEEP_DARK) ? 1 : 0;
                }
            }
        }
        double lush = counts.getOrDefault("lush_caves", 0) / (double) samples;
        double dripstone = counts.getOrDefault("dripstone_caves", 0) / (double) samples;
        double deepDark = counts.getOrDefault("deep_dark", 0) / (double) samples;
        Vastlands.LOGGER.info("CaveBiomeTest: {} underground samples: lush {}, dripstone {}, deep dark {}; deep dark at Y -27 in {} of {} land columns; surface differs in {} of {}",
                samples, lush, dripstone, deepDark, deepDarkAtCities, citySamples, surfaceDifferent, surfaceChecked);
        assertEquals(0, surfaceDifferent, "surface biomes changed");
        assertTrue(lush > 0.03 && lush < 0.3, "lush caves share " + lush);
        assertTrue(dripstone > 0.08 && dripstone < 0.5, "dripstone share " + dripstone);
        assertTrue(deepDark > 0.003 && deepDark < 0.12, "deep dark share " + deepDark);
        assertTrue(deepDarkAtCities > 0, "no deep dark at Y -27 for ancient cities");
        assertEquals(0, lushCold, "lush caves under frozen climates");
        assertTrue(lushSea * 50 < counts.getOrDefault("lush_caves", 1), "lush caves under the sea: " + lushSea);

        // Provinces: along rows 120 blocks under the ground, the cave biome changes rarely.
        int changes = 0;
        int steps = 0;
        for (int z = -8000; z <= 8000; z += 2000) {
            CaveBiome previous = null;
            for (int x = -8000; x <= 8000; x += 16) {
                int y = (int) Math.floor(systems.surfaceHeight(x, z)) - 120;
                CaveBiome slot = caves.caveBiome(x, y, z);
                if (previous != null) {
                    changes += slot != previous ? 1 : 0;
                    steps++;
                }
                previous = slot;
            }
        }
        double run = 16.0 * steps / Math.max(1, changes);
        Vastlands.LOGGER.info("CaveBiomeTest: mean cave biome run {} blocks along rows 120 blocks deep", run);
        assertTrue(run > 200.0, "cave biome regions too small: " + run);
    }
}
