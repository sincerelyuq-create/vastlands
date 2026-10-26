package com.sincerelyuq.vastlands.worldgen.system;

import com.sincerelyuq.vastlands.worldgen.nature.FormationSpec;
import java.util.function.Consumer;

/**
 * Natural decoration and geological wonders: boulders, tors, spires, arches, driftwood and shore
 * stones at the surface, the formations of every cave (dripstone, roots and moss, sculk,
 * crystals, basalt, rubble, pillars) and rare monumental ones. Owned by Agent 6.
 *
 * <p>Not part of the noise router. The features {@code vastlands:surface_formations} and
 * {@code vastlands:cave_formations} (added to the overworld's biomes in their local
 * modifications) reach this system through {@code GenerationContext.of(ServerLevel)} and build
 * each chunk's part of every formation that reaches it.
 *
 * <p>May depend on {@link TerrainSystem}, {@link HydrologySystem} and {@link CaveSystem}; the
 * forests depend on it (trees keep clear of surface formations).
 */
public interface NatureSystem extends GenerationSystem {
    /**
     * Every formation of the surface ({@code surface}) or of the caves whose blocks can reach the
     * box (inclusive), in a fixed order; nothing before the system is bound to its world's biomes.
     */
    default void formations(int minX, int minZ, int maxX, int maxZ, boolean surface, Consumer<FormationSpec> out) {
    }

    /**
     * False where a surface formation keeps a tree away (its trunk within {@code trunk} blocks of the
     * formation's footprint, its crown of radius {@code crown} over a tall one). True where there is
     * none, and before the system is bound.
     */
    default boolean clearOfFormations(int x, int z, double trunk, double crown) {
        return true;
    }
}
