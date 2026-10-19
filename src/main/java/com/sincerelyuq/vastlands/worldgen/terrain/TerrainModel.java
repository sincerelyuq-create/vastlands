package com.sincerelyuq.vastlands.worldgen.terrain;

/**
 * The Vastlands height field: a pure function from (seed, x, z) to a {@link TerrainColumn}.
 *
 * <p>Built in three layers:
 * <ol>
 *     <li>Macro shape: continents and sea floor, interior swells and basins, rolling hills,
 *     mountain belts (long warped bands with a crest profile, massifs and passes along them) and
 *     plateaus with escarpments.</li>
 *     <li>Gully erosion: a filter that cuts drainage-aligned gullies into the slopes of the macro
 *     shape at four scales, giving mountains their spurs and side valleys.</li>
 *     <li>Small detail: plains undulation, micro relief, sea floor texture.</li>
 * </ol>
 *
 * <p>Pure Java (no game classes), immutable and thread-safe.
 */
public final class TerrainModel {
    private static final double ROTATE_COS = Math.cos(0.9);
    private static final double ROTATE_SIN = Math.sin(0.9);
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

    private final SimplexNoise continentWarpX, continentWarpZ, continent;
    private final SimplexNoise basinNoise, swellNoise;
    private final SimplexNoise hillMask, hillNoise, microNoise, undulationNoise;
    private final SimplexNoise beltWarpX, beltWarpZ, belt, beltDetail, beltActivity, uplift;
    private final SimplexNoise crest, peaks, alpine, alpineRidges;
    private final SimplexNoise plateauWarpX, plateauWarpZ, plateauMask, plateauHeight;
    private final SimplexNoise seaFloor;
    private final SimplexNoise gully;
    private final SimplexNoise weirdnessSign, wetland, windswept;
    private final SimplexNoise crags, roughness;

    public TerrainModel(long seed, double seaLevel, double maxHeight) {
        this.seaLevel = seaLevel;
        this.maxHeight = maxHeight;
        long s = SimplexNoise.mix(seed);
        this.continentWarpX = new SimplexNoise(s + 1);
        this.continentWarpZ = new SimplexNoise(s + 2);
        this.continent = new SimplexNoise(s + 3);
        this.basinNoise = new SimplexNoise(s + 4);
        this.swellNoise = new SimplexNoise(s + 5);
        this.hillMask = new SimplexNoise(s + 6);
        this.hillNoise = new SimplexNoise(s + 7);
        this.microNoise = new SimplexNoise(s + 8);
        this.undulationNoise = new SimplexNoise(s + 9);
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
        this.seaFloor = new SimplexNoise(s + 23);
        this.gully = new SimplexNoise(s + 24);
        this.weirdnessSign = new SimplexNoise(s + 25);
        this.wetland = new SimplexNoise(s + 26);
        this.windswept = new SimplexNoise(s + 27);
        this.alpine = new SimplexNoise(s + 28);
        this.alpineRidges = new SimplexNoise(s + 29);
        this.crags = new SimplexNoise(s + 30);
        this.roughness = new SimplexNoise(s + 31);
    }

    /** Macro shape at one point. */
    private static final class Macro {
        double height;
        double base;
        double continent;
        double land;
        double hills;
        double mountain;
        double mountainHeight;
        /** 0 for old, rounded ranges .. 1 for young, sharp alpine ranges. */
        double alpine;
        double plateau;
    }

    public TerrainColumn sample(double x, double z) {
        Macro m = new Macro();
        this.macro(x, z, m);
        // Gradient of the plane through three samples on a circle around (x, z): centred like a
        // central difference, one sample cheaper.
        double h0 = this.smoothHeight(x + SLOPE_STEP, z);
        double h1 = this.smoothHeight(x - 0.5 * SLOPE_STEP, z + SQRT3_2 * SLOPE_STEP);
        double h2 = this.smoothHeight(x - 0.5 * SLOPE_STEP, z - SQRT3_2 * SLOPE_STEP);
        double slopeX = (2.0 * h0 - h1 - h2) / (3.0 * SLOPE_STEP);
        double slopeZ = (h1 - h2) / (2.0 * SQRT3_2 * SLOPE_STEP);
        double slope = Math.sqrt(slopeX * slopeX + slopeZ * slopeZ);

        double gullyDepth = -this.gullies(x, z, slopeX, slopeZ, slope, m);
        double height = this.softCap(m.height - gullyDepth + this.detail(x, z, m, slope));

        double relief = Math.max(0.0, height - m.base);
        double ruggedness = clamp01(Math.max(Math.max(m.mountainHeight / 200.0, m.hills * 0.5), smoothstep(0.3, 1.2, slope) * 0.7));
        double continentalness = this.climateContinentalness(m.continent, height);
        double erosion = this.climateErosion(x, z, height, slope, ruggedness, m);
        double weirdness = this.climateWeirdness(x, z, relief, m);
        return new TerrainColumn(height, m.base, m.continent, m.mountain, m.plateau, gullyDepth, ruggedness,
                slopeX, slopeZ, continentalness, erosion, weirdness);
    }

    /** Macro height without its sharp small-scale parts; its slope steers the gullies. */
    private double smoothHeight(double x, double z) {
        Macro m = new Macro();
        this.macro(x, z, m, false);
        return m.height;
    }

    private void macro(double x, double z, Macro out) {
        this.macro(x, z, out, true);
    }

    private void macro(double x, double z, Macro out, boolean sharp) {
        // Continents: warped low-frequency fBm. 0 is the coastline.
        double wx = x + 1800.0 * fbm(this.continentWarpX, x / 5000.0, z / 5000.0, 3, 0.5);
        double wz = z + 1800.0 * fbm(this.continentWarpZ, x / 5000.0, z / 5000.0, 3, 0.5);
        // Only the broad octaves are warped; warping the fine ones would stretch them into parallel
        // bands along coasts and on the sea floor.
        double c = (fbm(this.continent, wx / 11000.0, wz / 11000.0, 4, 0.5) * 0.945
                + fbm(this.continent, x / 690.0 + 57.1, z / 690.0 - 23.9, 3, 0.5) * 0.055) * 1.7 + 0.36;
        double land = smoothstep(-0.02, 0.07, c);
        double inland = smoothstep(0.1, 0.6, c);

        double base = baseHeight(c);
        // Broad swells and basins of the interior.
        base += inland * 16.0 * fbm(this.swellNoise, x / 3500.0, z / 3500.0, 2, 0.5);
        double basin = smoothstep(0.25, 0.65, fbm(this.basinNoise, x / 5000.0, z / 5000.0, 3, 0.5)) * inland;
        base -= 22.0 * basin;

        // Rolling hills.
        double hilly = smoothstep(-0.25, 0.45, fbm(this.hillMask, x / 3000.0, z / 3000.0, 2, 0.5)) * land;
        double hn = 0.5 + 0.5 * fbm(this.hillNoise, x / 600.0, z / 600.0, sharp ? 4 : 2, 0.5);
        double hills = hilly * 34.0 * hn * hn;

        // Mountain belts: broad warped bands around the zero-lines of a low-frequency noise,
        // active in segments. d is 0 on the belt axis and 1 at its foot.
        double bx = x + 2200.0 * fbm(this.beltWarpX, x / 6000.0, z / 6000.0, 2, 0.5);
        double bz = z + 2200.0 * fbm(this.beltWarpZ, x / 6000.0, z / 6000.0, 2, 0.5);
        double bn = (this.belt.sample(bx / 10000.0, bz / 10000.0) + 0.06 * this.beltDetail.sample(bx / 3000.0, bz / 3000.0)) / 0.7;
        // Soft absolute value: rounds the crest on the axis instead of a knife edge.
        double d = Math.sqrt(bn * bn + 0.01) - 0.1;
        double activity = smoothstep(-0.1, 0.35, fbm(this.beltActivity, x / 13000.0, z / 13000.0, 2, 0.5))
                * smoothstep(-0.3, 0.05, c);
        double mountainHeight = 0.0;
        double mountain = 0.0;
        // Young, sharp ("alpine") regions vs old, rounded ones; everywhere, so it stays continuous.
        double alpine = smoothstep(-0.35, 0.35, fbm(this.alpine, x / 9000.0, z / 9000.0, 2, 0.5));
        if (d < 1.0 && activity > 0.0) {
            double t = 1.0 - d;
            double profile = t * t * (1.6 - 0.6 * t);
            double stature = 0.35 + 0.65 * smoothstep(-0.2, 0.7, fbm(this.uplift, x / 16000.0, z / 16000.0, 2, 0.5));
            // Massifs and passes along the range.
            double crestLine = 0.3 + 0.7 * smoothstep(-0.55, 0.45, fbm(this.crest, x / 2200.0, z / 2200.0, 2, 0.5));
            // Individual peaks and cols.
            double peaky = sharp ? smoothstep(-0.6, 0.6, fbm(this.peaks, x / 800.0, z / 800.0, 3, 0.5)) : 0.5;
            double envelope = 380.0 * stature * activity * crestLine;
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
            hills = Math.max(hills, smoothstep(0.0, 0.6, mountain) * 30.0 * hn);
        }

        // Plateaus with escarpments, away from mountain belts.
        double plateau = 0.0;
        if (c > 0.25) {
            double px = x + 900.0 * fbm(this.plateauWarpX, x / 2400.0, z / 2400.0, 3, 0.5);
            double pz = z + 900.0 * fbm(this.plateauWarpZ, x / 2400.0, z / 2400.0, 3, 0.5);
            // Low-frequency mask with a rough edge term: bays, spurs and buttes along the escarpment.
            double mask = fbm(this.plateauMask, px / 9000.0, pz / 9000.0, 2, 0.4)
                    + 0.07 * fbm(this.plateauMask, px / 700.0 + 71.3, pz / 700.0 - 19.7, sharp ? 3 : 1, 0.5);
            plateau = smoothstep(0.50, 0.58, mask)
                    * smoothstep(0.25, 0.5, c) * (1.0 - smoothstep(0.0, 0.5, mountain));
        }
        double plateauRise = 0.0;
        if (plateau > 0.0) {
            plateauRise = plateau * (40.0 + 50.0 * (0.5 + 0.5 * this.plateauHeight.sample(x / 6000.0, z / 6000.0)));
            hills *= 1.0 - 0.7 * plateau;
        }

        out.height = base + hills + mountainHeight + plateauRise;
        out.base = base;
        out.continent = c;
        out.land = land;
        out.hills = hilly;
        out.mountain = mountain;
        out.mountainHeight = mountainHeight;
        out.alpine = alpine;
        out.plateau = plateau;
    }

    /** Small-scale relief that does not steer the gullies. */
    private double detail(double x, double z, Macro m, double slope) {
        double v = m.land * (2.5 * fbm(this.microNoise, x / 140.0, z / 140.0, 3, 0.5)
                + (1.0 - 0.5 * m.hills) * 5.0 * fbm(this.undulationNoise, x / 380.0, z / 380.0, 3, 0.5));
        // Uneven ground: bumps of a few blocks that grow with the slope, so long slopes do not
        // read as even staircases.
        v += m.land * (1.0 + 4.0 * smoothstep(0.1, 0.8, slope)) * fbm(this.roughness, x / 36.0, z / 36.0, 3, 0.5);
        // Crags and ledges on steep mountain and escarpment ground, sharper on young ranges.
        double rocky = clamp01(1.5 * m.mountain + m.plateau) * smoothstep(0.15, 0.8, slope);
        if (rocky > 0.0) {
            v += rocky * (6.0 + 8.0 * m.alpine) * (ridged(this.crags, x / 70.0, z / 70.0, 3) - 0.35);
        }
        if (m.land < 1.0) {
            v += (1.0 - m.land) * 5.0 * fbm(this.seaFloor, x / 450.0, z / 450.0, 3, 0.5);
        }
        return v;
    }

    /**
     * Gully erosion filter (after Clay John's "eroded terrain noise"): stripes running downhill along
     * the slope of the macro shape, blended over jittered cells. Later octaves follow the gullies of
     * earlier ones, which makes them branch. Only lowers terrain; zero on flat ground, and it never
     * cuts below the regional base or the sea.
     */
    private double gullies(double x, double z, double slopeX, double slopeZ, double slope, Macro m) {
        if (slope <= 0.05) {
            return 0.0;
        }
        double relief = m.height - Math.max(m.base, this.seaLevel + 2.0);
        // Plateau escarpments are lower than mountains, so they reach full strength at less relief.
        double fullRelief = 110.0 - 40.0 * smoothstep(0.0, 0.3, m.plateau);
        double strength = smoothstep(0.05, 0.8, slope) * clamp01(relief / fullRelief) * (0.6 + 0.5 * m.alpine);
        if (strength <= 0.0) {
            return 0.0;
        }
        // Stripes run along the gradient: their wave vector is perpendicular to it.
        double dirX = slopeZ / slope;
        double dirZ = -slopeX / slope;
        double[] g = new double[3];
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
            this.gullyOctave(x / cell, z / cell, ox, oz, g);
            total += cell * GULLY_DEPTH * (g[0] - 1.0) * 0.5;
            accX += g[1] * 0.5;
            accZ += g[2] * 0.5;
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

    /*
     * Climate parameters. Biomes are chosen by vanilla's overworld table (OverworldBiomeBuilder)
     * from these values, so they are derived from the terrain instead of independent noises:
     * - continentalness: ocean (< -0.19, deep below -0.455) exactly where the ground is under sea
     *   level, the coast band (-0.19..-0.11: beaches, stony shores) at the shoreline, then rising
     *   inland (near -0.11..0.03, mid 0.03..0.3, far 0.3..1).
     * - erosion: low where rugged. E0 (< -0.78) and E1 (< -0.375) select peak and slope biomes on
     *   mountains, E2 plateau biomes (meadow, savanna plateau, badlands) on plateaus, E3/E4 the
     *   ordinary biomes of hills and plains; E5 marks windswept hills, E6 flat wet lowland (swamps).
     * - weirdness: its folded value "peaks and valleys" places peaks on crests, slopes below them
     *   and low/mid slices on plains. |w| < 0.05 (the river biomes) is never produced: rivers are
     *   Hydrology's. The sign picks vanilla's variant biomes and comes from its own noise.
     */

    private double climateContinentalness(double c, double height) {
        double depth = this.seaLevel - height;
        if (depth > 0.5) {
            // Ocean: -0.19 at the shore down to -1.0 at 60 blocks depth; deep ocean below -0.455 (~20 deep).
            return -0.19 - 0.81 * clamp01((depth - 0.5) / 60.0);
        }
        // Coast band right at the shoreline, then inland.
        double v = -0.11 + 1.11 * clamp01(c / 1.1);
        if (c < 0.06 && height < this.seaLevel + 5.0) {
            v = Math.min(v, -0.15);
        }
        return v;
    }

    private double climateErosion(double x, double z, double height, double slope, double ruggedness, Macro m) {
        double e = 0.45 - 1.4 * ruggedness;
        if (m.plateau > 0.5) {
            e = Math.min(e, -0.3);
        }
        if (ruggedness < 0.3 && slope < 0.06 && height < this.seaLevel + 6.0 && height > this.seaLevel - 1.0
                && this.wetland.sample(x / 1500.0, z / 1500.0) > 0.25) {
            e = 0.7;
        } else if (m.hills > 0.6 && m.mountain < 0.2 && slope > 0.25 && this.windswept.sample(x / 2000.0, z / 2000.0) > 0.35) {
            e = 0.5;
        }
        return e;
    }

    private double climateWeirdness(double x, double z, double relief, Macro m) {
        // Peaks-and-valleys from the height above the regional base.
        double pv;
        if (m.mountainHeight > 40.0) {
            double q = relief / m.mountainHeight;
            pv = -0.5 + 1.7 * clamp01(q);
        } else {
            pv = -0.5 + clamp01(relief / 40.0) * 0.9;
        }
        pv = Math.max(-0.85, Math.min(1.0, pv));
        // Invert vanilla's folding pv = 1 - |3|w| - 2| on the |w| <= 2/3 branch; |w| >= 0.05 keeps rivers out.
        double w = Math.max(0.06, (pv + 1.0) / 3.0);
        return this.weirdnessSign.sample(x / 1800.0, z / 1800.0) < 0.0 ? -w : w;
    }

    /** Ground level of a continent value without relief: sea floor, shelf, coast, interior. */
    static double baseHeight(double c) {
        return CONTINENT_SPLINE.at(c);
    }

    private static final Spline CONTINENT_SPLINE = new Spline(
            new double[] {-1.4, -0.8, -0.45, -0.3, -0.12, -0.03, 0.0, 0.05, 0.2, 0.5, 0.9, 1.5},
            new double[] {-30.0, -5.0, 14.0, 26.0, 48.0, 57.0, 61.0, 64.5, 70.0, 82.0, 95.0, 110.0});

    static double fbm(SimplexNoise noise, double x, double z, int octaves, double gain) {
        double sum = 0.0;
        double amplitude = 1.0;
        double norm = 0.0;
        for (int i = 0; i < octaves; i++) {
            sum += amplitude * noise.sample(x, z);
            norm += amplitude;
            amplitude *= gain;
            double nx = (x * ROTATE_COS - z * ROTATE_SIN) * 2.0 + 31.7;
            double nz = (x * ROTATE_SIN + z * ROTATE_COS) * 2.0 - 12.3;
            x = nx;
            z = nz;
        }
        return sum / norm;
    }

    /** Ridged multifractal (Musgrave) in [0, 1]: sharp connected crests, detail concentrated on them. */
    static double ridged(SimplexNoise noise, double x, double z, int octaves) {
        double sum = 0.0;
        double amplitude = 1.0;
        double norm = 0.0;
        double weight = 1.0;
        for (int i = 0; i < octaves; i++) {
            double signal = 1.0 - Math.abs(noise.sample(x, z));
            signal *= signal;
            signal *= weight;
            weight = clamp01(signal * 2.0);
            sum += signal * amplitude;
            norm += amplitude;
            amplitude *= 0.5;
            double nx = (x * ROTATE_COS - z * ROTATE_SIN) * 2.0 + 31.7;
            double nz = (x * ROTATE_SIN + z * ROTATE_COS) * 2.0 - 12.3;
            x = nx;
            z = nz;
        }
        return sum / norm;
    }

    static double smoothstep(double edge0, double edge1, double v) {
        double t = clamp01((v - edge0) / (edge1 - edge0));
        return t * t * (3.0 - 2.0 * t);
    }

    static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    /** Monotone cubic (Fritsch-Carlson) interpolation through fixed points; clamps outside them. */
    static final class Spline {
        private final double[] xs, ys, tangents;

        Spline(double[] xs, double[] ys) {
            this.xs = xs;
            this.ys = ys;
            int n = xs.length;
            double[] secants = new double[n - 1];
            for (int i = 0; i < n - 1; i++) {
                secants[i] = (ys[i + 1] - ys[i]) / (xs[i + 1] - xs[i]);
            }
            this.tangents = new double[n];
            this.tangents[0] = secants[0];
            this.tangents[n - 1] = secants[n - 2];
            for (int i = 1; i < n - 1; i++) {
                this.tangents[i] = secants[i - 1] * secants[i] <= 0.0 ? 0.0 : (secants[i - 1] + secants[i]) / 2.0;
            }
            for (int i = 0; i < n - 1; i++) {
                if (secants[i] == 0.0) {
                    this.tangents[i] = 0.0;
                    this.tangents[i + 1] = 0.0;
                    continue;
                }
                double a = this.tangents[i] / secants[i];
                double b = this.tangents[i + 1] / secants[i];
                double h = a * a + b * b;
                if (h > 9.0) {
                    double t = 3.0 / Math.sqrt(h);
                    this.tangents[i] = t * a * secants[i];
                    this.tangents[i + 1] = t * b * secants[i];
                }
            }
        }

        double at(double x) {
            if (x <= this.xs[0]) {
                return this.ys[0];
            }
            int n = this.xs.length;
            if (x >= this.xs[n - 1]) {
                return this.ys[n - 1];
            }
            int i = 0;
            while (x > this.xs[i + 1]) {
                i++;
            }
            double h = this.xs[i + 1] - this.xs[i];
            double t = (x - this.xs[i]) / h;
            double t2 = t * t;
            double t3 = t2 * t;
            return (2 * t3 - 3 * t2 + 1) * this.ys[i] + (t3 - 2 * t2 + t) * h * this.tangents[i]
                    + (-2 * t3 + 3 * t2) * this.ys[i + 1] + (t3 - t2) * h * this.tangents[i + 1];
        }
    }
}
