package com.sincerelyuq.vastlands.worldgen.system;

/**
 * Rivers, tributaries, downhill drainage and waterfalls. Owned by Agent 3.
 *
 * <p>Router binding: {@code vastlands:hydrology} in {@code vastlands:overworld/hydrology}. Its input
 * is the terrain density; its output is the landscape with river channels carved.
 * Density can only shape channels: water above sea level is not placed by vanilla aquifers and
 * needs a separate step (see {@code VastlandsChunkGenerator}).
 *
 * <p>May depend on {@link TerrainSystem}.
 */
public interface HydrologySystem extends DensityStage {
}
