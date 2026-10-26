package com.sincerelyuq.vastlands.worldgen.biome;

import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.smoothstep;

import com.sincerelyuq.vastlands.worldgen.geography.GeographySample;
import com.sincerelyuq.vastlands.worldgen.noise.NoiseMath;
import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;
import com.sincerelyuq.vastlands.worldgen.terrain.TerrainColumn;
import java.util.Locale;

/**
 * Regional surface biomes: where the surface biomes of other mods (Sincerelyuq's Biomes) take the
 * place of vanilla's in a Vastlands world, chosen from the geography rather than from vanilla's
 * climate table. Each region is a continuous strength field (0..1) built from the terrain's
 * climate parameters (the values vanilla's table reads, so a region is a part of the vanilla
 * biomes of the same climate), the landform and the rock, with a regional selector noise of its
 * own, so a region covers whole landscapes thousands of blocks across in the places that suit it:
 * <ul>
 *     <li>{@link Region#PAINTED_RANGE}: mountain ranges (vanilla's peak and slope erosion) in warm,
 *     not arid climates, below the snow; some ranges, not all.</li>
 *     <li>{@link Region#VOLCANIC_WASTE}: the volcanic hotspots of the rock (the caves' volcanic
 *     field, under which the hot caves lie) in warm, dry country off the high ranges.</li>
 *     <li>{@link Region#DUNE_SEA}: the hottest, most arid deserts of dry provinces, flat and far
 *     from the sea: sand seas inside vanilla's deserts.</li>
 *     <li>{@link Region#WETLAND}: warm, humid lowland: flat ground near sea level, interior basins
 *     and the carved floodplains of river valleys.</li>
 *     <li>{@link Region#DUSK_WOOD}: temperate, humid forest country off the mountains (a share of
 *     vanilla's dark forests and their neighbours).</li>
 * </ul>
 * A small noise at ~50 blocks makes the edges ragged where a field crosses the threshold, so
 * borders wander like vanilla's and never follow a grid. Pure function of the seed and the
 * position (no game classes); thread-safe.
 */
public final class RegionalBiomes {
    /** The regions, in priority order (the first whose field passes wins). */
    public enum Region {
        PAINTED_RANGE,
        VOLCANIC_WASTE,
        DUNE_SEA,
        WETLAND,
        DUSK_WOOD;

        public static final Region[] VALUES = values();

        /** Path of the region's biome tag under {@code vastlands:region/}. */
        public String tagPath() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    /** What the regions read: other systems' models. */
    public interface Land {
        /** The terrain model's column (climate parameters, slope, geography). */
        TerrainColumn column(int x, int z);

        /** 0..1, volcanic strength of the rock (the caves' geology). */
        double volcanic(int x, int z);

        /** Blocks the rivers' valleys cut below the terrain (0 away from rivers). */
        double valleyDepth(int x, int z);
    }

    /** The field a region needs at a column to hold it (before the edge noise). */
    static final double THRESHOLD = 0.5;
    private static final double EDGE = 0.1;

    private final Land land;
    private final int seaLevel;
    private final SimplexNoise painted;
    private final SimplexNoise sands;
    private final SimplexNoise wetlands;
    private final SimplexNoise dusk;
    private final SimplexNoise edges;
    private final long seed;
    private volatile DuneField dunes;

    public RegionalBiomes(long seed, Land land, int seaLevel) {
        this.seed = seed;
        this.land = land;
        this.seaLevel = seaLevel;
        this.painted = new SimplexNoise(seed + 11);
        this.sands = new SimplexNoise(seed + 12);
        this.wetlands = new SimplexNoise(seed + 13);
        this.dusk = new SimplexNoise(seed + 14);
        this.edges = new SimplexNoise(seed + 15);
    }

    /** The region holding a column among the enabled ones (a bit per {@link Region} ordinal), or null. */
    public Region region(int x, int z, int enabled) {
        if (enabled == 0) {
            return null;
        }
        TerrainColumn column = this.land.column(x, z);
        if (column.geography().continentalness() < 0.0) {
            return null;
        }
        double edge = EDGE * NoiseMath.fbm(this.edges, x / 52.0, z / 52.0, 2, 0.5);
        for (Region r : Region.VALUES) {
            if ((enabled & 1 << r.ordinal()) != 0 && this.strength(r, x, z, column) + edge > THRESHOLD) {
                return r;
            }
        }
        return null;
    }

    /**
     * How far a column lies inside a region's field: the field plus the edge noise minus the
     * threshold (positive where the field holds the region, other regions aside).
     */
    public double margin(Region region, int x, int z) {
        TerrainColumn column = this.land.column(x, z);
        if (column.geography().continentalness() < 0.0) {
            return -1.0;
        }
        return this.strength(region, x, z, column) + EDGE * NoiseMath.fbm(this.edges, x / 52.0, z / 52.0, 2, 0.5) - THRESHOLD;
    }

    /** The dunes of the dune sea (built on first use). */
    public DuneField dunes() {
        DuneField d = this.dunes;
        if (d == null) {
            d = new DuneField(this.seed, this);
            this.dunes = d;
        }
        return d;
    }

    TerrainColumn column(int x, int z) {
        return this.land.column(x, z);
    }

    double valleyDepth(int x, int z) {
        return this.land.valleyDepth(x, z);
    }

    /** The field of a region at a column, 0..1 (above {@value #THRESHOLD} it holds the region). */
    public double strength(Region region, int x, int z) {
        return this.strength(region, x, z, this.land.column(x, z));
    }

    private double strength(Region region, int x, int z, TerrainColumn c) {
        GeographySample g = c.geography();
        double t = c.climateTemperature();
        double h = c.climateHumidity();
        double land = smoothstep(0.02, 0.1, g.continentalness());
        if (land <= 0.0) {
            return 0.0;
        }
        return switch (region) {
            case PAINTED_RANGE -> {
                // Vanilla's peaks and slopes (erosion E0/E1), warm and not arid, below the snow.
                double mountains = 1.0 - smoothstep(-0.42, -0.28, c.climateErosion());
                double warm = smoothstep(0.08, 0.22, g.seaLevelTemperature()) * (1.0 - smoothstep(0.75, 0.9, g.seaLevelTemperature()));
                double moist = smoothstep(-0.3, -0.05, h);
                double belowSnow = smoothstep(-0.4, -0.2, t);
                if (mountains * warm * moist * belowSnow <= 0.0) {
                    yield 0.0;
                }
                double ranges = smoothstep(-0.1, 0.25, NoiseMath.fbm(this.painted, x / 6500.0, z / 6500.0, 2, 0.5));
                yield land * mountains * warm * moist * belowSnow * ranges;
            }
            case VOLCANIC_WASTE -> {
                double warm = smoothstep(0.0, 0.2, t);
                double dry = 1.0 - smoothstep(-0.12, 0.08, h);
                double offPeaks = 1.0 - smoothstep(0.5, 0.75, g.mountain());
                if (warm * dry * offPeaks <= 0.0) {
                    yield 0.0;
                }
                double hot = smoothstep(0.45, 0.72, this.land.volcanic(x, z));
                yield land * warm * dry * offPeaks * hot;
            }
            case DUNE_SEA -> {
                double hot = smoothstep(0.5, 0.65, t);
                double arid = 1.0 - smoothstep(-0.5, -0.3, h);
                double province = smoothstep(0.25, 0.55, g.dryness());
                double flat = 1.0 - smoothstep(0.08, 0.3, g.mountain());
                double interior = smoothstep(0.12, 0.3, g.continentality());
                if (hot * arid * province * flat * interior <= 0.0) {
                    yield 0.0;
                }
                double sea = smoothstep(-0.15, 0.2, NoiseMath.fbm(this.sands, x / 4200.0, z / 4200.0, 2, 0.5));
                yield hot * arid * province * flat * interior * sea;
            }
            case WETLAND -> {
                double warm = smoothstep(0.1, 0.22, t) * (1.0 - smoothstep(0.6, 0.72, t));
                double wet = smoothstep(0.05, 0.22, h);
                double flat = 1.0 - smoothstep(0.07, 0.18, c.slope());
                if (warm * wet * flat <= 0.0) {
                    yield 0.0;
                }
                double mosaic = smoothstep(0.0, 0.3, NoiseMath.fbm(this.wetlands, x / 1800.0, z / 1800.0, 2, 0.5));
                if (mosaic <= 0.0) {
                    yield 0.0;
                }
                double lowland = 1.0 - smoothstep(this.seaLevel + 12.0, this.seaLevel + 30.0, g.elevation());
                double basin = smoothstep(0.25, 0.6, g.basin());
                double floodplain = smoothstep(1.0, 4.0, this.land.valleyDepth(x, z));
                yield land * warm * wet * flat * mosaic * Math.max(lowland, Math.max(basin, floodplain));
            }
            case DUSK_WOOD -> {
                double temperate = smoothstep(-0.25, -0.1, t) * (1.0 - smoothstep(0.15, 0.3, t));
                double humid = smoothstep(0.18, 0.38, h);
                double offMountains = 1.0 - smoothstep(0.3, 0.55, g.mountain());
                if (temperate * humid * offMountains <= 0.0) {
                    yield 0.0;
                }
                double woods = smoothstep(0.0, 0.25, NoiseMath.fbm(this.dusk, x / 3200.0, z / 3200.0, 2, 0.5));
                yield land * temperate * humid * offMountains * woods;
            }
        };
    }
}
