package com.sincerelyuq.vastlands.worldgen.system;

import com.sincerelyuq.vastlands.worldgen.forest.ForestSample;
import com.sincerelyuq.vastlands.worldgen.forest.TreeSpec;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * Large forests, tree diversity, giant trees and vegetation distribution. Owned by Agent 5.
 *
 * <p>Not part of the noise router. The forest feature ({@code vastlands:forest}, added to the
 * biomes of the forest tags) reaches this system through {@code GenerationContext.of(ServerLevel)}
 * during feature placement and builds each chunk's part of every tree that reaches it.
 *
 * <p>May depend on {@link TerrainSystem}, {@link HydrologySystem} and {@link CaveSystem}.
 */
public interface ForestSystem extends GenerationSystem {
    /**
     * Relative density of Vastlands trees for a column, in {@code [0, 1]}: the share of the tree
     * lattice that holds a tree there. 0 means Vastlands adds no trees there (open land, unmanaged
     * biomes, before the forest feature first ran in this world).
     */
    double treeDensity(int blockX, int blockZ);

    /** The forest around a column (community, density, age, clearings, water, treeline), or null if none is known. */
    @Nullable
    default ForestSample forestAt(int blockX, int blockZ) {
        return null;
    }

    /**
     * Every tree, shrub and fallen log whose blocks can reach the box (inclusive), in a fixed
     * order: for structures that want to stay clear of large trees, natural wonders and tests.
     */
    default void trees(int minX, int minZ, int maxX, int maxZ, Consumer<TreeSpec> out) {
    }
}
