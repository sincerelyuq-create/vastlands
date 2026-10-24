package com.sincerelyuq.vastlands.worldgen.system;

/**
 * Placeholder for systems that are not implemented yet: density stages return their input
 * unchanged (a passthrough cave system keeps vanilla's fluid picker); forests add nothing.
 * Terrain, hydrology and caves are implemented ({@code VastlandsTerrain},
 * {@code VastlandsHydrology}, {@code VastlandsCaves}); the passthrough cave system remains for
 * tests and for routers that bind the caves stage without Vastlands terrain.
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
