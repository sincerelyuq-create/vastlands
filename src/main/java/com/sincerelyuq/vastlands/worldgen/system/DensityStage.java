package com.sincerelyuq.vastlands.worldgen.system;

/**
 * A system that takes part in the noise router by transforming density.
 * Positive density is solid; zero or negative is air, or fluid where aquifers place it.
 *
 * <p>Stages are bound into the router by {@code StageDensityFunction}. Inside
 * {@code minecraft:interpolated} they are evaluated only at noise cell corners (every 4×8×4 blocks
 * in the overworld) and trilinearly interpolated in between.
 */
public interface DensityStage extends GenerationSystem {
    /**
     * @param density the density produced by the router input of this stage
     * @return the transformed density at the given block position
     */
    double apply(int blockX, int blockY, int blockZ, double density);

    /**
     * Bounds of {@link #apply} for any input within {@code input}. Must be conservative: the real
     * output may be narrower, never wider. Vanilla's density functions use these bounds to skip
     * work (for example {@code minecraft:min} skips its second argument when the first is already
     * below it), so a range that is too narrow silently produces wrong terrain.
     */
    DensityRange outputRange(DensityRange input);
}
