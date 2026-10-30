package com.sincerelyuq.vastlands.worldgen.ocean;

import com.sincerelyuq.vastlands.worldgen.context.GenerationContext;
import com.sincerelyuq.vastlands.worldgen.geography.FrozenSea;
import com.sincerelyuq.vastlands.worldgen.geography.GeographyModel;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSystems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Block-level finishing of the Vastlands oceans, run by {@code VastlandsChunkGenerator}:
 * <ul>
 *     <li>{@link #fillDeepWater}: after noise filling and after carving, the open water of abyssal
 *     trenches below Y -54 is water, not the lava (and the aquifer lid above it) vanilla puts there.</li>
 *     <li>{@link #placeIceFields}: after feature decoration, the surface of polar seas becomes the
 *     fractured ice field of {@link FrozenSea}, replacing vanilla's frozen-ocean freezing (rivers
 *     and lakes keep vanilla's).</li>
 * </ul>
 * Both only read the chunk's own columns and pure functions of (x, z), so they do not depend on
 * generation order.
 */
public final class OceanSurface {
    /** Vanilla's global fluid picker makes every open block below this Y lava. */
    public static final int LAVA_LEVEL = -54;
    /** The aquifer barrier between that lava and the sea stays below this Y; deep columns are opened up to it. */
    public static final int TRENCH_FIX_TOP = -30;
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState ICE = Blocks.ICE.defaultBlockState();
    private static final BlockState PACKED_ICE = Blocks.PACKED_ICE.defaultBlockState();
    /** Ice field strength from which river and lake water at sea level freezes over too. */
    private static final double RIVER_ICE_STRENGTH = 0.3;

    private OceanSurface() {
    }

    /**
     * Opens the water column of abyssal trenches. Below Y {@value #LAVA_LEVEL} vanilla's global
     * fluid picker fills every open block with lava, and the aquifers wall that lava off from the
     * sea with a lid of rock and an air gap up to ~20 blocks above it, also over trench walls whose
     * floor is above that level. In columns whose generated floor lies below Y
     * {@value #TRENCH_FIX_TOP}, every block from the floor up to that Y becomes water. Carvers cut through water (it is in their replaceable tag) and fill their
     * tunnels below Y {@value #LAVA_LEVEL} with lava, so this runs again after carving. Lava in caves
     * under the floor stays.
     */
    public static void fillDeepWater(ChunkAccess chunk, GenerationSystems systems) {
        ChunkPos chunkPos = chunk.getPos();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = chunkPos.getMinBlockX() + dx;
                int z = chunkPos.getMinBlockZ() + dz;
                double surface = systems.surfaceHeight(x, z);
                if (surface >= TRENCH_FIX_TOP) {
                    continue;
                }
                // The top block of the landscape is ceil(surface) - 1; everything above it is open sea.
                // Interpolated density can leave the top block itself open: fill it if it is fluid or air.
                int open = (int) Math.ceil(surface);
                for (int y = Math.max(chunk.getMinY(), open - 2); y < TRENCH_FIX_TOP; y++) {
                    BlockState state = chunk.getBlockState(pos.set(x, y, z));
                    if (!state.is(Blocks.WATER) && (y >= open || state.isAir() || state.is(Blocks.LAVA))) {
                        chunk.setBlockState(pos, WATER);
                    }
                }
            }
        }
    }

    /**
     * Lays the fractured ice field over the polar sea columns of a chunk: ice, thick packed-ice
     * floes and pressure ridges at the water surface (sea level - 1), open water in cracks and
     * leads. Only touches the water or ice of open sea columns (ground below the surface; see
     * {@link #iceFieldColumn}); icebergs (vanilla's frozen-ocean surface extension and iceberg
     * feature) and other blocks stay, so the bergs stand frozen into the field. River and lake
     * water that Vastlands places at sea level is not sea: no cracks or leads open in it; inside
     * the firm field (strength {@value #RIVER_ICE_STRENGTH} and more) it freezes over whole, also
     * where its biome would not freeze it (a river mouth in taiga).
     */
    public static void placeIceFields(ChunkAccess chunk, GenerationSystems systems, int seaLevel) {
        GeographyModel geography = systems.terrain().geography();
        ChunkPos chunkPos = chunk.getPos();
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        // The ice strength follows a ~26 000-block temperature field: if no corner of the chunk is
        // near the freezing band, no column is.
        double warmest = Math.min(
                Math.min(geography.seaLevelTemperature(minX, minZ), geography.seaLevelTemperature(minX + 15, minZ)),
                Math.min(geography.seaLevelTemperature(minX, minZ + 15), geography.seaLevelTemperature(minX + 15, minZ + 15)));
        if (warmest > FrozenSea.FREEZE_TEMPERATURE + 0.02) {
            return;
        }
        FrozenSea sea = geography.frozenSea();
        int y = seaLevel - 1;
        boolean rivers = systems.hydrology().mayHoldWater(minX, minZ, minX + 15, minZ + 15);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = minX + dx;
                int z = minZ + dz;
                double strength = FrozenSea.strength(geography.seaLevelTemperature(x, z));
                if (strength <= 0.0 || !underWater(systems, x, y, z)) {
                    continue;
                }
                BlockState top = chunk.getBlockState(pos.set(x, y, z));
                if (!top.is(Blocks.WATER) && !top.is(Blocks.ICE)) {
                    continue;
                }
                if (!iceFieldColumn(systems, rivers, x, y, z)) {
                    // River or lake water at sea level inside the frozen sea (river mouths): frozen
                    // over whole, whatever its biome (a taiga's would stay open otherwise).
                    if (top.is(Blocks.WATER) && strength >= RIVER_ICE_STRENGTH) {
                        chunk.setBlockState(pos, ICE);
                    }
                    continue;
                }
                switch (sea.cover(x, z, strength)) {
                    case OPEN_WATER -> {
                        if (!top.is(Blocks.WATER)) {
                            chunk.setBlockState(pos, WATER);
                        }
                    }
                    case ICE -> chunk.setBlockState(pos, ICE);
                    case THICK_ICE -> {
                        chunk.setBlockState(pos, PACKED_ICE);
                        if (chunk.getBlockState(pos.set(x, y - 1, z)).is(Blocks.WATER)) {
                            chunk.setBlockState(pos, PACKED_ICE);
                        }
                    }
                    case PRESSURE_RIDGE -> {
                        chunk.setBlockState(pos, ICE);
                        placeIfAir(chunk, pos.set(x, y + 1, z), PACKED_ICE);
                    }
                    case HIGH_RIDGE -> {
                        chunk.setBlockState(pos, PACKED_ICE);
                        if (placeIfAir(chunk, pos.set(x, y + 1, z), PACKED_ICE)) {
                            placeIfAir(chunk, pos.set(x, y + 2, z), PACKED_ICE);
                        }
                    }
                }
            }
        }
    }

    /**
     * True if the block at {@code water} is in an open crack or lead of a Vastlands ice field, so
     * weather must not freeze it shut (see {@code ServerLevelMixin}).
     */
    public static boolean isOpenLead(ServerLevel level, BlockPos water) {
        if (water.getY() != level.getSeaLevel() - 1) {
            return false;
        }
        return GenerationContext.of(level).map(context -> {
            GenerationSystems systems = context.systems();
            GeographyModel geography = systems.terrain().geography();
            double strength = FrozenSea.strength(geography.seaLevelTemperature(water.getX(), water.getZ()));
            return strength > 0.0
                    && iceFieldColumn(systems, true, water.getX(), water.getY(), water.getZ())
                    && geography.frozenSea().cover(water.getX(), water.getZ(), strength) == FrozenSea.Cover.OPEN_WATER;
        }).orElse(false);
    }

    /**
     * True if the surface block at {@code y} (sea level - 1) of a column belongs to an ice field:
     * the ground lies below it and the water is the sea's (open sea, a sea-level basin, a river
     * mouth), not river or lake water that Vastlands places at sea level. Those are frozen, or not,
     * by their biome like every other river and lake; before, the field's cracks and leads opened
     * the lower courses of polar rivers, and weather could not close them again.
     *
     * @param rivers false if the hydrology says no placed water can lie near the column (skips the
     *               water query)
     */
    static boolean iceFieldColumn(GenerationSystems systems, boolean rivers, int x, int y, int z) {
        if (!underWater(systems, x, y, z)) {
            return false;
        }
        return !rivers || !systems.hydrology().water(x, z).placed();
    }

    /**
     * True if the landscape's top solid block ({@code ceil(surface) - 1}) lies below {@code y}.
     * Before, the test was surface &lt;= y - 0.5, which left out the shallowest sea along the
     * shores (ground top one block under the surface): open water there in taiga and beach biomes.
     */
    private static boolean underWater(GenerationSystems systems, int x, int y, int z) {
        return Math.ceil(systems.surfaceHeight(x, z)) - 1.0 < y;
    }

    private static boolean placeIfAir(ChunkAccess chunk, BlockPos pos, BlockState state) {
        if (chunk.getBlockState(pos).isAir()) {
            chunk.setBlockState(pos, state);
            return true;
        }
        return false;
    }
}
