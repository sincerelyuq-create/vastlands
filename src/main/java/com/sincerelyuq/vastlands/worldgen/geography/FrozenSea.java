package com.sincerelyuq.vastlands.worldgen.geography;

import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.clamp01;
import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.fbm;
import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.smoothstep;

import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;

/**
 * Fractured ice fields of the polar seas: a pure function from (x, z) and the local ice strength
 * to what covers the sea surface.
 *
 * <p>The sheet is cut into floes by two warped Voronoi networks: large floes (~170 blocks) whose
 * edges are, depending on a noise that varies along them, narrow cracks, wide open leads or
 * closed pressure ridges; and smaller cracks (~60 blocks) that run partly across the floes and end
 * where their own noise says so, so the network branches instead of forming closed polygons
 * everywhere. Towards the warm edge of the ice field (lower strength) leads widen and floes shrink
 * until only drifting pieces remain. Some floes are thick multi-year ice.
 *
 * <p>The edge distance is exact for the jittered cells it considers, so cracks are continuous across
 * cell and chunk borders. Immutable and thread-safe; no game classes.
 */
public final class FrozenSea {
    /** Vanilla's frozen temperature band starts here; no ice field above it. */
    public static final double FREEZE_TEMPERATURE = -0.45;
    /** Sea-level temperature at which the field is a closed sheet. */
    public static final double SOLID_TEMPERATURE = -0.65;
    /** Size of the large floes and of the small crack cells, in blocks. */
    static final double FLOE_CELL = 170.0;
    static final double CRACK_CELL = 58.0;

    /** What covers one column of the sea surface. */
    public enum Cover {
        /** Open water: a crack, a lead or the sea beyond the ice. */
        OPEN_WATER,
        /** One block of ice at the water surface. */
        ICE,
        /** Multi-year floe: packed ice, two blocks thick. */
        THICK_ICE,
        /** Pressure ridge: packed ice rising one block above the sheet. */
        PRESSURE_RIDGE,
        /** Crest of a pressure ridge: two blocks above the sheet. */
        HIGH_RIDGE;

        public boolean isIce() {
            return this != OPEN_WATER;
        }
    }

    private final SimplexNoise warpX, warpZ, jagX, jagZ;
    private final SimplexNoise floes, cracks, leads, crackGate, ridgeCrest, crackWidth, iceEdge;

    public FrozenSea(long seed) {
        long s = SimplexNoise.mix(seed ^ 0x1CEF1E1DL);
        this.warpX = new SimplexNoise(s + 1);
        this.warpZ = new SimplexNoise(s + 2);
        this.jagX = new SimplexNoise(s + 3);
        this.jagZ = new SimplexNoise(s + 4);
        this.floes = new SimplexNoise(s + 5);
        this.cracks = new SimplexNoise(s + 6);
        this.leads = new SimplexNoise(s + 7);
        this.crackGate = new SimplexNoise(s + 8);
        this.ridgeCrest = new SimplexNoise(s + 9);
        this.crackWidth = new SimplexNoise(s + 10);
        this.iceEdge = new SimplexNoise(s + 11);
    }

    /** 0..1 ice strength from the sea-level temperature: 0 at the freezing band's edge, 1 deep in the cold. */
    public static double strength(double seaLevelTemperature) {
        return smoothstep(FREEZE_TEMPERATURE, SOLID_TEMPERATURE, seaLevelTemperature);
    }

    /**
     * Surface cover at (x, z) for an ice field of the given regional strength (0..1, from
     * {@link #strength}); a noise roughens the ice edge before the floes are cut.
     */
    public Cover cover(double x, double z, double strength) {
        if (strength <= 0.0) {
            return Cover.OPEN_WATER;
        }
        // The ice edge follows the temperature only roughly: tongues and bays of a few thousand blocks.
        strength = clamp01(strength + 0.35 * fbm(this.iceEdge, x / 2200.0, z / 2200.0, 2, 0.5) * smoothstep(0.0, 0.3, strength));
        double open = 1.0 - strength;
        // Warped coordinates: floe edges curve, and jag at the scale of a few blocks.
        double wx = x + 40.0 * fbm(this.warpX, x / 300.0, z / 300.0, 2, 0.5) + 5.0 * this.jagX.sample(x / 40.0, z / 40.0);
        double wz = z + 40.0 * fbm(this.warpZ, x / 300.0, z / 300.0, 2, 0.5) + 5.0 * this.jagZ.sample(x / 40.0, z / 40.0);

        double[] cell = new double[2];
        voronoi(this.floes, wx / FLOE_CELL, wz / FLOE_CELL, cell);
        double edge = cell[0] * FLOE_CELL;
        double floeId = cell[1];
        // Character of the large floe edges, varying along them: leads where the ice pulls apart,
        // pressure ridges where it is pushed together, plain cracks in between.
        double lead = fbm(this.leads, x / 650.0, z / 650.0, 2, 0.5);
        double leadOpen = smoothstep(0.05, 0.45, lead);
        double compress = smoothstep(-0.1, -0.4, lead);
        double width = (0.85 + 0.6 * (0.5 + 0.5 * this.crackWidth.sample(x / 90.0, z / 90.0))
                + 13.0 * leadOpen * Math.sqrt(leadOpen)) * (1.0 - compress)
                + 110.0 * Math.pow(open, 2.2);
        if (edge < width) {
            return Cover.OPEN_WATER;
        }
        boolean thick = floeId < 0.1 + 0.2 * strength;

        // Smaller cracks across the floes; each runs only where its gate is open, so cracks end and branch.
        voronoi(this.cracks, wx / CRACK_CELL, wz / CRACK_CELL, cell);
        double crackEdge = cell[0] * CRACK_CELL;
        double gate = fbm(this.crackGate, x / 220.0, z / 220.0, 2, 0.5) - (thick ? 0.2 : 0.0);
        // At least ~0.75 blocks to either side: a diagonal crack stays a connected line of water blocks.
        double crackWidth = (gate > 0.05 ? 0.75 + 0.35 * smoothstep(0.05, 0.4, gate) : 0.0) + 30.0 * Math.pow(open, 2.5);
        if (crackEdge < crackWidth) {
            return Cover.OPEN_WATER;
        }

        // Pressure ridges along the closed floe edges.
        if (compress > 0.3) {
            double ridgeWidth = 1.2 + 1.3 * compress;
            if (edge < ridgeWidth) {
                return edge < 0.6 * ridgeWidth && this.ridgeCrest.sample(x / 25.0, z / 25.0) > 0.2 ? Cover.HIGH_RIDGE : Cover.PRESSURE_RIDGE;
            }
        }
        return thick ? Cover.THICK_ICE : Cover.ICE;
    }

    /**
     * Distance to the nearest Voronoi edge (cell units) and an id in [0, 1) of the nearest cell, for
     * one feature point per unit cell jittered over the whole cell. The nearest point is searched in
     * the 5×5 cells around (x, z), the edges in the 5×5 around it (after Inigo Quilez).
     */
    static void voronoi(SimplexNoise points, double x, double z, double[] out) {
        long ix = (long) Math.floor(x);
        long iz = (long) Math.floor(z);
        double best = Double.POSITIVE_INFINITY;
        long bi = 0;
        long bj = 0;
        double bx = 0.0;
        double bz = 0.0;
        for (long i = ix - 2; i <= ix + 2; i++) {
            for (long j = iz - 2; j <= iz + 2; j++) {
                double px = i + points.hash01(i, j, 0);
                double pz = j + points.hash01(i, j, 1);
                double d2 = (px - x) * (px - x) + (pz - z) * (pz - z);
                if (d2 < best) {
                    best = d2;
                    bi = i;
                    bj = j;
                    bx = px;
                    bz = pz;
                }
            }
        }
        double edge = Double.POSITIVE_INFINITY;
        for (long i = bi - 2; i <= bi + 2; i++) {
            for (long j = bj - 2; j <= bj + 2; j++) {
                if (i == bi && j == bj) {
                    continue;
                }
                double px = i + points.hash01(i, j, 0);
                double pz = j + points.hash01(i, j, 1);
                double nx = px - bx;
                double nz = pz - bz;
                double len = Math.sqrt(nx * nx + nz * nz);
                if (len < 1e-9) {
                    continue;
                }
                // Distance from (x, z) to the bisector of the two points, measured towards the other point.
                double distance = ((0.5 * (px + bx) - x) * nx + (0.5 * (pz + bz) - z) * nz) / len;
                edge = Math.min(edge, distance);
            }
        }
        out[0] = Math.max(0.0, edge);
        out[1] = points.hash01(bi, bj, 2);
    }
}
