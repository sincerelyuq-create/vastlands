package com.sincerelyuq.vastlands.worldgen.system;

import com.sincerelyuq.vastlands.worldgen.hydrology.VastlandsHydrology;
import com.sincerelyuq.vastlands.worldgen.terrain.VastlandsTerrain;

/**
 * The systems active in one Vastlands world.
 *
 * <p>{@link #create} is the single place where implementations are chosen. Systems are created in
 * dependency order and may only receive systems created before them:
 * terrain → hydrology → caves, forests. Nothing depends on caves or forests.
 */
public record GenerationSystems(TerrainSystem terrain, HydrologySystem hydrology, CaveSystem caves, ForestSystem forests) {
    public static GenerationSystems create(GenerationSetup setup) {
        TerrainSystem terrain = new VastlandsTerrain(setup);
        HydrologySystem hydrology = new VastlandsHydrology(setup, terrain);
        CaveSystem caves = Passthrough.INSTANCE;
        ForestSystem forests = Passthrough.INSTANCE;
        return new GenerationSystems(terrain, hydrology, caves, forests);
    }

    /**
     * Height of the generated ground surface (the landscape after all density stages that reshape
     * it), used to decide where surface rules apply (see {@code SurfaceRulesContextMixin}): the
     * terrain with the hydrology's channels, valleys and levees. Caves (Agent 4) only hollow out
     * rock below it; a cave stage that reshapes the surface itself must be reflected here.
     */
    public double surfaceHeight(int blockX, int blockZ) {
        return this.hydrology.surfaceHeight(blockX, blockZ);
    }

    public String describe() {
        return "terrain=" + this.terrain.id()
                + " hydrology=" + this.hydrology.id()
                + " caves=" + this.caves.id()
                + " forests=" + this.forests.id();
    }
}
