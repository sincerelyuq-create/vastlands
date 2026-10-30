package com.sincerelyuq.vastlands.worldgen.caves;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sincerelyuq.vastlands.Vastlands;
import com.sincerelyuq.vastlands.worldgen.WorldHeight;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSetup;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSystems;
import org.junit.jupiter.api.Test;

/**
 * The bioluminescent depths expanded (Agent 8), only where a luminous cave biome is installed:
 * more of the eligible provinces are luminous and their caves are roomier; without it (the
 * default) the caves stay as they were, and outside the eligible provinces (cold climates here)
 * nothing changes either way. Seed 20261018.
 */
class LuminousCavesTest {
    private static final long SEED = 20261018L;

    private static VastlandsCaves caves(boolean luminous) {
        return (VastlandsCaves) GenerationSystems.create(new GenerationSetup(SEED, WorldHeight.MIN_Y, WorldHeight.HEIGHT, 63, luminous)).caves();
    }

    @Test
    void luminousCavesExpandOnlyWithALuminousBiome() {
        assertFalse(new GenerationSetup(SEED, WorldHeight.MIN_Y, WorldHeight.HEIGHT, 63).luminousCaves(), "no luminous biome without other mods");
        VastlandsCaves plain = caves(false);
        VastlandsCaves expanded = caves(true);
        // A warm, humid region rich in luminous provinces: more luminous cave space and more cave space.
        long[] before = luminous(plain, -6048 - 64, 3552 - 64);
        long[] after = luminous(expanded, -6048 - 64, 3552 - 64);
        Vastlands.LOGGER.info("LuminousCavesTest: cave blocks {} -> {}, luminous {} -> {}", before[0], after[0], before[1], after[1]);
        assertTrue(after[1] > 1.5 * before[1], "luminous cave space: " + before[1] + " -> " + after[1]);
        assertTrue(after[0] > before[0], "roomier luminous caves: " + before[0] + " -> " + after[0]);
        // Under the cold high mountains no province is eligible: the same caves block for block.
        int differ = 0;
        int open = 0;
        for (int x = -11520 - 32; x < -11520 + 32; x += 2) {
            for (int z = -10752 - 32; z < -10752 + 32; z += 2) {
                for (int y = WorldHeight.MIN_Y; y < 300; y++) {
                    boolean a = plain.isCave(x, y, z);
                    differ += a != expanded.isCave(x, y, z) || a && plain.caveFluid(x, y, z) != expanded.caveFluid(x, y, z) ? 1 : 0;
                    open += a ? 1 : 0;
                }
            }
        }
        assertTrue(open > 1000, "too few cave blocks to compare: " + open);
        assertEquals(0, differ, "caves outside the luminous provinces changed");
    }

    /** Cave blocks and luminous cave blocks of a 128-block square (every second column). */
    private static long[] luminous(VastlandsCaves caves, int x0, int z0) {
        long[] out = new long[2];
        for (int x = x0; x < x0 + 128; x += 2) {
            for (int z = z0; z < z0 + 128; z += 2) {
                for (int y = WorldHeight.MIN_Y + 7; y < 200; y++) {
                    if (caves.isCave(x, y, z)) {
                        out[0]++;
                        if (caves.luminous(x, y, z) && caves.caveBiome(x, y, z) != CaveBiome.SURFACE) {
                            out[1]++;
                        }
                    }
                }
            }
        }
        return out;
    }
}
