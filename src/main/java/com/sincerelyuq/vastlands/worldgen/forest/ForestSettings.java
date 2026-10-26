package com.sincerelyuq.vastlands.worldgen.forest;

/**
 * Tuning of the Vastlands forests. Code constants, not a config file, like the other systems'
 * settings: a world's forests must not depend on a mutable file, and changing a value changes
 * generation (seams against chunks generated before).
 *
 * @param density   multiplies the tree density everywhere
 * @param clearings multiplies the strength of clearings and meadows (0: none)
 * @param riparian  multiplies the extra trees along rivers and lakes
 * @param age       shifts the stand age field (positive: more old growth)
 * @param treeSize  multiplies tree heights
 * @param shrubs    multiplies the shrub layer
 * @param deadwood  multiplies fallen logs
 * @param understory multiplies the ground vegetation the block step adds
 */
public record ForestSettings(double density, double clearings, double riparian, double age, double treeSize, double shrubs, double deadwood,
                             double understory) {
    public static final ForestSettings DEFAULT = new ForestSettings(1.0, 1.0, 1.0, 0.0, 1.0, 1.0, 1.0, 1.0);
}
