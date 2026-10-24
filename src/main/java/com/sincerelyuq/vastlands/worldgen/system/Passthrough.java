package com.sincerelyuq.vastlands.worldgen.system;

/**
 * Placeholder for systems that are not implemented yet: density stages return their input
 * unchanged; forests add nothing. Terrain ({@code VastlandsTerrain}) and hydrology
 * ({@code VastlandsHydrology}) are implemented and have no passthrough: later systems rely on
 * their height and water queries.
 */
public enum Passthrough implements CaveSystem, ForestSystem {
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
