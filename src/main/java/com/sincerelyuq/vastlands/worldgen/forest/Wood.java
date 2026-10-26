package com.sincerelyuq.vastlands.worldgen.forest;

/**
 * The wood a tree is made of (its logs and its leaves). Vastlands adds no wood or leaf blocks of
 * its own: the first seven are vanilla's, the rest belong to other mods and are used only in their
 * biomes (Sincerelyuq's Biomes: dusk wood of the twilight thicket, charred wood of the ashen
 * wastes; see {@code CompatBlocks}).
 */
public enum Wood {
    OAK,
    BIRCH,
    SPRUCE,
    DARK_OAK,
    JUNGLE,
    ACACIA,
    /** Huge mushrooms: stems and caps instead of logs and leaves. */
    MUSHROOM,
    /** Sincerelyuq's Biomes: dusk logs and violet dusk leaves (the twilight thicket). */
    DUSK,
    /** Sincerelyuq's Biomes: charred logs, no leaves (the ashen wastes). */
    CHARRED;

    /** The vanilla woods (and huge mushrooms): the ones with their own species noise. */
    static final int VANILLA = 7;

    static final Wood[] VALUES = values();
}
