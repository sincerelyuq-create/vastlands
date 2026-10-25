package com.sincerelyuq.vastlands.worldgen.caves;

import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.clamp01;
import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.fbm;
import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.smoothstep;

import com.sincerelyuq.vastlands.worldgen.geography.GeographySample;
import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;

/**
 * The rock the caves form in, as continuous 0..1 fields of a column. Vastlands has no rock types,
 * so the caves derive them from the geography (Agent 2.5) and add their own large-scale lithology
 * noise:
 * <ul>
 *     <li>{@code karst}: soluble, limestone-like rock: a 5 200-block lithology field, favoured on
 *     tablelands and in hill country, wet climates dissolving it faster, absent from the crystalline
 *     cores of high ranges. Karst gets dense multi-level networks, wide phreatic tubes, chambers,
 *     dolines and canyon passages.</li>
 *     <li>{@code fracture}: jointed, faulted rock: mountain systems (young alpine ranges most) and
 *     plateau escarpments. Fractured rock gets chasms, tall rifts and shafts.</li>
 *     <li>{@code volcanic}: hot deep crust: a 9 000-block hotspot field plus young ranges. Raises
 *     deep lava levels, lines lava chambers with basalt, blackstone and magma.</li>
 *     <li>{@code abundance}: how cavernous the rock is overall; plains provinces are quiet and a
 *     2 400-block field leaves some regions almost solid.</li>
 *     <li>{@code coast}: 1 at the coastline, 0 more than ~600 blocks from it; sea caves.</li>
 *     <li>{@code wetness}: 0..1 from the climate's moisture and dryness; how often the upper caves
 *     hold a water table.</li>
 * </ul>
 */
final class CaveGeology {
    record Geology(double karst, double fracture, double volcanic, double abundance, double coast, double wetness) {
    }

    private final SimplexNoise lithology;
    private final SimplexNoise hotspots;
    private final SimplexNoise voids;
    private final double abundanceScale;

    CaveGeology(long seed, CaveSettings settings) {
        this.lithology = new SimplexNoise(seed + 101);
        this.hotspots = new SimplexNoise(seed + 102);
        this.voids = new SimplexNoise(seed + 103);
        this.abundanceScale = settings.abundance();
    }

    Geology at(double x, double z, GeographySample g) {
        double lith = fbm(this.lithology, x / 5200.0, z / 5200.0, 2, 0.5);
        double highCore = smoothstep(0.55, 0.9, g.mountain()) * smoothstep(120.0, 260.0, g.mountainHeight());
        double karst = smoothstep(-0.2, 0.4, lith + 0.35 * g.plateau() + 0.2 * g.hilliness() - 0.6 * highCore - 0.2 * g.dryness());
        double escarpment = 4.0 * g.plateau() * (1.0 - g.plateau());
        double fracture = clamp01(0.06 + smoothstep(0.12, 0.7, g.mountain()) * (0.55 + 0.45 * g.alpine()) + 0.55 * escarpment);
        double hot = fbm(this.hotspots, x / 9000.0, z / 9000.0, 2, 0.5) + 0.4 * g.alpine() * g.mountain();
        double volcanic = smoothstep(0.3, 0.75, hot);
        double quiet = smoothstep(-0.65, 0.25, fbm(this.voids, x / 2400.0, z / 2400.0, 2, 0.5));
        double abundance = (0.45 + 0.7 * karst + 0.25 * fracture) * (1.0 - 0.45 * g.plains()) * (0.25 + 0.85 * quiet) * this.abundanceScale;
        double coast = 1.0 - smoothstep(0.0, 0.12, Math.abs(g.continentalness()));
        double wetness = smoothstep(-0.55, 0.45, g.moisture()) * (1.0 - 0.6 * g.dryness());
        return new Geology(karst, fracture, volcanic, abundance, coast, wetness);
    }
}
