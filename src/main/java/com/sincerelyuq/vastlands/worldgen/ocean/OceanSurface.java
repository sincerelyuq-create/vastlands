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
 *     <li>{@link #fillDeepWater}: after noise filling, open water below Y -54 (abyssal trenches)
 *     is water, not the lava vanilla's global fluid picker puts there.</li>
 *     <li>{@link #placeIceFields}: after feature decoration, the surface of polar seas becomes the
 *     fractured ice field of {@link FrozenSea}, replacing vanilla's frozen-ocean freezing.</li>
 * </ul>
 * Both only read the chunk's own columns and pure functions of (x, z), so they do not depend on
 * generation order.
 */
public final class OceanSurface {
    /** Vanilla's global fluid picker makes every open block below this Y lava. */
    public static final int LAVA_LEVEL = -54;
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState ICE = Blocks.ICE.defaultBlockState();
    private static final BlockState PACKED_ICE = Blocks.PACKED_ICE.defaultBlockState();

    private OceanSurface() {
    }

    /**
     * Turns the lava of open water columns below {@value #LAVA_LEVEL} into water: from the generated
     * floor of each column up to Y -55. Lava in caves under the floor stays.
     */
    public static void fillDeepWater(ChunkAccess chunk, GenerationSystems systems) {
        ChunkPos chunkPos = chunk.getPos();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = chunkPos.getMinBlockX() + dx;
                int z = chunkPos.getMinBlockZ() + dz;
                double surface = systems.surfaceHeight(x, z);
                if (surface >= LAVA_LEVEL) {
                    continue;
                }
                // The top block of the landscape is ceil(surface) - 1; everything above it is open.
                for (int y = Math.max(chunk.getMinY(), (int) Math.ceil(surface)); y < LAVA_LEVEL; y++) {
                    if (chunk.getBlockState(pos.set(x, y, z)).is(Blocks.LAVA)) {
                        chunk.setBlockState(pos, WATER);
                    }
                }
            }
        }
    }

    /**
     * Lays the fractured ice field over the polar sea columns of a chunk: ice, thick packed-ice
     * floes and pressure ridges at the water surface (sea level - 1), open water in cracks and
     * leads. Only touches the water or ice of open sea columns (ground below the surface); icebergs
     * and other blocks stay.
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
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = minX + dx;
                int z = minZ + dz;
                double strength = FrozenSea.strength(geography.seaLevelTemperature(x, z));
                if (strength <= 0.0 || systems.surfaceHeight(x, z) > y - 0.5) {
                    continue;
                }
                BlockState top = chunk.getBlockState(pos.set(x, y, z));
                if (!top.is(Blocks.WATER) && !top.is(Blocks.ICE)) {
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
                    && systems.surfaceHeight(water.getX(), water.getZ()) <= water.getY() - 0.5
                    && geography.frozenSea().cover(water.getX(), water.getZ(), strength) == FrozenSea.Cover.OPEN_WATER;
        }).orElse(false);
    }

    private static boolean placeIfAir(ChunkAccess chunk, BlockPos pos, BlockState state) {
        if (chunk.getBlockState(pos).isAir()) {
            chunk.setBlockState(pos, state);
            return true;
        }
        return false;
    }
}
