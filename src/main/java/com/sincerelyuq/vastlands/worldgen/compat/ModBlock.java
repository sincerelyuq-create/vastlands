package com.sincerelyuq.vastlands.worldgen.compat;

/**
 * Blocks of Sincerelyuq's Biomes ({@code sincerelyuqs_biomes}, verified against the 1.0.0 jar for
 * Minecraft 1.21.8) that the Vastlands models build with. Only ids live here, so the pure models
 * (nature, forests) can name them in their block codes; {@link CompatBlocks} resolves them in the
 * game, and every one is absent when the mod is not installed. The models only use them for the
 * mod's own biomes, which exist only when the mod does.
 */
public enum ModBlock {
    /** Bioluminescent depths: the stem of a giant lumen mushroom (a pillar block, axis property). */
    LUMEN_STEM("lumen_stem", true),
    /** The cap (light 10). */
    LUMEN_CAP("lumen_cap", true),
    /** The gills under a cap (light 12). */
    LUMEN_GILLS("lumen_gills", true),
    /** A glowing bulb hanging under a sturdy block (light 12). */
    LUMEN_BULB("lumen_bulb", false),
    /** Glowing moss (light 3). */
    LUMEN_MOSS("lumen_moss", true),
    /** Sprouts on a sturdy floor (light 7). */
    LUMEN_SPROUTS("lumen_sprouts", false),
    /** Amethyst caves: amethyst that glows (light 5). */
    GLOWING_AMETHYST("glowing_amethyst", true),
    /** A segment of an amethyst spike ({@code onebyone} 1..4 standing: tip, frustum, middle, base; 5..8 hanging). */
    AMETHYST_SPIKE("amethyst_spike", false),
    /** The amethyst caves' liquid. */
    LAVENDER_SPINEL("lavender_spinel", false),
    /** Smoldering caves: magma that erupts when open above (random ticks). */
    SMOLDERING_MAGMA("smoldering_magma", true),
    /** Ashen wastes: the ash ground. */
    ASH_BLOCK("ash_block", true),
    /** Ashen wastes: a vent puffing hot ash (damage, particles). */
    ASH_VENT("ash_vent", true),
    /** Ashen wastes: burnt wood (a pillar block). */
    CHARRED_LOG("charred_log", true),
    /** Twilight thicket: dusk wood (a pillar block, in {@code #minecraft:logs}). */
    DUSK_LOG("dusk_log", true),
    /** Twilight thicket: violet leaves (vanilla leaves: distance, persistent). */
    DUSK_LEAVES("dusk_leaves", false),
    /** Twilight thicket: the glowing flower. */
    DUSK_BLOOM("dusk_bloom", false),
    /** Bone dunes: bleached bone (a pillar block). */
    BLEACHED_BONE("bleached_bone", true),
    /** Lotus pools: a lotus pad on water ({@code flower} property). */
    LOTUS_PAD("lotus_pad", false);

    public static final String NAMESPACE = "sincerelyuqs_biomes";
    public static final ModBlock[] VALUES = values();

    public final String path;
    /** A full block a formation is built of (else a decoration or a fluid). */
    public final boolean solid;

    ModBlock(String path, boolean solid) {
        this.path = path;
        this.solid = solid;
    }

    public String id() {
        return NAMESPACE + ":" + this.path;
    }
}
