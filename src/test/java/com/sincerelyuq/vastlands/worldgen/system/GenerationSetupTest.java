package com.sincerelyuq.vastlands.worldgen.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class GenerationSetupTest {
    private static long sample(long seed, String systemId) {
        return new GenerationSetup(seed, -256, 768, 63).random(systemId).at(1200, -40, -3500).nextLong();
    }

    @Test
    void randomIsDeterministicPerSeedSystemAndPosition() {
        assertEquals(sample(42L, "terrain"), sample(42L, "terrain"));
    }

    @Test
    void randomDiffersBetweenSystemsAndSeeds() {
        assertNotEquals(sample(42L, "terrain"), sample(42L, "hydrology"));
        assertNotEquals(sample(42L, "terrain"), sample(43L, "terrain"));
    }

    @Test
    void terrainAndHydrologyAreImplementedAndTheOtherSystemsArePassthrough() {
        GenerationSystems systems = GenerationSystems.create(new GenerationSetup(42L, -256, 768, 63));
        assertEquals("terrain=vastlands hydrology=vastlands caves=passthrough forests=passthrough", systems.describe());
    }
}
