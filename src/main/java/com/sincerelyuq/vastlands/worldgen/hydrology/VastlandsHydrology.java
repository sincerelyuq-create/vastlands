package com.sincerelyuq.vastlands.worldgen.hydrology;

import com.sincerelyuq.vastlands.worldgen.system.DensityRange;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSetup;
import com.sincerelyuq.vastlands.worldgen.system.HydrologySystem;
import com.sincerelyuq.vastlands.worldgen.system.TerrainParameter;
import com.sincerelyuq.vastlands.worldgen.system.TerrainSystem;
import com.sincerelyuq.vastlands.worldgen.terrain.VastlandsTerrain;

/**
 * The Vastlands hydrology system: a {@link HydrologyModel} turned into router density, router
 * parameters and block water.
 *
 * <ul>
 *     <li>Density: the terrain density with the column's surface moved from the terrain height to
 *     the carved height of the model (channels, valleys, gorges, levees).</li>
 *     <li>Weirdness: 0 (vanilla's valley slice: river and frozen river biomes) at grid columns
 *     inside a channel at least 6 blocks wide.</li>
 *     <li>Initial density: from the lowest carved height of the noise cell, so aquifers and the
 *     preliminary surface never see ground above a channel floor.</li>
 * </ul>
 * Node columns are cached per thread like the terrain's; a hit returns exactly what the model
 * computes.
 */
public final class VastlandsHydrology implements HydrologySystem {
    private final TerrainSystem terrain;
    private final HydrologyModel model;
    private final int seaTop;
    private final ThreadLocal<NodeCache> nodes;
    private final ThreadLocal<HydrologyModel.Column> scratch = ThreadLocal.withInitial(HydrologyModel.Column::new);

    public VastlandsHydrology(GenerationSetup setup, TerrainSystem terrain) {
        this.terrain = terrain;
        this.model = new HydrologyModel(terrain.geography(), (x, z) -> terrain.sample(x, z).height());
        this.seaTop = setup.seaLevel() - 1;
        this.nodes = ThreadLocal.withInitial(NodeCache::new);
    }

    @Override
    public String id() {
        return "vastlands";
    }

    public HydrologyModel model() {
        return this.model;
    }

    /** Carved height of a grid column (any column, in fact: exact model value). */
    public double carvedHeight(int blockX, int blockZ) {
        return this.node(blockX, blockZ).carved;
    }

    /** True if the grid column lies in a channel that gets the river biome. */
    public boolean riverBiome(int blockX, int blockZ) {
        return this.node(blockX, blockZ).river;
    }

    @Override
    public double surfaceHeight(int blockX, int blockZ) {
        int x0 = blockX & ~3;
        int z0 = blockZ & ~3;
        double fx = (blockX - x0) * 0.25;
        double fz = (blockZ - z0) * 0.25;
        double h00 = this.carvedHeight(x0, z0);
        if (fx == 0.0 && fz == 0.0) {
            return h00;
        }
        double h10 = this.carvedHeight(x0 + 4, z0);
        double h01 = this.carvedHeight(x0, z0 + 4);
        double h11 = this.carvedHeight(x0 + 4, z0 + 4);
        return (h00 + (h10 - h00) * fx) * (1.0 - fz) + (h01 + (h11 - h01) * fx) * fz;
    }

    @Override
    public WaterColumn water(int blockX, int blockZ) {
        double surface = this.surfaceHeight(blockX, blockZ);
        int ground = (int) Math.ceil(surface) - 1;
        HydrologyModel.Column column = this.scratch.get();
        this.model.sample(blockX, blockZ, Double.POSITIVE_INFINITY, column);
        // The lowest body that holds water here: a channel always does inside its wetted width,
        // any river pool or lake does where the ground lies below its surface.
        int top = column.channelTop;
        for (int k = 0; k < column.topCount && column.tops[k] < top; k++) {
            if (surface <= column.tops[k]) {
                top = column.tops[k];
                break;
            }
        }
        if (top != Integer.MAX_VALUE) {
            WaterColumn.Kind kind = top == column.lakeTop && top != column.channelTop ? WaterColumn.Kind.LAKE : WaterColumn.Kind.RIVER;
            return new WaterColumn(surface, Math.min(ground, top - 1), top, kind);
        }
        if (surface <= this.seaTop) {
            return new WaterColumn(surface, ground, this.seaTop, WaterColumn.Kind.SEA);
        }
        return new WaterColumn(surface, ground, Integer.MIN_VALUE, WaterColumn.Kind.DRY);
    }

    /** Diagnostics of the nearest channel (F3 screen). */
    public HydrologyModel.Column describe(int blockX, int blockZ) {
        HydrologyModel.Column column = new HydrologyModel.Column();
        this.model.sample(blockX, blockZ, this.terrain.column(blockX, blockZ).height(), column);
        return column;
    }

    @Override
    public double apply(int blockX, int blockY, int blockZ, double density) {
        double surface = this.terrain.column(blockX, blockZ).height();
        double carved = this.carvedHeight(blockX, blockZ);
        if (carved == surface) {
            return density;
        }
        return density - terrainDensity(surface, blockY) + terrainDensity(carved, blockY);
    }

    @Override
    public DensityRange outputRange(DensityRange input) {
        double limit = 2.0 * VastlandsTerrain.DENSITY_LIMIT;
        return new DensityRange(input.min() - limit, input.max() + limit);
    }

    @Override
    public double parameter(TerrainParameter parameter, int blockX, int blockY, int blockZ) {
        int x = blockX & ~3;
        int z = blockZ & ~3;
        return switch (parameter) {
            case WEIRDNESS -> this.riverBiome(x, z) ? 0.0 : this.terrain.parameter(parameter, blockX, blockY, blockZ);
            case INITIAL_DENSITY -> {
                double lowest = Math.min(
                        Math.min(this.carvedHeight(x, z), this.carvedHeight(x + 4, z)),
                        Math.min(this.carvedHeight(x, z + 4), this.carvedHeight(x + 4, z + 4)));
                yield terrainDensity(lowest, blockY);
            }
            default -> this.terrain.parameter(parameter, blockX, blockY, blockZ);
        };
    }

    private static double terrainDensity(double surface, int blockY) {
        double density = (surface - blockY) * VastlandsTerrain.DENSITY_PER_BLOCK;
        double limit = VastlandsTerrain.DENSITY_LIMIT;
        return density > limit ? limit : (density < -limit ? -limit : density);
    }

    private Node node(int x, int z) {
        return this.nodes.get().get(x, z);
    }

    private record Node(double carved, boolean river) {
    }

    /** Direct-mapped per-thread cache of node columns; a hit is exactly the model's value. */
    private final class NodeCache {
        private static final int BITS = 12;
        private static final int MASK = (1 << BITS) - 1;
        private final long[] keys = new long[1 << BITS];
        private final Node[] values = new Node[1 << BITS];
        private final HydrologyModel.Column column = new HydrologyModel.Column();

        Node get(int x, int z) {
            long key = (long) x << 32 | (z & 0xFFFFFFFFL);
            int h = (x >> 2) * 0x9E3779B1 ^ (z >> 2) * 0x85EBCA77 ^ (x & 3) << 7 ^ (z & 3) << 11;
            int slot = (h ^ h >>> 15) & MASK;
            Node node = this.values[slot];
            if (node != null && this.keys[slot] == key) {
                return node;
            }
            double surface = VastlandsHydrology.this.terrain.column(x, z).height();
            VastlandsHydrology.this.model.sample(x, z, surface, this.column);
            node = new Node(this.column.carved, this.column.riverBiome);
            this.keys[slot] = key;
            this.values[slot] = node;
            return node;
        }
    }
}
