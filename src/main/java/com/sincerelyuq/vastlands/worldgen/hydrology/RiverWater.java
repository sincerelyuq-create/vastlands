package com.sincerelyuq.vastlands.worldgen.hydrology;

import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;
import com.sincerelyuq.vastlands.worldgen.system.HydrologySystem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.material.Fluids;

/**
 * Places the water of rivers and lakes above sea level (vanilla's aquifers only fill water up to
 * sea level) and keeps it where it belongs. Run by {@code VastlandsChunkGenerator} after noise
 * filling (so surface rules see the water and give channel floors vanilla's underwater materials)
 * and again after carving (carvers cut through water and banks).
 *
 * <p>Every decision is a pure function of the hydrology model for the column and its neighbours,
 * so chunks agree across their borders whatever order they generate in. The water is placed in
 * configurations that vanilla's fluid rules leave as they are, so no fluid ticks are scheduled
 * and nothing flows when the chunk loads:
 * <ul>
 *     <li>A water column is source blocks from its ground up to its body's water top, on solid
 *     ground (beds cut by carvers are sealed).</li>
 *     <li>Next to a water block, a neighbour column is either water of a body at least as high,
 *     or solid at that height: banks cut by caves are sealed, and dry columns lower than a
 *     neighbouring water surface are raised to it (a one-block levee; rare, the density already
 *     keeps banks above the water).</li>
 *     <li>Water tops differ by 0 or by at least 2 blocks (see {@link HydrologyModel}). Where a
 *     higher body meets a lower one (a fall), the upper column is a sill one water block deep and
 *     the lower one holds a curtain: a flowing block at the upper water top and falling water
 *     down to its own surface. Vanilla computes exactly these states for them, and the air next
 *     to the curtain has no solid or source block below and next to it in the way that turns
 *     water into new sources, so the fall stays as generated.</li>
 *     <li>Boulders are solid columns of andesite, cobblestone and mossy cobblestone (mossier at
 *     the water) from the ground to at least the water around them. Surface rules only repaint
 *     plain stone, so they keep these materials.</li>
 * </ul>
 */
public final class RiverWater {
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState LIP = Fluids.FLOWING_WATER.getFlowing(7, false).createLegacyBlock();
    private static final BlockState FALLING = Fluids.FLOWING_WATER.getFlowing(8, true).createLegacyBlock();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState ANDESITE = Blocks.ANDESITE.defaultBlockState();
    private static final BlockState COBBLESTONE = Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState MOSSY_COBBLESTONE = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
    private static final int MARGIN = 2;
    private static final int WIDTH = 16 + 2 * MARGIN;

    private RiverWater() {
    }

    /**
     * @param finished false right after noise filling (seals are plain stone that surface rules
     *                 paint), true after carving (seals get surface materials)
     * @return the number of blocks changed
     */
    public static int place(ChunkAccess chunk, HydrologySystem hydrology, boolean finished) {
        ChunkPos chunkPos = chunk.getPos();
        int minX = chunkPos.getMinBlockX() - MARGIN;
        int minZ = chunkPos.getMinBlockZ() - MARGIN;
        if (!hydrology.mayHoldWater(minX, minZ, minX + WIDTH - 1, minZ + WIDTH - 1)) {
            return 0;
        }
        int[] ground = new int[WIDTH * WIDTH];
        int[] dug = new int[WIDTH * WIDTH];
        int[] top = new int[WIDTH * WIDTH];
        boolean[] placed = new boolean[WIDTH * WIDTH];
        boolean[] rock = new boolean[WIDTH * WIDTH];
        int[] rockWater = new int[WIDTH * WIDTH];
        boolean any = false;
        for (int j = 0; j < WIDTH; j++) {
            for (int i = 0; i < WIDTH; i++) {
                WaterColumn column = hydrology.water(minX + i, minZ + j);
                int c = j * WIDTH + i;
                ground[c] = column.groundTop();
                dug[c] = column.surfaceTop();
                rock[c] = column.kind() == WaterColumn.Kind.ROCK;
                top[c] = rock[c] ? Integer.MIN_VALUE : column.waterTop();
                rockWater[c] = rock[c] ? column.waterTop() : Integer.MIN_VALUE;
                placed[c] = column.placed();
                any |= placed[c] || rock[c];
            }
        }
        if (!any) {
            return 0;
        }
        // Effective ground: sills under the upper edge of a fall, levees beside dry ground.
        int[] effective = ground.clone();
        for (int j = 1; j < WIDTH - 1; j++) {
            for (int i = 1; i < WIDTH - 1; i++) {
                int c = j * WIDTH + i;
                if (top[c] != Integer.MIN_VALUE) {
                    for (int n : neighbours(c)) {
                        if (top[n] != Integer.MIN_VALUE && top[n] <= top[c] - 2 && placed[c]) {
                            effective[c] = Math.max(effective[c], top[c] - 1);
                        }
                    }
                } else {
                    for (int n : neighbours(c)) {
                        if (placed[n]) {
                            effective[c] = Math.max(effective[c], top[n]);
                        }
                    }
                }
            }
        }
        int changed = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int j = MARGIN; j < MARGIN + 16; j++) {
            for (int i = MARGIN; i < MARGIN + 16; i++) {
                int c = j * WIDTH + i;
                int x = minX + i;
                int z = minZ + j;
                boolean wet = top[c] != Integer.MIN_VALUE;
                if (rock[c]) {
                    // A boulder: rock from the density's ground up; in a channel, the ramp of the
                    // density above it is cut away like the water's.
                    int water = rockWater[c] != Integer.MIN_VALUE ? rockWater[c] : ground[c] - 2;
                    for (int y = Math.max(chunk.getMinY(), Math.min(dug[c], effective[c]) + 1); y <= effective[c]; y++) {
                        BlockState state = chunk.getBlockState(pos.set(x, y, z));
                        if (!isSolid(state)) {
                            chunk.setBlockState(pos, rock(x, y, z, water));
                            changed++;
                        }
                    }
                    if (rockWater[c] != Integer.MIN_VALUE) {
                        for (int y = effective[c] + 1; y <= dug[c]; y++) {
                            if (!chunk.getBlockState(pos.set(x, y, z)).isAir()) {
                                chunk.setBlockState(pos, AIR);
                                changed++;
                            }
                        }
                    }
                }
                // Solid ground up to the effective ground: the bed under placed water (sealed where
                // carvers cut it), sills at the upper edge of falls, levees of dry columns.
                int from = placed[c] ? ground[c] : ground[c] + 1;
                for (int y = Math.max(chunk.getMinY(), from); y <= effective[c]; y++) {
                    BlockState state = chunk.getBlockState(pos.set(x, y, z));
                    if (!isSolid(state)) {
                        chunk.setBlockState(pos, steady(chunk, pos, seal(finished, wet, y, effective[c], top[c])));
                        changed++;
                    }
                }
                if (placed[c] && finished) {
                    changed += support(chunk, pos.set(x, effective[c], z));
                }
                if (placed[c]) {
                    // Water up to the top; inside a channel at the foot of a fall this cuts away the
                    // ramp the density interpolates between the sill and the plunge pool.
                    for (int y = effective[c] + 1; y <= top[c]; y++) {
                        BlockState state = chunk.getBlockState(pos.set(x, y, z));
                        if (state != WATER) {
                            chunk.setBlockState(pos, WATER);
                            changed++;
                        }
                    }
                    for (int y = top[c] + 1; y <= dug[c]; y++) {
                        if (!chunk.getBlockState(pos.set(x, y, z)).isAir()) {
                            chunk.setBlockState(pos, AIR);
                            changed++;
                        }
                    }
                }
                // Banks and walls next to neighbouring water stay solid.
                int curtain = Integer.MIN_VALUE;
                for (int n : neighbours(c)) {
                    if (!placed[n]) {
                        // Dry ground needs nothing; the sea is vanilla's (and never above a river).
                        continue;
                    }
                    if (wet && top[n] >= top[c] + 2) {
                        curtain = Math.max(curtain, top[n]);
                        continue;
                    }
                    int ownTop = wet ? top[c] : Integer.MIN_VALUE;
                    for (int y = effective[n] + 1; y <= top[n]; y++) {
                        if (wet && y > effective[c] && y <= ownTop) {
                            continue;
                        }
                        BlockState state = chunk.getBlockState(pos.set(x, y, z));
                        if (!isSolid(state)) {
                            chunk.setBlockState(pos, rock[c] ? rock(x, y, z, top[n]) : steady(chunk, pos, seal(finished, wet, y, effective[c], top[c])));
                            changed++;
                        } else if (finished && y == effective[n] + 1) {
                            changed += support(chunk, pos);
                        }
                    }
                }
                // The curtain of a fall: a flowing lip at the upper water top, falling water below,
                // through any rock the rough walls put in its way.
                if (curtain != Integer.MIN_VALUE) {
                    for (int y = top[c] + 1; y <= curtain; y++) {
                        BlockState state = chunk.getBlockState(pos.set(x, y, z));
                        BlockState water = y == curtain ? LIP : FALLING;
                        if (state != water) {
                            chunk.setBlockState(pos, water);
                            changed++;
                        }
                    }
                }
            }
        }
        return changed;
    }

    private static final int[] OFFSETS = {1, -1, WIDTH, -WIDTH};

    private static int[] neighbours(int c) {
        return new int[] {c + OFFSETS[0], c + OFFSETS[1], c + OFFSETS[2], c + OFFSETS[3]};
    }

    private static boolean isSolid(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty();
    }

    /**
     * Sand or gravel that holds water back (a bed, a bank) over a cave would fall at the first
     * block update and let the water out: the block under it becomes stone.
     */
    private static int support(ChunkAccess chunk, BlockPos.MutableBlockPos pos) {
        if (pos.getY() <= chunk.getMinY() || !(chunk.getBlockState(pos).getBlock() instanceof FallingBlock)) {
            return 0;
        }
        BlockPos below = pos.below();
        if (!FallingBlock.isFree(chunk.getBlockState(below))) {
            return 0;
        }
        chunk.setBlockState(below, STONE);
        return 1;
    }

    /**
     * Gravel only on solid ground: over a cave it would fall at the first block update and open
     * the bed. Stone there instead.
     */
    private static BlockState steady(ChunkAccess chunk, BlockPos pos, BlockState seal) {
        if (seal != GRAVEL || pos.getY() <= chunk.getMinY()) {
            return seal;
        }
        return isSolid(chunk.getBlockState(pos.below())) ? seal : STONE;
    }

    /** Boulder material: mossy cobblestone (most often at the water), cobblestone and andesite. */
    private static BlockState rock(int x, int y, int z, int water) {
        long h = SimplexNoise.mix(x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L);
        double u = (h >>> 11) * 0x1.0p-53;
        double moss = y <= water + 1 ? 0.45 : 0.2;
        if (u < moss) {
            return MOSSY_COBBLESTONE;
        }
        return u < moss + 0.3 ? COBBLESTONE : ANDESITE;
    }

    /** Material for a sealed or raised block: stone before surface rules, surface materials after. */
    private static BlockState seal(boolean finished, boolean wet, int y, int ground, int top) {
        if (!finished || y < ground - 1) {
            return STONE;
        }
        return wet || y <= top ? GRAVEL : DIRT;
    }
}
