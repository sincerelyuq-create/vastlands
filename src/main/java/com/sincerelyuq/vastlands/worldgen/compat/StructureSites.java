package com.sincerelyuq.vastlands.worldgen.compat;

import com.sincerelyuq.vastlands.Vastlands;
import com.sincerelyuq.vastlands.worldgen.caves.CaveFluid;
import com.sincerelyuq.vastlands.worldgen.caves.VastlandsCaves;
import com.sincerelyuq.vastlands.worldgen.context.GenerationContext;
import com.sincerelyuq.vastlands.worldgen.hydrology.WaterColumn;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSystems;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLongArray;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/**
 * Where other mods' structures may start in a Vastlands world. Their placement rules were made
 * for vanilla's terrain: a start on a Vastlands mountainside, in a river or across a flooded
 * cave would leave the structure floating over a slope, buried in it, standing in water or
 * flooded. A start is dropped (as vanilla drops one in the wrong biome) when it fails a check of
 * the structure tags it is in:
 * <ul>
 *     <li>{@code #vastlands:site/level_ground}: the ground under the start piece (its corners,
 *     edges and centre on the Vastlands surface) varies by at most 4 blocks, 6 for pieces wider
 *     than 16 (the structure's own terrain adaptation evens out the rest);</li>
 *     <li>{@code #vastlands:site/dry_ground}: no river, lake or sea water under the start piece;</li>
 *     <li>{@code #vastlands:site/sealed}: no flooded or lava-filled cave space at the corners and
 *     centres of any piece (a chamber cut open into a cave lake would flood);</li>
 *     <li>{@code #vastlands:site/apart_from_sanctum}: not within 3 chunks of a chunk where
 *     Sincerelyuq's Biomes' strata sanctum can start (its placement grid, as vanilla's exclusion
 *     zones work), so nothing stands on the temple's entrance.</li>
 * </ul>
 * Each check reads only pure model queries and the structure's own pieces, so it is the same in
 * every generation order. Structures in none of the tags (vanilla's included) are untouched;
 * other worlds are untouched.
 */
public final class StructureSites {
    public static final TagKey<Structure> LEVEL_GROUND = tag("level_ground");
    public static final TagKey<Structure> DRY_GROUND = tag("dry_ground");
    public static final TagKey<Structure> SEALED = tag("sealed");
    public static final TagKey<Structure> APART_FROM_SANCTUM = tag("apart_from_sanctum");
    private static final ResourceKey<StructureSet> SANCTUM = ResourceKey.create(Registries.STRUCTURE_SET,
            ResourceLocation.fromNamespaceAndPath(ModBlock.NAMESPACE, "strata_sanctum"));
    /** Starts checked and dropped, per check (diagnostics). */
    private static final AtomicLongArray COUNTS = new AtomicLongArray(5);

    private StructureSites() {
    }

    private static TagKey<Structure> tag(String name) {
        return TagKey.create(Registries.STRUCTURE, Vastlands.id("site/" + name));
    }

    /** False if a valid start fails a site check of its tags (only called for Vastlands generators). */
    public static boolean accept(Holder<Structure> structure, StructureStart start, RandomState random, RegistryAccess registries, long seed) {
        boolean level = structure.is(LEVEL_GROUND);
        boolean dry = structure.is(DRY_GROUND);
        boolean sealed = structure.is(SEALED);
        boolean apart = structure.is(APART_FROM_SANCTUM);
        if (!level && !dry && !sealed && !apart) {
            return true;
        }
        Optional<GenerationContext> context = GenerationContext.of(random);
        List<StructurePiece> pieces = start.getPieces();
        if (context.isEmpty() || pieces.isEmpty()) {
            return true;
        }
        GenerationSystems systems = context.get().systems();
        COUNTS.incrementAndGet(0);
        BoundingBox first = pieces.get(0).getBoundingBox();
        if ((level || dry) && !ground(systems, first, level, dry)) {
            count(level ? 1 : 2, structure, start);
            return false;
        }
        if (sealed && systems.caves() instanceof VastlandsCaves caves) {
            for (StructurePiece piece : pieces) {
                if (!sealed(caves, piece.getBoundingBox())) {
                    count(3, structure, start);
                    return false;
                }
            }
        }
        if (apart && nearSanctum(registries, seed, start.getChunkPos())) {
            count(4, structure, start);
            return false;
        }
        return true;
    }

    private static void count(int check, Holder<Structure> structure, StructureStart start) {
        long dropped = COUNTS.incrementAndGet(check);
        if (Long.bitCount(dropped) == 1) {
            Vastlands.LOGGER.debug("Vastlands structure sites: {} of {} starts dropped by check {} (latest {} at {})", dropped, COUNTS.get(0), check,
                    structure.unwrapKey().map(k -> k.location().toString()).orElse("?"), start.getChunkPos());
        }
    }

    /** The ground under a box: its relief within the limit (level) and no water (dry), at 9 points. */
    static boolean ground(GenerationSystems systems, BoundingBox box, boolean level, boolean dry) {
        double lo = Double.POSITIVE_INFINITY;
        double hi = Double.NEGATIVE_INFINITY;
        for (int i = 0; i <= 2; i++) {
            for (int j = 0; j <= 2; j++) {
                int x = box.minX() + (box.maxX() - box.minX()) * i / 2;
                int z = box.minZ() + (box.maxZ() - box.minZ()) * j / 2;
                if (dry) {
                    WaterColumn water = systems.hydrology().water(x, z);
                    if (water.wet()) {
                        return false;
                    }
                }
                double h = systems.surfaceHeight(x, z);
                lo = Math.min(lo, h);
                hi = Math.max(hi, h);
            }
        }
        double limit = Math.max(box.getXSpan(), box.getZSpan()) > 16 ? 6.0 : 4.0;
        return !level || hi - lo <= limit;
    }

    /** No cave water or lava at a box's corners and centre. */
    static boolean sealed(VastlandsCaves caves, BoundingBox box) {
        for (int i = 0; i <= 2; i++) {
            for (int j = 0; j <= 2; j++) {
                for (int k = 0; k <= 2; k++) {
                    if ((i == 1) != (j == 1) || (j == 1) != (k == 1)) {
                        continue;
                    }
                    int x = box.minX() + (box.maxX() - box.minX()) * i / 2;
                    int y = box.minY() + (box.maxY() - box.minY()) * j / 2;
                    int z = box.minZ() + (box.maxZ() - box.minZ()) * k / 2;
                    if (caves.isCave(x, y, z) && caves.caveFluid(x, y, z) != CaveFluid.AIR) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** True if the strata sanctum's placement grid has a potential start within 3 chunks. */
    static boolean nearSanctum(RegistryAccess registries, long seed, ChunkPos chunk) {
        Optional<Holder.Reference<StructureSet>> set = registries.lookupOrThrow(Registries.STRUCTURE_SET).get(SANCTUM);
        if (set.isEmpty() || !(set.get().value().placement() instanceof RandomSpreadStructurePlacement spread)) {
            return false;
        }
        int range = 3;
        for (int dz = -range; dz <= range; dz += range) {
            for (int dx = -range; dx <= range; dx += range) {
                ChunkPos potential = spread.getPotentialStructureChunk(seed, chunk.x + dx, chunk.z + dz);
                if (Math.abs(potential.x - chunk.x) <= range && Math.abs(potential.z - chunk.z) <= range) {
                    return true;
                }
            }
        }
        return false;
    }
}
