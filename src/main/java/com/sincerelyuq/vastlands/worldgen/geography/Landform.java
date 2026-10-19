package com.sincerelyuq.vastlands.worldgen.geography;

/**
 * Regional landform of a column, classified from the macro geography (before gullies and small
 * detail). Meant for later systems and diagnostics; terrain shape and biomes use the continuous
 * fields of {@link GeographySample}, not this classification.
 */
public enum Landform {
    /** Sea floor from the coast to the shelf break (down to ~15 blocks of water). */
    SHELF(true),
    /** Continental slope and rise between the shelf and the abyssal plain. */
    CONTINENTAL_SLOPE(true),
    /** Flat deep-ocean floor, ~80..95 blocks below sea level. */
    ABYSSAL_PLAIN(true),
    /** Mid-ocean ridge: a long, rugged submarine range with a central rift. */
    SUBMARINE_RIDGE(true),
    /** Isolated submarine volcano; some reach close to the surface. */
    SEAMOUNT(true),
    /** Rare abyssal trench, down to ~Y -160. */
    TRENCH(true),
    /** Land near the shoreline at sea level: beaches, stony shores. */
    COAST(false),
    /** Flat or gently rolling lowland. */
    PLAINS(false),
    /** Rolling hill country. */
    HILLS(false),
    /** Interior basin below the surrounding land. */
    BASIN(false),
    /** Raised tableland behind escarpments. */
    PLATEAU(false),
    /** Outer zone of a mountain system: foothills and low parallel ridges. */
    FOOTHILLS(false),
    /** Mountain ranges. */
    MOUNTAINS(false),
    /** The high cores of the largest ranges (more than ~180 blocks of relief). */
    HIGH_MOUNTAINS(false);

    private final boolean ocean;

    Landform(boolean ocean) {
        this.ocean = ocean;
    }

    public boolean isOcean() {
        return this.ocean;
    }
}
