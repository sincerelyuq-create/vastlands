package com.sincerelyuq.vastlands.worldgen.forest;

/**
 * The growth forms of Vastlands trees (see {@link TreeShapes}). A form and a {@link Wood} make a
 * tree; size, crown, lean and the random details come from its {@link TreeSpec}.
 */
public enum TreeForm {
    /** A log with a ball of leaves: shrubs of the understory and of forest edges. */
    BUSH(1.5, 5),
    /** Broadleaf tree: a trunk forking into a few branches that end in leaf clusters (oak, birch). */
    BROADLEAF(3.4, 13),
    /** An ancient broadleaf: a flared 2 x 2 trunk, heavy curving limbs, a crown up to ~28 blocks across. */
    ANCIENT(8.0, 16),
    /** Slender birch: a narrow crown on the upper trunk, a few twigs. */
    BIRCH(2.8, 8),
    /** Spruce: a conical crown in whorls of branches. */
    CONIFER(3.0, 9),
    /** Pine: a long bare trunk, a small rounded crown at the top. */
    PINE(3.2, 7),
    /** A giant spruce on a 2 x 2 trunk. */
    MEGA_CONIFER(6.0, 12),
    /** A giant pine on a 2 x 2 trunk. */
    MEGA_PINE(6.0, 11),
    /** Stunted, wind-shaped spruce of the treeline. */
    KRUMMHOLZ(2.0, 7),
    /** Dark oak: a 2 x 2 trunk, spreading limbs, a broad flat canopy. */
    DARK_OAK(4.6, 12),
    /** A tall, thin jungle tree with a small crown, vines and cocoa. */
    TALL_JUNGLE(3.2, 10),
    /** A giant jungle tree on a 2 x 2 trunk with branches up its length. */
    MEGA_JUNGLE(6.5, 13),
    /** Acacia: a leaning, forked trunk under flat canopies. */
    ACACIA(3.6, 14),
    /** Swamp oak: a wide flat crown hung with vines. */
    SWAMP(3.6, 10),
    /** A dead standing trunk without leaves. */
    SNAG(2.0, 5),
    /** A fallen log on the forest floor, sometimes beside its stump, sometimes broken or rotting. */
    FALLEN(2.2, 11),
    /**
     * Huge mushrooms keep a canopy's width from trees, so caps and crowns rarely tangle. Their
     * shapes are procedural (flat, umbrella, wide, tiered and lopsided brown caps; round, tall,
     * flattened and bell-shaped red ones; stems that lean and curve, thick stems under big caps).
     */
    HUGE_BROWN_MUSHROOM(4.6, 8),
    HUGE_RED_MUSHROOM(4.0, 7),
    /** A broken-off stump: a short snapped trunk, sometimes with a bark collar and moss or mushrooms on top. */
    STUMP(1.8, 3),
    /** A wind-thrown tree: the log with its root plate standing up at one end. */
    UPROOTED(2.4, 10),
    /** Woody debris: fallen branches lying across each other. */
    DEBRIS(1.5, 5),
    /** A rare giant mushroom of either colour (tiered brown caps, great red bells) on a thick stem. */
    GIANT_MUSHROOM(7.0, 11);

    /**
     * Spacing radius: two trees stand at least the mean of their spacings apart (trunk to trunk),
     * so giants keep room around them and shrubs fill gaps.
     */
    public final double spacing;
    /**
     * The farthest any block of the form lies from its root, horizontally (the builder drops
     * blocks beyond it): a box farther away than this from a root never receives its blocks.
     * Two or three blocks above the largest radius measured over ~170 000 trees of ten squares
     * ({@code forestPreview --reach}), so the limit practically never cuts a crown.
     */
    public final int reach;

    TreeForm(double spacing, int reach) {
        this.spacing = spacing;
        this.reach = reach;
    }

    /** A 2 x 2 trunk: its root is the north-west column. */
    public boolean wide() {
        return this == ANCIENT || this == MEGA_CONIFER || this == MEGA_PINE || this == DARK_OAK || this == MEGA_JUNGLE;
    }

    /** Lies on or hugs the ground rather than standing up (no crown, no leaf litter shade). */
    public boolean lowGrowth() {
        return this == BUSH || this == FALLEN || this == KRUMMHOLZ || this == STUMP || this == UPROOTED || this == DEBRIS;
    }

    /** Deadwood of the floor lattice: lies on the ground (fallen logs, stumps, root plates, branches). */
    public boolean deadwood() {
        return this == FALLEN || this == STUMP || this == UPROOTED || this == DEBRIS;
    }

    /** Huge and giant mushrooms. */
    public boolean mushroom() {
        return this == HUGE_BROWN_MUSHROOM || this == HUGE_RED_MUSHROOM || this == GIANT_MUSHROOM;
    }
}
