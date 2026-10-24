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
        this(setup, terrain, hydrology, CaveSettings.DEFAULT);
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

    /** Fluid placed in an open cave block (below the ground), for block steps and tests. */
    public CaveFluid caveFluid(int x, int y, int z) {
        return this.column(x, z).domain(y).at(y);
    }

    /** Y of the fluid top a spillway notch lets over a dam in this column, or {@link Integer#MIN_VALUE}. */
    public int spillway(int x, int z) {
        return this.column(x, z).notchY;
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
