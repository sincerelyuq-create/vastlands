package com.sincerelyuq.vastlands.worldgen.system;

/**
 * The systems active in one Vastlands world.
 *
 * <p>{@link #create} is the single place where implementations are chosen. Systems are created in
 * dependency order and may only receive systems created before them:
 * terrain → hydrology → caves, forests. Nothing depends on caves or forests.
 */
public record GenerationSystems(TerrainSystem terrain, HydrologySystem hydrology, CaveSystem caves, ForestSystem forests) {
    public static GenerationSystems create(GenerationSetup setup) {
        TerrainSystem terrain = Passthrough.INSTANCE;
        HydrologySystem hydrology = Passthrough.INSTANCE;
        CaveSystem caves = Passthrough.INSTANCE;
        ForestSystem forests = Passthrough.INSTANCE;
        return new GenerationSystems(terrain, hydrology, caves, forests);
    }

    public String describe() {
        return "terrain=" + this.terrain.id()
                + " hydrology=" + this.hydrology.id()
                + " caves=" + this.caves.id()
                + " forests=" + this.forests.id();
    }
}
