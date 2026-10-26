package com.sincerelyuq.vastlands.worldgen.nature;

/**
 * One natural formation: where it stands and everything its shape is built from. Immutable; a
 * pure function of the seed and its lattice cell (see {@link NatureModel}).
 *
 * @param key    identity (lattice layer, cell, and for caves the run of the column), the same from every chunk
 * @param kind   what it is
 * @param x      root column
 * @param z      root column
 * @param y      anchor: the first open block above the ground or cave floor (floor formations), the
 *               last open block under the ceiling (ceiling formations)
 * @param height its vertical size in blocks (or the room it may use)
 * @param radius its horizontal size in blocks
 * @param span   cave formations: blocks from the floor to the ceiling at the root (the run's height),
 *               for islets the water's top above the floor; driftwood: the wood ({@code Wood} ordinal)
 * @param angle  the direction of its long axis (radians), where it has one
 * @param rock   the palette of its stone
 * @param flags  {@link #MOSSY}, {@link #SCULK}, {@link #FLOODED}, {@link #LAVA}, {@link #SNOWY}, {@link #DRY},
 *               {@link #DRIPSTONE}, {@link #CEILING}, {@link #LUSH}, {@link #SPINEL}
 * @param seed   the random details of its shape
 */
public record FormationSpec(long key, FormationKind kind, int x, int z, int y, double height, double radius, int span, double angle, Rock rock,
                            int flags, long seed) {
    /** Moss on its faces (damp woods, lush caves). */
    public static final int MOSSY = 1;
    /** Sculk on its faces (the deep dark). */
    public static final int SCULK = 2;
    /** Stands in water: its lower cells take the water. */
    public static final int FLOODED = 4;
    /** Stands in lava (basalt columns of volcanic caves). */
    public static final int LAVA = 8;
    /** Snowy climate. */
    public static final int SNOWY = 16;
    /** Dry climate (bleached wood, no moss). */
    public static final int DRY = 32;
    /** A dripstone cave (pointed dripstone tips, dripstone bodies). */
    public static final int DRIPSTONE = 64;
    /** Hangs from the ceiling (stalactites, pendants, root curtains). */
    public static final int CEILING = 128;
    /** Clothed in moss blocks (lush caves) rather than weathered with mossy stone. */
    public static final int LUSH = 256;
    /** A pool of lavender spinel rather than water (amethyst caves). */
    public static final int SPINEL = 512;

    public boolean has(int flag) {
        return (this.flags & flag) != 0;
    }
}
