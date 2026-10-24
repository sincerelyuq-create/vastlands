package com.sincerelyuq.vastlands.worldgen.hydrology;

/**
 * The water of one block column as generated. Immutable.
 *
 * @param surface   ground surface of the density (terrain and hydrology); its top solid block is
 *                  {@code ceil(surface) - 1}
 * @param groundTop top solid block under the water: the density's, or lower inside a river
 *                  channel at the foot of a fall, where the block step cuts the bilinear ramp of the
 *                  density down to below the water; for a boulder, the boulder's top
 * @param waterTop  Y of the top water block of the body covering the column (every block from
 *                  {@code groundTop + 1} up to it is water), or {@link Integer#MIN_VALUE} if dry;
 *                  for a boulder, the water it stands in (or {@link Integer#MIN_VALUE})
 * @param kind      what the water is
 */
public record WaterColumn(double surface, int groundTop, int waterTop, Kind kind) {
    public enum Kind {
        /** No standing water: ground at or above every water surface around. */
        DRY,
        /** Sea level water, placed by vanilla's aquifers (oceans, sea-level basins, river mouths). */
        SEA,
        /** A river pool above sea level (or a river at sea level inside its channel). */
        RIVER,
        /** A lake. */
        LAKE,
        /**
         * A boulder in or beside a river: rock placed by the block step from the density's ground
         * up to {@code groundTop}, at or above the water around it.
         */
        ROCK
    }

    /** Top solid block of the density's landscape. */
    public int surfaceTop() {
        return (int) Math.ceil(this.surface) - 1;
    }

    public boolean wet() {
        return this.kind == Kind.SEA || this.kind == Kind.RIVER || this.kind == Kind.LAKE;
    }

    /** Water that Vastlands places itself (vanilla's aquifers do not fill water above sea level). */
    public boolean placed() {
        return this.kind == Kind.RIVER || this.kind == Kind.LAKE;
    }
}
