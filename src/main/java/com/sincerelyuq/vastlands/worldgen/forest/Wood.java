package com.sincerelyuq.vastlands.worldgen.forest;

/**
 * The vanilla wood a tree is made of (its logs and its leaves). Vastlands adds no wood or leaf
 * blocks of its own.
 */
public enum Wood {
    OAK,
    BIRCH,
    SPRUCE,
    DARK_OAK,
    JUNGLE,
    ACACIA,
    /** Huge mushrooms: stems and caps instead of logs and leaves. */
    MUSHROOM;

    static final Wood[] VALUES = values();
}
