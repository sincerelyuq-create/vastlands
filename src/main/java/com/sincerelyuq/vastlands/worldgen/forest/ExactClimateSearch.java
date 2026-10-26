package com.sincerelyuq.vastlands.worldgen.forest;

import java.util.function.Supplier;

/**
 * Makes biome lookups independent of what the thread looked up before. Vanilla's climate index
 * ({@code Climate.RTree.search}) starts from the last leaf the thread found and keeps it on an
 * exact tie, so a column whose climate lies on a parameter boundary gets whichever biome the
 * thread happened to find last. The forests decide about each tree from several chunks, so they
 * need the same answer every time: inside {@link #call}, {@code ClimateRTreeMixin} searches from
 * scratch and leaves the thread's last result alone. Any biome source that uses the index (vanilla's
 * multi-noise source, other mods' region lists) answers exactly; everything else is unchanged.
 */
public final class ExactClimateSearch {
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private ExactClimateSearch() {
    }

    public static boolean active() {
        return DEPTH.get()[0] > 0;
    }

    public static <T> T call(Supplier<T> lookup) {
        int[] depth = DEPTH.get();
        depth[0]++;
        try {
            return lookup.get();
        } finally {
            depth[0]--;
        }
    }
}
