package com.sincerelyuq.vastlands.worldgen.system;

import com.sincerelyuq.vastlands.worldgen.hydrology.WaterColumn;

/**
 * Rivers, tributaries, lakes, downhill drainage and waterfalls. Owned by Agent 3.
 *
 * <p>Router binding:
 * <ul>
 *     <li>{@code vastlands:hydrology} in {@code vastlands:overworld/hydrology}: its input is the
 *     terrain density, its output the landscape with river channels, valleys, gorges and levees.</li>
 *     <li>{@code vastlands:hydrology_parameter} nodes: router outputs that hydrology reshapes
 *     (weirdness for river biomes, the initial density that aquifers and the preliminary surface
 *     use); the others pass the terrain's values through.</li>
 * </ul>
 * Density can only shape channels: water above sea level is not placed by vanilla aquifers, so
 * {@code VastlandsChunkGenerator} places it from {@link #water} (see {@code RiverWater}).
 *
 * <p>Deterministic, thread-safe, independent of chunk generation order. May depend on
 * {@link TerrainSystem}.
 */
public interface HydrologySystem extends DensityStage {
    /**
     * Ground surface as the world generates it, channels included: the hydrology's node heights
     * on the 4-block noise grid, interpolated bilinearly like the density. The top solid block of
     * the landscape (before caves) is {@code ceil(surfaceHeight) - 1}.
     */
    double surfaceHeight(int blockX, int blockZ);

    /** The water body of a block column: rivers, lakes, the sea, or none. */
    WaterColumn water(int blockX, int blockZ);

    /** Value of a router output as reshaped by hydrology, within the parameter's range. */
    double parameter(TerrainParameter parameter, int blockX, int blockY, int blockZ);
}
