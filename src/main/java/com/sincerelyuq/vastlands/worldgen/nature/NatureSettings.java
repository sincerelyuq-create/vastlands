package com.sincerelyuq.vastlands.worldgen.nature;

/**
 * Tuning of the natural formations. Code constants like the other systems' settings: a world's
 * formations must not depend on a mutable file, and changing a value changes generation (seams
 * against chunks generated before). Biomes take part through tags ({@code #vastlands:nature/...}),
 * and the configured features' {@code density} can thin them out in a datapack (see
 * {@code NatureFeature}).
 *
 * @param surface multiplies the chance of every surface formation (boulders, outcrops, spires, driftwood)
 * @param caves   multiplies the chance of every cave formation
 * @param large   multiplies the chance of large formations (arches, spire fields, giant dripstone, collapse piles)
 * @param wonders multiplies the chance of monumental wonders (great arches and spires, stone forests,
 *                monumental formations in megacaves)
 * @param mossy   multiplies how often rocks in damp woods are weathered with moss
 */
public record NatureSettings(double surface, double caves, double large, double wonders, double mossy) {
    public static final NatureSettings DEFAULT = new NatureSettings(1.0, 1.0, 1.0, 1.0, 1.0);
}
