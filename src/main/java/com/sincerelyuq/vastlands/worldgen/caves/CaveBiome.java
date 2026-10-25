package com.sincerelyuq.vastlands.worldgen.caves;

/**
 * The cave biome slot of a block (see {@link VastlandsCaves#caveBiome}). The biome source turns a
 * slot into a biome from its biome tag ({@code #vastlands:cave/<slot>}), so other mods' cave biomes
 * can join a slot without a dependency either way.
 */
public enum CaveBiome {
    /** Near the surface (within {@value CaveBiomes#SURFACE_DEPTH} blocks of the ground): the surface biome. */
    SURFACE,
    /** Plain rock caves: the biome of the surface above. */
    NONE,
    /** Lush caves: under moist, mild climates, in the shallower caves. */
    LUSH,
    /** Dripstone caves: karst and fractured rock, at any depth. */
    DRIPSTONE,
    /** The deep dark: rare, deep (around Y -27, where ancient cities start, and below the aquitard), under mountains. */
    DEEP_DARK,
    /** Crystal caves of other mods (amethyst caves of Sincerelyuq's Biomes); plain rock without them. */
    CRYSTAL,
    /** Hot caves of other mods (the smoldering cave of Sincerelyuq's Biomes), deep and in volcanic rock; plain rock without them. */
    VOLCANIC,
    /** Any other mod's cave biomes added to the {@code other} tag; plain rock without them. */
    OTHER;

    /** The slot taking this one's place when its tag holds no biome. */
    public CaveBiome fallback() {
        return switch (this) {
            case CRYSTAL, VOLCANIC, OTHER, LUSH, DRIPSTONE, DEEP_DARK -> NONE;
            default -> this;
        };
    }
}
