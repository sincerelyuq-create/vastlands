package com.sincerelyuq.vastlands.worldgen.system;

import net.minecraft.world.level.levelgen.Aquifer;

/**
 * Large caverns, tunnels, vertical shafts and other underground formations, and the fluids in
 * them. Owned by Agent 4.
 *
 * <p>Router binding: {@code vastlands:caves} wraps the complete {@code final_density} of
 * {@code vastlands:overworld} (landscape, slides), so it is evaluated per block, outside
 * interpolation. Its output is the final density: the caves only ever lower it (open cave space)
 * below the ground surface, or raise it where they roof over a river gorge.
 *
 * <p>Fluids: the Vastlands noise settings disable vanilla's aquifers, and
 * {@code NoiseBasedChunkGeneratorMixin} hands noise filling {@link #fluidPicker}, which decides the
 * fluid of every open block: vanilla's sea below sea level above the ground, and the caves' own
 * water, lava or air below it.
 *
 * <p>May depend on {@link TerrainSystem} and {@link HydrologySystem}.
 */
public interface CaveSystem extends DensityStage {
    /**
     * Ground surface as generated once the caves have shaped it, given the surface of the stages
     * before (terrain and hydrology). Never lower than {@code ground}: caves only raise it where
     * they roof over a river gorge (a natural tunnel); elsewhere it is {@code ground}.
     */
    default double surfaceHeight(int blockX, int blockZ, double ground) {
        return ground;
    }

    /**
     * The fluid picker for noise filling in this world. With aquifers disabled, vanilla asks it
     * for every open block: {@code computeFluid(x, y, z).at(y)} is the block placed.
     */
    default Aquifer.FluidPicker fluidPicker(Aquifer.FluidPicker vanilla) {
        return vanilla;
    }
}
