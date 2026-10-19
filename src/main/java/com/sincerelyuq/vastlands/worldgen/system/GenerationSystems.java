package com.sincerelyuq.vastlands.worldgen.system;

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
        HydrologySystem hydrology = Passthrough.INSTANCE;
        CaveSystem caves = Passthrough.INSTANCE;
        ForestSystem forests = Passthrough.INSTANCE;
        return new GenerationSystems(terrain, hydrology, caves, forests);
    }

    /**
     * Height of the generated ground surface (the landscape after all density stages that reshape
     * it), used to decide where surface rules apply (see {@code SurfaceRulesContextMixin}).
     * Terrain only today; a stage that cuts below the terrain surface (river channels) must be
     * reflected here, or surface rules stop short of its floor.
     */
    public double surfaceHeight(int blockX, int blockZ) {
        return this.terrain.surfaceHeight(blockX, blockZ);
    }

    public String describe() {
        return "terrain=" + this.terrain.id()
                + " hydrology=" + this.hydrology.id()
                + " caves=" + this.caves.id()
                + " forests=" + this.forests.id();
    }
}
