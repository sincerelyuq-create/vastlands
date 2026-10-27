package com.sincerelyuq.vastlands.worldgen.nature;

/**
 * Rock palettes of natural formations: the stone of the region a formation stands in. The block
 * step picks each block from the palette by position (patches a few blocks across, not salt and
 * pepper), the role of the cell (body, accent, cap) and its coat (moss, sculk), so the same
 * formation looks the same from every chunk. Vanilla blocks only.
 */
public enum Rock {
    /** Stone, andesite, cobblestone: most of the world's rock. */
    STONE,
    /** Granite and andesite with stone: crystalline cores of mountain ranges, tors. */
    GRANITE,
    /** Limestone country (karst): stone, diorite and calcite, pale and weathered. */
    LIMESTONE,
    /** Deserts: sandstone and smooth sandstone. */
    SANDSTONE,
    /** Badlands: terracotta in bands by height, red sandstone caps. */
    TERRACOTTA,
    /** Volcanic rock: basalt, smooth basalt, blackstone, tuff. */
    BASALT,
    /** Deep rock (below Y 0): deepslate, cobbled deepslate, tuff. */
    DEEPSLATE,
    /** Dripstone caves: dripstone blocks with a little calcite. */
    DRIPSTONE,
    /** Rubble and debris: cobblestone, gravel, stone. */
    RUBBLE,
    /** Deep rubble: cobbled deepslate, gravel, tuff. */
    DEEP_RUBBLE,
    /** Shores and river beds: stone, cobblestone, gravel. */
    SHORE,
    /** Crystal caves: calcite with amethyst. */
    CALCITE,
    /** Pale rock of cold coasts and snowfields: diorite and stone. */
    PALE,
    /** Amethyst caves (Sincerelyuq's Biomes): amethyst with calcite, glowing and budding amethyst. */
    AMETHYST,
    /** Smoldering caves: basalt and blackstone veined with magma. */
    MAGMATIC,
    /** Painted strata: the strata's colour bands of terracotta by height. */
    STRATA;

    public static final Rock[] VALUES = values();

    /** True for the deep palettes (deepslate). */
    public boolean deep() {
        return this == DEEPSLATE || this == DEEP_RUBBLE;
    }
}
