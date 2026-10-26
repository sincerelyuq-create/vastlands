package com.sincerelyuq.vastlands.worldgen.nature;

/**
 * Block codes of natural formations: plain ints, so the model stays pure Java and the block step
 * ({@link NatureBlocks}) turns them into block states. A code is
 * {@code kind | extra << 6 | flags} with the flags in the top bits.
 *
 * <ul>
 *     <li>{@link #ROCK}: extra is the {@link Rock} palette, the cell's role ({@link #BODY},
 *     {@link #ACCENT}, {@link #CAP}) and its coat ({@link #MOSSY}, {@link #SCULKY}): the block step
 *     picks the block from the palette by position, so a formation looks the same from every chunk.</li>
 *     <li>{@link #POINTED}: pointed dripstone; extra bit 0 points up, bits 1..3 the thickness
 *     (0 tip, 1 frustum, 2 middle, 3 base, 4 merged tip).</li>
 *     <li>{@link #LOG}: extra bits 0..2 the wood ({@code Wood} ordinal), 3..4 the axis (0 y, 1 x,
 *     2 z), 5 stripped (bleached driftwood, rotting wood).</li>
 *     <li>{@link #CAVE_VINES}: bit 0 berries, bit 1 the tip (the lowest block).</li>
 *     <li>{@link #SCULK_VEIN}, {@link #GLOW_LICHEN}: the faces they cover (1 down, 2 up, 4 north,
 *     8 south, 16 west, 32 east).</li>
 *     <li>{@link #AMETHYST}: bits 0..2 the facing (0 down, 1 up, 2 north, 3 south, 4 west, 5 east),
 *     bits 3..4 the size (0 small bud .. 3 cluster).</li>
 *     <li>{@link #BASALT}: bits 0..1 the axis.</li>
 *     <li>{@link #AZALEA}, {@link #AZALEA_LEAVES}: bit 0 flowering.</li>
 * </ul>
 *
 * Flags: {@link #EMBED} may replace natural ground (the foot of a rock sunk into the soil);
 * {@link #WET} may replace water (submerged rock; waterloggable decorations take the water);
 * {@link #SNAP_FLOOR} and {@link #SNAP_CEILING} mark stacks that follow uneven ground: when written,
 * the snapped cells of a column move together so that the lowest (highest) of them lands on the
 * floor (ceiling) block of that column near its height: a carpet's stack is {@code [KEEP, carpet]}
 * (the floor stays, the carpet lies on it), a moss patch's {@code [moss block, carpet]} (the floor
 * block becomes moss), a hanging plant's {@code [plant, KEEP]}. {@link #EXPOSED} and {@link #TOP}
 * are set by the shape builder on solid cells with an open face (above).
 */
public final class FormationBlocks {
    public static final int ROCK = 1;
    public static final int POINTED = 2;
    public static final int DRIPSTONE = 3;
    public static final int MOSS_BLOCK = 4;
    public static final int MOSS_CARPET = 5;
    public static final int ROOTED_DIRT = 6;
    public static final int HANGING_ROOTS = 7;
    public static final int CAVE_VINES = 8;
    public static final int AZALEA = 9;
    public static final int AZALEA_LEAVES = 10;
    public static final int SCULK = 11;
    public static final int SCULK_VEIN = 12;
    public static final int LOG = 13;
    public static final int GRAVEL = 14;
    public static final int CLAY = 15;
    public static final int CALCITE = 16;
    public static final int AMETHYST_BLOCK = 17;
    public static final int AMETHYST = 18;
    public static final int BASALT = 19;
    public static final int GLOW_LICHEN = 20;
    public static final int MAGMA = 21;
    public static final int COARSE_DIRT = 22;
    public static final int MUD = 23;
    public static final int SPORE_BLOSSOM = 24;
    public static final int SAND = 25;
    public static final int PACKED_MUD = 26;
    /** The floor or ceiling block a snapped stack is aligned to, left as it is. */
    public static final int KEEP = 27;

    /** Roles of a rock cell. */
    public static final int BODY = 0;
    public static final int ACCENT = 1;
    public static final int CAP = 2;
    /** Coats of a rock cell's exposed faces. */
    public static final int MOSSY = 1 << 8;
    public static final int SCULKY = 1 << 9;
    /** Moss blocks (lush caves) rather than weathered mossy stone. */
    public static final int LUSHY = 1 << 10;

    public static final int EMBED = 1 << 26;
    public static final int WET = 1 << 27;
    public static final int SNAP_FLOOR = 1 << 28;
    public static final int SNAP_CEILING = 1 << 29;
    /** Set by the shape builder on solid cells with an open face (see {@link FormationShape}). */
    public static final int EXPOSED = 1 << 30;
    /** Set by the shape builder on solid cells open above. */
    public static final int TOP = 1 << 31;
    private static final int FLAGS = EMBED | WET | SNAP_FLOOR | SNAP_CEILING | EXPOSED | TOP;

    private FormationBlocks() {
    }

    public static int code(int kind, int extra) {
        return kind | extra << 6;
    }

    public static int rock(Rock palette, int role, int coat) {
        return ROCK | (palette.ordinal() | role << 6 | coat) << 6;
    }

    public static int kind(int code) {
        return code & 63;
    }

    public static int extra(int code) {
        return (code & ~FLAGS) >>> 6;
    }

    public static boolean has(int code, int flag) {
        return (code & flag) != 0;
    }

    /** Rock and other solid blocks: the body of a formation (feet extend under them, decorations hang on them). */
    public static boolean isSolid(int code) {
        int kind = code & 63;
        return kind == ROCK || kind == DRIPSTONE || kind == MOSS_BLOCK || kind == ROOTED_DIRT || kind == SCULK || kind == LOG || kind == GRAVEL
                || kind == CLAY || kind == CALCITE || kind == AMETHYST_BLOCK || kind == BASALT || kind == MAGMA || kind == COARSE_DIRT
                || kind == MUD || kind == SAND || kind == PACKED_MUD;
    }

    /** Rock palette of a {@link #ROCK} code. */
    public static Rock palette(int code) {
        return Rock.VALUES[extra(code) & 63];
    }

    public static int role(int code) {
        return (extra(code) >>> 6) & 3;
    }
}
