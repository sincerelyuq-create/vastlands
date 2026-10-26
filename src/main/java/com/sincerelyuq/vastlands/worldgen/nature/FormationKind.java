package com.sincerelyuq.vastlands.worldgen.nature;

/**
 * The kinds of natural formations. Each has a horizontal reach (no block lies farther from its
 * root; a chunk builds only the formations whose root lies within their reach of it) and a
 * height class: tall formations keep tree crowns away, low ones only trunks.
 */
public enum FormationKind {
    // ---------------------------------------------------------------- surface
    /** A boulder sunk into the ground: rounded or angular, small to large, mossy in damp woods. */
    BOULDER(true, 7, false),
    /** A few rocks of different sizes lying together. */
    ROCK_CLUSTER(true, 8, false),
    /** Scree: a field of rocks and boulders on a steep mountain slope. */
    TALUS(true, 14, false),
    /** A tor: blocks of rock stacked on a crest or a hilltop, jointed in layers. */
    OUTCROP(true, 12, true),
    /** A rock spire: a needle on a mountainside, a hoodoo with its cap rock, a sea stack. */
    SPIRE(true, 10, true),
    /** A natural arch on two legs. */
    ARCH(true, 16, true),
    /** Logs washed up at the water's edge, half in the water, some bleached. */
    DRIFTWOOD(true, 7, false),
    /** Stones along a shore or on a river bed. */
    SHORE_STONES(true, 6, false),
    /** A small rocky island in a lake or a shallow sea. */
    ISLET(true, 9, false),
    /** A great boulder balanced on a narrow pedestal. */
    BALANCED_ROCK(true, 9, true),
    /** Wonders: a great arch tens of blocks across. */
    GREAT_ARCH(true, 32, true),
    /** Wonders: a monumental spire with ledges, up to ~70 blocks tall. */
    GREAT_SPIRE(true, 16, true),
    /** Wonders: a stone forest, a crowd of limestone pinnacles (karst). */
    STONE_FOREST(true, 30, true),

    // ---------------------------------------------------------------- caves
    /** Fallen rock on a cave floor: a few blocks of rubble, gravel. */
    RUBBLE(false, 4, false),
    /** A boulder on a cave floor. */
    CAVE_BOULDER(false, 6, false),
    /** A stalagmite rising from the floor (dripstone, or rock outside dripstone caves). */
    STALAGMITE(false, 9, false),
    /** A stalactite hanging from the ceiling. */
    STALACTITE(false, 9, false),
    /** A column where a stalactite and a stalagmite met: floor to ceiling with a waist. */
    COLUMN(false, 10, false),
    /** A group of pointed dripstone on the ceiling and the floor below it. */
    POINTED_GROUP(false, 4, false),
    /** A rock pendant: a blunt protrusion of the ceiling. */
    PENDANT(false, 7, false),
    /** A pillar of rock from floor to ceiling. */
    ROCK_PILLAR(false, 8, false),
    /** A collapse pile: a cone of breakdown under a high ceiling. */
    COLLAPSE(false, 12, false),
    /** Lush caves: rooted earth in the ceiling, hanging roots, glow berry vines. */
    ROOT_CURTAIN(false, 6, false),
    /** Lush caves: a moss terrace with azaleas and carpets on the floor. */
    MOSS_GARDEN(false, 8, false),
    /** Lush caves: a column clothed in moss with vines hanging from it. */
    MOSSY_PILLAR(false, 9, false),
    /** Deep dark: a deepslate mound coated in sculk. */
    SCULK_MOUND(false, 8, false),
    /** Deep dark: a sculk-encrusted pillar or pendant. */
    SCULK_PILLAR(false, 9, false),
    /** Deep dark: sculk veins spreading over the floor around a sculk patch. */
    SCULK_GROWTH(false, 6, false),
    /** Crystal caves: calcite with amethyst. */
    CRYSTALS(false, 6, false),
    /** Volcanic caves: a cluster of basalt columns, out of lava too. */
    BASALT_COLUMNS(false, 7, false),
    /** Flooded caves: a boulder or a pillar under water, rising through the surface. */
    SUBMERGED_ROCK(false, 8, false),
    /** Flooded caves: a little island of rock and gravel in an underground lake. */
    CAVE_ISLET(false, 8, false),
    /** Monumental dripstone: a giant column or spike in a tall cave, tens of blocks high. */
    GIANT_DRIPSTONE(false, 16, false),
    /** A field of stalagmites and stalactites of many sizes. */
    SPIKE_FIELD(false, 14, false),
    /** A massive column of rock in a megacave. */
    GIANT_COLUMN(false, 16, false),

    // ---------------------------------------------------------------- other mods' biomes (Agent 7)
    /** Ashen wastes: a volcanic fissure in the ground, blackstone lips, magma deep in it, ash vents along it. */
    FISSURE(true, 14, false),
    /** Bioluminescent depths: a lumen mushroom of one of several forms, young ones at its foot. */
    LUMEN_MUSHROOM(false, 10, false),
    /** Bioluminescent depths: a giant lumen mushroom of a tall cave or a megacave, its own colony around it. */
    LUMEN_GIANT(false, 16, false),
    /** Bioluminescent depths: a colony of young lumen mushrooms. */
    LUMEN_COLONY(false, 8, false),
    /** Bioluminescent depths: lumen moss and sprouts on the floor, moss and bulbs on a low ceiling. */
    LUMEN_GROWTH(false, 6, false),
    /** A pool in a hollow of a cave floor: water (lumen ponds) or lavender spinel (amethyst caves). */
    POOL(false, 9, false),
    /** Amethyst caves: amethyst spikes of many lengths and buds on a floor or a ceiling. */
    CRYSTAL_GARDEN(false, 6, false),
    /** Amethyst caves: a group of large leaning amethyst crystals with a calcite and basalt collar. */
    CRYSTAL_SPIRES(false, 14, false),
    /** Amethyst caves: a giant crystal in a megacave. */
    GIANT_CRYSTAL(false, 16, false),
    /** Smoldering caves: a low vent of blackstone around smoldering magma. */
    MAGMA_VENT(false, 8, false),
    /** Smoldering caves: a geothermal field: magma floor, vents and magma-veined pillars in a great hall. */
    GEOTHERMAL_FIELD(false, 16, false),
    /** Ashen wastes: a few ash vents over magma, each in a ring of basalt (fumaroles on level ground). */
    ASH_VENTS(true, 7, false);

    public static final FormationKind[] VALUES = values();

    public final boolean surface;
    public final int reach;
    /** Tall enough to keep tree crowns away (surface). */
    public final boolean tall;

    FormationKind(boolean surface, int reach, boolean tall) {
        this.surface = surface;
        this.reach = reach;
        this.tall = tall;
    }
}
