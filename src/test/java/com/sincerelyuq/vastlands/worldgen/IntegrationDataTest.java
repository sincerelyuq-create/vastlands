package com.sincerelyuq.vastlands.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sincerelyuq.vastlands.Vastlands;
import com.sincerelyuq.vastlands.worldgen.biome.VastlandsBiomeSource;
import com.sincerelyuq.vastlands.worldgen.caves.CaveBiome;
import com.sincerelyuq.vastlands.worldgen.compat.AddBiomesModifier;
import com.sincerelyuq.vastlands.worldgen.compat.CompatBlocks;
import com.sincerelyuq.vastlands.worldgen.compat.IntegrationFeatures;
import com.sincerelyuq.vastlands.worldgen.compat.IntegrationSurface;
import com.sincerelyuq.vastlands.worldgen.compat.ModBlock;
import com.sincerelyuq.vastlands.worldgen.compat.StructureSites;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.common.world.StructureModifier;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Agent 7's integration data on a datapack-loaded server without Sincerelyuq's Biomes or
 * Sincerelyuq's Overhaul: every tag, structure modifier and preset field loads, every optional
 * entry is simply absent, and nothing of the integration is active (no regional or luminous
 * biome, empty crystal and volcanic slots as before, no surface rules, no mod blocks), so a
 * Vastlands world generates as without the integration.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class IntegrationDataTest {
    private static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET, Vastlands.id("vastlands"));
    private static final List<String> THEMES = List.of("ashen_wastes", "bone_dunes", "lotus_pools", "painted_strata", "twilight_thicket",
            "amethyst_caves", "smoldering_cave", "bioluminescent_depths");

    @Test
    void integrationIsInactiveWithoutTheMods(MinecraftServer server) {
        VastlandsChunkGenerator generator = assertInstanceOf(VastlandsChunkGenerator.class,
                server.registryAccess().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(PRESET).value().overworld().orElseThrow().generator());
        VastlandsBiomeSource source = assertInstanceOf(VastlandsBiomeSource.class, generator.getBiomeSource());
        assertEquals(0, source.regionMask(), "regional biomes without Sincerelyuq's Biomes");
        assertTrue(source.luminousBiomes().isEmpty(), "luminous biomes without Sincerelyuq's Biomes");
        assertTrue(source.regionBiomes().values().stream().allMatch(List::isEmpty));
        // The crystal and volcanic slots have no biome of their own and fall back as before (crystal
        // to dripstone, volcanic to plain rock); vanilla's slots keep their biomes.
        assertEquals(source.slots().get(CaveBiome.DRIPSTONE), source.slots().get(CaveBiome.CRYSTAL));
        assertTrue(source.slots().get(CaveBiome.VOLCANIC).isEmpty());
        assertFalse(source.slots().get(CaveBiome.LUSH).isEmpty());

        assertFalse(CompatBlocks.biomesInstalled());
        for (ModBlock block : ModBlock.VALUES) {
            assertFalse(CompatBlocks.present(block), block.id());
        }
        assertTrue(IntegrationSurface.rules().isEmpty(), "integration surface rules without the mod");
        SurfaceRules.RuleSource original = SurfaceRules.state(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        assertSame(original, IntegrationSurface.wrap(original));
    }

    @Test
    void tagsAndModifiersLoadWithAbsentEntries(MinecraftServer server) {
        HolderLookup.RegistryLookup<Biome> biomes = server.registryAccess().lookupOrThrow(Registries.BIOME);
        HolderLookup.RegistryLookup<Structure> structures = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        int tags = 0;
        for (String theme : THEMES) {
            TagKey<Biome> biomeTag = TagKey.create(Registries.BIOME, Vastlands.id("overhaul/" + theme));
            TagKey<Structure> structureTag = TagKey.create(Registries.STRUCTURE, Vastlands.id("overhaul/" + theme));
            assertTrue(biomes.get(biomeTag).isPresent(), biomeTag + " missing");
            assertTrue(structures.get(structureTag).isPresent(), structureTag + " missing");
            assertEquals(0, biomes.get(biomeTag).get().size(), biomeTag + " has entries without the mod");
            assertEquals(0, structures.get(structureTag).get().size(), structureTag + " has entries without the mod");
            tags += 2;
        }
        for (TagKey<Structure> site : List.of(StructureSites.LEVEL_GROUND, StructureSites.DRY_GROUND, StructureSites.SEALED, StructureSites.APART_FROM_SANCTUM)) {
            assertTrue(structures.get(site).isPresent(), site + " missing");
            tags++;
        }
        var placed = server.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
        for (var tag : List.of(IntegrationFeatures.REPLACED, IntegrationFeatures.GUARD_SMALL, IntegrationFeatures.GUARD_MEDIUM, IntegrationFeatures.GUARD_LARGE,
                IntegrationFeatures.LEVEL_GROUND)) {
            assertTrue(placed.get(tag).isPresent(), tag + " missing");
            assertEquals(0, placed.get(tag).get().size(), tag + " has entries without the mod");
            tags++;
        }
        for (String region : List.of("painted_range", "volcanic_waste", "dune_sea", "wetland", "dusk_wood", "keep")) {
            assertTrue(biomes.get(TagKey.create(Registries.BIOME, Vastlands.id("region/" + region))).isPresent(), region);
            tags++;
        }
        for (String path : List.of("nature/cave/amethyst", "nature/cave/smoldering", "nature/cave/luminous", "nature/surface/ashen",
                "nature/surface/strata", "nature/surface/dunes", "cave/luminous", "forest/dusk", "forest/charred")) {
            assertTrue(biomes.get(TagKey.create(Registries.BIOME, Vastlands.id(path))).isPresent(), path);
            tags++;
        }
        var modifiers = server.registryAccess().lookupOrThrow(NeoForgeRegistries.Keys.STRUCTURE_MODIFIERS);
        int found = 0;
        for (String theme : THEMES) {
            StructureModifier modifier = modifiers.getOrThrow(ResourceKey.create(NeoForgeRegistries.Keys.STRUCTURE_MODIFIERS,
                    Vastlands.id("overhaul_" + theme))).value();
            AddBiomesModifier add = assertInstanceOf(AddBiomesModifier.class, modifier);
            assertEquals(0, add.structures().size(), theme);
            found++;
        }
        Vastlands.LOGGER.info(String.format(Locale.ROOT, "IntegrationDataTest: %d tags and %d structure modifiers loaded, all empty without the mods", tags, found));
    }
}
