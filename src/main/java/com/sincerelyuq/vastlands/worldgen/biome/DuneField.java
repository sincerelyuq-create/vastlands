package com.sincerelyuq.vastlands.worldgen.biome;

import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.smoothstep;

import com.sincerelyuq.vastlands.worldgen.geography.GeographySample;
import com.sincerelyuq.vastlands.worldgen.noise.NoiseMath;
import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;

/**
 * The dunes of the dune sea ({@link RegionalBiomes.Region#DUNE_SEA}, Sincerelyuq's Biomes' bone
 * dunes): sand heaped over the ground, a pure function of the column.
 *
 * <ul>
 *     <li>Transverse dunes: crests across the prevailing wind of the geography model, a long gentle
 *     windward slope and a short steep slip face downwind; crests wander (sinuous, sometimes
 *     breaking into crescents where a crest's height dies away), their spacing breathes (about 45
 *     to 65 blocks), each ridge has its own height, and wind ripples run across the windward
 *     slopes.</li>
 *     <li>The wind is the geography's, read once per cell of {@value #CELL} blocks (it turns over
 *     tens of thousands of blocks, so a cell's dunes are straight); neighbouring cells blend over
 *     their borders.</li>
 *     <li>Height: up to {@value #MAX_HEIGHT} blocks, nothing at the region's border (the dunes grow
 *     over the first stretch inside it), less where a region with priority starts, and none in
 *     river valleys.</li>
 * </ul>
 * The block step (water, structures, the biome of each column) is
 * {@code com.sincerelyuq.vastlands.worldgen.compat.DuneSand}. Thread-safe.
 */
public final class DuneField {
    public static final double MAX_HEIGHT = 11.0;
    static final int CELL = 2048;
    private static final double BLEND = 160.0;
    private static final double WAVELENGTH = 54.0;
    /** The crest's place in a dune's period: windward slope before it, slip face after. */
    private static final double CREST = 0.68;

    private final RegionalBiomes regions;
    private final SimplexNoise spacing;
    private final SimplexNoise sinuous;
    private final SimplexNoise crests;
    private final SimplexNoise size;
    private final SimplexNoise ripples;

    DuneField(long seed, RegionalBiomes regions) {
        this.regions = regions;
        this.spacing = new SimplexNoise(seed + 21);
        this.sinuous = new SimplexNoise(seed + 22);
        this.crests = new SimplexNoise(seed + 23);
        this.size = new SimplexNoise(seed + 24);
        this.ripples = new SimplexNoise(seed + 25);
    }

    /**
     * How much of the dunes' height a column takes, 0..1: 0 outside the dune sea and at its border,
     * 1 well inside; regions before it in {@code enabled} (a bit per region) take it back near their
     * borders, river valleys take it all.
     */
    public double amplitude(int x, int z, int enabled) {
        if ((enabled & 1 << RegionalBiomes.Region.DUNE_SEA.ordinal()) == 0) {
            return 0.0;
        }
        double margin = this.regions.margin(RegionalBiomes.Region.DUNE_SEA, x, z);
        if (margin <= 0.0) {
            return 0.0;
        }
        double a = smoothstep(0.0, 0.14, margin);
        for (RegionalBiomes.Region r : RegionalBiomes.Region.VALUES) {
            if (r == RegionalBiomes.Region.DUNE_SEA) {
                break;
            }
            if ((enabled & 1 << r.ordinal()) != 0) {
                a *= 1.0 - smoothstep(-0.12, 0.0, this.regions.margin(r, x, z));
            }
        }
        if (a <= 0.0) {
            return 0.0;
        }
        return a * (1.0 - smoothstep(0.3, 2.0, this.regions.valleyDepth(x, z)));
    }

    /** The dunes' height over the ground at a column, in blocks (before rounding), at full amplitude. */
    public double height(int x, int z) {
        int cx = Math.floorDiv(x, CELL);
        int cz = Math.floorDiv(z, CELL);
        double h = 0.0;
        for (int dz = -1; dz <= 1; dz++) {
            double wz = weight(z, cz + dz);
            if (wz <= 0.0) {
                continue;
            }
            for (int dx = -1; dx <= 1; dx++) {
                double wx = weight(x, cx + dx);
                if (wx > 0.0) {
                    h += wx * wz * this.cellHeight(x, z, cx + dx, cz + dz);
                }
            }
        }
        return h;
    }

    /** The weight of a cell's dunes at a coordinate: 1 inside, blending with the neighbour over its border (sums to 1). */
    private static double weight(int v, int cell) {
        double lo = v - (double) cell * CELL + BLEND;
        double hi = (cell + 1.0) * CELL + BLEND - v;
        return smoothstep(0.0, 2 * BLEND, lo) * smoothstep(0.0, 2 * BLEND, hi);
    }

    /** The dunes of one cell (its wind, coordinates from its centre) at a column. */
    private double cellHeight(int x, int z, int cx, int cz) {
        int ox = cx * CELL + CELL / 2;
        int oz = cz * CELL + CELL / 2;
        GeographySample g = this.regions.column(ox, oz).geography();
        double wx = g.windX();
        double wz = g.windZ();
        double len = Math.hypot(wx, wz);
        if (len < 1e-6) {
            wx = 1.0;
            wz = 0.0;
        } else {
            wx /= len;
            wz /= len;
        }
        // u downwind, v along the crests.
        double u = (x - ox) * wx + (z - oz) * wz;
        double v = -(x - ox) * wz + (z - oz) * wx;
        double phase = u / WAVELENGTH + 1.2 * NoiseMath.fbm(this.spacing, x / 760.0, z / 760.0, 2, 0.5)
                + 0.32 * this.sinuous.sample(v / 170.0, u / 420.0);
        double ridge = Math.floor(phase);
        double t = phase - ridge;
        double profile = t < CREST ? 0.5 - 0.5 * Math.cos(Math.PI * t / CREST) : 0.5 + 0.5 * Math.cos(Math.PI * (t - CREST) / (1.0 - CREST));
        // Each ridge its own height along its length: crests break into crescents where it dies away.
        double crest = smoothstep(-0.55, 0.45, this.crests.sample(v / 130.0 + ridge * 7.31, ridge * 3.17));
        double big = 0.62 + 0.38 * smoothstep(-0.4, 0.4, NoiseMath.fbm(this.size, x / 1500.0, z / 1500.0, 2, 0.5));
        double h = MAX_HEIGHT * big * (0.25 + 0.75 * crest) * profile;
        if (t < CREST) {
            // Wind ripples across the windward slope.
            h += 0.42 * Math.sin(2 * Math.PI * (u / 6.5 + 0.7 * this.ripples.sample(x / 36.0, z / 36.0))) * Math.min(1.0, profile * 3.0);
        }
        return Math.max(0.0, h);
    }
}
