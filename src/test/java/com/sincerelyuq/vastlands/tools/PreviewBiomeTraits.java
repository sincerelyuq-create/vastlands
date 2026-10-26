package com.sincerelyuq.vastlands.tools;

import com.sincerelyuq.vastlands.worldgen.biome.GrassColors;
import java.util.HashMap;
import java.util.Map;

/**
 * Vanilla biomes' grass colours without the game, for previews and model tests: each overworld
 * biome's temperature, downfall, grass colour override and grass colour modifier as vanilla
 * registers them ({@code OverworldBiomes}), turned into the grass colour the client draws by
 * {@link GrassColors}. {@code BiomeColorsTest} checks every entry against the game's own biomes.
 */
public final class PreviewBiomeTraits {
    /** temperature, downfall, grass override (-1 none), modifier (0 none, 1 dark forest, 2 swamp). */
    static final Map<String, double[]> CLIMATE = new HashMap<>();

    static {
        put(0.8, 0.4, -1, 0, "plains", "sunflower_plains", "beach");
        put(0.0, 0.5, -1, 0, "snowy_plains", "ice_spikes", "frozen_river", "frozen_ocean");
        put(2.0, 0.0, -1, 0, "desert", "savanna", "savanna_plateau", "windswept_savanna");
        put(0.8, 0.9, -1, 2, "swamp", "mangrove_swamp");
        put(0.7, 0.8, -1, 0, "forest", "flower_forest");
        put(0.6, 0.6, -1, 0, "birch_forest", "old_growth_birch_forest");
        put(0.7, 0.8, -1, 1, "dark_forest");
        put(0.7, 0.8, 0x778272, 0, "pale_garden");
        put(0.3, 0.8, -1, 0, "old_growth_pine_taiga");
        put(0.25, 0.8, -1, 0, "old_growth_spruce_taiga", "taiga");
        put(-0.5, 0.4, -1, 0, "snowy_taiga");
        put(0.2, 0.3, -1, 0, "windswept_hills", "windswept_gravelly_hills", "windswept_forest", "stony_shore");
        put(0.95, 0.9, -1, 0, "jungle", "bamboo_jungle");
        put(0.95, 0.8, -1, 0, "sparse_jungle");
        put(2.0, 0.0, 0x90814D, 0, "badlands", "eroded_badlands", "wooded_badlands");
        put(0.5, 0.8, -1, 0, "meadow");
        put(0.5, 0.8, 0xB6DB61, 0, "cherry_grove");
        put(-0.2, 0.8, -1, 0, "grove");
        put(-0.3, 0.9, -1, 0, "snowy_slopes");
        put(-0.7, 0.9, -1, 0, "frozen_peaks", "jagged_peaks");
        put(1.0, 0.3, -1, 0, "stony_peaks");
        put(0.5, 0.5, -1, 0, "river", "ocean", "deep_ocean", "lukewarm_ocean", "deep_lukewarm_ocean", "warm_ocean", "cold_ocean", "deep_cold_ocean",
                "deep_frozen_ocean", "lush_caves");
        put(0.05, 0.3, -1, 0, "snowy_beach");
        put(0.9, 1.0, -1, 0, "mushroom_fields");
        put(0.8, 0.4, -1, 0, "dripstone_caves", "deep_dark");
    }

    private PreviewBiomeTraits() {
    }

    private static void put(double temperature, double downfall, int override, int modifier, String... biomes) {
        for (String b : biomes) {
            CLIMATE.put(b, new double[] {temperature, downfall, override, modifier});
        }
    }

    /** The grass tint of a vanilla biome (its path), or -1 if the table has none. */
    public static int grassTint(String biome) {
        double[] c = CLIMATE.get(biome);
        if (c == null) {
            return -1;
        }
        int base = c[2] >= 0 ? (int) c[2] : GrassColors.tint(c[0], c[1]);
        return c[3] == 1 ? GrassColors.darkForest(base) : (c[3] == 2 ? GrassColors.swamp() : base);
    }

    /** How well moss blends into a vanilla biome's grass (1 for biomes missing from the table). */
    public static double mossBlend(String biome) {
        int tint = grassTint(biome);
        return tint < 0 ? 1.0 : GrassColors.mossBlend(GrassColors.grassTop(tint));
    }
}
