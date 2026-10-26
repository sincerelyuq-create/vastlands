package com.sincerelyuq.vastlands.worldgen.nature;

import java.util.Locale;

/**
 * The decorative identity of a surface biome: which natural formations it gets and of what rock.
 * Each is a biome tag {@code #vastlands:nature/surface/<name>} (vanilla's biomes by default;
 * other mods and datapacks add theirs); a biome in none of them is judged by its climate (see
 * {@code NatureBiomes}), a biome in {@code none} gets no formations at all.
 */
public enum SurfaceStyle {
    /** Plains, forests, birch forests, flower forests, meadows and cherry groves: sparse rocks, erratics, mossy boulders in the woods. */
    TEMPERATE,
    /** Dark forests and pale gardens: mossy (weathered) boulders, deadwood. */
    DARK,
    /** Taiga: rocks and boulders, granite in the north. */
    BOREAL,
    /** Old growth taiga: mossy boulders and rock clusters among the giants. */
    OLD_GROWTH,
    /** Snowy plains and snowy taiga: pale rocks under snow. */
    SNOWY,
    /** Mountains (windswept hills, groves, slopes, peaks, meadows): scree, tors, spires, arches. */
    MOUNTAIN,
    /** Swamps and mangroves: few rocks, driftwood at the water. */
    WETLAND,
    /** Jungles: mossy rocks, limestone stone forests. */
    JUNGLE,
    /** Savannas: granite boulders and tors (kopjes). */
    SAVANNA,
    /** Deserts: sandstone rocks, hoodoos, arches. */
    DESERT,
    /** Badlands: terracotta hoodoos, spires and arches. */
    BADLANDS,
    /** Beaches: driftwood, shore stones. */
    BEACH,
    /** Stony shores: boulders, sea stacks, sea arches. */
    STONY_SHORE,
    /** Rivers: river bed stones, driftwood at the banks. */
    RIVER,
    /** Oceans: islets and sea stacks near the coast. */
    OCEAN,
    /** Mushroom fields: nothing but a few rocks. */
    MUSHROOM,
    /** No natural formations (an opt-out for other mods' biomes). */
    NONE;

    public static final SurfaceStyle[] VALUES = values();

    public String tagPath() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    /** Woods where moss weathers the rocks. */
    public boolean wooded() {
        return this == TEMPERATE || this == DARK || this == BOREAL || this == OLD_GROWTH || this == JUNGLE || this == WETLAND;
    }
}
