package com.sincerelyuq.vastlands.worldgen.caves;

import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;

import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.smoothstep;

/**
 * Everything the caves need to decide the blocks of one column, computed once per column (see
 * {@link CaveModel#column}): the ground, the fluid domains above and below the aquitard, the
 * heights where caves must stay solid (dams between domains, a closed aquitard, rock between
 * cave fluid and a different fluid outside), and the cave pieces whose bounds contain the column.
 * {@link #sdf} then evaluates any height with a few float operations per piece.
 *
 * <p>Mutable and reused by the per-thread column cache; never shared between threads.
 */
final class CaveColumn {
    /** At most 64 pieces: their indices fit the bit masks of {@link #slabs}. */
    static final int MAX_PIECES = 64;
    /** Slabs of 16 blocks from Y -512 up: enough for any world height Vastlands supports. */
    private static final int SLAB_OFFSET = 512;
    private static final int SLABS = 128;
    static final int MAX_SOLIDS = 24;

    int x;
    int z;
    boolean valid;
    /** Generated ground surface (terrain and river channels) and the lowest ground within one block. */
    double surface;
    double low;
    /** Ordinary caves stay at or below this Y; entrances may cut above it. */
    int coverTop;
    /** No cave space below {@link #bottom} or above {@link #top}. */
    int bottom;
    int top;
    FluidDomains.Domain upper;
    FluidDomains.Domain deep;
    int bandLo;
    int bandHi;
    boolean bandOpen;
    /** Water top of river or lake water within one block, or {@link Integer#MIN_VALUE}. */
    int riverTop;
    /** Volcanic strength of the rock (see {@link CaveGeology}). */
    double volcanic;
    final int[] solidLo = new int[MAX_SOLIDS];
    final int[] solidHi = new int[MAX_SOLIDS];
    int solids;
    final CavePrimitive[] pieces = new CavePrimitive[MAX_PIECES];
    /** For each 16-block slab, the pieces whose height range meets it (bit i: piece i). */
    final long[] slabs = new long[SLABS];
    /**
     * Chambers: squared horizontal distance to the centre, the rubble of the floor, how far the
     * ceiling's breakdown hangs below the dome and the height offset of the terrace edges here.
     */
    final double[] dh2 = new double[MAX_PIECES];
    final double[] rubble = new double[MAX_PIECES];
    final double[] breakdown = new double[MAX_PIECES];
    final double[] phase = new double[MAX_PIECES];
    /** Chambers: the terrace band last evaluated and its wall factor (heights come in runs). */
    final long[] terraceBand = new long[MAX_PIECES];
    final double[] terraceFactor = new double[MAX_PIECES];
    /** Noise for the ledges of chamber walls (set by the model). */
    SimplexNoise ledges;
    int count;
    final Fractures.Crack upperCrack = new Fractures.Crack();
    final Fractures.Crack deepCrack = new Fractures.Crack();
    /** Sections of the upper and deep zone's megacaves at this column. */
    final Megacaves.Section upperCave = new Megacaves.Section();
    final Megacaves.Section deepCave = new Megacaves.Section();
    /** True if a piece of an underground river reaches the column (its dams are notched). */
    boolean riverPiece;
    /** Spillway: a dam notched at this water (or lava) top, else {@link Integer#MIN_VALUE}. */
    int notchY;
    CaveFluid notchFluid;
    /** Roughness of the piece nearest to the last {@link #sdf} height. */
    double roughness;
    /** Rock restored over a river gorge (a natural tunnel), if any. */
    final RiverTunnels.Roof roof = new RiverTunnels.Roof();

    /** True if the roof over a river gorge makes this block rock (above the carved ground). */
    boolean roofed(int y) {
        RiverTunnels.Roof r = this.roof;
        return r.active && y >= this.surface && y < r.surface && !(r.vault && y <= r.ceiling);
    }

    /** The ground surface as generated, roofs over gorges included. */
    double groundSurface() {
        return this.roof.active ? Math.max(this.surface, this.roof.surface) : this.surface;
    }

    /** Registers piece {@code i} in the slabs its height range meets. */
    void index(int i) {
        CavePrimitive p = this.pieces[i];
        int lo = Math.max(0, ((int) Math.floor(p.minY) + SLAB_OFFSET) >> 4);
        int hi = Math.min(SLABS - 1, ((int) Math.ceil(p.maxY) + SLAB_OFFSET) >> 4);
        for (int k = lo; k <= hi; k++) {
            this.slabs[k] |= 1L << i;
        }
    }

    void addSolid(int lo, int hi) {
        if (lo > hi) {
            return;
        }
        // Merge with an overlapping or touching interval.
        for (int i = 0; i < this.solids; i++) {
            if (lo <= this.solidHi[i] + 1 && hi >= this.solidLo[i] - 1) {
                this.solidLo[i] = Math.min(this.solidLo[i], lo);
                this.solidHi[i] = Math.max(this.solidHi[i], hi);
                return;
            }
        }
        if (this.solids < MAX_SOLIDS) {
            this.solidLo[this.solids] = lo;
            this.solidHi[this.solids] = hi;
            this.solids++;
        } else {
            // Out of slots: grow the last one (conservative: more rock).
            this.solidLo[MAX_SOLIDS - 1] = Math.min(this.solidLo[MAX_SOLIDS - 1], lo);
            this.solidHi[MAX_SOLIDS - 1] = Math.max(this.solidHi[MAX_SOLIDS - 1], hi);
        }
    }

    boolean solid(int y) {
        for (int i = 0; i < this.solids; i++) {
            if (y >= this.solidLo[i] && y <= this.solidHi[i]) {
                return true;
            }
        }
        return false;
    }

    /** The fluid that fills open cave space at a height (below the surface). */
    FluidDomains.Domain domain(int y) {
        return y >= this.bandLo ? this.upper : this.deep;
    }

    /**
     * Signed distance (blocks, negative inside) to the nearest cave wall at a height, from the
     * analytic pieces alone (no wall noise, no solid intervals); sets {@link #roughness}.
     */
    double sdf(int y) {
        double best = Double.POSITIVE_INFINITY;
        double rough = 1.0;
        boolean covered = y <= this.coverTop;
        double fade = Math.max(0.0, y - (this.coverTop - 4.0));
        int slab = (y + SLAB_OFFSET) >> 4;
        long mask = slab >= 0 && slab < SLABS ? this.slabs[slab] : 0L;
        while (mask != 0L) {
            int i = Long.numberOfTrailingZeros(mask);
            mask &= mask - 1;
            CavePrimitive p = this.pieces[i];
            if (y < p.minY || y > p.maxY || !covered && !p.ignoresCover) {
                continue;
            }
            double d = p.type == CavePrimitive.CHAMBER ? this.chamber(p, i, y) : this.segment(p, y);
            if (!p.ignoresCover) {
                d += fade;
            }
            if (d < best) {
                best = d;
                rough = p.roughness;
            }
        }
        if (this.upperCrack.open() && y >= this.upperCrack.low() && y <= this.upperCrack.high() && (covered || this.upperCrack.ravine)) {
            double d = this.upperCrack.sdf(y);
            if (d < best) {
                best = d;
                rough = 0.7;
            }
        }
        if (this.deepCrack.open() && y >= this.deepCrack.low() && y <= this.deepCrack.high()) {
            double d = this.deepCrack.sdf(y);
            if (d < best) {
                best = d;
                rough = 0.8;
            }
        }
        if (covered) {
            if (this.upperCave.present() && y >= this.upperCave.low() && y <= this.upperCave.high()) {
                double d = this.upperCave.sdf(y) + fade;
                if (d < best) {
                    best = d;
                    rough = 2.8 + 0.008 * this.upperCave.cave.radius;
                }
            }
            if (this.deepCave.present() && y >= this.deepCave.low() && y <= this.deepCave.high()) {
                double d = this.deepCave.sdf(y) + fade;
                if (d < best) {
                    best = d;
                    rough = 2.8 + 0.008 * this.deepCave.cave.radius;
                }
            }
        }
        this.roughness = rough;
        return best;
    }

    private double segment(CavePrimitive p, int y) {
        double s = p.shape;
        double ay = p.ay / s;
        double by = p.by / s;
        double py = y / s;
        double dx = p.bx - p.ax;
        double dy = by - ay;
        double dz = p.bz - p.az;
        double len2 = dx * dx + dy * dy + dz * dz;
        double t = len2 > 1e-9 ? ((this.x - p.ax) * dx + (py - ay) * dy + (this.z - p.az) * dz) / len2 : 0.0;
        t = t < 0.0 ? 0.0 : (t > 1.0 ? 1.0 : t);
        double qx = p.ax + t * dx;
        double qz = p.az + t * dz;
        double ex = this.x - qx;
        double ey = py - (ay + t * dy);
        double ez = this.z - qz;
        double r = p.ra + t * (p.rb - p.ra);
        if (p.funnelTop == p.funnelTop) {
            r *= 1.0 + 2.0 * smoothstep(p.funnelTop - 14.0, p.funnelTop, y);
        }
        double d = Math.sqrt(ex * ex + ey * ey + ez * ez) - r;
        if (p.floorCut < 1.0 || p.slotDepth > 0.0) {
            double cy = p.ay + t * (p.by - p.ay);
            if (p.floorCut < 1.0) {
                d = Math.max(d, cy - r * s * p.floorCut - y);
            }
            if (p.slotDepth > 0.0) {
                double slotTop = cy - 0.3 * r * s;
                double slotBottom = cy - r * s - p.slotDepth;
                if (y >= slotBottom && y <= slotTop) {
                    double hd = Math.sqrt(ex * ex + ez * ez);
                    d = Math.min(d, hd - p.slotWidth * (0.35 + 0.65 * (y - slotBottom) / (slotTop - slotBottom)));
                }
            }
        }
        return d;
    }

    private double chamber(CavePrimitive p, int i, int y) {
        // Far outside the widest the terraces make it: solid whatever the wall noise (see carve).
        double far = 1.07 * p.ra + 1.45 * p.roughness + 0.5;
        if (this.dh2[i] > far * far) {
            return Math.sqrt(this.dh2[i]) - 1.065 * p.ra;
        }
        // Terraces: bands about 5 blocks high whose edges wander up and down along the wall and
        // whose step differs around it, so ledges come and go instead of forming rings.
        long band = (long) Math.floor((y + this.phase[i] + 1000.0) / 5.0);
        if (this.terraceBand[i] != band) {
            double wobble = this.ledges.sample(this.x / 19.0 + band * 3.7, this.z / 19.0 - band * 1.9);
            this.terraceBand[i] = band;
            this.terraceFactor[i] = 1.0 + 0.05 * (FluidDomains.unit(p.seed ^ band, 5) - 0.5) + 0.04 * wobble;
        }
        double rh = p.ra * this.terraceFactor[i];
        double q2 = this.dh2[i] / (rh * rh);
        if (q2 >= 1.0) {
            return (Math.sqrt(q2) - 1.0) * rh;
        }
        double s = Math.sqrt(1.0 - q2);
        double ceiling = p.ay + p.rb * s - this.breakdown[i] * s;
        double floor = p.ay - p.rb * p.shape * Math.sqrt(s) + this.rubble[i];
        return Math.max(floor - y, y - ceiling);
    }
}
