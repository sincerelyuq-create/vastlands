package com.sincerelyuq.vastlands.worldgen.hydrology;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Small thread-safe least-recently-used cache of values that are expensive to compute. A missing
 * value is computed once, by the first thread that asks for it, outside the lock; other threads
 * asking for the same key wait for that result. Evicted values are simply computed again later:
 * every value is a pure function of its key, so eviction never changes results.
 */
final class LruCache<K, V> {
    private final Map<K, CompletableFuture<V>> map;

    LruCache(int capacity) {
        this.map = new LinkedHashMap<>(16, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, CompletableFuture<V>> eldest) {
                return this.size() > capacity;
            }
        };
    }

    V get(K key, Function<K, V> loader) {
        CompletableFuture<V> future;
        boolean compute = false;
        synchronized (this.map) {
            future = this.map.get(key);
            if (future == null) {
                future = new CompletableFuture<>();
                this.map.put(key, future);
                compute = true;
            }
        }
        if (compute) {
            try {
                future.complete(loader.apply(key));
            } catch (RuntimeException | Error e) {
                future.completeExceptionally(e);
                synchronized (this.map) {
                    this.map.remove(key, future);
                }
                throw e;
            }
        }
        return future.join();
    }
}
