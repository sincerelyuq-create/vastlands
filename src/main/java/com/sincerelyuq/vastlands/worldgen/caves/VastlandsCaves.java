package com.sincerelyuq.vastlands.worldgen.caves;

import com.sincerelyuq.vastlands.worldgen.geography.GeographySample;
import com.sincerelyuq.vastlands.worldgen.hydrology.VastlandsHydrology;
import com.sincerelyuq.vastlands.worldgen.hydrology.WaterColumn;
import com.sincerelyuq.vastlands.worldgen.system.CaveSystem;
import com.sincerelyuq.vastlands.worldgen.system.DensityRange;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSetup;
import com.sincerelyuq.vastlands.worldgen.system.HydrologySystem;
import com.sincerelyuq.vastlands.worldgen.system.TerrainSystem;
import java.util.Locale;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;

/**
 * The Vastlands cave system: a {@link CaveModel} turned into router density and the fluids of
 * noise filling.
 *
 * <ul>
 *     <li>Density: the final density, lowered (open) wherever the model carves cave space. The
 *     caves never open space above the ground except at their entrances, and only lower the
 *     density, so the landscape, rivers and seas are untouched.</li>
 *     <li>Fluids: {@link #fluidPicker}: above the ground vanilla's sea (water up to sea level);
 *     below it the cave domain's fluid: water, lava or air.</li>
 * </ul>
 * Columns are cached per thread (a 32 × 32 window: the columns of a chunk never evict each
 * other); a hit is exactly what the model computes.
 */
public final class VastlandsCaves implements CaveSystem {
    /** Density inside open cave space is at most this (vanilla's caves reach about -0.4). */
    private static final double OPEN = -0.45;
    private static final double DENSITY_PER_BLOCK = 0.2;
    /** Density of rock restored over a river gorge. */
    private static final double ROOF = 0.3;

    private final CaveModel model;
    private final int minY;
    private final int seaTop;
    private final ThreadLocal<ColumnCache> columns = ThreadLocal.withInitial(ColumnCache::new);
    private final int seaLevel;
    /** Built on first use: block states need a bootstrapped game, previews construct the system without one. */
    private volatile Statuses statuses;

    /** The fluid statuses the picker hands out, one per fluid and level (no allocation per block). */
    private record Statuses(Aquifer.FluidStatus air, Aquifer.FluidStatus sea, Aquifer.FluidStatus[] water, Aquifer.FluidStatus[] lava) {
    }

    public VastlandsCaves(GenerationSetup setup, TerrainSystem terrain, HydrologySystem hydrology) {
        this(setup, terrain, hydrology, setup.luminousCaves() ? CaveSettings.DEFAULT.withLuminousCaves(1.0) : CaveSettings.DEFAULT);
    }

    public VastlandsCaves(GenerationSetup setup, TerrainSystem terrain, HydrologySystem hydrology, CaveSettings settings) {
        this.minY = setup.minY();
        this.seaTop = setup.seaLevel() - 1;
        CaveModel.Ground ground = new CaveModel.Ground() {
            @Override
            public double surface(double x, double z) {
                return hydrology.surfaceHeight((int) Math.floor(x), (int) Math.floor(z));
            }

            @Override
            public double estimate(double x, double z) {
                return terrain.sample(x, z).height();
            }

            @Override
            public GeographySample geography(int x, int z) {
                return terrain.column(x & ~3, z & ~3).geography();
            }

            @Override
            public double terrain(int x, int z) {
                return terrain.surfaceHeight(x, z);
            }
        };
        RiverTunnels.Rivers rivers = new RiverTunnels.Rivers() {
            @Override
            public void channels(int x, int z, double reach, com.sincerelyuq.vastlands.worldgen.hydrology.HydrologyModel.ChannelVisitor visitor) {
                if (hydrology instanceof VastlandsHydrology vastlands) {
                    vastlands.model().channels(x, z, reach, visitor);
                }
            }

            @Override
            public java.util.List<double[]> falls(int x, int z) {
                return hydrology instanceof VastlandsHydrology vastlands ? vastlands.model().falls(x, z) : java.util.List.of();
            }
        };
        CaveModel.Waters waters = new CaveModel.Waters() {
            @Override
            public boolean mayHoldWater(int minX, int minZ, int maxX, int maxZ) {
                return hydrology.mayHoldWater(minX, minZ, maxX, maxZ);
            }

            @Override
            public int placedTop(int x, int z) {
                WaterColumn column = hydrology.water(x, z);
                return column.placed() ? column.waterTop() : Integer.MIN_VALUE;
            }
        };
        long seed = setup.random("caves").fromHashOf("cave_model").nextLong();
        this.model = new CaveModel(seed, terrain.geography(), ground, waters, rivers, settings, setup.minY(), setup.seaLevel());
        this.seaLevel = setup.seaLevel();
    }

    private Statuses statuses() {
        Statuses statuses = this.statuses;
        if (statuses == null) {
            BlockState waterState = Blocks.WATER.defaultBlockState();
            BlockState lavaState = Blocks.LAVA.defaultBlockState();
            int levels = CaveModel.MAX_LEVEL - this.minY + 2;
            Aquifer.FluidStatus[] water = new Aquifer.FluidStatus[levels];
            Aquifer.FluidStatus[] lava = new Aquifer.FluidStatus[levels];
            for (int i = 0; i < levels; i++) {
                // FluidStatus.at(y) is the fluid for y < fluidLevel: a top block at level L needs L + 1.
                water[i] = new Aquifer.FluidStatus(this.minY + i + 1, waterState);
                lava[i] = new Aquifer.FluidStatus(this.minY + i + 1, lavaState);
            }
            statuses = new Statuses(new Aquifer.FluidStatus(Integer.MIN_VALUE, Blocks.AIR.defaultBlockState()),
                    new Aquifer.FluidStatus(this.seaLevel, waterState), water, lava);
            this.statuses = statuses;
        }
        return statuses;
    }

    @Override
    public String id() {
        return "vastlands";
    }

    public CaveModel model() {
        return this.model;
    }

    /** The cached column (per thread). */
    CaveColumn column(int x, int z) {
        return this.columns.get().get(this.model, x, z);
    }

    @Override
    public double apply(int blockX, int blockY, int blockZ, double density) {
        CaveColumn column = this.column(blockX, blockZ);
        if (column.roofed(blockY)) {
            return Math.max(density, ROOF);
        }
        if (density <= OPEN) {
            return density;
        }
        double d = this.model.carve(column, blockY);
        if (d >= 0.0) {
            return density;
        }
        return Math.min(density, Math.max(OPEN, d * DENSITY_PER_BLOCK));
    }

    @Override
    public DensityRange outputRange(DensityRange input) {
        return new DensityRange(Math.min(input.min(), OPEN), Math.max(input.max(), ROOF));
    }

    @Override
    public double surfaceHeight(int blockX, int blockZ, double ground) {
        CaveColumn column = this.column(blockX, blockZ);
        return column.roof.active ? Math.max(ground, column.roof.surface) : ground;
    }

    /** Grottoes behind falls near a column: face x, z, floor and ceiling (previews and tests). */
    public java.util.List<int[]> grottoesNear(int x, int z) {
        return this.model.tunnels.grottoesNear(x, z);
    }

    /** True if the block lies in a grotto behind a waterfall. */
    public boolean isGrotto(int x, int y, int z) {
        CaveColumn column = this.column(x, z);
        return y >= column.roof.grottoLo && y <= column.roof.grottoHi;
    }

    /**
     * Entrances of the cave network in the 64-block cells of a square, for previews and tests:
     * {inner x, y, z, mouth x, y, z, radius, 0 for an adit or 1 for a sinkhole}.
     */
    public java.util.List<double[]> entrances(int minX, int minZ, int maxX, int maxZ) {
        java.util.List<double[]> out = new java.util.ArrayList<>();
        for (long i = Math.floorDiv(minX, (int) CaveNetwork.CELL); i <= Math.floorDiv(maxX, (int) CaveNetwork.CELL); i++) {
            for (long j = Math.floorDiv(minZ, (int) CaveNetwork.CELL); j <= Math.floorDiv(maxZ, (int) CaveNetwork.CELL); j++) {
                for (CavePrimitive p : this.model.network.cell(i, j).primitives) {
                    if (p.ignoresCover) {
                        boolean sinkhole = p.funnelTop == p.funnelTop;
                        out.add(new double[] {p.ax, p.ay, p.az, p.bx, p.by, p.bz, p.ra, sinkhole ? 1 : 0});
                    }
                }
            }
        }
        return out;
    }

    /** The megacaves (both zones) whose bounds meet a square: for natural wonders, previews and tests. */
    public java.util.List<MegacaveInfo> megacaves(int minX, int minZ, int maxX, int maxZ) {
        return this.model.megacaves.near(minX, minZ, maxX, maxZ).stream().map(MegacaveInfo::of).toList();
    }

    /** Middle points (x, y, z) of the underground river galleries over a square (previews and tests). */
    public java.util.List<double[]> riverGalleries(int minX, int minZ, int maxX, int maxZ) {
        java.util.List<double[]> out = new java.util.ArrayList<>();
        for (long i = Math.floorDiv(minX, (int) Galleries.CELL); i <= Math.floorDiv(maxX, (int) Galleries.CELL); i++) {
            for (long j = Math.floorDiv(minZ, (int) Galleries.CELL); j <= Math.floorDiv(maxZ, (int) Galleries.CELL); j++) {
                for (CavePrimitive p : this.model.galleries.cell(i, j).primitives) {
                    if (p.river && p.type == CavePrimitive.SEGMENT) {
                        out.add(new double[] {0.5 * (p.ax + p.bx), 0.5 * (p.ay + p.by), 0.5 * (p.az + p.bz)});
                    }
                }
            }
        }
        return out;
    }

    /**
     * Counts of the cave pieces built in the cells over a square (diagnostics): network tunnel
     * pieces, chambers, entrances, lava tube pieces and rooms, galleries by size class (radius
     * under 30, 30..55, over 55) and river gallery pieces.
     */
    public java.util.Map<String, Integer> census(int minX, int minZ, int maxX, int maxZ) {
        java.util.Map<String, Integer> out = new java.util.TreeMap<>();
        for (long i = Math.floorDiv(minX, (int) CaveNetwork.CELL); i <= Math.floorDiv(maxX, (int) CaveNetwork.CELL); i++) {
            for (long j = Math.floorDiv(minZ, (int) CaveNetwork.CELL); j <= Math.floorDiv(maxZ, (int) CaveNetwork.CELL); j++) {
                for (CavePrimitive p : this.model.network.cell(i, j).primitives) {
                    String kind = p.type == CavePrimitive.CHAMBER ? "chambers" : p.ignoresCover ? "entrances" : "tunnel pieces";
                    out.merge(kind, 1, Integer::sum);
                }
            }
        }
        for (long i = Math.floorDiv(minX, (int) LavaTubes.CELL); i <= Math.floorDiv(maxX, (int) LavaTubes.CELL); i++) {
            for (long j = Math.floorDiv(minZ, (int) LavaTubes.CELL); j <= Math.floorDiv(maxZ, (int) LavaTubes.CELL); j++) {
                for (CavePrimitive p : this.model.lavaTubes.cell(i, j).primitives) {
                    out.merge(p.type == CavePrimitive.CHAMBER ? "lava tube rooms" : "lava tube pieces", 1, Integer::sum);
                }
            }
        }
        for (long i = Math.floorDiv(minX, (int) Galleries.CELL); i <= Math.floorDiv(maxX, (int) Galleries.CELL); i++) {
            for (long j = Math.floorDiv(minZ, (int) Galleries.CELL); j <= Math.floorDiv(maxZ, (int) Galleries.CELL); j++) {
                for (CavePrimitive p : this.model.galleries.cell(i, j).primitives) {
                    String kind;
                    if (p.type == CavePrimitive.CHAMBER) {
                        kind = "gallery halls";
                    } else if (p.ignoresCover) {
                        kind = "gallery mouths";
                    } else {
                        double r = Math.max(p.ra, p.rb);
                        kind = r < 30.0 ? "gallery pieces r<30" : (r < 55.0 ? "gallery pieces r30-55" : "gallery pieces r55+");
                    }
                    out.merge(kind, 1, Integer::sum);
                    if (p.river) {
                        out.merge("river gallery pieces", 1, Integer::sum);
                    }
                }
            }
        }
        return out;
    }

    /** Shaft positions (x, z) and river water tops of the swallets sited near a column, for previews and tests. */
    public java.util.List<int[]> swalletsNear(int x, int z) {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (RiverTunnels.Swallet s : this.model.tunnels.swalletsNear(x, z)) {
            long c = s.anyShaftCell();
            out.add(new int[] {(int) (c >> 32), (int) c, s.water(), s.bottom()});
        }
        return out;
    }

    /** The part of a swallet a column belongs to (NONE, NOTCH, SHAFT, LIP), for previews and tests. */
    public String swalletPart(int x, int z) {
        return this.column(x, z).roof.part.name();
    }

    /** Strength of the roof over a river gorge at a column (0 where there is none). */
    public double roofStrength(int x, int z) {
        CaveColumn column = this.column(x, z);
        return column.roof.active ? column.roof.strength : 0.0;
    }

    /** True if the block is rock restored over a river gorge (a natural tunnel's roof). */
    public boolean isRoof(int x, int y, int z) {
        return this.column(x, z).roofed(y);
    }

    /** True if the block is open cave space (whatever the landscape). */
    public boolean isCave(int x, int y, int z) {
        return this.model.carve(this.column(x, z), y) < 0.0;
    }

    /** Fields of one run in {@link #runs}' output. */
    public static final int RUN_FLOOR = 0;
    public static final int RUN_CEILING = 1;
    /** The fluid on the floor: 0 air, 1 water, 2 lava. */
    public static final int RUN_FLUID = 2;
    /** Y of the fluid's top block (the floor's domain level), or {@link Integer#MIN_VALUE} when dry. */
    public static final int RUN_LEVEL = 3;
    /** The deepest point of the run below its walls, in tenths of a block: about the radius of free space around the column. */
    public static final int RUN_ROOM = 4;
    /** 0, or 1 / 2 if the run lies in an upper / deep megacave. */
    public static final int RUN_MEGACAVE = 5;
    /** 1 if the run reaches the ground (a cave mouth, a shaft open to the sky). */
    public static final int RUN_OPEN = 6;
    public static final int RUN_FIELDS = 7;

    /**
     * The open runs of a column, bottom-up (for natural formations, Agent 6): each run is the open
     * cave space between a floor (its lowest block, solid below) and a ceiling (its highest block,
     * solid above), exactly as noise filling opens it (the same {@code carve} as the density). Up to
     * {@code out.length / RUN_FIELDS} runs, fields at {@code run * RUN_FIELDS + RUN_*}; returns their
     * number. Columns of swallets and grottoes behind falls report none (river water stands there).
     * Rock is crossed in steps bounded by the distance to the nearest cave piece, so a column costs
     * a few dozen block queries in solid ground.
     */
    public int runs(int x, int z, int[] out) {
        CaveColumn c = this.column(x, z);
        if (c.roof.part != RiverTunnels.SwalletPart.NONE || c.roof.grottoLo <= c.roof.grottoHi) {
            return 0;
        }
        int top = Math.min(c.top, (int) Math.ceil(c.surface) - 1);
        int max = out.length / RUN_FIELDS;
        int n = 0;
        int start = Integer.MIN_VALUE;
        double room = 0.0;
        int y = Math.max(c.bottom, this.minY + 1);
        int previous = y - 1;
        while (y <= top && n < max) {
            double d = this.model.carve(c, y);
            if (d < 0.0) {
                if (start == Integer.MIN_VALUE) {
                    // After a long step the floor may lie below: walk back to it.
                    while (y - 1 > previous && this.model.carve(c, y - 1) < 0.0) {
                        y--;
                    }
                    start = y;
                    room = 0.0;
                }
                room = Math.max(room, -d);
                previous = y;
                y++;
                continue;
            }
            if (start != Integer.MIN_VALUE) {
                this.emit(c, out, n++, start, y - 1, room, false);
                start = Integer.MIN_VALUE;
            }
            previous = y;
            // The pieces' distance changes by at most a few blocks per block of height (flattened
            // tubes, funnels, the fade under the cover); wall noise adds at most 1.45 roughness.
            double clear = d - 1.45 * c.roughness - 1.5;
            // No piece in this slab at all (an infinite distance): a short step, the cracks and
            // megacaves are not indexed by slab.
            y += Double.isInfinite(clear) ? 6 : (clear > 10.0 ? (int) Math.min(30.0, clear / 5.0) : 1);
        }
        if (start != Integer.MIN_VALUE && n < max) {
            this.emit(c, out, n++, start, Math.min(y - 1, top), room, true);
        }
        return n;
    }

    private void emit(CaveColumn c, int[] out, int run, int floor, int ceiling, double room, boolean open) {
        int o = run * RUN_FIELDS;
        FluidDomains.Domain domain = c.domain(floor);
        CaveFluid fluid = domain.at(floor);
        out[o + RUN_FLOOR] = floor;
        out[o + RUN_CEILING] = ceiling;
        out[o + RUN_FLUID] = fluid == CaveFluid.WATER ? 1 : (fluid == CaveFluid.LAVA ? 2 : 0);
        out[o + RUN_LEVEL] = fluid == CaveFluid.AIR ? Integer.MIN_VALUE : domain.level;
        out[o + RUN_ROOM] = (int) Math.min(100000.0, room * 10.0);
        int mega = 0;
        if (c.upperCave.present() && floor >= c.upperCave.low() && floor <= c.upperCave.high()) {
            mega = 1;
        } else if (c.deepCave.present() && floor >= c.deepCave.low() && floor <= c.deepCave.high()) {
            mega = 2;
        }
        out[o + RUN_MEGACAVE] = mega;
        out[o + RUN_OPEN] = open || ceiling >= c.surface - 2.0 ? 1 : 0;
    }

    /** The rock of a column (see {@code CaveGeology}): karst, fractured and volcanic strength, 0..1 each. */
    public double[] geology(int x, int z) {
        CaveGeology.Geology g = this.model.geologyAt(x, z);
        return new double[] {g.karst(), g.fracture(), g.volcanic()};
    }

    @Override
    public Aquifer.FluidPicker fluidPicker(Aquifer.FluidPicker vanilla) {
        Statuses statuses = this.statuses();
        return (x, y, z) -> this.fluid(statuses, x, y, z);
    }

    private Aquifer.FluidStatus fluid(Statuses statuses, int x, int y, int z) {
        int code = this.fluidCode(x, y, z);
        if (code == AIR_CODE) {
            return statuses.air;
        }
        if (code == SEA_CODE) {
            return statuses.sea;
        }
        return (code & 1) != 0 ? statuses.lava[code >> 1] : statuses.water[code >> 1];
    }

    private static final int AIR_CODE = -1;
    private static final int SEA_CODE = -2;

    /**
     * What noise filling puts into an open block: {@link #AIR_CODE}, {@link #SEA_CODE} (vanilla's
     * sea, outside above the ground) or {@code (level - minY) << 1 | lava} for a cave domain.
     */
    public int fluidCode(int x, int y, int z) {
        if (y > CaveModel.MAX_LEVEL) {
            return AIR_CODE;
        }
        CaveColumn column = this.column(x, z);
        if (y >= column.surface) {
            // Outside, above the ground: vanilla's sea.
            return y <= this.seaTop ? SEA_CODE : AIR_CODE;
        }
        FluidDomains.Domain domain = column.domain(y);
        if (domain.fluid == CaveFluid.AIR || y > domain.level) {
            return AIR_CODE;
        }
        return Math.max(0, domain.level - this.minY) << 1 | (domain.fluid == CaveFluid.LAVA ? 1 : 0);
    }

    /**
     * The cave biome slot of a block (see {@link CaveBiomes}): {@link CaveBiome#SURFACE} near the
     * ground, {@link CaveBiome#NONE} for plain rock caves under the surface biome, else the cave
     * biome of the block's province or megacave. The biome source maps slots to biomes.
     */
    public CaveBiome caveBiome(int x, int y, int z) {
        return this.model.caveBiome(x, y, z);
    }

    /**
     * True if the block lies in a luminous lush or dripstone province or megacave (middle and deep
     * caves under warm, humid climates), where a luminous cave biome of another mod takes the
     * slot's place (the {@code luminous} list of the biome source); cheap anywhere.
     */
    public boolean luminous(int x, int y, int z) {
        return this.model.luminous(x, y, z);
    }

    /** Fluid placed in an open cave block (below the ground), for block steps and tests. */
    public CaveFluid caveFluid(int x, int y, int z) {
        return this.column(x, z).domain(y).at(y);
    }

    /** Y of the fluid top a spillway notch lets over a dam in this column, or {@link Integer#MIN_VALUE}. */
    public int spillway(int x, int z) {
        return this.column(x, z).notchY;
    }

    /** What decides a block: the signed distance of every piece reaching it, the cracks, the megacave sections and the result (diagnostics). */
    public String explain(int x, int y, int z) {
        CaveColumn c = this.column(x, z);
        StringBuilder out = new StringBuilder(String.format(Locale.ROOT, "block %d %d %d: carve %.2f, sdf %.2f (rough %.2f), coverTop %d, solid %s, range %d..%d%n",
                x, y, z, this.model.carve(c, y), c.sdf(y), c.roughness, c.coverTop, c.solid(y), c.bottom, c.top));
        for (Megacaves.Section section : new Megacaves.Section[] {c.upperCave, c.deepCave}) {
            if (section.present()) {
                out.append(String.format(Locale.ROOT, "  megacave %s at %.0f %.0f: q %.3f, floor %.1f, ceiling %.1f, sdf %.2f, scales %.2f %.2f, blocks/q %.1f%n",
                        section.cave.kind, section.cave.x, section.cave.z, section.q, section.floor, section.ceiling, section.sdf(y), section.floorScale,
                        section.ceilingScale, section.blocksPerQ));
            }
        }
        if (c.upperCrack.open()) {
            out.append(String.format(Locale.ROOT, "  upper crack: distance %.2f width %.2f, sdf %.2f%n", c.upperCrack.distance, c.upperCrack.width, c.upperCrack.sdf(y)));
        }
        if (c.deepCrack.open()) {
            out.append(String.format(Locale.ROOT, "  deep crack: distance %.2f width %.2f, sdf %.2f%n", c.deepCrack.distance, c.deepCrack.width, c.deepCrack.sdf(y)));
        }
        for (int i = 0; i < c.count; i++) {
            CavePrimitive p = c.pieces[i];
            if (y >= p.minY && y <= p.maxY) {
                out.append(String.format(Locale.ROOT, "  piece %s r %.1f..%.1f shape %.2f rough %.2f river %s y %.0f..%.0f%n", p.type == CavePrimitive.CHAMBER ? "chamber" : "segment",
                        p.ra, p.rb, p.shape, p.roughness, p.river, p.minY, p.maxY));
            }
        }
        return out.toString();
    }

    /** Detailed diagnostics of a column (previews and tests). */
    public String debug(int x, int z) {
        return this.model.debug(this.column(x, z));
    }

    /** Diagnostics for the F3 screen. */
    public String describe(int x, int y, int z) {
        CaveColumn column = this.column(x, z);
        String here = this.isCave(x, y, z) ? "cave " + this.caveFluid(x, y, z).name().toLowerCase(Locale.ROOT) : "rock";
        return here + ", " + this.model.describe(column, this.model.geologyAt(x, z));
    }

    private static final class ColumnCache {
        private static final int SIZE = 1024;
        private final CaveColumn[] slots = new CaveColumn[SIZE];

        ColumnCache() {
            for (int i = 0; i < SIZE; i++) {
                this.slots[i] = new CaveColumn();
            }
        }

        CaveColumn get(CaveModel model, int x, int z) {
            CaveColumn column = this.slots[(x & 31) << 5 | (z & 31)];
            if (!column.valid || column.x != x || column.z != z) {
                column.valid = false;
                model.column(x, z, column);
            }
            return column;
        }
    }
}
