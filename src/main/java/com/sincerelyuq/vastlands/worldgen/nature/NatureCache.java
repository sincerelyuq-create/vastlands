package com.sincerelyuq.vastlands.worldgen.nature;

import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;
import java.util.function.LongFunction;

/**
 * A per-thread cache of values built from a {@code long} key (lattice candidates, shapes): open
 * addressing over four slots, like the caves' and forests' caches. A hit returns exactly what a
 * miss would build. Not thread-safe: keep one per thread.
 */
final class NatureCache<T> {
    private static final int WAYS = 4;
    private final long[] keys;
    private final Object[] values;
    private final int mask;
    private int victim;

    NatureCache(int size) {
        int n = Integer.highestOneBit(Math.max(16, size - 1) << 1);
        this.keys = new long[n];
        this.values = new Object[n];
        this.mask = n - 1;
    }

    @SuppressWarnings("unchecked")
    T get(long key, LongFunction<T> builder) {
        int home = (int) (SimplexNoise.mix(key ^ 0x4E61747572L) & this.mask);
        for (int p = 0; p < WAYS; p++) {
            int slot = (home + p) & this.mask;
            Object value = this.values[slot];
            if (value == null) {
                T built = builder.apply(key);
                this.keys[slot] = key;
                this.values[slot] = built;
                return built;
            }
            if (this.keys[slot] == key) {
                return (T) value;
            }
        }
        T built = builder.apply(key);
        int slot = (home + (this.victim++ & (WAYS - 1))) & this.mask;
        this.keys[slot] = key;
        this.values[slot] = built;
        return built;
    }

    static long key(int layer, long i, long j) {
        return (long) layer << 58 ^ (i & 0x1FFFFFFFL) << 29 ^ (j & 0x1FFFFFFFL);
    }
}
