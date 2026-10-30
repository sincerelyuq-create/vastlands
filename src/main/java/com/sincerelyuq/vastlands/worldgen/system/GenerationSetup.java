package com.sincerelyuq.vastlands.worldgen.system;

import com.sincerelyuq.vastlands.Vastlands;
import com.sincerelyuq.vastlands.worldgen.compat.CompatBlocks;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.WorldgenRandom;

/**
 * Immutable inputs available to every generation system when it is created for a world.
 *
 * @param minY          lowest Y of the noise range
 * @param height        height of the noise range
 * @param seaLevel      sea level of the noise settings
 * @param luminousCaves a luminous cave biome is installed (Sincerelyuq's Biomes' bioluminescent
 *                      depths): the caves of its provinces are built larger. False without the
 *                      mod, so such worlds keep their caves exactly
 */
public record GenerationSetup(long worldSeed, int minY, int height, int seaLevel, boolean luminousCaves) {
    /** Without a luminous cave biome (tests, previews, worlds without other mods). */
    public GenerationSetup(long worldSeed, int minY, int height, int seaLevel) {
        this(worldSeed, minY, height, seaLevel, false);
    }

    public static GenerationSetup of(long worldSeed, NoiseGeneratorSettings settings) {
        return new GenerationSetup(worldSeed, settings.noiseSettings().minY(), settings.noiseSettings().height(), settings.seaLevel(),
                CompatBlocks.biomesInstalled());
    }

    /**
     * Deterministic, position-addressable randomness for one system. Use {@code random(id).at(x, y, z)}
     * rather than a shared {@code RandomSource}: the result then depends only on seed and position,
     * not on chunk generation order. Each system should use its own id so streams never overlap.
     */
    public PositionalRandomFactory random(String systemId) {
        return WorldgenRandom.Algorithm.XOROSHIRO.newInstance(this.worldSeed)
                .forkPositional()
                .fromHashOf(Vastlands.id(systemId))
                .forkPositional();
    }
}
