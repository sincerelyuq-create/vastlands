package com.sincerelyuq.vastlands.worldgen.caves;

/**
 * Tuning of the Vastlands caves, one record used by {@link VastlandsCaves}. Code constants, not a
 * config file (like {@code GeographySettings}): a world's caves must not depend on a mutable file,
 * and changing any value changes generation (seams against existing chunks). Every multiplier is 1
 * at its default unless noted.
 *
 * <p>Amount and shape of the cave space:
 * @param frequency        how many cave systems there are: the share of network nodes that exist
 *                         on each level and of the gallery nodes (0 = almost none)
 * @param density          how interconnected a system is: the chance that two neighbouring nodes
 *                         are joined by a tunnel (more loops and branches)
 * @param passageWidth     multiplies the radius of every network tunnel and gallery (1 = small
 *                         tunnels about 4..10 blocks wide, medium 10..25)
 * @param largePassages    multiplies the share of galleries: long passages 25..60 blocks wide,
 *                         major corridors 60..120 and rare colossal ones on a 288-block lattice
 * @param chambers         multiplies the chance that a junction of the network is a chamber
 * @param megacaves        multiplies the chance that a 1 024-block cell holds a megacave
 * @param megacaveScale    multiplies the size of megacaves (radius about 80..380 blocks at 1)
 * @param vertical         multiplies shafts, pits, ramps between levels and the opening of
 *                         fractures into chasms
 * @param regionVariation  contrast between dense cave regions and solid ones (0: the same density
 *                         everywhere, 1: default, up to 2: stronger contrast)
 *
 * <p>Water and lava:
 * @param water            multiplies the share of wet domains and of megacaves holding a lake
 * @param spillways        share of the notch windows along a dam between two water (or lava)
 *                         levels that are open, so the higher level spills into the lower one
 * @param lava             multiplies the chance that a deep domain or megacave holds lava
 * @param lavaTubes        multiplies the share of lava domain nodes that hold the lava tubes near
 *                         the bottom of the world (0 = none)
 *
 * <p>Openings and connections:
 * @param entrances        multiplies the chance of hillside, cliff and valley entrances
 * @param seaCaves         multiplies the chance of coastal and submerged sea cave mouths
 * @param riverTunnels     share of the deep river gorges that are roofed into natural tunnels (0..1)
 *
 * <p>Biomes:
 * @param caveBiomes       share of cave provinces that hold a cave biome (lush, dripstone, deep
 *                         dark, crystal, volcanic) instead of plain rock caves under the surface
 *                         biome (0..1)
 */
public record CaveSettings(
        double frequency,
        double density,
        double passageWidth,
        double largePassages,
        double chambers,
        double megacaves,
        double megacaveScale,
        double vertical,
        double regionVariation,
        double water,
        double spillways,
        double lava,
        double lavaTubes,
        double entrances,
        double seaCaves,
        double riverTunnels,
        double caveBiomes
) {
    public static final CaveSettings DEFAULT = new CaveSettings(
            1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0,
            1.0, 0.3, 1.0, 1.0,
            1.0, 1.0, 0.5,
            0.62);
}
