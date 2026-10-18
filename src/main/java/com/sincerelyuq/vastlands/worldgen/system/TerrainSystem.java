package com.sincerelyuq.vastlands.worldgen.system;

/**
 * Base landscape: mountain ranges, valleys, plains, plateaus and cliffs. Owned by Agent 2.
 *
 * <p>Router binding: {@code vastlands:terrain} in {@code vastlands:overworld/terrain}. Its input is
 * vanilla's {@code minecraft:overworld/sloped_cheese}; its output replaces sloped_cheese everywhere
 * in the Vastlands final density (via {@code vastlands:overworld/landscape}), before rivers and caves.
 *
 * <p>Depends on nothing. Hydrology is expected to need a cheap 2D height query from this
 * interface; adding it is part of Agent 2's work.
 */
public interface TerrainSystem extends DensityStage {
}
