package com.sincerelyuq.vastlands.worldgen.geography;

import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.clamp;
import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.fbm;
import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.ridged;
import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.smoothMax;
import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.smoothstep;

import com.sincerelyuq.vastlands.worldgen.noise.NoiseMath;
import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;

/**
 * The Vastlands macro geography: a pure function from (seed, x, z) to the large-scale shape of the
 * world and its climate. The terrain model ({@code TerrainModel}) adds gully erosion and small
 * detail on top of {@link GeographySample#elevation}; biomes are chosen from its climate.
 *
 * <p>Layers, coarse to fine, each conditioned on the ones before:
 * <ol>
 *     <li>Continents and oceans: a warped continent field shifted by ~42 000-block oceanic and
 *     continental regions, so there are open oceans as well as inland seas. The sea floor falls
 *     from the coast over a shelf and a continental slope to abyssal plains, with mid-ocean ridges,
 *     seamounts and rare trenches.</li>
 *     <li>Provinces (~16 000 blocks): vast plains vs hill country, and dry provinces with more
 *     tablelands.</li>
 *     <li>Mountain systems: long warped belts with a main crest, massifs and passes (Agent 2), and
 *     branch ranges springing from them; plateaus with escarpments; basins.</li>
 *     <li>Climate: temperature zones cooled by elevation, moisture from moisture zones, distance to
 *     the sea and rain shadows of upwind ranges.</li>
 * </ol>
 *
 * <p>Immutable and thread-safe. Pure Java (no game classes), so it can be sampled and rendered
 * without the game. Every value is computed from noise at the queried position and a few bounded
 * neighbours (rain shadow: three upwind samples); nothing depends on other chunks or on order.
 */
public final class GeographyModel {
    /** Wavelengths in blocks at {@code regionScale} 1. */
    public static final double CONTINENT_SCALE = 11000.0;
    public static final double OCEAN_REGION_SCALE = 42000.0;
    public static final double PROVINCE_SCALE = 16000.0;
    public static final double TEMPERATURE_SCALE = 26000.0;
    public static final double MOISTURE_SCALE = 17000.0;

    /** Weight of the oceanic/continental regions in the continent field. */
    private static final double OCEAN_REGION_AMPLITUDE = 1.3;
    /** Mountain crest envelope of the strongest ranges, in blocks (Agent 2). */
    private static final double MOUNTAIN_ENVELOPE = 380.0;
    /**
     * The greatest mountain systems: in a few regions of a 30 000-block field, the cores of major
     * ranges (stature near 1) rise up to this share higher, so their summits stand above those of
     * every other range (about Y 470..480 after the terrain's soft cap, against 455..470).
     */
    private static final double EXCEPTIONAL_UPLIFT = 0.16;
    private static final double EXCEPTIONAL_SCALE = 30000.0;
    /** Depth of the strongest trenches below the abyssal plain, in blocks. */
    private static final double TRENCH_DEPTH = 135.0;
    /** Temperature: gain and offset of the zonal noise, calibrated to vanilla's band shares. */
    private static final double TEMPERATURE_GAIN = 1.45;
    private static final double TEMPERATURE_BIAS = 0.05;
    private static final double MOISTURE_GAIN = 1.15;
    /** Ocean features stay at least this many blocks below sea level. */
    private static final double SUBMARINE_CLEARANCE = 6.0;

    private final GeographySettings settings;
    private final double seaLevel;

    // Agent 2's macro noises: same seeds, so continents and ranges stay where they were.
    private final SimplexNoise continentWarpX, continentWarpZ, continent;
    private final SimplexNoise basinNoise, swellNoise;
    private final SimplexNoise hillMask, hillNoise;
    private final SimplexNoise beltWarpX, beltWarpZ, belt, beltDetail, beltActivity, uplift, exceptional;
    private final SimplexNoise crest, peaks, alpineNoise, alpineRidges;
    private final SimplexNoise plateauWarpX, plateauWarpZ, plateauMask, plateauHeight;
    // Geography.
    private final SimplexNoise oceanRegions, province, spur;
    private final SimplexNoise ridgeWarpX, ridgeWarpZ, oceanRidge, ridgeAlong, ridgeTexture, seamounts;
    private final SimplexNoise trenchWarpX, trenchWarpZ, trench, trenchGate;
    private final SimplexNoise temperatureNoise, temperatureLocal, moistureNoise, moistureLocal, wind;
    private final FrozenSea frozenSea;

    public GeographyModel(long seed, double seaLevel, GeographySettings settings) {
        this.settings = settings;
        this.seaLevel = seaLevel;
        long s = SimplexNoise.mix(seed);
        this.continentWarpX = new SimplexNoise(s + 1);
        this.continentWarpZ = new SimplexNoise(s + 2);
        this.continent = new SimplexNoise(s + 3);
        this.basinNoise = new SimplexNoise(s + 4);
        this.swellNoise = new SimplexNoise(s + 5);
        this.hillMask = new SimplexNoise(s + 6);
        this.hillNoise = new SimplexNoise(s + 7);
        this.beltWarpX = new SimplexNoise(s + 10);
        this.beltWarpZ = new SimplexNoise(s + 11);
        this.belt = new SimplexNoise(s + 12);
        this.beltDetail = new SimplexNoise(s + 13);
        this.beltActivity = new SimplexNoise(s + 14);
        this.uplift = new SimplexNoise(s + 15);
        this.crest = new SimplexNoise(s + 17);
        this.peaks = new SimplexNoise(s + 18);
        this.plateauWarpX = new SimplexNoise(s + 19);
        this.plateauWarpZ = new SimplexNoise(s + 20);
        this.plateauMask = new SimplexNoise(s + 21);
        this.plateauHeight = new SimplexNoise(s + 22);
        this.alpineNoise = new SimplexNoise(s + 28);
        this.alpineRidges = new SimplexNoise(s + 29);
        this.oceanRegions = new SimplexNoise(s + 40);
        this.province = new SimplexNoise(s + 41);
        this.spur = new SimplexNoise(s + 46);
        this.ridgeWarpX = new SimplexNoise(s + 47);
        this.ridgeWarpZ = new SimplexNoise(s + 48);
        this.oceanRidge = new SimplexNoise(s + 49);
        this.ridgeAlong = new SimplexNoise(s + 50);
        this.ridgeTexture = new SimplexNoise(s + 51);
        this.seamounts = new SimplexNoise(s + 52);
        this.trenchWarpX = new SimplexNoise(s + 53);
        this.trenchWarpZ = new SimplexNoise(s + 54);
        this.trench = new SimplexNoise(s + 55);
        this.trenchGate = new SimplexNoise(s + 56);
        this.temperatureNoise = new SimplexNoise(s + 57);
        this.temperatureLocal = new SimplexNoise(s + 58);
        this.moistureNoise = new SimplexNoise(s + 59);
        this.moistureLocal = new SimplexNoise(s + 60);
        this.wind = new SimplexNoise(s + 61);
        this.exceptional = new SimplexNoise(s + 63);
        this.frozenSea = new FrozenSea(seed);
    }

    public GeographySettings settings() {
        return this.settings;
    }

    public double seaLevel() {
        return this.seaLevel;
    }

    /** The fracture pattern of the polar ice fields; strength from {@link #seaLevelTemperature}. */
    public FrozenSea frozenSea() {
        return this.frozenSea;
    }

    /** Macro fields of one point. Mutable scratch space, never shared between threads. */
    private static final class Macro {
        double height;
        double base;
        double continent;
        double plains;
        double hills;
        double mountain;
        double mountainHeight;
        double alpine;
        double plateau;
        double basin;
        double dryness;
        double moistureZone;
        double trench;
        double ridge;
        double seamount;
    }

    /** Full sample: macro shape with all detail, climate and landform. */
    public GeographySample sample(double x, double z) {
        Macro m = new Macro();
        this.macro(x, z, m, true);
        double k = this.settings.regionScale();

        double seaTemperature = this.seaLevelTemperature(x, z);
        double elevation = Math.max(m.height, this.seaLevel);
        double temperature = clamp(seaTemperature - this.settings.lapseRate() * Math.max(0.0, elevation - this.seaLevel - 12.0), -1.0, 1.0);

        // Prevailing wind: mostly westerly, turning slowly over ~70 000 blocks.
        double angle = 2.2 * this.wind.sample(x / (70000.0 * k), z / (70000.0 * k));
        double windX = Math.cos(angle);
        double windZ = Math.sin(angle);
        double shadow = this.rainShadow(x, z, windX, windZ);
        // Wetter coasts, drier deep interiors.
        double coastal = 0.2 * (1.0 - smoothstep(0.0, 0.5, m.continent)) - 0.2 * smoothstep(0.6, 1.4, m.continent);
        double moisture = clamp(m.moistureZone * MOISTURE_GAIN + 0.08 * this.moistureLocal.sample(x / 2600.0, z / 2600.0)
                + coastal - this.settings.rainShadow() * shadow, -1.0, 1.0);

        return new GeographySample(m.height, m.base, m.continent, m.plains, m.hills, m.mountain, m.mountainHeight,
                m.alpine, m.plateau, m.basin, m.dryness, m.trench, temperature, seaTemperature, moisture, shadow,
                windX, windZ, this.classify(m));
    }

    /**
     * Regional elevation without the sharp small-scale parts of the macro shape (individual peaks,
     * aretes, ridge texture): smooth enough to steer gully erosion and to plan drainage on.
     */
    public double smoothElevation(double x, double z) {
        Macro m = new Macro();
        this.macro(x, z, m, false);
        return m.height;
    }

    /** Climate temperature at sea level (no elevation term); exactly {@link GeographySample#seaLevelTemperature}. */
    public double seaLevelTemperature(double x, double z) {
        double k = this.settings.regionScale();
        return clamp(fbm(this.temperatureNoise, x / (TEMPERATURE_SCALE * k), z / (TEMPERATURE_SCALE * k), 3, 0.5) * TEMPERATURE_GAIN
                + TEMPERATURE_BIAS + 0.07 * this.temperatureLocal.sample(x / 3200.0, z / 3200.0), -1.0, 1.0);
    }

    private void macro(double x, double z, Macro out, boolean sharp) {
        double k = this.settings.regionScale();
        // Continents: warped low-frequency fBm (0 is the coastline) shifted by oceanic regions.
        // Only the broad octaves are warped; warping the fine ones would stretch them into parallel
        // bands along coasts and on the sea floor.
        double wx = x + 1800.0 * k * fbm(this.continentWarpX, x / (5000.0 * k), z / (5000.0 * k), 3, 0.5);
        double wz = z + 1800.0 * k * fbm(this.continentWarpZ, x / (5000.0 * k), z / (5000.0 * k), 3, 0.5);
        double c = (fbm(this.continent, wx / (CONTINENT_SCALE * k), wz / (CONTINENT_SCALE * k), 4, 0.5) * 0.945
                + fbm(this.continent, x / 690.0 + 57.1, z / 690.0 - 23.9, 3, 0.5) * 0.055) * 1.7 + 0.36;
        c += OCEAN_REGION_AMPLITUDE * fbm(this.oceanRegions, x / (OCEAN_REGION_SCALE * k), z / (OCEAN_REGION_SCALE * k), 2, 0.5)
                - this.settings.oceanBias();
        double land = smoothstep(-0.02, 0.07, c);
        double inland = smoothstep(0.1, 0.6, c);

        // Provinces: vast plains vs hill country, and dry provinces (the broad part of the moisture zones).
        double provinceValue = fbm(this.province, x / (PROVINCE_SCALE * k), z / (PROVINCE_SCALE * k), 2, 0.5);
        double plainsBias = this.settings.plainsBias();
        double plains = smoothstep(plainsBias + 0.1, plainsBias - 0.1, provinceValue) * land;
        double moistureZone = fbm(this.moistureNoise, x / (MOISTURE_SCALE * k), z / (MOISTURE_SCALE * k), 2, 0.5);
        double dryness = smoothstep(-0.06, -0.3, moistureZone);

        double base = baseHeight(c);
        // Broad swells and basins of the interior; plains provinces are flatter.
        base += inland * 16.0 * (1.0 - 0.5 * plains) * fbm(this.swellNoise, x / 3500.0, z / 3500.0, 2, 0.5);
        double basin = smoothstep(0.25, 0.65, fbm(this.basinNoise, x / 5000.0, z / 5000.0, 3, 0.5)) * inland;
        base -= 22.0 * basin;

        // Rolling hills: rare in plains provinces, common in hill country.
        double hillField = fbm(this.hillMask, x / 3000.0, z / 3000.0, 2, 0.5) + 0.3 * provinceValue;
        double hilly = smoothstep(-0.25, 0.45, hillField) * land * (1.0 - 0.85 * plains);
        double hn = 0.5 + 0.5 * fbm(this.hillNoise, x / 600.0, z / 600.0, sharp ? 4 : 2, 0.5);
        double hills = hilly * 34.0 * hn * hn;

        // Mountain systems (Agent 2's belts): broad warped bands around the zero-lines of a
        // low-frequency noise, active in segments. d is 0 on the belt axis and 1 at its foot.
        double bn = this.beltField(x, z, k);
        // Soft absolute value: rounds the crest on the axis instead of a knife edge.
        double d = Math.sqrt(bn * bn + 0.01) - 0.1;
        // Mountain systems avoid the interior of plains provinces, but may rise from their margins.
        double activity = smoothstep(-0.15, 0.3, fbm(this.beltActivity, x / (13000.0 * k), z / (13000.0 * k), 2, 0.5))
                * smoothstep(-0.3, 0.05, c) * (1.0 - 0.6 * plains);
        double mountainHeight = 0.0;
        double mountain = 0.0;
        // Young, sharp ("alpine") regions vs old, rounded ones; everywhere, so it stays continuous.
        double alpine = smoothstep(-0.35, 0.35, fbm(this.alpineNoise, x / (9000.0 * k), z / (9000.0 * k), 2, 0.5));
        if (d < 1.3 && activity > 0.0) {
            double stature = 0.35 + 0.65 * smoothstep(-0.2, 0.7, fbm(this.uplift, x / (16000.0 * k), z / (16000.0 * k), 2, 0.5));
            stature *= this.exceptional(x, z, k, stature);
            // Massifs and passes along the range.
            double crestLine = 0.3 + 0.7 * smoothstep(-0.55, 0.45, fbm(this.crest, x / 2200.0, z / 2200.0, 2, 0.5));
            // Individual peaks and cols.
            double peaky = sharp ? smoothstep(-0.6, 0.6, fbm(this.peaks, x / 800.0, z / 800.0, 3, 0.5)) : 0.5;
            double range = MOUNTAIN_ENVELOPE * stature * crestLine;
            double envelope = range * activity;
            if (d < 1.0) {
                double t = 1.0 - d;
                double profile = t * t * (1.6 - 0.6 * t);
                mountainHeight = envelope * profile * (0.6 + 0.6 * peaky);
                mountain = t * activity;
                // Young ranges get aretes and horns near their crests; old ones stay rounded.
                if (sharp && alpine > 0.0) {
                    double crestZone = smoothstep(0.3, 0.85, profile);
                    if (crestZone > 0.0) {
                        double aretes = ridged(this.alpineRidges, x / 420.0, z / 420.0, 4);
                        mountainHeight += alpine * crestZone * 0.18 * mountainHeight * (aretes - 0.4);
                    }
                }
            }
            // Branch ranges: zero-lines of an unwarped smaller noise, only within reach of an active
            // belt, so they spring from the main ranges (junctions, knots, side chains) instead of
            // standing alone. Their height fades with the square of the reach, so they leave the
            // main range as broad ridges and die out instead of trailing off as thin low ones.
            double reach = activity * (1.0 - smoothstep(0.5, 1.15, d));
            if (reach > 0.0) {
                double sn = this.spur.sample(x / (5000.0 * k), z / (5000.0 * k)) / 0.3;
                double sd = Math.sqrt(sn * sn + 0.04) - 0.2;
                if (sd < 1.0) {
                    double st = 1.0 - sd;
                    double branch = 0.6 * range * reach * reach * st * st * (1.6 - 0.6 * st) * (0.6 + 0.6 * peaky);
                    mountainHeight = smoothMax(mountainHeight, branch, 24.0);
                    mountain = Math.max(mountain, 0.8 * st * reach);
                }
            }
            hills = Math.max(hills, smoothstep(0.0, 0.6, mountain) * 30.0 * hn);
        }

        // Plateaus with escarpments, away from mountain belts; commoner in dry provinces (tablelands).
        double plateau = 0.0;
        if (c > 0.25) {
            double px = x + 900.0 * fbm(this.plateauWarpX, x / 2400.0, z / 2400.0, 3, 0.5);
            double pz = z + 900.0 * fbm(this.plateauWarpZ, x / 2400.0, z / 2400.0, 3, 0.5);
            // Low-frequency mask with a rough edge term: bays, spurs and buttes along the escarpment.
            double mask = fbm(this.plateauMask, px / (9000.0 * k), pz / (9000.0 * k), 2, 0.4)
                    + 0.07 * fbm(this.plateauMask, px / 700.0 + 71.3, pz / 700.0 - 19.7, sharp ? 3 : 1, 0.5);
            double shift = 0.14 * dryness;
            plateau = smoothstep(0.50 - shift, 0.58 - shift, mask)
                    * smoothstep(0.25, 0.5, c) * (1.0 - smoothstep(0.0, 0.5, mountain)) * (1.0 - 0.8 * plains);
        }
        double plateauRise = 0.0;
        if (plateau > 0.0) {
            plateauRise = plateau * (40.0 + 50.0 * (0.5 + 0.5 * this.plateauHeight.sample(x / 6000.0, z / 6000.0)));
            hills *= 1.0 - 0.7 * plateau;
        }

        // Sea floor: ridges, seamounts and trenches on the deep ocean floor.
        double floor = 0.0;
        double deep = smoothstep(-0.35, -0.8, c);
        if (deep > 0.0) {
            floor += deep * this.oceanRidge(x, z, out, sharp, k);
            floor += deep * this.seamount(x, z, out);
            double clearance = this.seaLevel - SUBMARINE_CLEARANCE - base;
            floor = submarineLimit(floor, clearance);
            floor -= this.trench(x, z, c, out, k);
        }

        out.height = base + floor + hills + mountainHeight + plateauRise;
        out.base = base + floor;
        out.continent = c;
        out.plains = plains;
        out.hills = hilly;
        out.mountain = mountain;
        out.mountainHeight = mountainHeight;
        out.alpine = alpine;
        out.plateau = plateau;
        out.basin = basin;
        out.dryness = dryness;
        out.moistureZone = moistureZone;
    }

    /** Signed belt field: mountain belt axes lie on its zero-lines, |value| ~1 at a belt's foot. */
    private double beltField(double x, double z, double k) {
        double bx = x + 2200.0 * k * fbm(this.beltWarpX, x / (6000.0 * k), z / (6000.0 * k), 2, 0.5);
        double bz = z + 2200.0 * k * fbm(this.beltWarpZ, x / (6000.0 * k), z / (6000.0 * k), 2, 0.5);
        return (this.belt.sample(bx / (10000.0 * k), bz / (10000.0 * k)) + 0.06 * this.beltDetail.sample(bx / 3000.0, bz / 3000.0)) / 0.7;
    }

    /** Mid-ocean ridges: zero-lines of a warped noise, a broad rugged rise with a central rift. */
    private double oceanRidge(double x, double z, Macro out, boolean sharp, double k) {
        double rx = x + 1500.0 * k * fbm(this.ridgeWarpX, x / (12000.0 * k), z / (12000.0 * k), 2, 0.5);
        double rz = z + 1500.0 * k * fbm(this.ridgeWarpZ, x / (12000.0 * k), z / (12000.0 * k), 2, 0.5);
        double rn = this.oceanRidge.sample(rx / (24000.0 * k), rz / (24000.0 * k)) / 0.22;
        double rr = 1.0 - rn * rn;
        if (rr <= 0.0) {
            return 0.0;
        }
        double along = 0.45 + 0.55 * smoothstep(-0.4, 0.4, fbm(this.ridgeAlong, x / 5000.0, z / 5000.0, 2, 0.5));
        double hump = rr * rr * (0.5 + 0.5 * rr);
        double riftU = rn / 0.06;
        double rift = riftU * riftU < 1.0 ? (1.0 - riftU * riftU) * (1.0 - riftU * riftU) : 0.0;
        double texture = sharp ? (ridged(this.ridgeTexture, x / 350.0, z / 350.0, 3) - 0.4) * 16.0 * hump : 0.0;
        out.ridge = hump * along;
        return along * (55.0 * hump - 14.0 * rift + texture);
    }

    /** Isolated seamounts: at most one per 2 600-block cell, cones with concave flanks, some flat-topped. */
    private double seamount(double x, double z, Macro out) {
        double cell = 2600.0;
        long ci = (long) Math.floor(x / cell);
        long cj = (long) Math.floor(z / cell);
        double best = 0.0;
        double strength = 0.0;
        for (long i = ci - 1; i <= ci + 1; i++) {
            for (long j = cj - 1; j <= cj + 1; j++) {
                if (this.seamounts.hash01(i, j, 0) > 0.3) {
                    continue;
                }
                double cx = (i + 0.15 + 0.7 * this.seamounts.hash01(i, j, 1)) * cell;
                double cz = (j + 0.15 + 0.7 * this.seamounts.hash01(i, j, 2)) * cell;
                double radius = 160.0 + 280.0 * this.seamounts.hash01(i, j, 3);
                double dx = x - cx;
                double dz = z - cz;
                double u = Math.sqrt(dx * dx + dz * dz) / radius;
                if (u >= 1.0) {
                    continue;
                }
                double h3 = this.seamounts.hash01(i, j, 4);
                double height = 25.0 + 60.0 * h3 * Math.sqrt(h3);
                double shape = Math.pow(1.0 - u, 1.7);
                if (this.seamounts.hash01(i, j, 5) < 0.3) {
                    // Guyot: an eroded flat top.
                    shape = Math.min(shape, 0.55) / 0.55;
                }
                // Smooth union with neighbouring cones.
                best = smoothMax(best, height * shape, 6.0);
                strength = Math.max(strength, shape);
            }
        }
        out.seamount = strength;
        return best;
    }

    /**
     * Abyssal trenches: long arcs along the zero-lines of a warped noise, only in deep basins and
     * where a 30 000-block rarity noise allows them. Steep walls and a narrow flat floor.
     */
    private double trench(double x, double z, double c, Macro out, double k) {
        double deepBasin = smoothstep(-0.55, -0.9, c);
        if (deepBasin <= 0.0) {
            return 0.0;
        }
        double threshold = this.settings.trenchThreshold();
        double gate = smoothstep(threshold, threshold + 0.15, fbm(this.trenchGate, x / (30000.0 * k), z / (30000.0 * k), 2, 0.5));
        if (gate <= 0.0) {
            return 0.0;
        }
        double tx = x + 2500.0 * k * fbm(this.trenchWarpX, x / (7000.0 * k), z / (7000.0 * k), 2, 0.5);
        double tz = z + 2500.0 * k * fbm(this.trenchWarpZ, x / (7000.0 * k), z / (7000.0 * k), 2, 0.5);
        double u = Math.abs(this.trench.sample(tx / (15000.0 * k), tz / (15000.0 * k))) / 0.035;
        if (u >= 1.0) {
            return 0.0;
        }
        double profile = 1.0 - smoothstep(0.12, 1.0, u);
        double strength = gate * deepBasin * profile;
        out.trench = strength;
        double along = 0.75 + 0.25 * this.ridgeAlong.sample(x / 3000.0 + 17.3, z / 3000.0 - 4.1);
        return TRENCH_DEPTH * strength * along;
    }

    /** Compresses positive sea floor relief towards {@code limit} so ridges and seamounts stay submerged. */
    static double submarineLimit(double relief, double limit) {
        if (relief <= 0.0) {
            return relief;
        }
        if (limit <= 0.0) {
            return 0.0;
        }
        double knee = 0.6 * limit;
        if (relief <= knee) {
            return relief;
        }
        double over = relief - knee;
        double room = limit - knee;
        return knee + over / (1.0 + over / room);
    }

    /**
     * Rain shadow: how far the mountain systems upwind (500, 1 300 and 2 600 blocks against the
     * prevailing wind) rise above the local ground, 0..1. Uses the cheap crest envelope only.
     */
    private double rainShadow(double x, double z, double windX, double windZ) {
        double here = this.barrier(x, z);
        double lee = 0.0;
        double[] distances = {500.0, 1300.0, 2600.0};
        double[] weights = {1.0, 0.85, 0.65};
        for (int i = 0; i < distances.length; i++) {
            double upwind = this.barrier(x - windX * distances[i], z - windZ * distances[i]);
            lee = Math.max(lee, (upwind - 0.5 * here) * weights[i]);
        }
        return smoothstep(25.0, 200.0, lee);
    }

    /** Height of the mountain crest envelope (no peaks, branches or plateaus): what blocks moist air. */
    private double barrier(double x, double z) {
        double k = this.settings.regionScale();
        double bx = x + 2200.0 * k * fbm(this.beltWarpX, x / (6000.0 * k), z / (6000.0 * k), 2, 0.5);
        double bz = z + 2200.0 * k * fbm(this.beltWarpZ, x / (6000.0 * k), z / (6000.0 * k), 2, 0.5);
        double bn = this.belt.sample(bx / (10000.0 * k), bz / (10000.0 * k)) / 0.7;
        double d = Math.sqrt(bn * bn + 0.01) - 0.1;
        if (d >= 1.0) {
            return 0.0;
        }
        double activity = smoothstep(-0.15, 0.3, fbm(this.beltActivity, x / (13000.0 * k), z / (13000.0 * k), 2, 0.5));
        if (activity <= 0.0) {
            return 0.0;
        }
        double stature = 0.35 + 0.65 * smoothstep(-0.2, 0.7, fbm(this.uplift, x / (16000.0 * k), z / (16000.0 * k), 2, 0.5));
        stature *= this.exceptional(x, z, k, stature);
        double t = 1.0 - d;
        return MOUNTAIN_ENVELOPE * 0.65 * stature * activity * t * t * (1.6 - 0.6 * t);
    }

    /**
     * Uplift of the greatest mountain systems (1 elsewhere): continuous, it rises with the
     * stature of the range from 0.75 to 1 inside the regions of a 30 000-block field, so a major
     * range swells into its highest massif over thousands of blocks instead of stepping up.
     */
    private double exceptional(double x, double z, double k, double stature) {
        if (stature <= 0.75) {
            return 1.0;
        }
        double field = smoothstep(0.3, 0.65, fbm(this.exceptional, x / (EXCEPTIONAL_SCALE * k), z / (EXCEPTIONAL_SCALE * k), 2, 0.5));
        return 1.0 + EXCEPTIONAL_UPLIFT * field * smoothstep(0.75, 1.0, stature);
    }

    private Landform classify(Macro m) {
        if (m.height < this.seaLevel - 0.5) {
            if (m.trench > 0.3) {
                return Landform.TRENCH;
            }
            if (m.seamount > 0.25 && m.seamount >= m.ridge) {
                return Landform.SEAMOUNT;
            }
            if (m.ridge > 0.25) {
                return Landform.SUBMARINE_RIDGE;
            }
            if (m.continent > -0.12) {
                return Landform.SHELF;
            }
            return m.continent > -0.6 ? Landform.CONTINENTAL_SLOPE : Landform.ABYSSAL_PLAIN;
        }
        if (m.continent < 0.06 && m.height < this.seaLevel + 6.0) {
            return Landform.COAST;
        }
        if (m.mountainHeight > 180.0) {
            return Landform.HIGH_MOUNTAINS;
        }
        if (m.mountainHeight > 60.0) {
            return Landform.MOUNTAINS;
        }
        if (m.mountain > 0.2 || m.mountainHeight > 25.0) {
            return Landform.FOOTHILLS;
        }
        if (m.plateau > 0.5) {
            return Landform.PLATEAU;
        }
        if (m.basin > 0.5) {
            return Landform.BASIN;
        }
        return m.hills > 0.45 ? Landform.HILLS : Landform.PLAINS;
    }

    /** Ground level of a continent value without relief: abyssal plain, slope, shelf, coast, interior. */
    public static double baseHeight(double c) {
        return CONTINENT_SPLINE.at(c);
    }

    /*
     * Sea floor: coast 61 at c = 0, a gentle shelf to the shelf break (50 at -0.1), the continental
     * slope (36 at -0.18, 12 at -0.3), the rise (-4 at -0.45, -14 at -0.65) and the abyssal plain
     * (-22 at -1.0, -30 at -1.8). Land: Agent 2's spline.
     */
    private static final NoiseMath.Spline CONTINENT_SPLINE = new NoiseMath.Spline(
            new double[] {-1.8, -1.0, -0.65, -0.45, -0.3, -0.18, -0.1, -0.03, 0.0, 0.05, 0.2, 0.5, 0.9, 1.5},
            new double[] {-30.0, -22.0, -14.0, -4.0, 12.0, 36.0, 50.0, 58.0, 61.0, 64.5, 70.0, 82.0, 95.0, 110.0});
}
