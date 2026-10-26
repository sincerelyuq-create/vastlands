package com.sincerelyuq.vastlands.worldgen.forest;

import com.sincerelyuq.vastlands.Vastlands;
import com.sincerelyuq.vastlands.worldgen.VastlandsChunkGenerator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * {@code vastlands:forest}: the Vastlands forests of one chunk (see {@link ForestBlocks}). Added
 * by the biome modifier {@code vastlands:forest} to the vegetal decoration of every biome in
 * {@code #vastlands:forest/all}, so it runs once in every chunk next to a forest biome, after the
 * biomes' own vegetation and before vanilla's top-layer freezing (snow lands on the crowns). It
 * places nothing outside Vastlands worlds: the same biomes in other worlds keep vanilla's trees.
 */
public final class ForestFeature extends Feature<NoneFeatureConfiguration> {
    /** Chunks decorated and the time spent, for the debug log line every 256 chunks. */
    private static final AtomicLong CHUNKS = new AtomicLong();
    private static final AtomicLong NANOS = new AtomicLong();
    private static final AtomicLong TREES = new AtomicLong();

    public ForestFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!(context.chunkGenerator() instanceof VastlandsChunkGenerator)) {
            return false;
        }
        WorldGenLevel level = context.level();
        Optional<VastlandsForests> forests = ForestBiomes.forests(level, context.chunkGenerator());
        if (forests.isEmpty()) {
            return false;
        }
        long t0 = System.nanoTime();
        ForestBlocks.Stats stats = ForestBlocks.decorate(level, level.getChunk(context.origin()), forests.get());
        long nanos = NANOS.addAndGet(System.nanoTime() - t0);
        long trees = TREES.addAndGet(stats.trees);
        long chunks = CHUNKS.incrementAndGet();
        if ((chunks & 255) == 0) {
            Vastlands.LOGGER.debug("Vastlands forests: {} chunks, {} ms per chunk, {} trees reaching a chunk", chunks,
                    String.format(java.util.Locale.ROOT, "%.2f", nanos / 1e6 / chunks), String.format(java.util.Locale.ROOT, "%.1f", trees / (double) chunks));
        }
        return stats.trees > 0 || stats.plants > 0;
    }
}
