package com.sincerelyuq.vastlands.worldgen.caves;

/**
 * A megacave as later systems see it (see {@link VastlandsCaves#megacaves}): where it is, how large,
 * and what fills it. Natural wonders (Agent 6) can place their formations from these: tall vaults
 * for giant stalactites, broad floors for underground forests, bridges and shelves, deep lakes for
 * underwater formations.
 *
 * @param zone        "upper" (above the aquitard) or "deep"
 * @param kind        hall, lake, canyon, abyss, complex or pillars
 * @param x           centre
 * @param z           centre
 * @param lengthRadius half length along the long axis (blocks)
 * @param widthRadius  half width across it (blocks)
 * @param angle       direction of the long axis (radians from +x towards +z)
 * @param floorY      floor at the centre
 * @param ceilingY    vault at the centre (the floor plus the vault's height)
 * @param fluid       what stands on its floor: AIR (dry), WATER or LAVA
 * @param fluidTop    Y of the top fluid block, or {@link Integer#MIN_VALUE} when dry
 * @param pillars     whether it holds pillars
 * @param bridges     number of natural bridges
 * @param shelf       Y of its mid shelf (a second floor), or NaN
 * @param cenoteX     centre of its collapse shaft to the sky, or NaN for none
 * @param cenoteZ     centre of its collapse shaft to the sky, or NaN for none
 */
public record MegacaveInfo(String zone, String kind, double x, double z, double lengthRadius, double widthRadius, double angle,
                           double floorY, double ceilingY, CaveFluid fluid, int fluidTop, boolean pillars, int bridges, double shelf,
                           double cenoteX, double cenoteZ) {
    static MegacaveInfo of(Megacaves.Megacave m) {
        double cx = Double.NaN;
        double cz = Double.NaN;
        for (CavePrimitive p : m.extras) {
            if (p.funnelTop == p.funnelTop) {
                cx = p.ax;
                cz = p.az;
            }
        }
        return new MegacaveInfo(m.zone == Megacaves.UPPER ? "upper" : "deep", m.kind.name().toLowerCase(java.util.Locale.ROOT), m.x, m.z,
                m.radius * (1.0 + m.elongation), m.radius * (1.0 - m.elongation), Math.atan2(m.sin, m.cos), m.floor, m.floor + m.height,
                m.domain.fluid, m.domain.fluid == CaveFluid.AIR ? Integer.MIN_VALUE : m.domain.level, m.pillarChance > 0.0, m.bridges.length, m.shelf,
                cx, cz);
    }
}
