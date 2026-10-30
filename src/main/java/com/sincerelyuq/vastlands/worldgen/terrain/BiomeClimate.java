package com.sincerelyuq.vastlands.worldgen.terrain;

import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.clamp;
import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.clamp01;
import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.smoothstep;

import com.sincerelyuq.vastlands.worldgen.geography.GeographySample;
import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;

/**
 * Biome selection: the five surface climate parameters of vanilla's overworld biome table
 * ({@code OverworldBiomeBuilder}), derived from the landform and the climate of a column instead of
 * independent noises. Vanilla's table then picks vanilla biomes, so biome features, structures,
 * mob spawns and {@code /locate} work unchanged.
 *
 * <ul>
 *     <li>continentalness — landform: ocean values (deep ocean below -0.455, ~20 blocks of water)
 *     exactly where the ground is under sea level; the coast band (beaches, stony shores) at the
 *     shoreline; rising inland. Polar seas with a firm ice field stay in the shallow ocean band over
 *     their shelves and slopes, so they are frozen oceans (snowfall, ice) down to
 *     {@value #FROZEN_SHELF_DEPTH} blocks of water; their deep basins are deep frozen oceans (ice
 *     fields over them all the same, see {@code OceanSurface}).</li>
 *     <li>erosion — landform: E0/E1 on mountains (peaks, slopes), E2 on plateaus (plateau biomes),
 *     E3/E4 on hills and plains, E5 on some steep hill country (windswept), E6 on flat wet lowland
 *     at sea level (swamps; never in dry climates).</li>
 *     <li>weirdness — landform: crests in the peaks slice, slopes high, plains low/mid; never the
 *     river slice (|w| &lt; 0.05: rivers are Hydrology's). Its sign (variant biomes) is its own
 *     1 800-block noise.</li>
 *     <li>temperature — climate, with two couplings to moisture: warm and arid land reads hotter
 *     (deserts need dryness, not only heat), hot and humid land reads cooler (savanna, forest and
 *     jungle instead of desert). Oceans use the sea-level temperature.</li>
 *     <li>humidity — climate moisture; temperate drylands are lifted out of vanilla's
 *     temperate-arid cell (flower forest) into steppe (plains), except in scattered flower
 *     patches.</li>
 * </ul>
 *
 * <p>Pure Java, immutable and thread-safe.
 */
final class BiomeClimate {
    /**
     * Water depth down to which an iced polar sea stays a frozen ocean; deeper, it is a deep frozen
     * ocean (Agent 8: before, every iced sea was a frozen ocean, and deep frozen oceans only lined
     * the warm edge of the freezing band).
     */
    static final double FROZEN_SHELF_DEPTH = 45.0;
    private final double seaLevel;
    private final SimplexNoise weirdnessSign, wetland, windswept, flowers;

    record Parameters(double continentalness, double erosion, double weirdness, double temperature, double humidity) {
    }

    BiomeClimate(long mixedSeed, double seaLevel) {
        this.seaLevel = seaLevel;
        this.weirdnessSign = new SimplexNoise(mixedSeed + 25);
        this.wetland = new SimplexNoise(mixedSeed + 26);
        this.windswept = new SimplexNoise(mixedSeed + 27);
        this.flowers = new SimplexNoise(mixedSeed + 62);
    }

    Parameters parameters(double x, double z, double height, double slope, double ruggedness, double relief, GeographySample g) {
        boolean underwater = this.seaLevel - height > 0.5;
        double temperature = this.temperature(underwater, g);
        return new Parameters(
                this.continentalness(g, height),
                this.erosion(x, z, height, slope, ruggedness, g),
                this.weirdness(x, z, relief, g),
                temperature,
                this.humidity(x, z, temperature, g));
    }

    private double continentalness(GeographySample g, double height) {
        double depth = this.seaLevel - height;
        if (depth > 0.5) {
            // Ocean: -0.19 at the shore down to -1.0 at 60 blocks depth; deep ocean below -0.455 (~20 deep).
            double v = -0.19 - 0.81 * clamp01((depth - 0.5) / 60.0);
            // Polar shelves: frozen ocean rather than deep frozen ocean (which has rain and never
            // freezes by weather); the deep basins are deep frozen oceans under the same ice field.
            return g.iceCover() > 0.3 && depth < FROZEN_SHELF_DEPTH ? Math.max(v, -0.42) : v;
        }
        // Coast band right at the shoreline, then inland.
        double c = g.continentalness();
        double v = -0.11 + 1.11 * clamp01(c / 1.1);
        if (c < 0.06 && height < this.seaLevel + 5.0) {
            v = Math.min(v, -0.15);
        }
        return v;
    }

    private double erosion(double x, double z, double height, double slope, double ruggedness, GeographySample g) {
        // Capped at 0.4: flat ground stays inside E4 instead of on the E4/E5 boundary.
        double e = Math.min(0.4, 0.45 - 1.4 * ruggedness);
        if (g.plateau() > 0.5) {
            e = Math.min(e, -0.3);
        }
        if (ruggedness < 0.3 && slope < 0.06 && height < this.seaLevel + 6.0 && height > this.seaLevel - 1.0
                && g.moisture() > -0.1 && this.wetland.sample(x / 1500.0, z / 1500.0) > 0.25) {
            e = 0.7;
        } else if (g.hilliness() > 0.6 && g.mountain() < 0.2 && slope > 0.25 && this.windswept.sample(x / 2000.0, z / 2000.0) > 0.35) {
            e = 0.5;
        }
        return e;
    }

    private double weirdness(double x, double z, double relief, GeographySample g) {
        // Peaks-and-valleys from the height above the regional base.
        double pv;
        if (g.mountainHeight() > 40.0) {
            double q = relief / g.mountainHeight();
            pv = -0.5 + 1.7 * clamp01(q);
        } else {
            pv = -0.5 + clamp01(relief / 40.0) * 0.9;
        }
        pv = Math.max(-0.85, Math.min(1.0, pv));
        // Invert vanilla's folding pv = 1 - |3|w| - 2| on the |w| <= 2/3 branch; |w| >= 0.05 keeps rivers out.
        double w = Math.max(0.06, (pv + 1.0) / 3.0);
        return this.weirdnessSign.sample(x / 1800.0, z / 1800.0) < 0.0 ? -w : w;
    }

    private double temperature(boolean underwater, GeographySample g) {
        if (underwater) {
            return g.seaLevelTemperature();
        }
        double t = g.temperature();
        double h = g.moisture();
        double warm = smoothstep(0.05, 0.4, t);
        double arid = smoothstep(-0.2, -0.45, h);
        double humid = smoothstep(-0.3, 0.0, h);
        double hot = smoothstep(0.4, 0.7, t);
        return clamp(t + 0.35 * warm * arid - 0.3 * humid * hot, -1.0, 1.0);
    }

    private double humidity(double x, double z, double biomeTemperature, GeographySample g) {
        double h = g.moisture();
        // Vanilla's temperate band (-0.15..0.2) maps its driest cell to flower forest; dry temperate
        // land is steppe here (plains, H1), with flower forests only in scattered patches.
        if (biomeTemperature >= -0.15 && biomeTemperature < 0.2 && h < -0.3 && this.flowers.sample(x / 1500.0, z / 1500.0) < 0.45) {
            return -0.3;
        }
        return h;
    }
}
