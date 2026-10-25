package com.sincerelyuq.vastlands.tools;

import com.sincerelyuq.vastlands.worldgen.caves.CaveBiome;
import com.sincerelyuq.vastlands.worldgen.caves.VastlandsCaves;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSystems;
import com.sincerelyuq.vastlands.worldgen.system.HydrologySystem;
import com.sincerelyuq.vastlands.worldgen.system.TerrainParameter;
import java.util.Locale;
import net.minecraft.world.level.biome.Climate;

/**
 * The biome the game would pick for a block, without the game (for {@link CaveSurvey}): what the
 * Vastlands biome source does with vanilla's tags (no other mods installed): the cave slot of the
 * block, plain rock and near-surface blocks from vanilla's overworld table on the router outputs.
 */
final class CaveSurveyBiomes {
    private CaveSurveyBiomes() {
    }

    static String biome(GenerationSystems systems, int x, int y, int z) {
        CaveBiome slot = ((VastlandsCaves) systems.caves()).caveBiome(x, y, z);
        return switch (slot) {
            case SURFACE -> vanilla(systems, x, y, z);
            case LUSH -> "lush_caves";
            case DRIPSTONE -> "dripstone_caves";
            case DEEP_DARK -> "deep_dark";
            // Without other mods these slots fall back to plain rock.
            case CRYSTAL, VOLCANIC, OTHER -> slot.name().toLowerCase(Locale.ROOT) + " (plain without mods)";
            case NONE -> "plain rock";
        };
    }

    /** Vanilla's overworld table applied to the router outputs of the block. */
    static String vanilla(GenerationSystems systems, int x, int y, int z) {
        HydrologySystem h = systems.hydrology();
        Climate.TargetPoint target = Climate.target(
                (float) h.parameter(TerrainParameter.TEMPERATURE, x, y, z),
                (float) h.parameter(TerrainParameter.VEGETATION, x, y, z),
                (float) h.parameter(TerrainParameter.CONTINENTALNESS, x, y, z),
                (float) h.parameter(TerrainParameter.EROSION, x, y, z),
                (float) h.parameter(TerrainParameter.DEPTH, x, y, z),
                (float) h.parameter(TerrainParameter.WEIRDNESS, x, y, z));
        return TerrainPreview.BIOME_TABLE.findValue(target).location().getPath();
    }
}
