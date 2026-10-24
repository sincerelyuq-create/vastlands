package com.sincerelyuq.vastlands.worldgen.hydrology;

import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.smoothstep;

import com.sincerelyuq.vastlands.worldgen.system.DensityRange;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSetup;
import com.sincerelyuq.vastlands.worldgen.system.HydrologySystem;
import com.sincerelyuq.vastlands.worldgen.system.TerrainParameter;
import com.sincerelyuq.vastlands.worldgen.system.TerrainSystem;
import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;
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
 *     <li>Depth (biomes): from the carved surface, so the floors of gorges and valleys get surface
 *     biomes rather than the cave biomes their depth below the uncarved terrain would give.</li>
 *     <li>Rough walls: where carving left a steep face (gorge walls, the cliffs around falls),
 *     the face juts out by up to {@value #ROUGHNESS} blocks in places that change every few
 *     blocks of height, so cliffs get ledges and buttresses instead of a single vertical outline.
 *     Only outward, never into the rock: no hollow ever opens below the 2D surface, which the
 *     water, aquifers and the preliminary surface rely on.</li>
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
    /** Farthest a steep face juts out (blocks). */
    static final double ROUGHNESS = 5.0;
    private final long roughSeed;

    public VastlandsHydrology(GenerationSetup setup, TerrainSystem terrain) {
        this.terrain = terrain;
        this.model = new HydrologyModel(terrain.geography(), (x, z) -> terrain.sample(x, z).height(), setup.worldSeed());
        this.seaTop = setup.seaLevel() - 1;
        this.nodes = ThreadLocal.withInitial(NodeCache::new);
        this.roughSeed = SimplexNoise.mix(setup.worldSeed() ^ 0x524F554748L);
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

    /** {@link #surfaceHeight} at any point (bilinear between the carved heights of grid columns). */
    private double surfaceAt(double x, double z) {
        double gx = Math.floor(x / 4.0);
        double gz = Math.floor(z / 4.0);
        int x0 = (int) gx * 4;
        int z0 = (int) gz * 4;
        double fx = x / 4.0 - gx;
        double fz = z / 4.0 - gz;
        double h00 = this.carvedHeight(x0, z0);
        double h10 = this.carvedHeight(x0 + 4, z0);
        double h01 = this.carvedHeight(x0, z0 + 4);
        double h11 = this.carvedHeight(x0 + 4, z0 + 4);
        return (h00 + (h10 - h00) * fx) * (1.0 - fz) + (h01 + (h11 - h01) * fx) * fz;
    }

    /**
     * Surface of a grid column at a height, with the outward roughness of steep carved faces: the
     * carved surface sampled up to {@link #ROUGHNESS} blocks away (a smooth 3D offset), where that
     * is higher. 0 roughness away from carved faces, near the bottom of a face (the water) and
     * where the terrain was not carved, so it fades in without seams.
     */
    private double roughSurface(int x, int y, int z, Node node) {
        double carved = node.carved;
        if (!node.near) {
            return carved;
        }
        if (node.faceLow != node.faceLow) {
            // The face around the grid column, once per column (the node is cached per thread).
            double e = this.carvedHeight(x + 4, z);
            double w = this.carvedHeight(x - 4, z);
            double s = this.carvedHeight(x, z + 4);
            double n = this.carvedHeight(x, z - 4);
            node.faceHigh = Math.max(Math.max(Math.max(e, w), Math.max(s, n)), carved);
            node.faceLow = Math.min(Math.min(Math.min(e, w), Math.min(s, n)), carved);
            double steep = (node.faceHigh - node.faceLow) * 0.25;
            node.faceWeight = 0.0;
            if (steep >= 1.5) {
                double cut = Math.max(Math.max(this.cutDepth(x + 4, z, e), this.cutDepth(x - 4, z, w)),
                        Math.max(Math.max(this.cutDepth(x, z + 4, s), this.cutDepth(x, z - 4, n)), this.cutDepth(x, z, carved)));
                node.faceWeight = smoothstep(1.5, 4.0, steep) * smoothstep(0.0, 8.0, cut);
            }
        }
        if (node.faceWeight <= 0.0 || y <= node.faceLow + 8.0 || y >= node.faceHigh) {
            return carved;
        }
        double amount = ROUGHNESS * node.faceWeight * smoothstep(node.faceLow + 8.0, node.faceLow + 16.0, y);
        // Wide and low: the face juts out in ledges that change from one 8-block cell to the next.
        double ox = amount * valueNoise3(this.roughSeed, x / 16.0, y / 5.0, z / 16.0);
        double oz = amount * valueNoise3(this.roughSeed + 1, x / 16.0, y / 5.0, z / 16.0);
        return Math.max(carved, this.surfaceAt(x + ox, z + oz));
    }

    /** How deep the hydrology cut into the terrain at a grid column. */
    private double cutDepth(int x, int z, double carved) {
        return Math.max(0.0, this.terrain.column(x, z).height() - carved);
    }

    /** Smooth 3D value noise in [-1, 1]. */
    static double valueNoise3(long seed, double x, double y, double z) {
        long xi = (long) Math.floor(x);
        long yi = (long) Math.floor(y);
        long zi = (long) Math.floor(z);
        double fx = x - xi;
        double fy = y - yi;
        double fz = z - zi;
        fx = fx * fx * (3.0 - 2.0 * fx);
        fy = fy * fy * (3.0 - 2.0 * fy);
        fz = fz * fz * (3.0 - 2.0 * fz);
        double c000 = lattice(seed, xi, yi, zi);
        double c100 = lattice(seed, xi + 1, yi, zi);
        double c010 = lattice(seed, xi, yi + 1, zi);
        double c110 = lattice(seed, xi + 1, yi + 1, zi);
        double c001 = lattice(seed, xi, yi, zi + 1);
        double c101 = lattice(seed, xi + 1, yi, zi + 1);
        double c011 = lattice(seed, xi, yi + 1, zi + 1);
        double c111 = lattice(seed, xi + 1, yi + 1, zi + 1);
        double x00 = c000 + (c100 - c000) * fx;
        double x10 = c010 + (c110 - c010) * fx;
        double x01 = c001 + (c101 - c001) * fx;
        double x11 = c011 + (c111 - c011) * fx;
        double y0 = x00 + (x10 - x00) * fy;
        double y1 = x01 + (x11 - x01) * fy;
        return y0 + (y1 - y0) * fz;
    }

    private static double lattice(long seed, long x, long y, long z) {
        long h = SimplexNoise.mix(seed ^ x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L);
        return (h >>> 11) * 0x1.0p-52 - 1.0;
    }

    @Override
    public WaterColumn water(int blockX, int blockZ) {
        double surface = this.surfaceHeight(blockX, blockZ);
        int ground = (int) Math.ceil(surface) - 1;
        HydrologyModel.Column column = this.scratch.get();
        this.model.sampleWater(blockX, blockZ, column);
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
            if (column.rockTop >= top) {
                return new WaterColumn(surface, column.rockTop, top, WaterColumn.Kind.ROCK);
            }
            WaterColumn.Kind kind = top == column.lakeTop && top != column.channelTop ? WaterColumn.Kind.LAKE : WaterColumn.Kind.RIVER;
            return new WaterColumn(surface, Math.min(ground, top - 1), top, kind);
        }
        if (surface <= this.seaTop) {
            if (column.rockTop >= this.seaTop) {
                return new WaterColumn(surface, column.rockTop, this.seaTop, WaterColumn.Kind.ROCK);
            }
            return new WaterColumn(surface, ground, this.seaTop, WaterColumn.Kind.SEA);
        }
        if (column.rockTop > ground) {
            return new WaterColumn(surface, column.rockTop, Integer.MIN_VALUE, WaterColumn.Kind.ROCK);
        }
        return new WaterColumn(surface, ground, Integer.MIN_VALUE, WaterColumn.Kind.DRY);
    }

    @Override
    public boolean mayHoldWater(int minX, int minZ, int maxX, int maxZ) {
        return this.model.mayHoldWater(minX, minZ, maxX, maxZ);
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
        double carved = this.roughSurface(blockX, blockY, blockZ, this.node(blockX, blockZ));
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
            case DEPTH -> Math.max(parameter.min(), Math.min(parameter.max(), VastlandsTerrain.depth(this.carvedHeight(x, z), blockY)));
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

    /**
     * A grid column: carved height, river biome, and whether any channel's valley reaches it (only
     * those can have carved faces nearby); the face around it (lowest and highest carved height of
     * it and its four neighbours, roughness weight), filled in on first use.
     */
    private static final class Node {
        final double carved;
        final boolean river;
        final boolean near;
        double faceLow = Double.NaN;
        double faceHigh;
        double faceWeight;

        Node(double carved, boolean river, boolean near) {
            this.carved = carved;
            this.river = river;
            this.near = near;
        }
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
            node = new Node(this.column.carved, this.column.riverBiome, this.column.near);
            this.keys[slot] = key;
            this.values[slot] = node;
            return node;
        }
    }
}
