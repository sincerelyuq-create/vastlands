package com.sincerelyuq.vastlands.worldgen.caves;

import com.sincerelyuq.vastlands.worldgen.geography.GeographyModel;
import com.sincerelyuq.vastlands.worldgen.geography.GeographySample;
import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;

/**
 * The Vastlands caves: a pure function from (seed, x, y, z) to cave space and the fluid in it.
 *
 * <p>Layers (see each class):
 * <ol>
 *     <li>{@link CaveGeology}: karst, fractured, volcanic and coastal rock from the geography.</li>
 *     <li>{@link FluidDomains}: dry, flooded and lava domains above and below the aquitard.</li>
 *     <li>Cave space: the {@link CaveNetwork} (multi-level tunnels, chambers, shafts, entrances),
 *     {@link Fractures} (chasms, ravines) and {@link Caverns} (enormous caverns).</li>
 *     <li>Per column ({@link #column}): the pieces reaching the column and the heights where cave
 *     space must stay rock: dams between domains that differ, the aquitard where the zones above
 *     and below it differ, and a skin of rock wherever the cave fluid would meet a different fluid
 *     outside (sea or river water against dry caves, flooded caves against open air).</li>
 *     <li>Per block ({@link #carve}): the pieces' signed distance with wall roughness; negative is
 *     cave space.</li>
 * </ol>
 *
 * <p>Reads the geography, the generated ground surface and the placed river and lake water
 * through narrow interfaces, never chunks; deterministic, thread-safe (every cache is per thread
 * or keyed by position and pure).
 */
public final class CaveModel {
    /** Margin of rock between cave fluid and a different fluid outside (blocks). */
    static final int CLEARANCE = 4;
    /** The same next to river and lake water, deeper: beds and banks stay intact. */
    static final int RIVER_CLEARANCE = 7;
    /** No fluid level of any domain lies above this Y. */
    public static final int MAX_LEVEL = 160;

    /** The generated ground: surface height and the geography of a column. */
    public interface Ground extends CaveNetwork.Ground {
        /** Geography of the column (exact or from its 4-block grid column). */
        GeographySample geography(int x, int z);

        /** The terrain surface before river channels were carved (for roofs over gorges). */
        double terrain(int x, int z);
    }

    /** River and lake water placed above the ground (Agent 3's hydrology). */
    public interface Waters {
        /** False if no river or lake water lies in the box (inclusive). */
        boolean mayHoldWater(int minX, int minZ, int maxX, int maxZ);

        /** Top of the river or lake water in a column, or {@link Integer#MIN_VALUE}. */
        int placedTop(int x, int z);
    }

    private final GeographyModel geography;
    private final Ground ground;
    private final Waters waters;
    private final CaveSettings settings;
    private final int minY;
    private final int seaTop;
    private final long seed;
    final CaveGeology geology;
    final FluidDomains domains;
    final CaveNetwork network;
    final Fractures fractures;
    final Caverns caverns;
    final RiverTunnels tunnels;
    private final Noise3 roughness;
    private final Noise3 detail;
    private final Noise3 bridges;
    private final SimplexNoise damNoise;
    private final SimplexNoise bandNoise;
    private final ThreadLocal<Scratch> scratch = ThreadLocal.withInitial(Scratch::new);

    private static final class Scratch {
        final FluidDomains.Hit upper = new FluidDomains.Hit();
        final FluidDomains.Hit deep = new FluidDomains.Hit();
        final GridWater water = new GridWater();
        final long[] riverTiles = new long[256];
        final byte[] riverFlags = new byte[256];
    }

    /** Whether river or lake water lies within reach of a 16-block tile (cached per thread). */
    private boolean nearRivers(Scratch scratch, int x, int z) {
        int tx = x >> 4;
        int tz = z >> 4;
        long key = (long) tx << 32 | (tz & 0xFFFFFFFFL);
        int slot = (tx & 15) << 4 | (tz & 15);
        if (scratch.riverFlags[slot] != 0 && scratch.riverTiles[slot] == key) {
            return scratch.riverFlags[slot] == 2;
        }
        int reach = (int) RiverTunnels.REACH;
        boolean near = this.waters.mayHoldWater(tx * 16 - reach, tz * 16 - reach, tx * 16 + 15 + reach, tz * 16 + 15 + reach);
        scratch.riverTiles[slot] = key;
        scratch.riverFlags[slot] = (byte) (near ? 2 : 1);
        return near;
    }

    public CaveModel(long seed, GeographyModel geography, Ground ground, Waters waters, RiverTunnels.Rivers rivers, CaveSettings settings,
                     int minY, int seaLevel) {
        this.geography = geography;
        this.ground = ground;
        this.waters = waters;
        this.settings = settings;
        this.minY = minY;
        this.seaTop = seaLevel - 1;
        long s = SimplexNoise.mix(seed ^ 0x43415645L);
        this.seed = s;
        this.geology = new CaveGeology(s, settings);
        this.domains = new FluidDomains(s, geography, this.geology, settings, this.seaTop);
        this.network = new CaveNetwork(s, geography, this.geology, this.domains, settings, ground, this.seaTop);
        this.fractures = new Fractures(s, settings);
        this.caverns = new Caverns(s, geography, this.geology, this.domains, ground, settings);
        this.tunnels = new RiverTunnels(s, geography, rivers, settings);
        this.roughness = new Noise3(s + 1);
        this.detail = new Noise3(s + 2);
        this.bridges = new Noise3(s + 3);
        this.damNoise = new SimplexNoise(s + 4);
        this.bandNoise = new SimplexNoise(s + 5);
    }

    public int seaTop() {
        return this.seaTop;
    }

    public int minY() {
        return this.minY;
    }

    public CaveSettings settings() {
        return this.settings;
    }

    /** Fills a column (any (x, z); exact model values). */
    void column(int x, int z, CaveColumn c) {
        Scratch scratch = this.scratch.get();
        c.x = x;
        c.z = z;
        c.valid = true;
        c.count = 0;
        c.solids = 0;
        c.notchY = Integer.MIN_VALUE;
        c.notchFluid = CaveFluid.AIR;
        c.surface = this.ground.surface(x, z);
        // The ground and the river water around the column: the corners of every 4-block cell the
        // column and its neighbours lie in (the density interpolates between them).
        int x0 = x & ~3;
        int z0 = z & ~3;
        double low = c.surface;
        int riverTop = Integer.MIN_VALUE;
        boolean rivers = this.waters.mayHoldWater(x0 - 4, z0 - 4, x0 + 4, z0 + 4);
        for (int dz = -4; dz <= 4; dz += 4) {
            for (int dx = -4; dx <= 4; dx += 4) {
                low = Math.min(low, this.ground.surface(x0 + dx, z0 + dz));
                if (rivers) {
                    riverTop = Math.max(riverTop, scratch.water.top(this.waters, x0 + dx, z0 + dz));
                }
            }
        }
        c.low = low;
        c.riverTop = riverTop;
        c.coverTop = (int) Math.floor(low) - (int) CaveNetwork.COVER;
        c.roof.active = false;
        if (this.nearRivers(scratch, x, z)) {
            this.tunnels.roof(x, z, c.surface, this.ground.terrain(x, z), c.roof);
        }
        GeographySample g = this.ground.geography(x, z);
        CaveGeology.Geology geo = this.geology.at(x, z, g);
        c.volcanic = geo.volcanic();

        // Fluid domains and the aquitard.
        double aquitard = this.domains.aquitard(x, z);
        c.bandLo = (int) Math.floor(aquitard - FluidDomains.AQUITARD_HALF + 1.5 * this.bandNoise.sample(x / 23.0, z / 23.0));
        c.bandHi = (int) Math.floor(aquitard + FluidDomains.AQUITARD_HALF + 1.5 * this.bandNoise.sample(x / 23.0 + 50.0, z / 23.0));
        this.domains.locate(x, z, false, scratch.upper);
        this.domains.locate(x, z, true, scratch.deep);
        c.upper = scratch.upper.owner;
        c.deep = scratch.deep.owner;
        CaveFluid above = c.upper.at(c.bandHi + 1);
        CaveFluid below = c.deep.at(c.bandLo - 1);
        c.bandOpen = above == below && (above == CaveFluid.AIR || c.upper.level >= c.bandHi + 1 && c.deep.level >= c.bandLo - 1);
        if (!c.bandOpen) {
            c.addSolid(c.bandLo, c.bandHi);
        }
        int top = (int) Math.ceil(c.surface) + 8;
        this.dams(c, scratch.upper, c.bandHi + 1, top);
        this.dams(c, scratch.deep, this.minY, c.bandLo - 1);

        // Clearance: where cave fluid would meet a different fluid outside, keep rock.
        int outsideTop = Math.max(low < this.seaTop + 1 ? this.seaTop : Integer.MIN_VALUE, riverTop);
        int margin = riverTop != Integer.MIN_VALUE ? RIVER_CLEARANCE : CLEARANCE;
        int from = (int) Math.floor(low) - margin + 1;
        int runStart = Integer.MIN_VALUE;
        for (int y = from; y <= top; y++) {
            CaveFluid outside = y <= outsideTop ? CaveFluid.WATER : CaveFluid.AIR;
            boolean mismatch = c.domain(y).at(y) != outside;
            if (mismatch && runStart == Integer.MIN_VALUE) {
                runStart = y;
            } else if (!mismatch && runStart != Integer.MIN_VALUE) {
                c.addSolid(runStart, y - 1);
                runStart = Integer.MIN_VALUE;
            }
        }
        if (runStart != Integer.MIN_VALUE) {
            c.addSolid(runStart, top);
        }

        // Cave space.
        this.network.gather(x, z, piece -> {
            if (c.count == CaveColumn.MAX_PIECES) {
                return;
            }
            int i = c.count++;
            c.pieces[i] = piece;
            if (piece.type == CavePrimitive.CHAMBER) {
                // Distance in the chamber's frame: stretched along its axis, lobed by noise.
                double ex = x - piece.ax;
                double ez = z - piece.az;
                double cos = Math.cos(piece.angle);
                double sin = Math.sin(piece.angle);
                double u = (ex * cos + ez * sin) / (1.0 + piece.elongation);
                double v = (-ex * sin + ez * cos) / (1.0 - piece.elongation);
                double lobes = 0.8 + 0.2 * (1.0 + this.damNoise.sample(x / (0.6 * piece.ra + 6.0) + piece.seed % 89, z / (0.6 * piece.ra + 6.0)));
                c.dh2[i] = (u * u + v * v) / (lobes * lobes);
                c.rubble[i] = 0.1 * piece.rb * this.damNoise.sample(x / 9.0 + piece.seed % 61, z / 9.0);
            }
        });
        this.fractures.upper(x, z, c.surface, c.bandHi + 6, geo, this.seaTop, c.upperCrack);
        this.fractures.deep(x, z, c.bandLo - 6, geo, c.deepCrack);
        this.caverns.section(x, z, c.cavern);

        int bottom = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int i = 0; i < c.count; i++) {
            bottom = Math.min(bottom, (int) Math.floor(c.pieces[i].minY));
            high = Math.max(high, (int) Math.ceil(c.pieces[i].maxY));
        }
        if (c.upperCrack.open()) {
            bottom = Math.min(bottom, (int) Math.floor(c.upperCrack.low()));
            high = Math.max(high, (int) Math.ceil(c.upperCrack.high()));
        }
        if (c.deepCrack.open()) {
            bottom = Math.min(bottom, (int) Math.floor(c.deepCrack.low()));
            high = Math.max(high, (int) Math.ceil(c.deepCrack.high()));
        }
        if (c.cavern.present()) {
            bottom = Math.min(bottom, (int) Math.floor(c.cavern.low()));
            high = Math.max(high, (int) Math.ceil(c.cavern.high()));
        }
        c.bottom = Math.max(bottom - 3, this.minY + 7);
        c.top = Math.min(high + 3, top);
    }

    /**
     * Dams between the column's domain and every neighbouring domain within reach of the edge:
     * rock over the heights where their fluids differ, two blocks above the higher level, and a
     * notch every so often along the edge where both hold water (or both lava) at different levels.
     */
    private void dams(CaveColumn c, FluidDomains.Hit hit, int zoneLo, int zoneHi) {
        if (zoneLo > zoneHi || hit.count == 0) {
            return;
        }
        double half = 2.5 + 1.25 * (1.0 + this.damNoise.sample(c.x / 24.0, c.z / 24.0));
        FluidDomains.Domain a = hit.owner;
        for (int k = 0; k < hit.count; k++) {
            if (hit.edge[k] >= half) {
                continue;
            }
            FluidDomains.Domain b = hit.neighbours[k];
            int high = Math.max(a.level, b.level);
            if (high == FluidDomains.NO_LEVEL) {
                continue;
            }
            int lowLevel = Math.min(a.level, b.level);
            int from = a.fluid != b.fluid ? zoneLo : lowLevel + 1;
            int crest = high + 2 + (int) Math.floor(1.0 + this.damNoise.sample(c.x / 11.0 + 30.0, c.z / 11.0));
            if (a.fluid == b.fluid && high - lowLevel >= 3 && this.notched(a, b, hit.along[k])) {
                crest = high - 1;
                c.notchY = high;
                c.notchFluid = a.fluid;
            }
            c.addSolid(Math.max(from, zoneLo), Math.min(crest, zoneHi));
        }
    }

    /** Spillway windows: 3 blocks every 20 along an edge, a share of them open. */
    private boolean notched(FluidDomains.Domain a, FluidDomains.Domain b, double along) {
        long pair = Math.min(a.key, b.key) * 0x9E3779B97F4A7C15L ^ Math.max(a.key, b.key);
        long window = (long) Math.floor(along / 20.0);
        long h = SimplexNoise.mix(this.seed ^ pair ^ window * 0xC2B2AE3D27D4EB4FL);
        if (FluidDomains.unit(h, 0) >= this.settings.spillways()) {
            return false;
        }
        double centre = (window + 0.25 + 0.5 * FluidDomains.unit(h, 1)) * 20.0;
        return Math.abs(along - centre) < 1.6;
    }

    /**
     * Signed distance to cave rock at a height (negative: open cave space). Positive where the
     * column must stay solid.
     */
    double carve(CaveColumn c, int y) {
        if (y < c.bottom || y > c.top) {
            return Double.POSITIVE_INFINITY;
        }
        double d = c.sdf(y);
        double rough = c.roughness;
        if (d > rough * 1.45) {
            return d;
        }
        // Wall roughness: its wavelength grows with its amplitude, so walls bulge and recede
        // instead of shedding detached pockets; a small fine octave for texture.
        double scale = 3.0 + 3.5 * rough;
        double n = this.roughness.sample(c.x / scale, y / (0.7 * scale), c.z / scale);
        d += rough * n + 0.3 * this.detail.sample(c.x / 2.7, y / 2.3, c.z / 2.7);
        if (d >= 0.0) {
            return d;
        }
        // Chockstones and natural bridges wedged in chasms.
        if (rough < 0.9 && this.bridges.sample(c.x / 8.0, y / 3.0, c.z / 8.0) > 0.55) {
            return 1.0;
        }
        return c.solid(y) ? 1.0 : d;
    }

    /** Domain description of a column, for diagnostics. */
    String describe(CaveColumn c, CaveGeology.Geology geo) {
        return String.format(java.util.Locale.ROOT, "upper %s, deep %s, aquitard %d..%d %s, karst %.2f fracture %.2f volcanic %.2f abundance %.2f",
                c.upper, c.deep, c.bandLo, c.bandHi, c.bandOpen ? "open" : "closed", geo.karst(), geo.fracture(), geo.volcanic(), geo.abundance());
    }

    CaveGeology.Geology geologyAt(int x, int z) {
        return this.geology.at(x, z, this.ground.geography(x, z));
    }

    /** River and lake water tops at 4-block grid columns, per thread. */
    private static final class GridWater {
        private static final int SIZE = 4096;
        private final long[] keys = new long[SIZE];
        private final int[] tops = new int[SIZE];
        private final boolean[] filled = new boolean[SIZE];

        int top(Waters waters, int x, int z) {
            long key = (long) x << 32 | (z & 0xFFFFFFFFL);
            int slot = ((x >> 2) & 63) << 6 | ((z >> 2) & 63);
            if (this.filled[slot] && this.keys[slot] == key) {
                return this.tops[slot];
            }
            int top = waters.placedTop(x, z);
            this.keys[slot] = key;
            this.tops[slot] = top;
            this.filled[slot] = true;
            return top;
        }
    }
}
