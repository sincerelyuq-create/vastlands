package com.sincerelyuq.vastlands.worldgen.terrain;

import com.sincerelyuq.vastlands.worldgen.system.DensityRange;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSetup;
import com.sincerelyuq.vastlands.worldgen.system.TerrainParameter;
import com.sincerelyuq.vastlands.worldgen.system.TerrainSystem;

/**
 * The Vastlands terrain system: a {@link TerrainModel} height field turned into router density and
 * router climate parameters.
 *
 * <p>Density is {@code (surface - y) * DENSITY_PER_BLOCK}, clamped to ±{@link #DENSITY_LIMIT}: one
 * surface per column, no floating terrain. Columns are cached per thread.
 */
public final class VastlandsTerrain implements TerrainSystem {
    /**
     * Density gradient below the surface. Vanilla's final density only lets cheese and spaghetti
     * caves through where the landscape exceeds 1.5625, i.e. from about 12 blocks below the surface
     * at this gradient (vanilla plains are similar).
     */
    public static final double DENSITY_PER_BLOCK = 0.125;
    public static final double DENSITY_LIMIT = 128.0;
    /** Highest surface the model may approach, if the world is tall enough. */
    public static final double MAX_SURFACE = 470.0;
    /** Vanilla's depth parameter changes by 1 per 128 blocks. */
    private static final double DEPTH_PER_BLOCK = 1.0 / 128.0;
    /**
     * Held depth of deep rock: above the cave biomes (0.2..0.9), nearest to the surface biomes'
     * second depth point (1.0), below the deep dark point (1.1).
     */
    public static final double DEEP_HOLD = 0.95;
    public static final int DEEP_DARK_BOTTOM = -64;
    public static final int DEEP_DARK_TOP = 0;

    private final TerrainModel model;
    private final ThreadLocal<ColumnCache> cache;

    public VastlandsTerrain(GenerationSetup setup) {
        int top = setup.minY() + setup.height() - 1;
        long seed = setup.random("terrain").fromHashOf("height_field").nextLong();
        this.model = new TerrainModel(seed, setup.seaLevel(), Math.min(MAX_SURFACE, top - 41));
        this.cache = ThreadLocal.withInitial(() -> new ColumnCache(this.model));
    }

    @Override
    public String id() {
        return "vastlands";
    }

    @Override
    public TerrainColumn column(int blockX, int blockZ) {
        return this.cache.get().get(blockX, blockZ);
    }

    @Override
    public double surfaceHeight(int blockX, int blockZ) {
        ColumnCache columns = this.cache.get();
        int x0 = blockX & ~3;
        int z0 = blockZ & ~3;
        double fx = (blockX - x0) * 0.25;
        double fz = (blockZ - z0) * 0.25;
        double h00 = columns.get(x0, z0).height();
        if (fx == 0.0 && fz == 0.0) {
            return h00;
        }
        double h10 = columns.get(x0 + 4, z0).height();
        double h01 = columns.get(x0, z0 + 4).height();
        double h11 = columns.get(x0 + 4, z0 + 4).height();
        return (h00 + (h10 - h00) * fx) * (1.0 - fz) + (h01 + (h11 - h01) * fx) * fz;
    }

    @Override
    public double apply(int blockX, int blockY, int blockZ, double density) {
        return density + terrainDensity(this.column(blockX, blockZ).height(), blockY);
    }

    @Override
    public DensityRange outputRange(DensityRange input) {
        return new DensityRange(input.min() - DENSITY_LIMIT, input.max() + DENSITY_LIMIT);
    }

    @Override
    public double parameter(TerrainParameter parameter, int blockX, int blockY, int blockZ) {
        // Climate parameters are read on the 4-block grid that biomes and the noise use anyway, so
        // off-grid callers (aquifer centres) hit the cache too.
        int x = blockX & ~3;
        int z = blockZ & ~3;
        return switch (parameter) {
            case CONTINENTALNESS -> this.column(x, z).climateContinentalness();
            case EROSION -> this.column(x, z).climateErosion();
            case WEIRDNESS -> this.column(x, z).climateWeirdness();
            case DEPTH -> clamp(depth(this.column(x, z).height(), blockY), parameter);
            case INITIAL_DENSITY -> {
                // Aquifers treat everything above the preliminary surface as open air, so it must not
                // lie above the real surface anywhere in the cell: take the lowest of its corners.
                ColumnCache columns = this.cache.get();
                double lowest = Math.min(
                        Math.min(columns.get(x, z).height(), columns.get(x + 4, z).height()),
                        Math.min(columns.get(x, z + 4).height(), columns.get(x + 4, z + 4).height()));
                yield terrainDensity(lowest, blockY);
            }
        };
    }

    /**
     * Biome depth: {@code (surface - y) / 128} like vanilla, so cave biomes (depth 0.2..0.9) appear
     * at vanilla distances below the surface. Deeper than {@link #DEEP_HOLD}, depth is held just
     * below the deep dark point (1.1) except in vanilla's deep dark band (Y {@value #DEEP_DARK_BOTTOM}
     * to {@value #DEEP_DARK_TOP}): otherwise the deep dark would fill everything more than ~140
     * blocks under the surface, a layer up to 400 blocks thick under high mountains, and its sculk
     * features would dominate generation time. The band keeps the deep dark and ancient cities
     * where vanilla has them. Caves (Agent 4) may redesign the deep layers.
     */
    public static double depth(double surface, int blockY) {
        double depth = (surface - blockY) * DEPTH_PER_BLOCK;
        if (depth > DEEP_HOLD && (blockY < DEEP_DARK_BOTTOM || blockY > DEEP_DARK_TOP)) {
            return DEEP_HOLD;
        }
        return depth;
    }

    private static double terrainDensity(double surface, int blockY) {
        double density = (surface - blockY) * DENSITY_PER_BLOCK;
        return density > DENSITY_LIMIT ? DENSITY_LIMIT : (density < -DENSITY_LIMIT ? -DENSITY_LIMIT : density);
    }

    private static double clamp(double value, TerrainParameter parameter) {
        return Math.max(parameter.min(), Math.min(parameter.max(), value));
    }
}
