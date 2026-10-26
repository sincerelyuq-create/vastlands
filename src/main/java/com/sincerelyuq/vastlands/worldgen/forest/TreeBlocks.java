package com.sincerelyuq.vastlands.worldgen.forest;

/**
 * Block codes of tree shapes: plain ints, so the forest model stays pure Java and the block step
 * maps them to block states. A code is {@code kind | wood << 5 | extra << 9}.
 *
 * <ul>
 *     <li>{@link #LOG}: extra is the axis (0 y, 1 x, 2 z);</li>
 *     <li>{@link #WOOD}: bark on all sides (root flares, branch forks);</li>
 *     <li>{@link #LEAVES}: extra is the leaf distance 1..6 to the nearest log of the tree, computed by
 *     the shape builder exactly as vanilla's leaves count it (6-neighbour steps through leaves), so
 *     no leaf of a generated tree decays;</li>
 *     <li>{@link #VINE}: extra is the set of faces (1 north, 2 east, 4 south, 8 west) it clings to;</li>
 *     <li>{@link #COCOA}: extra is the facing towards its log (0 north, 1 east, 2 south, 3 west) and
 *     the age ({@code << 2});</li>
 *     <li>{@link #BEE_NEST}: extra is the facing (0 north, 1 east, 2 south, 3 west);</li>
 *     <li>{@link #STEM}, {@link #BROWN_CAP}, {@link #RED_CAP}: huge mushrooms; extra holds the cap's
 *     faces (1 north, 2 east, 4 south, 8 west, 16 up, 32 down; a set bit shows the cap);</li>
 *     <li>{@link #MOSS_CARPET}, {@link #MUSHROOM} (extra 0 brown, 1 red): on fallen logs.</li>
 *     <li>{@link #STRIPPED}: a stripped log (rotting wood without its bark); extra is the axis.</li>
 *     <li>{@link #ROOT}: tangled roots (vanilla's mangrove roots) of root plates and exposed roots.</li>
 *     <li>{@link #EARTH}: soil held in a root plate (extra 0 rooted dirt, 1 coarse dirt, 2 dirt).</li>
 *     <li>{@link #HANGING_ROOTS}: hanging roots under a root plate.</li>
 * </ul>
 */
public final class TreeBlocks {
    public static final int LOG = 1;
    public static final int WOOD = 2;
    public static final int LEAVES = 3;
    public static final int VINE = 4;
    public static final int COCOA = 5;
    public static final int BEE_NEST = 6;
    public static final int STEM = 7;
    public static final int BROWN_CAP = 8;
    public static final int RED_CAP = 9;
    public static final int MOSS_CARPET = 10;
    public static final int MUSHROOM = 11;
    public static final int STRIPPED = 12;
    public static final int ROOT = 13;
    public static final int EARTH = 14;
    public static final int HANGING_ROOTS = 15;

    public static final int AXIS_Y = 0;
    public static final int AXIS_X = 1;
    public static final int AXIS_Z = 2;

    /** Ground codes of {@link TreeShape#ground}: what the top ground block becomes. */
    public static final int GROUND_PODZOL = 1;
    public static final int GROUND_COARSE_DIRT = 2;
    public static final int GROUND_MOSS = 3;
    public static final int GROUND_ROOTED_DIRT = 4;

    private TreeBlocks() {
    }

    public static int code(int kind, Wood wood, int extra) {
        return kind | wood.ordinal() << 5 | extra << 9;
    }

    public static int kind(int code) {
        return code & 31;
    }

    public static Wood wood(int code) {
        return Wood.VALUES[(code >>> 5) & 15];
    }

    public static int extra(int code) {
        return code >>> 9;
    }

    /** Logs and wood: what leaves count their distance from (vanilla's {@code #logs}, stripped logs included). */
    public static boolean isLog(int code) {
        int kind = code & 31;
        return kind == LOG || kind == WOOD || kind == STRIPPED;
    }

    /** Blocks that hold up vines and leaves (logs, wood, leaves, mushroom blocks, root plates). */
    public static boolean isSolid(int code) {
        int kind = code & 31;
        return kind == LOG || kind == WOOD || kind == LEAVES || kind == STEM || kind == BROWN_CAP || kind == RED_CAP || kind == STRIPPED
                || kind == ROOT || kind == EARTH;
    }

    /** Placed like logs (into air, plants and snow; never into water outside swamps). */
    public static boolean isBody(int code) {
        int kind = code & 31;
        return kind == LOG || kind == WOOD || kind == STEM || kind == STRIPPED || kind == ROOT || kind == EARTH;
    }
}
