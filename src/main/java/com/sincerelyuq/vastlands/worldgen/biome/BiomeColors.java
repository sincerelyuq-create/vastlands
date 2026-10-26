package com.sincerelyuq.vastlands.worldgen.biome;

import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;

/**
 * Colours of a biome's ground as the client draws them, from the biome's own data on the server
 * (see {@link GrassColors}): the grass colour override or vanilla's colour map at the biome's
 * temperature and downfall, then its grass colour modifier (dark forest, swamp). Biomes of other
 * mods are read the same way, so a datapack or mod biome gets its own colour without a list.
 */
public final class BiomeColors {
    private BiomeColors() {
    }

    /** The grass tint of a biome (modifiers applied; the swamp's two colours averaged). */
    public static int grassTint(Biome biome) {
        BiomeSpecialEffects effects = biome.getModifiedSpecialEffects();
        Biome.ClimateSettings climate = biome.getModifiedClimateSettings();
        int base = effects.getGrassColorOverride().orElseGet(() -> GrassColors.tint(climate.temperature(), climate.downfall()));
        BiomeSpecialEffects.GrassColorModifier modifier = effects.getGrassColorModifier();
        if (modifier == BiomeSpecialEffects.GrassColorModifier.NONE) {
            return base;
        }
        if (modifier == BiomeSpecialEffects.GrassColorModifier.DARK_FOREST) {
            return GrassColors.darkForest(base);
        }
        if (modifier == BiomeSpecialEffects.GrassColorModifier.SWAMP) {
            return GrassColors.swamp();
        }
        try {
            // Other mods' modifiers (an extensible enum): their colour at the origin.
            return modifier.modifyColor(0.0, 0.0, base);
        } catch (RuntimeException | LinkageError e) {
            return base;
        }
    }

    /** 0..1: how well moss blends into the biome's grass (see {@link GrassColors#mossBlend}). */
    public static double mossBlend(Biome biome) {
        return GrassColors.mossBlend(GrassColors.grassTop(grassTint(biome)));
    }
}
