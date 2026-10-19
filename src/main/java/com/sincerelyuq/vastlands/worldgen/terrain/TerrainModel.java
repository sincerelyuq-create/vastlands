package com.sincerelyuq.vastlands.worldgen.terrain;

import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.clamp01;
import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.fbm;
import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.ridged;
import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.smoothstep;

import com.sincerelyuq.vastlands.worldgen.geography.GeographyModel;
import com.sincerelyuq.vastlands.worldgen.geography.GeographySample;
import com.sincerelyuq.vastlands.worldgen.geography.GeographySettings;
import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;

/**
 * The Vastlands height field: a pure function from (seed, x, z) to a {@link TerrainColumn}.
 *
 * <p>Built on the {@link GeographyModel}, in three layers:
 * <ol>
 *     <li>Macro geography ({@link GeographySample#elevation}): continents and sea floor, provinces,
 *     mountain systems, plateaus, basins, and the climate.</li>
 *     <li>Gully erosion: a filter that cuts drainage-aligned gullies into the slopes of the macro
 *     shape at four scales, giving mountains their spurs and side valleys.</li>
 *     <li>Small detail: plains undulation, micro relief, crags, sea floor texture.</li>
 * </ol>
 * The vanilla biome parameters of the column come from {@link BiomeClimate}.
 *
 * <p>Pure Java (no game classes), immutable and thread-safe.
 */
public final class TerrainModel {
    /** Finite-difference step for the slope of the macro shape. */
    private static final double SLOPE_STEP = 32.0;
    private static final double SQRT3_2 = Math.sqrt(3.0) / 2.0;
    /** Largest gully cell size in blocks; each further octave halves it. */
    private static final double GULLY_CELL = 360.0;
    private static final int GULLY_OCTAVES = 4;
    /** Gully depth of an octave relative to its cell size, at full strength. */
    private static final double GULLY_DEPTH = 0.22;
    private static final double GULLY_BRANCHING = 1.5;
    /** Heights above maxHeight - SOFT_CAP_RANGE are compressed towards maxHeight. */
    private static final double SOFT_CAP_RANGE = 60.0;

    private final double seaLevel;
    private final double maxHeight;
    private final GeographyModel geography;
    private final BiomeClimate climate;

    private final SimplexNoise microNoise, undulationNoise;
    private final SimplexNoise seaFloor;
    private final SimplexNoise gully;
    private final SimplexNoise crags, roughness;

    public TerrainModel(long seed, double seaLevel, double maxHeight) {
        this(seed, seaLevel, maxHeight, GeographySettings.DEFAULT);
    }

    public TerrainModel(long seed, double seaLevel, double maxHeight, GeographySettings settings) {
        this.seaLevel = seaLevel;
        this.maxHeight = maxHeight;
        this.geography = new GeographyModel(seed, seaLevel, settings);
        long s = SimplexNoise.mix(seed);
        this.microNoise = new SimplexNoise(s + 8);
        this.undulationNoise = new SimplexNoise(s + 9);
        this.seaFloor = new SimplexNoise(s + 23);
        this.gully = new SimplexNoise(s + 24);
        this.crags = new SimplexNoise(s + 30);
        this.roughness = new SimplexNoise(s + 31);
        this.climate = new BiomeClimate(s, seaLevel);
    }

    /** The macro geography this terrain is built on. */
    public GeographyModel geography() {
        return this.geography;
    }

    public TerrainColumn sample(double x, double z) {
        GeographySample g = this.geography.sample(x, z);
        // Gradient of the plane through three samples on a circle around (x, z): centred like a
        // central difference, one sample cheaper.
        double h0 = this.geography.smoothElevation(x + SLOPE_STEP, z);
        double h1 = this.geography.smoothElevation(x - 0.5 * SLOPE_STEP, z + SQRT3_2 * SLOPE_STEP);
        double h2 = this.geography.smoothElevation(x - 0.5 * SLOPE_STEP, z - SQRT3_2 * SLOPE_STEP);
        double slopeX = (2.0 * h0 - h1 - h2) / (3.0 * SLOPE_STEP);
        double slopeZ = (h1 - h2) / (2.0 * SQRT3_2 * SLOPE_STEP);
        double slope = Math.sqrt(slopeX * slopeX + slopeZ * slopeZ);

        double gullyDepth = -this.gullies(x, z, slopeX, slopeZ, slope, g);
        double height = this.softCap(g.elevation() - gullyDepth + this.detail(x, z, g, slope));

        double relief = Math.max(0.0, height - g.baseHeight());
        double ruggedness = clamp01(Math.max(Math.max(g.mountainHeight() / 200.0, g.hilliness() * 0.5), smoothstep(0.3, 1.2, slope) * 0.7));
        BiomeClimate.Parameters p = this.climate.parameters(x, z, height, slope, ruggedness, relief, g);
        return new TerrainColumn(height, g.baseHeight(), g.continentalness(), g.mountain(), g.plateau(), gullyDepth, ruggedness,
                slopeX, slopeZ, p.continentalness(), p.erosion(), p.weirdness(), p.temperature(), p.humidity(), g);
    }

    /** Small-scale relief that does not steer the gullies. */
    private double detail(double x, double z, GeographySample g, double slope) {
        double land = g.land();
        double v = land * (2.5 * fbm(this.microNoise, x / 140.0, z / 140.0, 3, 0.5)
                + (1.0 - 0.5 * g.hilliness()) * (1.0 - 0.4 * g.plains()) * 5.0 * fbm(this.undulationNoise, x / 380.0, z / 380.0, 3, 0.5));
        // Uneven ground: bumps of a few blocks that grow with the slope, so long slopes do not
        // read as even staircases.
        v += land * (1.0 + 4.0 * smoothstep(0.1, 0.8, slope)) * fbm(this.roughness, x / 36.0, z / 36.0, 3, 0.5);
        // Crags and ledges on steep mountain and escarpment ground, sharper on young ranges and
        // in dry provinces.
        double rocky = clamp01(1.5 * g.mountain() + g.plateau()) * smoothstep(0.15, 0.8, slope);
        if (rocky > 0.0) {
            v += rocky * (6.0 + 8.0 * g.alpine() + 4.0 * g.dryness()) * (ridged(this.crags, x / 70.0, z / 70.0, 3) - 0.35);
        }
        if (land < 1.0) {
            v += (1.0 - land) * 5.0 * fbm(this.seaFloor, x / 450.0, z / 450.0, 3, 0.5);
        }
        return v;
    }

    /**
     * Gully erosion filter (after Clay John's "eroded terrain noise"): stripes running downhill along
     * the slope of the macro shape, blended over jittered cells. Later octaves follow the gullies of
     * earlier ones, which makes them branch. Only lowers terrain; zero on flat ground, and it never
     * cuts below the regional base or the sea. Weaker in dry provinces (less runoff).
     */
    private double gullies(double x, double z, double slopeX, double slopeZ, double slope, GeographySample g) {
        if (slope <= 0.05) {
            return 0.0;
        }
        double relief = g.elevation() - Math.max(g.baseHeight(), this.seaLevel + 2.0);
        // Plateau escarpments are lower than mountains, so they reach full strength at less relief.
        double fullRelief = 110.0 - 40.0 * smoothstep(0.0, 0.3, g.plateau());
        double strength = smoothstep(0.05, 0.8, slope) * clamp01(relief / fullRelief) * (0.6 + 0.5 * g.alpine())
                * (1.0 - 0.3 * g.dryness());
        if (strength <= 0.0) {
            return 0.0;
        }
        // Stripes run along the gradient: their wave vector is perpendicular to it.
        double dirX = slopeZ / slope;
        double dirZ = -slopeX / slope;
        double[] out = new double[3];
        double total = 0.0;
        double accX = 0.0;
        double accZ = 0.0;
        double cell = GULLY_CELL;
        for (int octave = 0; octave < GULLY_OCTAVES; octave++) {
            double ox = dirX + accZ * GULLY_BRANCHING;
            double oz = dirZ - accX * GULLY_BRANCHING;
            double len = Math.sqrt(ox * ox + oz * oz);
            if (len > 1e-9) {
                ox /= len;
                oz /= len;
            }
            this.gullyOctave(x / cell, z / cell, ox, oz, out);
            total += cell * GULLY_DEPTH * (out[0] - 1.0) * 0.5;
            accX += out[1] * 0.5;
            accZ += out[2] * 0.5;
            cell *= 0.5;
        }
        return Math.max(total * strength, -Math.max(0.0, relief));
    }

    private void gullyOctave(double x, double z, double dirX, double dirZ, double[] out) {
        long ix = (long) Math.floor(x);
        long iz = (long) Math.floor(z);
        double fx = x - ix;
        double fz = z - iz;
        double value = 0.0;
        double dx = 0.0;
        double dz = 0.0;
        double weights = 0.0;
        for (int i = -2; i <= 1; i++) {
            for (int j = -2; j <= 1; j++) {
                long hash = this.gully.hash(ix - i, iz - j);
                double px = fx + i - (hash & 0xFFFFFFFFL) * 0x1.0p-33;
                double pz = fz + j - (hash >>> 32) * 0x1.0p-33;
                // Compact stand-in for exp(-2 d^2): (1 - d^2 / 1.5^2)^3. This 4x4 window holds every
                // feature point within distance 1.5, so the weight of points entering or leaving it
                // is already zero and the pattern stays continuous across cell borders.
                double t = 1.0 - (px * px + pz * pz) * (1.0 / 2.25);
                if (t <= 0.0) {
                    continue;
                }
                double w = t * t * t;
                double cycles = px * dirX + pz * dirZ;
                double cos = sinTurns(cycles + 0.25);
                double sin = sinTurns(cycles);
                value += cos * w;
                dx -= sin * dirX * w;
                dz -= sin * dirZ * w;
                weights += w;
            }
        }
        out[0] = value / weights;
        out[1] = dx / weights;
        out[2] = dz / weights;
    }

    /** sin(2 pi u) to within 4e-6, faster than {@link Math#sin}. */
    static double sinTurns(double u) {
        u -= Math.rint(u);
        if (u > 0.25) {
            u = 0.5 - u;
        } else if (u < -0.25) {
            u = -0.5 - u;
        }
        double x = u * (2.0 * Math.PI);
        double x2 = x * x;
        return x * (1.0 - x2 / 6.0 * (1.0 - x2 / 20.0 * (1.0 - x2 / 42.0 * (1.0 - x2 / 72.0))));
    }

    /**
     * Compresses heights approaching the top of the world into an asymptote at {@link #maxHeight}.
     * The knee is high, so only the very highest summits are flattened a little.
     */
    private double softCap(double h) {
        double knee = this.maxHeight - SOFT_CAP_RANGE;
        if (h <= knee) {
            return h;
        }
        double over = h - knee;
        return knee + over / (1.0 + over / SOFT_CAP_RANGE);
    }
}
