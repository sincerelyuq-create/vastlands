package com.sincerelyuq.vastlands.worldgen.caves;

/**
 * Tuning of the Vastlands caves, one record used by {@link VastlandsCaves}. Code constants, not a
 * config file (like {@code GeographySettings}): a world's caves must not depend on a mutable file,
 * and changing any value changes generation (seams against existing chunks).
 *
 * @param abundance     multiplies how many tunnels and nodes the cave network has everywhere
 * @param chambers      multiplies the chance that a junction of the network is a chamber
 * @param giantCaverns  chance that a 640-block cell holds an enormous cavern (before geology)
 * @param fractures     multiplies how much of the fracture field opens into chasms
 * @param entrances     multiplies the chance of hillside, cliff and valley entrances
 * @param seaCaves      multiplies the chance of coastal and submerged sea cave mouths
 * @param riverTunnels  share of the deep river gorges that are roofed into natural tunnels (0..1)
 * @param spillways     share of the notch windows along a dam between two water (or lava)
 *                      levels that are open, so the higher level spills into the lower one
 * @param lava          multiplies the chance that a deep domain holds lava
 */
public record CaveSettings(
        double abundance,
        double chambers,
        double giantCaverns,
        double fractures,
        double entrances,
        double seaCaves,
        double riverTunnels,
        double spillways,
        double lava
) {
    public static final CaveSettings DEFAULT = new CaveSettings(1.2, 1.0, 0.32, 1.0, 1.0, 1.0, 0.5, 0.3, 1.0);
}
