package com.sincerelyuq.vastlands.worldgen.system;

/**
 * Placeholder for systems that are not implemented yet: density stages return their input
 * unchanged, so the Vastlands preset generates vanilla-shaped terrain; forests add nothing.
 */
public enum Passthrough implements TerrainSystem, HydrologySystem, CaveSystem, ForestSystem {
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
