package com.sincerelyuq.vastlands.worldgen.system;

import com.sincerelyuq.vastlands.worldgen.geography.GeographyModel;
import com.sincerelyuq.vastlands.worldgen.terrain.TerrainColumn;

/**
 * Base landscape: continents, plains, hills, mountain ranges, plateaus. Owned by Agent 2.
 *
 * <p>Router binding:
 * <ul>
 *     <li>{@code vastlands:terrain} in {@code vastlands:overworld/terrain}: the landscape density.
 *     Its output replaces sloped_cheese everywhere in the Vastlands final density (via
 *     {@code vastlands:overworld/landscape}), before rivers and caves. The JSON input is added to
 *     the terrain density (it is the constant 0 today).</li>
 *     <li>{@code vastlands:terrain_parameter} nodes: the router outputs listed in
 *     {@link TerrainParameter} (continents, erosion, ridges, depth, initial density), so biomes and
 *     aquifers follow this terrain.</li>
 * </ul>
 *
 * <p>Height queries are 2D, deterministic, thread-safe and cached per thread, so later systems can
 * call them freely during generation. Depends on nothing.
 *
 * <p>Geography (macro landforms, oceans, climate) is part of the terrain: {@link #geography()} for
 * any position, {@code column(x, z).geography()} for cached grid columns.
 */
public interface TerrainSystem extends DensityStage {
    /**
     * Everything the terrain knows about one block column: surface height, regional base, masks,
     * slope and the climate parameters used for biome selection. Exact model value at (x, z).
     */
    TerrainColumn column(int blockX, int blockZ);

    /**
     * Surface height as the world generates it: the model is evaluated on the 4-block noise cell
     * grid and interpolated bilinearly in between, exactly like the density. The top solid block of
     * the landscape (before caves and later stages) is {@code ceil(surfaceHeight) - 1}.
     */
    double surfaceHeight(int blockX, int blockZ);

    /** Value of a router output owned by the terrain, within the parameter's range. */
    double parameter(TerrainParameter parameter, int blockX, int blockY, int blockZ);

    /**
     * Exact model column at any point, uncached: for coarse planning (river paths and banks) that
     * should not evict the per-thread cache of the chunk being generated.
     */
    TerrainColumn sample(double x, double z);

    /**
     * The macro geography the terrain is built on: regional elevation, landforms, ocean floor,
     * climate. Uncached, so it suits coarse sampling (drainage planning on a grid) without evicting
     * the column cache; {@code column(x, z).geography()} returns the same values for grid columns.
     */
    GeographyModel geography();
}
