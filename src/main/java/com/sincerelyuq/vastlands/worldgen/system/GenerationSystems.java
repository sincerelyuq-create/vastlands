package com.sincerelyuq.vastlands.worldgen.system;

import com.sincerelyuq.vastlands.worldgen.caves.VastlandsCaves;
import com.sincerelyuq.vastlands.worldgen.forest.VastlandsForests;
import com.sincerelyuq.vastlands.worldgen.hydrology.VastlandsHydrology;
import com.sincerelyuq.vastlands.worldgen.nature.VastlandsNature;
import com.sincerelyuq.vastlands.worldgen.terrain.VastlandsTerrain;

/**
 * The systems active in one Vastlands world.
 *
 * <p>{@link #create} is the single place where implementations are chosen. Systems are created in
 * dependency order and may only receive systems created before them:
 * terrain → hydrology → caves → nature → forests. Nothing depends on forests; the forests keep
 * their trees clear of the natural formations.
 */
public record GenerationSystems(TerrainSystem terrain, HydrologySystem hydrology, CaveSystem caves, NatureSystem nature, ForestSystem forests) {
    public static GenerationSystems create(GenerationSetup setup) {
        TerrainSystem terrain = new VastlandsTerrain(setup);
        HydrologySystem hydrology = new VastlandsHydrology(setup, terrain);
        CaveSystem caves = new VastlandsCaves(setup, terrain, hydrology);
        NatureSystem nature = new VastlandsNature(setup, terrain, hydrology, caves);
        ForestSystem forests = new VastlandsForests(setup, terrain, hydrology, caves, nature);
        return new GenerationSystems(terrain, hydrology, caves, nature, forests);
    }

    /**
     * Height of the generated ground surface (the landscape after all density stages that reshape
     * it), used to decide where surface rules apply (see {@code SurfaceRulesContextMixin}): the
     * terrain with the hydrology's channels, valleys and levees, raised where the caves roof over
     * a river gorge. Caves otherwise only hollow out rock below it (entrances aside).
     */
    public double surfaceHeight(int blockX, int blockZ) {
        return this.caves.surfaceHeight(blockX, blockZ, this.hydrology.surfaceHeight(blockX, blockZ));
    }

    public String describe() {
        return "terrain=" + this.terrain.id()
                + " hydrology=" + this.hydrology.id()
                + " caves=" + this.caves.id()
                + " nature=" + this.nature.id()
                + " forests=" + this.forests.id();
    }
}
