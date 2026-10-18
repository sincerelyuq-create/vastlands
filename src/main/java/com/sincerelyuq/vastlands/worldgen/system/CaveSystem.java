package com.sincerelyuq.vastlands.worldgen.system;

/**
 * Large caverns, tunnels, vertical shafts and other underground formations. Owned by Agent 4.
 *
 * <p>Router binding: {@code vastlands:caves} wraps the complete {@code final_density} of
 * {@code vastlands:overworld} (landscape, vanilla noise caves, slides and noodle caves), so it is
 * evaluated per block, outside interpolation. Its output is the final density.
 *
 * <p>May depend on {@link TerrainSystem} and {@link HydrologySystem}.
 */
public interface CaveSystem extends DensityStage {
}
