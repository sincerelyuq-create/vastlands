package com.sincerelyuq.vastlands.worldgen.caves;

import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.clamp;
import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.clamp01;
import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.fbm;
import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.smoothstep;

import com.sincerelyuq.vastlands.worldgen.geography.GeographySample;
import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;

/**
 * The rock the caves form in, as continuous fields of a column. Vastlands has no rock types, so
 * the caves derive them from the geography (Agent 2.5) and add their own large-scale noises:
 * <ul>
 *     <li>{@code karst} (0..1): soluble, limestone-like rock: a 5 200-block lithology field,
 *     favoured on tablelands and in hill country, wet climates dissolving it faster, absent from
 *     the crystalline cores of high ranges. Karst gets dense multi-level networks, wide phreatic
 *     tubes, chambers, dolines and canyon passages.</li>
 *     <li>{@code fracture} (0..1): jointed, faulted rock: mountain systems (young alpine ranges
 *     most) and plateau escarpments. Fractured rock gets chasms, tall rifts, shafts and canyon
 *     galleries.</li>
 *     <li>{@code volcanic} (0..1): hot deep crust: a 9 000-block hotspot field plus young ranges.
 *     Raises deep lava levels, lines lava chambers with basalt, blackstone and magma.</li>
 *     <li>{@code density} (0..about 1.6): how cavernous the rock is: the cave regime of the region.
 *     Karst and fractured rock are dense, plains provinces quieter, and a 2 600-block field makes
 *     some regions labyrinths of interconnected passages and leaves others almost solid.</li>
 *     <li>{@code scale} (about 0.6..1.8): how large the passages of the region are: a 3 400-block
 *     field of grand galleries against regions of narrow passages, larger in karst.</li>
 *     <li>{@code vertical} (0..1): how vertical the region's caves are: shafts, pits, ramps and
 *     chasms; fractured rock and a 2 000-block field.</li>
 *     <li>{@code coast} (0..1): 1 at the coastline, 0 more than ~600 blocks from it; sea caves.</li>
 *     <li>{@code wetness} (0..1): from the climate's moisture and dryness; how often the upper
 *     caves hold a water table.</li>
 * </ul>
 */
final class CaveGeology {
    record Geology(double karst, double fracture, double volcanic, double density, double scale, double vertical, double coast, double wetness) {
    }

    private final SimplexNoise lithology;
    private final SimplexNoise hotspots;
    private final SimplexNoise voids;
    private final SimplexNoise sizes;
    private final SimplexNoise verticals;
    private final CaveSettings settings;

    CaveGeology(long seed, CaveSettings settings) {
        this.lithology = new SimplexNoise(seed + 101);
        this.hotspots = new SimplexNoise(seed + 102);
        this.voids = new SimplexNoise(seed + 103);
        this.sizes = new SimplexNoise(seed + 104);
        this.verticals = new SimplexNoise(seed + 105);
        this.settings = settings;
    }

    Geology at(double x, double z, GeographySample g) {
        double lith = fbm(this.lithology, x / 5200.0, z / 5200.0, 2, 0.5);
        double highCore = smoothstep(0.55, 0.9, g.mountain()) * smoothstep(120.0, 260.0, g.mountainHeight());
        double karst = smoothstep(-0.2, 0.4, lith + 0.35 * g.plateau() + 0.2 * g.hilliness() - 0.6 * highCore - 0.2 * g.dryness());
        double escarpment = 4.0 * g.plateau() * (1.0 - g.plateau());
        double fracture = clamp01(0.06 + smoothstep(0.12, 0.7, g.mountain()) * (0.55 + 0.45 * g.alpine()) + 0.55 * escarpment);
        double hot = fbm(this.hotspots, x / 9000.0, z / 9000.0, 2, 0.5) + 0.4 * g.alpine() * g.mountain();
        double volcanic = smoothstep(0.3, 0.75, hot);
        // The cave regime: labyrinth regions and nearly solid ones, a contrast scaled by the setting.
        double regime = fbm(this.voids, x / 2600.0, z / 2600.0, 2, 0.5);
        double contrast = this.settings.regionVariation();
        double regional = clamp(1.0 + contrast * (1.25 * smoothstep(-0.55, 0.45, regime) - 0.75), 0.05, 2.0);
        double density = (0.55 + 0.55 * karst + 0.25 * fracture) * (1.0 - 0.15 * g.plains()) * regional;
        double size = fbm(this.sizes, x / 3400.0 + 17.0, z / 3400.0, 2, 0.5);
        double scale = clamp((1.0 + 0.55 * size * Math.max(0.3, contrast)) * (1.0 + 0.2 * karst - 0.12 * g.plains()), 0.55, 1.85);
        double vertical = clamp01(0.15 + 0.6 * fracture + 0.35 * smoothstep(-0.3, 0.6, fbm(this.verticals, x / 2000.0, z / 2000.0, 2, 0.5)));
        double coast = 1.0 - smoothstep(0.0, 0.12, Math.abs(g.continentalness()));
        double wetness = smoothstep(-0.55, 0.45, g.moisture()) * (1.0 - 0.6 * g.dryness());
        return new Geology(karst, fracture, volcanic, density, scale, vertical, coast, wetness);
    }
}
