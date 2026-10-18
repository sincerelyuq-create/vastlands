package com.sincerelyuq.vastlands.worldgen.system;

/**
 * Large forests, tree diversity, giant trees and vegetation distribution. Owned by Agent 5.
 *
 * <p>Not part of the noise router. Forest features reach this system through
 * {@code GenerationContext.of(ServerLevel)} during feature placement.
 *
 * <p>May depend on {@link TerrainSystem} and {@link HydrologySystem}.
 */
public interface ForestSystem extends GenerationSystem {
    /**
     * Relative density of Vastlands trees for a column, in {@code [0, 1]}. 0 means Vastlands adds
     * no trees there; vanilla biome decoration is not affected by this value.
     */
    double treeDensity(int blockX, int blockZ);
}
