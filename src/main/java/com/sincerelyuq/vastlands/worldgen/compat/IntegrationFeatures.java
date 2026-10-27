package com.sincerelyuq.vastlands.worldgen.compat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sincerelyuq.vastlands.Vastlands;
import com.sincerelyuq.vastlands.registry.VastlandsRegistries;
import com.sincerelyuq.vastlands.worldgen.VastlandsChunkGenerator;
import com.sincerelyuq.vastlands.worldgen.context.GenerationContext;
import com.sincerelyuq.vastlands.worldgen.hydrology.WaterColumn;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSystems;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Other mods' placed features in Vastlands worlds, chosen by placed-feature tags (optional
 * entries, so no dependency either way). In other worlds every feature places exactly as before.
 * <ul>
 *     <li>{@code #vastlands:integration/replaced}: not placed at all, because Vastlands builds the
 *     same thing from its models (Sincerelyuq's Biomes' giant lumen mushrooms, lumen floors and
 *     ponds, amethyst spikes and crystal columns, spinel lakes, the smoldering forge, sand dunes and
 *     ash vents: see ARCHITECTURE.md, Integration), with the mod's own blocks.</li>
 *     <li>{@code #vastlands:integration/guard_small}, {@code guard_medium}, {@code guard_large}:
 *     placed only where no structure piece lies within 8, 16 or 24 blocks of the position
 *     (horizontally; from 8 below to 24 above it), so a feature that does not check structures
 *     itself (bone ribcages, boulders, rope bridges) does not grow into a ruin or a temple.</li>
 *     <li>{@code #vastlands:integration/level_ground}: placed only on dry ground (no river, lake or
 *     sea within 6 blocks) whose height varies by at most 3 blocks within 6 (the Vastlands
 *     surface), so a pool dug at the ground's height (lotus pools) never hangs on a slope or sits
 *     in a river.</li>
 * </ul>
 * The checks read only what a decorating region may read: structure references of the chunks
 * around the decorated one and starts within 8 chunks of it (every such start is fixed before
 * any chunk is decorated), and pure model queries, so the result does not depend on generation
 * order.
 */
public final class IntegrationFeatures {
    public static final TagKey<PlacedFeature> REPLACED = tag("replaced");
    public static final TagKey<PlacedFeature> GUARD_SMALL = tag("guard_small");
    public static final TagKey<PlacedFeature> GUARD_MEDIUM = tag("guard_medium");
    public static final TagKey<PlacedFeature> GUARD_LARGE = tag("guard_large");
    public static final TagKey<PlacedFeature> LEVEL_GROUND = tag("level_ground");

    private IntegrationFeatures() {
    }

    private static TagKey<PlacedFeature> tag(String name) {
        return TagKey.create(Registries.PLACED_FEATURE, Vastlands.id("integration/" + name));
    }

    /**
     * The filters a feature gets in Vastlands worlds (empty for most features); a replaced feature
     * gets one that never passes.
     */
    public static List<PlacementModifier> filters(Holder<PlacedFeature> feature) {
        if (feature.is(REPLACED)) {
            return List.of(new Filter(Mode.NEVER, 1));
        }
        List<PlacementModifier> filters = new ArrayList<>(2);
        int radius = feature.is(GUARD_LARGE) ? 24 : (feature.is(GUARD_MEDIUM) ? 16 : (feature.is(GUARD_SMALL) ? 8 : 0));
        if (radius > 0) {
            filters.add(new Filter(Mode.CLEAR_OF_STRUCTURES, radius));
        }
        if (feature.is(LEVEL_GROUND)) {
            filters.add(new Filter(Mode.LEVEL_GROUND, 6));
        }
        return filters;
    }

    public enum Mode implements StringRepresentable {
        /** Vastlands builds this itself: nothing is placed. */
        NEVER("never"),
        CLEAR_OF_STRUCTURES("clear_of_structures"),
        LEVEL_GROUND("level_ground");

        public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);
        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    /** {@code vastlands:integration}: one of the checks above at a radius. Passes everything outside Vastlands worlds. */
    public static final class Filter extends PlacementFilter {
        public static final MapCodec<Filter> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Mode.CODEC.fieldOf("mode").forGetter(f -> f.mode),
                Codec.intRange(1, 32).fieldOf("radius").forGetter(f -> f.radius)
        ).apply(instance, Filter::new));

        private final Mode mode;
        private final int radius;

        public Filter(Mode mode, int radius) {
            this.mode = mode;
            this.radius = radius;
        }

        @Override
        protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
            if (!(context.generator() instanceof VastlandsChunkGenerator)) {
                return true;
            }
            return switch (this.mode) {
                case NEVER -> false;
                case CLEAR_OF_STRUCTURES -> clearOfStructures(context.getLevel(), pos, this.radius, 8, 24);
                case LEVEL_GROUND -> GenerationContext.of(context.getLevel().getLevel()).map(c -> levelGround(c.systems(), pos, this.radius)).orElse(true);
            };
        }

        @Override
        public PlacementModifierType<?> type() {
            return VastlandsRegistries.INTEGRATION_FILTER.get();
        }
    }

    /** Dry ground whose height varies by at most 3 blocks within a radius (9 points of the Vastlands surface). */
    static boolean levelGround(GenerationSystems systems, BlockPos pos, int radius) {
        double lo = Double.POSITIVE_INFINITY;
        double hi = Double.NEGATIVE_INFINITY;
        for (int k = 0; k <= 8; k++) {
            int x = pos.getX();
            int z = pos.getZ();
            if (k > 0) {
                double a = k * Math.PI / 4;
                x += (int) Math.round(Math.cos(a) * radius);
                z += (int) Math.round(Math.sin(a) * radius);
            }
            WaterColumn water = systems.hydrology().water(x, z);
            if (water.wet() || water.kind() == WaterColumn.Kind.ROCK) {
                return false;
            }
            double h = systems.surfaceHeight(x, z);
            lo = Math.min(lo, h);
            hi = Math.max(hi, h);
        }
        return hi - lo <= 3.0;
    }

    /**
     * True if no piece of a structure lies within {@code radius} blocks of a position horizontally
     * and between {@code below} under and {@code above} over it: the structures referenced by the
     * decorated chunk and its neighbours, with starts the region can read.
     */
    public static boolean clearOfStructures(WorldGenLevel level, BlockPos pos, int radius, int below, int above) {
        for (BoundingBox b : piecesAround(level, pos.getX() >> 4, pos.getZ() >> 4)) {
            if (b.maxX() >= pos.getX() - radius && b.minX() <= pos.getX() + radius && b.maxZ() >= pos.getZ() - radius && b.minZ() <= pos.getZ() + radius
                    && b.maxY() >= pos.getY() - below && b.minY() <= pos.getY() + above) {
                return false;
            }
        }
        return true;
    }

    /**
     * The pieces of the structures referenced by a chunk and its eight neighbours whose starts lie
     * within 8 chunks of the chunk being decorated (what a features region may read).
     */
    public static List<BoundingBox> piecesAround(WorldGenLevel level, int cx, int cz) {
        List<BoundingBox> boxes = new ArrayList<>();
        ChunkPos center = level instanceof WorldGenRegion region ? region.getCenter() : new ChunkPos(cx, cz);
        java.util.Set<StructureStart> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int rx = cx + dx;
                int rz = cz + dz;
                if (Math.max(Math.abs(rx - center.x), Math.abs(rz - center.z)) > 1) {
                    continue;
                }
                ChunkAccess chunk = level.getChunk(rx, rz, ChunkStatus.STRUCTURE_REFERENCES, false);
                if (chunk == null) {
                    continue;
                }
                for (Map.Entry<Structure, LongSet> entry : chunk.getAllReferences().entrySet()) {
                    for (long start : entry.getValue()) {
                        int sx = ChunkPos.getX(start);
                        int sz = ChunkPos.getZ(start);
                        if (Math.max(Math.abs(sx - center.x), Math.abs(sz - center.z)) > 8) {
                            continue;
                        }
                        ChunkAccess holder = level.getChunk(sx, sz, ChunkStatus.STRUCTURE_STARTS, false);
                        StructureStart s = holder == null ? null : holder.getStartForStructure(entry.getKey());
                        if (s == null || !s.isValid() || !seen.add(s)) {
                            continue;
                        }
                        for (StructurePiece piece : s.getPieces()) {
                            boxes.add(piece.getBoundingBox());
                        }
                    }
                }
            }
        }
        return boxes;
    }

}
