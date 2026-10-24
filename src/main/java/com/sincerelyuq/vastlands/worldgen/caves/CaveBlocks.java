package com.sincerelyuq.vastlands.worldgen.caves;

import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.material.Fluids;

/**
 * Block step of the caves, run by {@code VastlandsChunkGenerator} right after noise filling (before
 * surface rules and before the river water step):
 * <ul>
 *     <li>Spillways: where a dam between two water (or two lava) domains is notched (see
 *     {@code CaveModel.dams}), the higher fluid stands in the notch next to open air on the lower
 *     side. Those fluid blocks are marked for post-processing, so when the chunk is loaded vanilla
 *     ticks them once and the fluid spills over the notch and runs down into the lower domain: an
 *     underground waterfall, stream or lava fall, fed forever by the higher domain's sources (which
 *     vanilla never drains). Only notch blocks are marked: a handful per spillway.</li>
 *     <li>Volcanic rock: in lava domains of volcanic regions, the stone touching lava becomes
 *     magma, blackstone and basalt, and the cave walls a few blocks above the lava basalt and
 *     smooth basalt (in other lava domains only a little magma at the shore). Surface rules only
 *     repaint stone, so these stay.</li>
 * </ul>
 * After carving (after the hydrology's last water step) {@link #afterRivers} places the water of
 * swallets. Every decision reads only the chunk's own blocks and the cave model for its columns,
 * so the steps do not depend on generation order.
 */
public final class CaveBlocks {
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState MAGMA = Blocks.MAGMA_BLOCK.defaultBlockState();
    private static final BlockState BLACKSTONE = Blocks.BLACKSTONE.defaultBlockState();
    private static final BlockState BASALT = Blocks.BASALT.defaultBlockState();
    private static final BlockState SMOOTH_BASALT = Blocks.SMOOTH_BASALT.defaultBlockState();
    private static final int[][] SIDES = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}};
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState LIP = Fluids.FLOWING_WATER.getFlowing(7, false).createLegacyBlock();
    private static final BlockState FALLING = Fluids.FLOWING_WATER.getFlowing(8, true).createLegacyBlock();

    private CaveBlocks() {
    }

    /** @return the number of blocks changed or marked */
    public static int afterNoise(ChunkAccess chunk, VastlandsCaves caves) {
        ChunkPos chunkPos = chunk.getPos();
        int changed = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = chunkPos.getMinBlockX() + dx;
                int z = chunkPos.getMinBlockZ() + dz;
                CaveColumn column = caves.column(x, z);
                if (column.notchY != Integer.MIN_VALUE) {
                    BlockState state = chunk.getBlockState(pos.set(x, column.notchY, z));
                    if (!state.getFluidState().isEmpty()) {
                        chunk.markPosForPostprocessing(pos);
                        changed++;
                    }
                }
                FluidDomains.Domain deep = column.deep;
                if (deep.fluid == CaveFluid.LAVA && column.bottom <= deep.level) {
                    changed += line(chunk, column, deep.level, x, z, dx, dz, pos, next);
                }
            }
        }
        return changed;
    }

    /**
     * Swallets, run after the hydrology's last water step (which seals every opening beside river
     * water, the notch included): the notch becomes source water at the river's water top on solid
     * rock, and the shaft columns beside it a curtain: a flowing block (amount 7) at the water top
     * and falling water down to the cave water. These are the states vanilla computes for them, so
     * nothing flows when the chunk loads, and the river pours into the caves without draining.
     *
     * @return the number of blocks changed
     */
    public static int afterRivers(ChunkAccess chunk, VastlandsCaves caves) {
        ChunkPos chunkPos = chunk.getPos();
        int changed = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = chunkPos.getMinBlockX() + dx;
                int z = chunkPos.getMinBlockZ() + dz;
                CaveColumn column = caves.column(x, z);
                RiverTunnels.SwalletPart part = column.roof.part;
                if (part == RiverTunnels.SwalletPart.NONE || part == RiverTunnels.SwalletPart.SHAFT) {
                    continue;
                }
                RiverTunnels.Swallet swallet = column.roof.swallet;
                int water = swallet.water();
                if (part == RiverTunnels.SwalletPart.NOTCH) {
                    changed += set(chunk, pos.set(x, water - 1, z), STONE, true);
                    changed += set(chunk, pos.set(x, water, z), WATER, false);
                    changed += set(chunk, pos.set(x, water + 1, z), AIR, false);
                } else {
                    changed += set(chunk, pos.set(x, water, z), LIP, false);
                    for (int y = water - 1; y > swallet.bottom() + 3; y--) {
                        changed += set(chunk, pos.set(x, y, z), FALLING, false);
                    }
                }
            }
        }
        return changed;
    }

    /** Sets a block (only if it is not solid already when {@code onlyIfOpen}). */
    private static int set(ChunkAccess chunk, BlockPos pos, BlockState state, boolean onlyIfOpen) {
        BlockState current = chunk.getBlockState(pos);
        if (current == state || onlyIfOpen && !current.isAir() && current.getFluidState().isEmpty()) {
            return 0;
        }
        chunk.setBlockState(pos, state);
        return 1;
    }

    /** Volcanic lining of one column around the lava of its deep domain. */
    private static int line(ChunkAccess chunk, CaveColumn column, int lavaTop, int x, int z, int dx, int dz,
                            BlockPos.MutableBlockPos pos, BlockPos.MutableBlockPos next) {
        double volcanic = column.volcanic;
        int top = Math.min(lavaTop + (volcanic > 0.25 ? 8 : 1), column.bandLo - 1);
        int changed = 0;
        for (int y = Math.max(chunk.getMinY() + 5, column.bottom); y <= top; y++) {
            if (chunk.getBlockState(pos.set(x, y, z)) != STONE) {
                continue;
            }
            boolean lava = false;
            boolean open = false;
            for (int[] side : SIDES) {
                int nx = dx + side[0];
                int nz = dz + side[2];
                if (nx < 0 || nx > 15 || nz < 0 || nz > 15) {
                    continue;
                }
                BlockState n = chunk.getBlockState(next.set(x + side[0], y + side[1], z + side[2]));
                if (n.is(Blocks.LAVA)) {
                    lava = true;
                } else if (n.isAir()) {
                    open = true;
                }
            }
            if (!lava && !open) {
                continue;
            }
            double u = unit(x, y, z);
            BlockState rock;
            if (volcanic > 0.25) {
                if (lava) {
                    rock = u < 0.3 ? MAGMA : (u < 0.7 ? BLACKSTONE : BASALT);
                } else {
                    rock = u < 0.55 ? BASALT : (u < 0.8 ? SMOOTH_BASALT : BLACKSTONE);
                }
            } else {
                if (!lava || u >= 0.15) {
                    continue;
                }
                rock = MAGMA;
            }
            chunk.setBlockState(pos, rock);
            changed++;
        }
        return changed;
    }

    private static double unit(int x, int y, int z) {
        long h = SimplexNoise.mix(x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L ^ 0x564F4C43L);
        return (h >>> 11) * 0x1.0p-53;
    }
}
