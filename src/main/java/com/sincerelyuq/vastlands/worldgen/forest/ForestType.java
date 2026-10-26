package com.sincerelyuq.vastlands.worldgen.forest;

import java.util.Locale;

/**
 * The forest communities of Vastlands. Each is a biome tag {@code #vastlands:forest/<name>}
 * listing the biomes whose trees Vastlands grows (vanilla's by default; other mods and datapacks
 * may add theirs). A biome in no such tag is left to its own features: Vastlands neither removes
 * its trees nor adds any.
 *
 * <p>{@link #capacity} is the share of the tree lattice a community fills where its climate suits
 * it best, before the regional structure (fragmentation, clearings, groves), the terrain and the
 * water change it; {@link #groves} how much of it stands in groves rather than spread evenly.
 */
public enum ForestType {
    /** Forest: oak and birch, spruce in cold stands; ancient oaks in old growth. */
    TEMPERATE(0.88, 0.0, 0.6),
    /** Flower forest: open, small oaks and birches over flowers. */
    FLOWER(0.42, 0.3, 0.3),
    /** Birch forest. */
    BIRCH(0.84, 0.0, 0.4),
    /** Old growth birch forest: tall birches. */
    OLD_BIRCH(0.86, 0.0, 0.7),
    /** Dark forest: dark oaks under a closed canopy, huge mushrooms. */
    DARK(0.97, 0.0, 0.6),
    /** Taiga and snowy taiga: spruce, pine and birch. */
    BOREAL(0.86, 0.0, 0.5),
    /** Old growth spruce taiga: giant spruces. */
    OLD_SPRUCE(0.82, 0.0, 0.9),
    /** Old growth pine taiga: giant pines. */
    OLD_PINE(0.78, 0.0, 0.9),
    /** Plains, meadows and snowy plains: open land with lone trees and groves. */
    GRASSLAND(0.035, 1.0, 0.4),
    /** Savanna: scattered acacias. */
    SAVANNA(0.17, 0.8, 0.3),
    /** Jungle: tall and giant jungle trees over dense shrubs. */
    JUNGLE(1.0, 0.0, 0.6),
    /** Sparse jungle. */
    SPARSE_JUNGLE(0.42, 0.5, 0.3),
    /** Swamp: vine-hung swamp oaks, in shallow water too. */
    SWAMP(0.5, 0.3, 0.3),
    /** Windswept hills and forests: spruce and oak in patches. */
    HIGHLAND(0.34, 0.7, 0.4),
    /** Grove: spruce on snowy mountain slopes. */
    GROVE(0.5, 0.4, 0.4),
    /** Snowy slopes: a few stunted trees at the treeline. */
    ALPINE(0.07, 0.6, 0.2),
    /** Wooded badlands: small oaks on dry plateaus. */
    DRY(0.26, 0.6, 0.2),
    /**
     * Sincerelyuq's Biomes' twilight thicket: a dense old forest of dusk trees (young, mature and
     * ancient, slender and broad, hanging leaves) with huge mushrooms in patches.
     */
    DUSK(0.92, 0.0, 0.8),
    /**
     * Sincerelyuq's Biomes' ashen wastes: what a fire left: scattered charred snags and stumps,
     * fallen burnt logs, here and there a stand of dead trees.
     */
    CHARRED(0.14, 0.75, 0.5);

    public static final ForestType[] VALUES = values();
    public static final int COUNT = VALUES.length;

    public final double capacity;
    public final double groves;
    /** How old its stands grow: shifts the age field towards old growth. */
    public final double maturity;

    ForestType(double capacity, double groves, double maturity) {
        this.capacity = capacity;
        this.groves = groves;
        this.maturity = maturity;
    }

    /** Path of the biome tag under {@code vastlands:forest/}. */
    public String tagPath() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    /** Closed forest rather than open land with trees. */
    public boolean closed() {
        return this.capacity >= 0.6;
    }
}
