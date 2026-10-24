package com.sincerelyuq.vastlands.worldgen.hydrology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sincerelyuq.vastlands.Vastlands;
import com.sincerelyuq.vastlands.worldgen.WorldHeight;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSetup;
import com.sincerelyuq.vastlands.worldgen.terrain.VastlandsTerrain;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * The block step of rivers and lakes on real chunks: 3 × 3 proto-chunks around falls are filled
 * with the model's ground (and sea-level water where aquifers would put it), get their river
 * water, have their beds and banks cut by a tunnel like a carver's, and get it again. Then no
 * water block of the middle chunk, borders included, may change under vanilla's fluid rules
 * (ported from {@code FlowingFluid}: {@code getNewLiquid} and spreading down and sideways), and
 * all water above sea level must be river or lake water of the model or a fall's curtain.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class RiverWaterTest {
    private static final GenerationSetup SETUP = new GenerationSetup(20261018L, WorldHeight.MIN_Y, WorldHeight.HEIGHT, 63);
    private static final int SEA_TOP = 62;

    @Test
    void placedWaterIsStableAcrossChunkBorders(MinecraftServer server) {
        VastlandsHydrology hydrology = new VastlandsHydrology(SETUP, new VastlandsTerrain(SETUP));
        List<int[]> centres = new ArrayList<>();
        for (int[] place : new int[][] {{-5925, 14778}, {-13312, 5888}, {25000, -25000}}) {
            // Two falls near each place (the tallest first), and the place itself.
            List<double[]> falls = new ArrayList<>(hydrology.model().falls(place[0], place[1]));
            falls.sort((a, b) -> Double.compare(b[2] - b[3], a[2] - a[3]));
            int taken = 0;
            for (double[] fall : falls) {
                if (taken < 2 && centres.stream().noneMatch(c -> Math.hypot(c[0] - fall[0], c[1] - fall[1]) < 64)) {
                    centres.add(new int[] {(int) fall[0], (int) fall[1]});
                    taken++;
                }
            }
            centres.add(place);
        }
        int water = 0;
        int curtains = 0;
        for (int[] centre : centres) {
            int[] counts = this.check(server, hydrology, centre[0], centre[1]);
            water += counts[0];
            curtains += counts[1];
        }
        Vastlands.LOGGER.info("RiverWaterTest: {} areas, {} water blocks above sea level checked, {} curtain blocks", centres.size(), water, curtains);
        assertTrue(water > 2000, "too little water to judge: " + water);
        assertTrue(curtains > 0, "no fall curtains in the checked areas");
    }

    /** Returns the water blocks above sea level and the curtain blocks of the middle chunk. */
    private int[] check(MinecraftServer server, VastlandsHydrology hydrology, int x, int z) {
        ChunkPos middle = new ChunkPos(x >> 4, z >> 4);
        LevelHeightAccessor height = LevelHeightAccessor.create(WorldHeight.MIN_Y, WorldHeight.HEIGHT);
        ProtoChunk[] chunks = new ProtoChunk[9];
        for (int k = 0; k < 9; k++) {
            ChunkPos pos = new ChunkPos(middle.x + k % 3 - 1, middle.z + k / 3 - 1);
            ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, height, server.registryAccess().lookupOrThrow(Registries.BIOME), null);
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    int bx = pos.getMinBlockX() + dx;
                    int bz = pos.getMinBlockZ() + dz;
                    int ground = (int) Math.ceil(hydrology.surfaceHeight(bx, bz)) - 1;
                    for (int y = ground - 30; y <= ground; y++) {
                        chunk.setBlockState(p.set(bx, y, bz), Blocks.STONE.defaultBlockState());
                    }
                    for (int y = ground + 1; y <= SEA_TOP; y++) {
                        chunk.setBlockState(p.set(bx, y, bz), Blocks.WATER.defaultBlockState());
                    }
                }
            }
            RiverWater.place(chunk, hydrology, false);
            chunks[k] = chunk;
        }
        // A carver's tunnel through every chunk at the level of its water: through bed and banks.
        for (ProtoChunk chunk : chunks) {
            ChunkPos pos = chunk.getPos();
            int level = Integer.MIN_VALUE;
            for (int d = 0; d < 16 && level == Integer.MIN_VALUE; d++) {
                WaterColumn column = hydrology.water(pos.getMinBlockX() + d, pos.getMinBlockZ() + d);
                if (column.placed()) {
                    level = column.groundTop();
                }
            }
            if (level != Integer.MIN_VALUE) {
                BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
                for (int d = 0; d < 16; d++) {
                    for (int y = level - 2; y <= level + 3; y++) {
                        chunk.setBlockState(p.set(pos.getMinBlockX() + d, y, pos.getMinBlockZ() + 7), Blocks.AIR.defaultBlockState());
                        chunk.setBlockState(p.set(pos.getMinBlockX() + 8, y, pos.getMinBlockZ() + d), Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        for (ProtoChunk chunk : chunks) {
            RiverWater.place(chunk, hydrology, true);
        }

        int water = 0;
        int curtains = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int bx = middle.getMinBlockX() + dx;
                int bz = middle.getMinBlockZ() + dz;
                WaterColumn column = hydrology.water(bx, bz);
                for (int y = SEA_TOP + 1; y < 400; y++) {
                    BlockState state = get(chunks, middle, p.set(bx, y, bz));
                    if (!state.is(Blocks.WATER)) {
                        continue;
                    }
                    water++;
                    int level = state.getValue(LiquidBlock.LEVEL);
                    if (level == 0) {
                        assertTrue(column.placed() && y > column.groundTop() && y <= column.waterTop(),
                                "source outside river and lake water at " + p + ": " + column);
                    } else {
                        curtains++;
                        assertTrue(column.wet() && y > column.waterTop(), "flowing water that is not a fall curtain at " + p);
                    }
                    String change = changeOnTick(chunks, middle, bx, y, bz);
                    assertEquals(null, change, "unstable water at " + bx + " " + y + " " + bz + " (" + column + ")");
                }
            }
        }
        return new int[] {water, curtains};
    }

    private static BlockState get(ProtoChunk[] chunks, ChunkPos middle, BlockPos pos) {
        int cx = (pos.getX() >> 4) - middle.x + 1;
        int cz = (pos.getZ() >> 4) - middle.z + 1;
        if (cx < 0 || cz < 0 || cx > 2 || cz > 2) {
            throw new IllegalArgumentException("outside the 3x3 chunks: " + pos);
        }
        return chunks[cz * 3 + cx].getBlockState(pos);
    }

    /**
     * What a fluid tick of vanilla water would do to the block, or null if it would leave the world
     * as it is. Port of {@code FlowingFluid.tick}, {@code getNewLiquid} and {@code spread} for
     * water among full blocks, water and air.
     */
    private static String changeOnTick(ProtoChunk[] chunks, ChunkPos middle, int x, int y, int z) {
        BlockState state = get(chunks, middle, new BlockPos(x, y, z));
        int level = state.getValue(LiquidBlock.LEVEL);
        boolean source = level == 0;
        BlockState below = get(chunks, middle, new BlockPos(x, y - 1, z));
        BlockState above = get(chunks, middle, new BlockPos(x, y + 1, z));
        BlockState[] sides = {
                get(chunks, middle, new BlockPos(x + 1, y, z)), get(chunks, middle, new BlockPos(x - 1, y, z)),
                get(chunks, middle, new BlockPos(x, y, z + 1)), get(chunks, middle, new BlockPos(x, y, z - 1))};
        if (!source) {
            int amount = 0;
            int sources = 0;
            for (BlockState side : sides) {
                if (side.is(Blocks.WATER)) {
                    int l = side.getValue(LiquidBlock.LEVEL);
                    amount = Math.max(amount, l == 0 || l >= 8 ? 8 : 8 - l);
                    sources += l == 0 ? 1 : 0;
                }
            }
            int expected;
            if (sources >= 2 && (isSolid(below) || (below.is(Blocks.WATER) && below.getValue(LiquidBlock.LEVEL) == 0))) {
                expected = 0;
            } else if (above.is(Blocks.WATER)) {
                expected = 8;
            } else {
                int k = amount - 1;
                expected = k <= 0 ? -1 : 8 - k;
            }
            if (expected != level) {
                return "becomes " + (expected < 0 ? "air" : "level " + expected) + " from level " + level;
            }
        }
        if (below.isAir()) {
            return "flows down";
        }
        boolean hole = below.is(Blocks.WATER);
        if (source || !hole) {
            int amount = source ? 8 : (level >= 8 ? 8 : 8 - level);
            for (BlockState side : sides) {
                if (side.isAir() && amount - 1 > 0) {
                    return "spreads sideways";
                }
            }
        }
        return null;
    }

    private static boolean isSolid(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty();
    }
}
