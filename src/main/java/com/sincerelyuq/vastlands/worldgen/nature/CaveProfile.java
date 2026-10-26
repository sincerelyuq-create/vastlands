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
    NONE,
    /**
     * Sincerelyuq's Biomes' amethyst caves: crystal gardens of amethyst spikes, crystal spires and
     * columns, rare giant crystals, pools of lavender spinel in floor hollows.
     */
    AMETHYST,
    /** Sincerelyuq's Biomes' smoldering cave: magma-veined pillars and outcrops, vents, geothermal fields. */
    SMOLDERING,
    /**
     * Sincerelyuq's Biomes' bioluminescent depths: lumen mushrooms of many forms and sizes, young
     * colonies, glowing moss and bulbs, ponds in floor hollows.
     */
    LUMINOUS;

    /** The profiles of other mods' biomes, checked before the others (their biomes also sit in the slot tags). */
    static final CaveProfile[] FIRST = {AMETHYST, SMOLDERING, LUMINOUS};

    public static final CaveProfile[] VALUES = values();

    public String tagPath() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
