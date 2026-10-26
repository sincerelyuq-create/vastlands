package com.sincerelyuq.vastlands.worldgen.nature;

import java.util.Locale;

/**
 * How a cave biome is decorated. Each is a biome tag {@code #vastlands:nature/cave/<name>}
 * (vanilla's lush caves, dripstone caves and the deep dark by default; other mods' cave biomes
 * join by an optional tag entry). Plain rock caves under a surface biome are {@link #PLAIN}; a
 * cave biome in no tag gets the restrained {@link #OTHER} decorations; {@link #NONE} opts out.
 */
public enum CaveProfile {
    /** Plain rock: rubble, boulders, pendants, pillars, collapse piles, giant columns. */
    PLAIN,
    /** Dripstone caves: pointed dripstone, stalagmites, stalactites, columns, giant dripstone, spike fields. */
    DRIPSTONE,
    /** Lush caves: root curtains, moss gardens, mossy pillars, glow berries. */
    LUSH,
    /** The deep dark: sculk growths, sculk-coated mounds and pillars (no sensors, shriekers or catalysts). */
    DEEP_DARK,
    /** Crystal caves (other mods' amethyst caves): calcite and amethyst. */
    CRYSTAL,
    /** Hot caves (other mods' volcanic caves): basalt columns, magma rubble. */
    VOLCANIC,
    /** Other mods' cave biomes in no tag: a little rubble only. */
    OTHER,
    /** No formations. */
    NONE;

    public static final CaveProfile[] VALUES = values();

    public String tagPath() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
