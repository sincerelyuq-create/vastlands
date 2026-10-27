package com.sincerelyuq.vastlands.worldgen.forest;

import com.google.common.collect.MapMaker;
import com.mojang.serialization.MapCodec;
import com.sincerelyuq.vastlands.Vastlands;
import com.sincerelyuq.vastlands.registry.VastlandsRegistries;
import com.sincerelyuq.vastlands.worldgen.VastlandsChunkGenerator;
import com.sincerelyuq.vastlands.worldgen.compat.IntegrationFeatures;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Vanilla's tree features give way to the Vastlands forests where Vastlands grows the trees.
 *
 * <p>The placed features in {@code #vastlands:forest/displaced} (vanilla's tree features of the
 * forest biomes, which the Vastlands trees replace, and vanilla's surface lava lakes, which would
 * set the dense forests alight) are placed in Vastlands worlds with one more filter at the end of their
 * placement, {@link Filter}, which drops every position whose biome (the one vanilla's biome
 * filter reads) belongs to a forest community ({@link VastlandsForests#replacesVanillaTrees}). Everywhere else (biomes of other
 * mods that reuse a vanilla tree feature, unmanaged vanilla biomes) they place exactly as
 * before, with the same random sequence; in other worlds nothing changes. Other mods' and
 * datapacks' features are never touched unless the tag lists them. Other mods' features that
 * Vastlands replaces or keeps clear of structures get their filters here too (see
 * {@link IntegrationFeatures}).
 *
 * <p>The placed features in {@code #vastlands:forest/buried} (vanilla's underground lava lakes) get
 * the same filter judged at the ground above them, and only while they start less than
 * {@link #BURIED_DEPTH} blocks under the ground: such a lake carves four layers of air above its
 * lava and opens out of a hillside, and a dense forest above it burns. Deeper ones are untouched.
 */
public final class VanillaTrees {
    public static final TagKey<PlacedFeature> DISPLACED = TagKey.create(Registries.PLACED_FEATURE, Vastlands.id("forest/displaced"));
    public static final TagKey<PlacedFeature> BURIED = TagKey.create(Registries.PLACED_FEATURE, Vastlands.id("forest/buried"));
    /** Depth under the ground (blocks) above which a buried feature counts as at the surface. */
    public static final int BURIED_DEPTH = 24;
    /** Each vanilla feature with the filter appended (identity keys: the registry's own values). */
    private static final ConcurrentMap<PlacedFeature, Optional<PlacedFeature>> FILTERED = new MapMaker().weakKeys().makeMap();

    private VanillaTrees() {
    }

    /** The feature with the forest filter appended if Vastlands replaces it in this world, else null. */
    @Nullable
    public static PlacedFeature filtered(PlacedFeature feature, WorldGenLevel level, ChunkGenerator generator) {
        if (!(generator instanceof VastlandsChunkGenerator)) {
            return null;
        }
        Optional<PlacedFeature> filtered = FILTERED.computeIfAbsent(feature, f -> {
            Holder<PlacedFeature> holder = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE).wrapAsHolder(f);
            Filter filter = holder.is(DISPLACED) ? Filter.INSTANCE : (holder.is(BURIED) ? Filter.BURIED : null);
            // Other mods' features that Vastlands replaces or guards (see IntegrationFeatures).
            List<PlacementModifier> integration = IntegrationFeatures.filters(holder);
            if (filter == null && integration.isEmpty()) {
                return Optional.empty();
            }
            List<PlacementModifier> placement = new ArrayList<>(f.placement());
            if (filter != null) {
                placement.add(filter);
            }
            placement.addAll(integration);
            return Optional.of(new PlacedFeature(f.feature(), List.copyOf(placement)));
        });
        if (filtered.isEmpty() || ForestBiomes.forests(level, generator).isEmpty()) {
            return null;
        }
        return filtered.get();
    }

    /** Context for placing a filtered copy as the original (its biome filter checks the original). */
    public static PlacementContext context(WorldGenLevel level, ChunkGenerator generator, PlacedFeature original) {
        return new PlacementContext(level, generator, Optional.of(original));
    }

    /** {@code vastlands:outside_forests}: keeps a position only where Vastlands does not grow the trees. */
    public static final class Filter extends PlacementFilter {
        public static final Filter INSTANCE = new Filter(false);
        /** For buried features: judged at the ground above, and only near it (never serialized). */
        static final Filter BURIED = new Filter(true);
        public static final MapCodec<Filter> CODEC = MapCodec.unit(() -> INSTANCE);

        private final boolean buried;

        private Filter(boolean buried) {
            this.buried = buried;
        }

        /**
         * Judges the biome vanilla's own biome filter judges (the level's biome at the position),
         * so no vanilla tree slips through where the two would disagree at a biome border.
         */
        @Override
        protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
            Optional<VastlandsForests> forests = ForestBiomes.forests(context.getLevel(), context.generator());
            if (forests.isEmpty()) {
                return true;
            }
            BlockPos at = pos;
            if (this.buried) {
                int ground = forests.get().groundTop(pos.getX(), pos.getZ());
                if (pos.getY() < ground - BURIED_DEPTH) {
                    return true;
                }
                at = new BlockPos(pos.getX(), ground, pos.getZ());
            }
            int category = ForestBiomes.category(context.getLevel().getBiome(at));
            return !forests.get().replacesVanillaTrees(category, pos.getX(), pos.getZ());
        }

        @Override
        public PlacementModifierType<?> type() {
            return VastlandsRegistries.OUTSIDE_FORESTS.get();
        }
    }
}
