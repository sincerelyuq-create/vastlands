package com.sincerelyuq.vastlands.worldgen.system;

/**
 * Placeholder for systems that are not implemented yet: density stages return their input
 * unchanged; forests add nothing. Terrain is implemented ({@code VastlandsTerrain}) and has no
 * passthrough: later systems rely on its height queries.
 */
public enum Passthrough implements HydrologySystem, CaveSystem, ForestSystem {
    INSTANCE;

    @Override
    public String id() {
        return "passthrough";
    }

    @Override
    public double apply(int blockX, int blockY, int blockZ, double density) {
        return density;
    }

    @Override
    public DensityRange outputRange(DensityRange input) {
        return input;
    }

    @Override
    public double treeDensity(int blockX, int blockZ) {
        return 0.0;
    }
}
