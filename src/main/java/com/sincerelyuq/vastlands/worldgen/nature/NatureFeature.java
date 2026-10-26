package com.sincerelyuq.vastlands.worldgen.nature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sincerelyuq.vastlands.Vastlands;
import com.sincerelyuq.vastlands.worldgen.VastlandsChunkGenerator;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * {@code vastlands:formations}: the natural formations of one chunk (see {@link NatureBlocks}),
 * the surface's or the caves' by its configuration, added to every biome in
 * {@code #vastlands:nature/all} (the overworld) by two biome modifiers, so they run once per chunk:
 * {@code vastlands:surface_formations} ({@code vastlands:nature}) in the local modifications, after
 * vanilla's boulders and before structures and vegetation (which grow around the formations) and
 * the forests (whose trees keep clear of them), then the dunes of the dune sea ({@link DuneSand});
 * {@code vastlands:cave_formations}
 * ({@code vastlands:nature_caves}) at the end of the underground decoration, after vanilla's
 * dripstone clusters, pointed dripstone and sculk patches (a dripstone cluster's pools replace floor
 * blocks: run before them, a stalagmite lost its foot and floated) and before the lush caves'
 * vegetation. They place nothing outside Vastlands worlds.
 *
 * <p>{@code density} (0..1, default 1) leaves out a share of the formations: a datapack can thin
 * them out by overriding the configured feature (the forests still keep the left-out ones' room).
 */
public final class NatureFeature extends Feature<NatureFeature.Config> {
    private static final AtomicLong CHUNKS = new AtomicLong();
    private static final AtomicLong NANOS = new AtomicLong();
    private static final AtomicLong FORMATIONS = new AtomicLong();

    /** The layer ({@code surface} or {@code caves}) and the share of its formations placed. */
    public record Config(String layer, float density) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("layer").forGetter(Config::layer),
                Codec.floatRange(0.0F, 1.0F).optionalFieldOf("density", 1.0F).forGetter(Config::density)
        ).apply(instance, Config::new));

        boolean surface() {
            return !"caves".equals(this.layer);
        }
    }

    public NatureFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        if (!(context.chunkGenerator() instanceof VastlandsChunkGenerator)) {
            return false;
        }
        WorldGenLevel level = context.level();
        Optional<VastlandsNature> nature = NatureBiomes.nature(level, context.chunkGenerator());
        if (nature.isEmpty()) {
            return false;
        }
        long t0 = System.nanoTime();
        NatureBlocks.Stats stats = NatureBlocks.decorate(level, level.getChunk(context.origin()), nature.get(), context.config().surface(),
                context.config().density());
        if (context.config().surface() && nature.get().model() != null) {
            // The dune sea's dunes (Agent 7), after the formations: see DuneSand.
            stats.blocks += DuneSand.build(level, level.getChunk(context.origin()), context.chunkGenerator(), nature.get().model());
        }
        long nanos = NANOS.addAndGet(System.nanoTime() - t0);
        long formations = FORMATIONS.addAndGet(stats.formations);
        long chunks = CHUNKS.incrementAndGet();
        if ((chunks & 511) == 0) {
            Vastlands.LOGGER.debug("Vastlands formations: {} chunk passes, {} ms per pass, {} formations reaching a chunk", chunks,
                    String.format(Locale.ROOT, "%.2f", nanos / 1e6 / chunks), String.format(Locale.ROOT, "%.2f", formations / (double) chunks));
        }
        return stats.blocks > 0;
    }
}
