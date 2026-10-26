package com.sincerelyuq.vastlands.worldgen.forest;

/**
 * What the forest model knows about one column (see {@link ForestModel#sample}): for the F3
 * screen, previews, tests and later systems (structures can avoid dense woods, natural wonders
 * can frame themselves with forest).
 *
 * @param type      the dominant forest community around the column, or null where none grows
 *                  (unmanaged biomes, oceans)
 * @param share     0..1, how much of the smoothed biome neighbourhood belongs to forest
 *                  communities (the rest: unmanaged biomes and the sea)
 * @param density   0..1, the tree density: the share of the tree lattice that holds a tree
 * @param age       0..1, stand age: young (0) to old growth (1)
 * @param clearing  0..1, how strongly a clearing or meadow opens the forest here
 * @param riparian  0..1, nearness of a river or lake (1 at the bank, 0 beyond ~18 blocks)
 * @param treeline  0..1, what the cold leaves of the forest (1 well below the treeline)
 * @param exposure  0..1, exposed ridges and crests (1) against sheltered ground
 * @param slope     local steepness of the ground, blocks per block (NaN where not measured)
 * @param temperature climate temperature (vanilla's scale, lapse rate applied)
 * @param moisture  climate moisture
 * @param old       0..1, old growth (from the age)
 */
public record ForestSample(ForestType type, double share, double density, double age, double clearing, double riparian, double treeline,
                           double exposure, double slope, double temperature, double moisture, double old) {
}
