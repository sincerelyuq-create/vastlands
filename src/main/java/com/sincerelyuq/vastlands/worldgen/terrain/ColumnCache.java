package com.sincerelyuq.vastlands.worldgen.terrain;

/**
 * Direct-mapped cache of terrain columns for one thread. A hit returns exactly what
 * {@link TerrainModel#sample} would compute, so the cache never changes results; it only saves the
 * repeated column evaluations of density interpolation, biome sampling, aquifers and surface rules.
 *
 * <p>Not thread-safe: {@link VastlandsTerrain} keeps one per thread.
 */
final class ColumnCache {
    private static final int BITS = 12;
    private static final int MASK = (1 << BITS) - 1;

    private final TerrainModel model;
    private final long[] keys = new long[1 << BITS];
    private final TerrainColumn[] columns = new TerrainColumn[1 << BITS];

    ColumnCache(TerrainModel model) {
        this.model = model;
    }

    TerrainColumn get(int x, int z) {
        long key = (long) x << 32 | (z & 0xFFFFFFFFL);
        // Most queries are on the 4-block noise grid; drop those two bits before mixing.
        int h = (x >> 2) * 0x9E3779B1 ^ (z >> 2) * 0x85EBCA77 ^ (x & 3) << 7 ^ (z & 3) << 11;
        int slot = (h ^ h >>> 15) & MASK;
        TerrainColumn column = this.columns[slot];
        if (column != null && this.keys[slot] == key) {
            return column;
        }
        column = this.model.sample(x, z);
        this.keys[slot] = key;
        this.columns[slot] = column;
        return column;
    }
}
