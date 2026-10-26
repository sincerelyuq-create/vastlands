package com.sincerelyuq.vastlands.worldgen.forest;

/**
 * One placed tree: where it stands and everything its shape is built from. Immutable; a pure
 * function of the seed and its lattice cell (see {@link ForestModel}).
 *
 * @param key     identity of the tree (its lattice cell and layer), the same from every chunk
 * @param x       root column (the north-west column of a 2 x 2 trunk)
 * @param z       root column
 * @param y       Y of the root: the first block above the ground
 * @param form    growth form
 * @param wood    logs and leaves
 * @param height  height of the crown's top above the root, in blocks
 * @param crown   horizontal radius of the crown, in blocks
 * @param leanX   horizontal offset of the crown's top from the trunk's base (towards light or
 *                water), x
 * @param leanZ   the same, z
 * @param windX   prevailing wind (unit vector), x: wind-shaped trees grow downwind
 * @param windZ   the same, z
 * @param vines   0..1, how many vines hang on the trunk and from the crown
 * @param flags   {@link #COCOA}, {@link #BEES}, {@link #MOSS}, {@link #PODZOL}, {@link #ROOTS}, {@link #RED},
 *                {@link #ROT}, a shape variant ({@link #style}) and a fallen log's step ({@link #step})
 * @param type    the forest community it belongs to
 * @param seed    the random details of its shape
 */
public record TreeSpec(long key, int x, int z, int y, TreeForm form, Wood wood, double height, double crown, double leanX, double leanZ,
                       double windX, double windZ, double vines, int flags, ForestType type, long seed) {
    public static final int COCOA = 1;
    public static final int BEES = 2;
    /** Moss on fallen logs, moss and mushrooms on the ground around old trees. */
    public static final int MOSS = 4;
    /** Podzol around the trunk (giant conifers). */
    public static final int PODZOL = 8;
    /** Rooted and coarse dirt around a flared trunk. */
    public static final int ROOTS = 16;
    /** Giant mushrooms: a red cap (else brown). */
    public static final int RED = 32;
    /** Deadwood on wet ground: mushrooms and moss on the log (rotting wood). */
    public static final int ROT = 64;
    /** Bits 8..11: the variant of a procedural shape (mushroom cap styles, deadwood kinds). */
    public static final int STYLE_SHIFT = 8;
    /** Bits 12..13: a fallen log's second piece one block up (1) or down (2) from the first. */
    public static final int STEP_SHIFT = 12;

    public boolean has(int flag) {
        return (this.flags & flag) != 0;
    }

    /** The shape variant (see {@link #STYLE_SHIFT}). */
    public int style() {
        return (this.flags >>> STYLE_SHIFT) & 15;
    }

    /** A fallen log's step between its pieces: 0, +1 or -1. */
    public int step() {
        int s = (this.flags >>> STEP_SHIFT) & 3;
        return s == 1 ? 1 : (s == 2 ? -1 : 0);
    }
}
