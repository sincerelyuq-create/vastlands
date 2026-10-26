package com.sincerelyuq.vastlands.worldgen.forest;

import com.sincerelyuq.vastlands.worldgen.compat.CompatBlocks;
import com.sincerelyuq.vastlands.worldgen.compat.ModBlock;
import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.LeafLitterBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.material.Fluids;

/**
 * The block step of the forests: in its own decoration, each chunk builds its part of every tree,
 * shrub and fallen log that reaches it, then the ground vegetation of its columns.
 *
 * <p>A tree is a pure function of the model, so the chunks it spans each place exactly their own
 * blocks of the same shape: trees cross chunk borders whole, nothing is written outside the chunk
 * being decorated, and the result does not depend on the order chunks are generated in.
 * Decisions that read blocks only read the chunk's own (what a trunk stands on, what a leaf
 * replaces); decisions that need more come from the model (where trees stand) or from
 * structures, which are fixed before any chunk is decorated:
 * <ul>
 *     <li>Logs replace air, plants, snow, leaves, melons and pumpkins; leaves and decorations only air, plants and
 *     snow (never water, except swamp trees in shallow water); a double plant loses both halves.</li>
 *     <li>A trunk whose ground is lower than the model's (a hollow, a dip, a plant or melon on the
 *     ground) grows down onto solid ground, up to 5 blocks; grass under a trunk becomes dirt.</li>
 *     <li>Trees whose footprint would overlap a piece of a surface structure starting within 7
 *     chunks of the tree's own chunk (villages, outposts, ruined portals, huts, temples) are left
 *     out whole (see {@link StructureGuard}).</li>
 *     <li>Ground vegetation by community, shade and moisture: leaf litter under broadleaf
 *     canopies, ferns, grass, bushes, mushrooms in deep shade, moss on old wet ground, dry grass in
 *     savannas; thin under closed canopies, never a solid carpet.</li>
 * </ul>
 * Leaves carry the distances the shape builder computed, so they never decay; block states are
 * vanilla's, written straight into the chunk without neighbour updates (like vanilla's tree
 * features), lighting is computed by the game's light step afterwards.
 */
public final class ForestBlocks {
    private static final Map<Integer, BlockState> STATES = new ConcurrentHashMap<>();
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private ForestBlocks() {
    }

    /** Counts of what one chunk received (tests and diagnostics). */
    public static final class Stats {
        public int trees;
        public int skippedForStructures;
        public int logs;
        public int leaves;
        public int other;
        public int extended;
        public int plants;
    }

    public static Stats decorate(WorldGenLevel level, ChunkAccess chunk, VastlandsForests forests) {
        Stats stats = new Stats();
        ForestModel model = forests.model();
        if (model == null) {
            return stats;
        }
        ChunkPos pos = chunk.getPos();
        int minX = pos.getMinBlockX();
        int minZ = pos.getMinBlockZ();
        int maxX = minX + 15;
        int maxZ = minZ + 15;
        List<TreeSpec> trees = new ArrayList<>();
        model.trees(minX, minZ, maxX, maxZ, trees::add);
        StructureGuard guard = new StructureGuard(level);
        int[] shade = new int[256];
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int bottom = chunk.getMinY();
        int top = chunk.getMaxY();
        for (TreeSpec spec : trees) {
            TreeShape shape = model.shape(spec);
            if (spec.x() + shape.maxX < minX || spec.x() + shape.minX > maxX || spec.z() + shape.maxZ < minZ || spec.z() + shape.minZ > maxZ) {
                continue;
            }
            if (guard.blocked(spec, shape)) {
                stats.skippedForStructures++;
                continue;
            }
            stats.trees++;
            boolean swamp = spec.type() == ForestType.SWAMP;
            // Moss around old giants only where the community grows moss and it suits the soil.
            double mossEco = spec.has(TreeSpec.MOSS) ? ForestModel.mossEcology(spec.type()) * model.settings().moss() : 0.0;
            double mossBlend = mossEco > 0.0 ? model.mossBlend(spec.x(), spec.y() - 1, spec.z()) : 0.0;
            for (int k = 0; k < shape.size(); k++) {
                int p = shape.positions[k];
                int x = spec.x() + TreeShape.dx(p);
                int z = spec.z() + TreeShape.dz(p);
                if (x < minX || x > maxX || z < minZ || z > maxZ) {
                    continue;
                }
                int y = spec.y() + TreeShape.dy(p);
                if (y < bottom || y > top) {
                    continue;
                }
                int code = shape.codes[k];
                // Flared roots (bark at the trunk's base) stay on land: none reaches out over a lake or river.
                if (TreeShape.dy(p) == 0 && TreeBlocks.kind(code) == TreeBlocks.WOOD && !swamp && overWater(chunk, m, x, y, z)) {
                    continue;
                }
                m.set(x, y, z);
                place(level, chunk, m, code, swamp, stats);
            }
            // Trunks reach down onto solid ground; grass under them turns to dirt.
            for (int column : shape.trunk) {
                int x = spec.x() + TreeShape.dx(column);
                int z = spec.z() + TreeShape.dz(column);
                if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                    stats.extended += root(chunk, m, x, spec.y(), z, rootLog(shape, column), swamp, spec.has(TreeSpec.PODZOL));
                }
            }
            for (int g = 0; g < shape.groundColumns.length; g++) {
                int x = spec.x() + TreeShape.dx(shape.groundColumns[g]);
                int z = spec.z() + TreeShape.dz(shape.groundColumns[g]);
                if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                    ground(chunk, m, x, spec.y(), z, shape.groundCodes[g], mossEco, mossBlend, forests.seed());
                }
            }
            for (int s = 0; s < shape.shadeColumns.length; s++) {
                int x = spec.x() + TreeShape.dx(shape.shadeColumns[s]) - minX;
                int z = spec.z() + TreeShape.dz(shape.shadeColumns[s]) - minZ;
                if (x >= 0 && x < 16 && z >= 0 && z < 16) {
                    shade[z << 4 | x] += shape.shadeCounts[s];
                }
            }
        }
        Understory.place(level, chunk, model, forests.seed(), shade, stats);
        return stats;
    }

    /** The state the trunk is extended with below a trunk column: the trunk's own lowest block. */
    private static BlockState rootLog(TreeShape shape, int column) {
        for (int k = 0; k < shape.size(); k++) {
            int p = shape.positions[k];
            if (TreeShape.dy(p) == 0 && TreeShape.dx(p) == TreeShape.dx(column) && TreeShape.dz(p) == TreeShape.dz(column)) {
                int code = shape.codes[k];
                if (TreeBlocks.kind(code) == TreeBlocks.LOG) {
                    code = TreeBlocks.code(TreeBlocks.LOG, TreeBlocks.wood(code), TreeBlocks.AXIS_Y);
                }
                return state(code);
            }
        }
        return Blocks.OAK_LOG.defaultBlockState();
    }

    // ------------------------------------------------------------------ writing blocks

    private static void place(WorldGenLevel level, ChunkAccess chunk, BlockPos.MutableBlockPos m, int code, boolean swamp, Stats stats) {
        int kind = TreeBlocks.kind(code);
        BlockState old = chunk.getBlockState(m);
        boolean water = old.is(Blocks.WATER) && old.getFluidState().isSource();
        switch (kind) {
            case TreeBlocks.LOG, TreeBlocks.WOOD, TreeBlocks.STEM, TreeBlocks.STRIPPED, TreeBlocks.ROOT, TreeBlocks.EARTH -> {
                if (water ? !swamp : !(isOpen(old) || old.getBlock() instanceof LeavesBlock || isCap(old) || givesWay(old))) {
                    return;
                }
                clearDoublePlant(chunk, m, old);
                chunk.setBlockState(m, state(code), Block.UPDATE_CLIENTS);
                if (old.is(Blocks.BAMBOO)) {
                    clearBambooAbove(chunk, m);
                }
                stats.logs++;
            }
            case TreeBlocks.LEAVES, TreeBlocks.BROWN_CAP, TreeBlocks.RED_CAP -> {
                BlockState state = state(code);
                if (state.isAir()) {
                    // A leafless wood (charred).
                    return;
                }
                if (water) {
                    if (!swamp || kind != TreeBlocks.LEAVES) {
                        return;
                    }
                    state = state.setValue(LeavesBlock.WATERLOGGED, true);
                } else if (!isOpen(old) && !(kind == TreeBlocks.LEAVES && isCap(old))) {
                    // Leaves win over mushroom caps whichever came first, so their paths to their logs stay whole.
                    return;
                }
                clearDoublePlant(chunk, m, old);
                chunk.setBlockState(m, state, Block.UPDATE_CLIENTS);
                stats.leaves++;
            }
            case TreeBlocks.MOSS_CARPET -> {
                if (old.isAir()) {
                    BlockState below = chunk.getBlockState(m.below());
                    if (below.is(BlockTags.LOGS)) {
                        chunk.setBlockState(m, state(code), Block.UPDATE_CLIENTS);
                        stats.other++;
                    }
                }
            }
            case TreeBlocks.BEE_NEST -> {
                if (old.isAir()) {
                    BlockPos at = m.immutable();
                    level.setBlock(at, state(code), Block.UPDATE_CLIENTS);
                    long h = SimplexNoise.mix(at.asLong() ^ 0x426565734E657374L);
                    level.getBlockEntity(at, BlockEntityType.BEEHIVE).ifPresent(hive -> {
                        int bees = 2 + (int) ((h >>> 60) & 1);
                        for (int b = 0; b < bees; b++) {
                            hive.storeBee(BeehiveBlockEntity.Occupant.create((int) ((h >>> (8 * b)) & 0xFF) * 2));
                        }
                    });
                    stats.other++;
                }
            }
            default -> {
                if (old.isAir()) {
                    chunk.setBlockState(m, state(code), Block.UPDATE_CLIENTS);
                    stats.other++;
                }
            }
        }
    }

    private static boolean isCap(BlockState state) {
        return state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK);
    }

    /** Air, plants and snow layers: what tree blocks may replace (no fluids, no leaves). */
    static boolean isOpen(BlockState state) {
        if (state.isAir()) {
            return true;
        }
        if (!state.getFluidState().isEmpty() || state.getBlock() instanceof LeavesBlock) {
            return false;
        }
        // Every plant (grass, flowers, flower beds, berry bushes, mushrooms, saplings), snow layers, moss carpets.
        return state.is(BlockTags.REPLACEABLE_BY_TREES) || state.getBlock() instanceof VegetationBlock || state.is(Blocks.SNOW)
                || state.is(Blocks.MOSS_CARPET);
    }

    /** Melons, pumpkins and bamboo of the biomes' own patches give way to a trunk. */
    private static boolean givesWay(BlockState state) {
        return state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN) || state.is(Blocks.BAMBOO) || state.is(Blocks.BAMBOO_SAPLING);
    }

    /** A bamboo stalk cut by a log: the part above would stand on wood, so it goes too (same column). */
    private static void clearBambooAbove(ChunkAccess chunk, BlockPos.MutableBlockPos m) {
        int x = m.getX();
        int y = m.getY();
        int z = m.getZ();
        for (int yy = y + 1; yy <= chunk.getMaxY(); yy++) {
            m.set(x, yy, z);
            if (!chunk.getBlockState(m).is(Blocks.BAMBOO)) {
                break;
            }
            chunk.setBlockState(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        m.set(x, y, z);
    }

    /** Replacing one half of a double plant: the other half goes too (both lie in this column). */
    private static void clearDoublePlant(ChunkAccess chunk, BlockPos.MutableBlockPos m, BlockState old) {
        if (old.getBlock() instanceof DoublePlantBlock && old.hasProperty(DoublePlantBlock.HALF)) {
            int dy = old.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER ? 1 : -1;
            BlockPos other = m.above(dy);
            if (chunk.getBlockState(other).is(old.getBlock())) {
                chunk.setBlockState(other, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    /**
     * Extends a trunk column below its root down onto solid ground (at most 5 blocks), then turns
     * grass under it to dirt. Returns the number of logs added.
     */
    private static int root(ChunkAccess chunk, BlockPos.MutableBlockPos m, int x, int y, int z, BlockState log, boolean swamp, boolean podzol) {
        if (!swamp && overWater(chunk, m, x, y, z)) {
            return 0;
        }
        int added = 0;
        int yy = y - 1;
        for (; yy >= y - 5 && yy >= chunk.getMinY(); yy--) {
            m.set(x, yy, z);
            BlockState below = chunk.getBlockState(m);
            if (supports(below) || !rootPasses(below, swamp)) {
                break;
            }
            chunk.setBlockState(m, log, Block.UPDATE_CLIENTS);
            added++;
        }
        m.set(x, yy, z);
        BlockState ground = chunk.getBlockState(m);
        if (ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.MYCELIUM) || (!podzol && ground.is(Blocks.PODZOL))) {
            chunk.setBlockState(m, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        return added;
    }

    /** What a trunk grows down through to reach the ground. */
    private static boolean rootPasses(BlockState state, boolean swamp) {
        return isOpen(state) || state.is(Blocks.POWDER_SNOW) || (swamp && state.is(Blocks.WATER)) || state.getBlock() instanceof LeavesBlock
                || givesWay(state);
    }

    /** True if a trunk column's block at y would come to rest on water rather than on the ground. */
    private static boolean overWater(ChunkAccess chunk, BlockPos.MutableBlockPos m, int x, int y, int z) {
        for (int yy = y - 1; yy >= y - 6 && yy >= chunk.getMinY(); yy--) {
            m.set(x, yy, z);
            BlockState below = chunk.getBlockState(m);
            if (supports(below) || !rootPasses(below, false)) {
                return !below.getFluidState().isEmpty();
            }
        }
        return false;
    }

    /** Solid ground a trunk can stand on. */
    static boolean supports(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty() && !state.is(Blocks.POWDER_SNOW) && !(state.getBlock() instanceof LeavesBlock)
                && !(state.getBlock() instanceof VegetationBlock) && !givesWay(state)
                && !state.canBeReplaced() && state.isSolid();
    }

    /**
     * Ground changes around a tree (podzol, rooted and coarse dirt, moss) on the column's top soil.
     * Moss is kept in a share {@code eco} × how well it suits the soil (brown soils take it, grass
     * by the biome's colour, {@code blend}); elsewhere the column keeps its soil.
     */
    private static void ground(ChunkAccess chunk, BlockPos.MutableBlockPos m, int x, int y, int z, int code, double eco, double blend, long seed) {
        for (int yy = y + 3; yy >= y - 5; yy--) {
            m.set(x, yy, z);
            BlockState state = chunk.getBlockState(m);
            if (state.isAir() || isOpen(state) || state.getBlock() instanceof LeavesBlock || state.is(BlockTags.LOGS)) {
                continue;
            }
            if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL)
                    || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.MOSS_BLOCK)) {
                if (code == TreeBlocks.GROUND_MOSS) {
                    double soil = state.is(Blocks.GRASS_BLOCK) ? blend : Math.max(blend, 0.85);
                    if (Understory.unit(seed, x, z, 16) >= eco * soil) {
                        return;
                    }
                }
                BlockState target = switch (code) {
                    case TreeBlocks.GROUND_PODZOL -> Blocks.PODZOL.defaultBlockState();
                    case TreeBlocks.GROUND_COARSE_DIRT -> Blocks.COARSE_DIRT.defaultBlockState();
                    case TreeBlocks.GROUND_MOSS -> Blocks.MOSS_BLOCK.defaultBlockState();
                    default -> Blocks.ROOTED_DIRT.defaultBlockState();
                };
                chunk.setBlockState(m, target, Block.UPDATE_CLIENTS);
            }
            return;
        }
    }

    // ------------------------------------------------------------------ block states

    /** The vanilla block state of a tree block code. */
    public static BlockState state(int code) {
        return STATES.computeIfAbsent(code, ForestBlocks::buildState);
    }

    private static BlockState buildState(int code) {
        Wood wood = TreeBlocks.wood(code);
        int extra = TreeBlocks.extra(code);
        return switch (TreeBlocks.kind(code)) {
            case TreeBlocks.LOG -> log(wood).defaultBlockState().setValue(RotatedPillarBlock.AXIS,
                    extra == TreeBlocks.AXIS_X ? Direction.Axis.X : (extra == TreeBlocks.AXIS_Z ? Direction.Axis.Z : Direction.Axis.Y));
            case TreeBlocks.WOOD -> bark(wood).defaultBlockState();
            case TreeBlocks.LEAVES -> leaves(wood) instanceof LeavesBlock leaves ? leaves.defaultBlockState()
                    .setValue(LeavesBlock.DISTANCE, Math.max(1, Math.min(7, extra))).setValue(LeavesBlock.PERSISTENT, false)
                    .setValue(LeavesBlock.WATERLOGGED, false) : Blocks.AIR.defaultBlockState();
            case TreeBlocks.VINE -> Blocks.VINE.defaultBlockState().setValue(VineBlock.NORTH, (extra & 1) != 0).setValue(VineBlock.EAST, (extra & 2) != 0)
                    .setValue(VineBlock.SOUTH, (extra & 4) != 0).setValue(VineBlock.WEST, (extra & 8) != 0);
            case TreeBlocks.COCOA -> Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.FACING, HORIZONTAL[extra & 3]).setValue(CocoaBlock.AGE, Math.min(2, extra >> 2));
            case TreeBlocks.BEE_NEST -> Blocks.BEE_NEST.defaultBlockState().setValue(BeehiveBlock.FACING, HORIZONTAL[extra & 3]);
            // Stems: extra is the set of sides hidden against other stem blocks (vanilla's rule for faces between blocks of the same kind).
            case TreeBlocks.STEM -> Blocks.MUSHROOM_STEM.defaultBlockState().setValue(HugeMushroomBlock.UP, false).setValue(HugeMushroomBlock.DOWN, false)
                    .setValue(HugeMushroomBlock.NORTH, (extra & 1) == 0).setValue(HugeMushroomBlock.EAST, (extra & 2) == 0)
                    .setValue(HugeMushroomBlock.SOUTH, (extra & 4) == 0).setValue(HugeMushroomBlock.WEST, (extra & 8) == 0);
            case TreeBlocks.STRIPPED -> stripped(wood).defaultBlockState().setValue(RotatedPillarBlock.AXIS,
                    extra == TreeBlocks.AXIS_X ? Direction.Axis.X : (extra == TreeBlocks.AXIS_Z ? Direction.Axis.Z : Direction.Axis.Y));
            case TreeBlocks.ROOT -> Blocks.MANGROVE_ROOTS.defaultBlockState();
            case TreeBlocks.EARTH -> extra == 0 ? Blocks.ROOTED_DIRT.defaultBlockState() : (extra == 1 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT.defaultBlockState());
            case TreeBlocks.HANGING_ROOTS -> Blocks.HANGING_ROOTS.defaultBlockState();
            case TreeBlocks.BROWN_CAP, TreeBlocks.RED_CAP -> (TreeBlocks.kind(code) == TreeBlocks.BROWN_CAP ? Blocks.BROWN_MUSHROOM_BLOCK : Blocks.RED_MUSHROOM_BLOCK)
                    .defaultBlockState().setValue(HugeMushroomBlock.NORTH, (extra & 1) != 0).setValue(HugeMushroomBlock.EAST, (extra & 2) != 0)
                    .setValue(HugeMushroomBlock.SOUTH, (extra & 4) != 0).setValue(HugeMushroomBlock.WEST, (extra & 8) != 0)
                    .setValue(HugeMushroomBlock.UP, (extra & 16) != 0).setValue(HugeMushroomBlock.DOWN, (extra & 32) != 0);
            case TreeBlocks.MOSS_CARPET -> Blocks.MOSS_CARPET.defaultBlockState();
            case TreeBlocks.MUSHROOM -> extra == 0 ? Blocks.BROWN_MUSHROOM.defaultBlockState() : Blocks.RED_MUSHROOM.defaultBlockState();
            default -> Blocks.AIR.defaultBlockState();
        };
    }

    /** A block of another mod (its biomes' woods), or a vanilla stand-in if the mod is absent (never used then). */
    private static Block mod(ModBlock block, Block fallback) {
        Block b = CompatBlocks.block(block);
        return b != null ? b : fallback;
    }

    static Block log(Wood wood) {
        return switch (wood) {
            case OAK -> Blocks.OAK_LOG;
            case BIRCH -> Blocks.BIRCH_LOG;
            case SPRUCE -> Blocks.SPRUCE_LOG;
            case DARK_OAK -> Blocks.DARK_OAK_LOG;
            case JUNGLE -> Blocks.JUNGLE_LOG;
            case ACACIA -> Blocks.ACACIA_LOG;
            case MUSHROOM -> Blocks.MUSHROOM_STEM;
            case DUSK -> mod(ModBlock.DUSK_LOG, Blocks.OAK_LOG);
            case CHARRED -> mod(ModBlock.CHARRED_LOG, Blocks.OAK_LOG);
        };
    }

    static Block stripped(Wood wood) {
        return switch (wood) {
            case OAK -> Blocks.STRIPPED_OAK_LOG;
            case BIRCH -> Blocks.STRIPPED_BIRCH_LOG;
            case SPRUCE -> Blocks.STRIPPED_SPRUCE_LOG;
            case DARK_OAK -> Blocks.STRIPPED_DARK_OAK_LOG;
            case JUNGLE -> Blocks.STRIPPED_JUNGLE_LOG;
            case ACACIA -> Blocks.STRIPPED_ACACIA_LOG;
            case MUSHROOM -> Blocks.MUSHROOM_STEM;
            case DUSK -> mod(ModBlock.DUSK_LOG, Blocks.STRIPPED_OAK_LOG);
            case CHARRED -> mod(ModBlock.CHARRED_LOG, Blocks.STRIPPED_OAK_LOG);
        };
    }

    static Block bark(Wood wood) {
        return switch (wood) {
            case OAK -> Blocks.OAK_WOOD;
            case BIRCH -> Blocks.BIRCH_WOOD;
            case SPRUCE -> Blocks.SPRUCE_WOOD;
            case DARK_OAK -> Blocks.DARK_OAK_WOOD;
            case JUNGLE -> Blocks.JUNGLE_WOOD;
            case ACACIA -> Blocks.ACACIA_WOOD;
            case MUSHROOM -> Blocks.MUSHROOM_STEM;
            case DUSK -> mod(ModBlock.DUSK_LOG, Blocks.OAK_WOOD);
            case CHARRED -> mod(ModBlock.CHARRED_LOG, Blocks.OAK_WOOD);
        };
    }

    static Block leaves(Wood wood) {
        return switch (wood) {
            case OAK -> Blocks.OAK_LEAVES;
            case BIRCH -> Blocks.BIRCH_LEAVES;
            case SPRUCE -> Blocks.SPRUCE_LEAVES;
            case DARK_OAK -> Blocks.DARK_OAK_LEAVES;
            case JUNGLE -> Blocks.JUNGLE_LEAVES;
            case ACACIA -> Blocks.ACACIA_LEAVES;
            case MUSHROOM -> Blocks.BROWN_MUSHROOM_BLOCK;
            case DUSK -> mod(ModBlock.DUSK_LEAVES, Blocks.OAK_LEAVES);
            case CHARRED -> Blocks.AIR;
        };
    }

    // ------------------------------------------------------------------ structures

    /**
     * Keeps trees out of structures. A tree is left out whole if its footprint (its blocks' x and z
     * range, one block wider) overlaps a piece of a surface structure (villages, outposts, temples,
     * huts, igloos, ruined portals, shipwrecks, mansions, and other mods' structures of that step)
     * that starts within 7 chunks of the tree's own chunk. The same structures are seen from every
     * chunk the tree reaches (those are within one chunk of the tree's, so every start is within
     * the 8 chunks a decorating region can read), so every chunk decides the same. Only the
     * horizontal extent counts: vanilla moves some pieces up or down when it places them (scattered
     * features, buried treasure, shipwrecks), so their heights depend on whether their chunk is
     * decorated yet, while their x and z never change. Underground structures (mineshafts,
     * strongholds, trial chambers, ancient cities, buried ruins) keep trees off only where a
     * piece reaches up to within 3 blocks of the tree's ground (since Agent 7: the entrance of
     * Sincerelyuq's Biomes' strata sanctum, buried ruins breaking the surface); deeper ones do
     * not keep forests off the ground above them.
     */
    static final class StructureGuard {
        private static final int START_RANGE = 7;
        private final WorldGenLevel level;
        private final Map<Long, List<BoundingBox>> pieces = new HashMap<>();
        private final Map<Long, List<BoundingBox>> underground = new HashMap<>();

        StructureGuard(WorldGenLevel level) {
            this.level = level;
        }

        boolean blocked(TreeSpec spec, TreeShape shape) {
            int cx = spec.x() >> 4;
            int cz = spec.z() >> 4;
            long key = ChunkPos.asLong(cx, cz);
            List<BoundingBox> boxes = this.pieces.get(key);
            if (boxes == null) {
                List<BoundingBox> deep = new ArrayList<>();
                boxes = this.collect(cx, cz, deep);
                this.pieces.put(key, boxes);
                this.underground.put(key, deep);
            }
            List<BoundingBox> deep = this.underground.get(key);
            if (boxes.isEmpty() && deep.isEmpty()) {
                return false;
            }
            int x0 = spec.x() + shape.minX - 1;
            int x1 = spec.x() + shape.maxX + 1;
            int z0 = spec.z() + shape.minZ - 1;
            int z1 = spec.z() + shape.maxZ + 1;
            for (BoundingBox b : boxes) {
                if (b.maxX() >= x0 && b.minX() <= x1 && b.maxZ() >= z0 && b.minZ() <= z1) {
                    return true;
                }
            }
            for (BoundingBox b : deep) {
                if (b.maxX() >= x0 && b.minX() <= x1 && b.maxZ() >= z0 && b.minZ() <= z1 && b.maxY() >= spec.y() - 4) {
                    return true;
                }
            }
            return false;
        }

        /** Surface structures' pieces; underground structures' pieces go to {@code deep}. */
        private List<BoundingBox> collect(int cx, int cz, List<BoundingBox> deep) {
            List<BoundingBox> boxes = new ArrayList<>();
            ChunkAccess root = this.level.getChunk(cx, cz, ChunkStatus.STRUCTURE_REFERENCES, false);
            if (root == null) {
                return boxes;
            }
            for (Map.Entry<Structure, LongSet> entry : root.getAllReferences().entrySet()) {
                List<BoundingBox> into = entry.getKey().step() == GenerationStep.Decoration.SURFACE_STRUCTURES ? boxes : deep;
                for (long start : entry.getValue()) {
                    int sx = ChunkPos.getX(start);
                    int sz = ChunkPos.getZ(start);
                    if (Math.max(Math.abs(sx - cx), Math.abs(sz - cz)) > START_RANGE) {
                        continue;
                    }
                    ChunkAccess holder = this.level.getChunk(sx, sz, ChunkStatus.STRUCTURE_STARTS, false);
                    StructureStart s = holder == null ? null : holder.getStartForStructure(entry.getKey());
                    if (s == null || !s.isValid()) {
                        continue;
                    }
                    for (StructurePiece piece : s.getPieces()) {
                        into.add(piece.getBoundingBox());
                    }
                }
            }
            return boxes;
        }
    }

    // ------------------------------------------------------------------ ground vegetation

    /** Ground vegetation of one chunk's columns. */
    static final class Understory {
        private Understory() {
        }

        static void place(WorldGenLevel level, ChunkAccess chunk, ForestModel model, long seed, int[] shade, Stats stats) {
            double amount = model.settings().understory();
            if (amount <= 0.0) {
                return;
            }
            ChunkPos pos = chunk.getPos();
            int minX = pos.getMinBlockX();
            int minZ = pos.getMinBlockZ();
            ForestSample[] cells = new ForestSample[16];
            for (int c = 0; c < 16; c++) {
                cells[c] = model.sample(minX + (c & 3) * 4 + 2, minZ + (c >> 2) * 4 + 2, false);
            }
            // How well moss suits each cell's grass (its biome's colour), looked up when first needed.
            double[] blends = new double[16];
            java.util.Arrays.fill(blends, Double.NaN);
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int j = 0; j < 16; j++) {
                for (int i = 0; i < 16; i++) {
                    ForestSample s = cells[(j >> 2) * 4 + (i >> 2)];
                    if (s.type() == null || s.share() < 0.25 || s.treeline() <= 0.05) {
                        continue;
                    }
                    int x = minX + i;
                    int z = minZ + j;
                    int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                    // Down through the canopy to the ground.
                    int floor = Math.max(chunk.getMinY(), y - 48);
                    BlockState ground = null;
                    for (; y > floor; y--) {
                        m.set(x, y, z);
                        BlockState state = chunk.getBlockState(m);
                        if (state.isAir() || state.getBlock() instanceof LeavesBlock || state.is(BlockTags.LOGS) || state.is(Blocks.VINE)
                                || state.is(Blocks.COCOA) || state.is(Blocks.BEE_NEST) || isOpen(state)) {
                            continue;
                        }
                        ground = state;
                        break;
                    }
                    if (ground == null || !ground.is(BlockTags.DIRT)) {
                        continue;
                    }
                    m.set(x, y + 1, z);
                    if (!chunk.getBlockState(m).isAir()) {
                        continue;
                    }
                    double canopy = Math.min(1.0, shade[j << 4 | i] / 4.0);
                    int cell = (j >> 2) * 4 + (i >> 2);
                    if (Double.isNaN(blends[cell])) {
                        blends[cell] = model.mossBlend(x, y, z);
                    }
                    int soil = floor(s, canopy, x, z, seed, model.settings(), ground, blends[cell]);
                    if (soil == MOSS_BLOCK || soil == MOSS_EDGE) {
                        if (soil == MOSS_BLOCK) {
                            m.set(x, y, z);
                            chunk.setBlockState(m, Blocks.MOSS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                        // A carpet at the clump's edge, and now and then on its core.
                        if (soil == MOSS_EDGE || (hash(seed, x, z, 9) & 7) == 0) {
                            m.set(x, y + 1, z);
                            chunk.setBlockState(m, Blocks.MOSS_CARPET.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                        stats.plants++;
                        continue;
                    }
                    if (soil != NO_CHANGE) {
                        ground = soil == PODZOL ? Blocks.PODZOL.defaultBlockState() : (soil == COARSE ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.MUD.defaultBlockState());
                        m.set(x, y, z);
                        chunk.setBlockState(m, ground, Block.UPDATE_CLIENTS);
                        stats.plants++;
                    }
                    BlockState plant = choose(s, canopy, x, z, seed, amount, ground);
                    if (plant == null) {
                        continue;
                    }
                    BlockPos at = new BlockPos(x, y + 1, z);
                    if (!plant.canSurvive(level, at)) {
                        continue;
                    }
                    if (plant.getBlock() instanceof DoublePlantBlock) {
                        BlockPos upper = at.above();
                        if (!chunk.getBlockState(upper).isAir()) {
                            continue;
                        }
                        chunk.setBlockState(at, plant.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS);
                        chunk.setBlockState(upper, plant.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
                    } else {
                        chunk.setBlockState(at, plant, Block.UPDATE_CLIENTS);
                    }
                    stats.plants++;
                }
            }
        }

        static long hash(long seed, int x, int z, int channel) {
            return SimplexNoise.mix(seed ^ x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ channel * 0x165667B19E3779F9L);
        }

        static double unit(long seed, int x, int z, int channel) {
            return (hash(seed, x, z, channel) >>> 11) * 0x1.0p-53;
        }

        /** Smooth patches (value noise at {@code scale} blocks), 0..1. */
        static double patch(long seed, int x, int z, double scale, int channel) {
            double fx = x / scale;
            double fz = z / scale;
            int ix = (int) Math.floor(fx);
            int iz = (int) Math.floor(fz);
            double tx = fx - ix;
            double tz = fz - iz;
            tx = tx * tx * (3 - 2 * tx);
            tz = tz * tz * (3 - 2 * tz);
            double a = unit(seed, ix, iz, channel);
            double b = unit(seed, ix + 1, iz, channel);
            double c = unit(seed, ix, iz + 1, channel);
            double d = unit(seed, ix + 1, iz + 1, channel);
            return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz;
        }

        static final int NO_CHANGE = 0;
        static final int MOSS_BLOCK = 1;
        static final int MOSS_EDGE = 2;
        static final int PODZOL = 3;
        static final int COARSE = 4;
        static final int MUD = 5;

        /**
         * The top soil of a column: moss, a dark floor of podzol and coarse dirt, mud, or unchanged.
         *
         * <p>Moss grows in small clumps (~3 blocks) gathered in colonies (~23 blocks), where the
         * community grows moss ({@link ForestModel#mossEcology}: old growth taiga most, the dark
         * forest little), on old, wet, shaded ground, and only as far as it suits the soil: brown
         * soils (podzol, coarse dirt, mud) take it, grass only as far as moss blends into the
         * biome's grass colour ({@code blend}), so bright moss stays off the dark grass of dark
         * forests and swamps (swamps keep a little at the water's edge). Weak spots get a thin
         * carpet, only the cores of dense clumps moss blocks. Dark forests get podzol patches and
         * specks of coarse dirt under their closed canopy instead; swamps mud at the water.
         */
        static int floor(ForestSample s, double canopy, int x, int z, long seed, ForestSettings settings, BlockState ground, double blend) {
            ForestType type = s.type();
            if (s.temperature() < -0.45) {
                return NO_CHANGE;
            }
            boolean grass = ground.is(Blocks.GRASS_BLOCK);
            boolean brown = ground.is(Blocks.PODZOL) || ground.is(Blocks.COARSE_DIRT) || ground.is(Blocks.MUD) || ground.is(Blocks.ROOTED_DIRT);
            double eco = ForestModel.mossEcology(type) * settings.moss();
            if (eco > 0.0 && (grass || brown)) {
                double wet = Math.max(smooth(-0.1, 0.6, s.moisture()), 0.8 * s.riparian());
                double soil = grass ? Math.max(blend, type == ForestType.SWAMP ? 0.3 * s.riparian() : 0.0) : Math.max(blend, 0.85);
                double chance = eco * (0.35 + 0.65 * s.old()) * wet * (0.4 + 0.6 * canopy) * soil;
                if (chance > 0.004) {
                    double colony = smooth(0.5, 0.72, patch(seed, x, z, 23.0, 10));
                    if (colony > 0.0) {
                        double clump = patch(seed, x, z, 3.3, 11);
                        double threshold = 1.0 - 0.55 * Math.min(1.0, chance) * colony;
                        if (clump > threshold) {
                            return clump > threshold + 0.1 && clump > 0.55 ? MOSS_BLOCK : MOSS_EDGE;
                        }
                    }
                }
            }
            if (!grass) {
                return NO_CHANGE;
            }
            // A dark floor under closed canopies: podzol patches, specks of coarse dirt (dark forests most).
            if (type == ForestType.DARK || type.closed() && canopy >= 1.0 && s.old() > 0.6) {
                double strength = type == ForestType.DARK ? 1.0 : 0.35;
                if (patch(seed, x, z, 6.5, 12) > 1.0 - (0.22 + 0.12 * canopy) * strength) {
                    return PODZOL;
                }
                if (unit(seed, x, z, 13) < 0.025 * canopy * strength) {
                    return COARSE;
                }
            }
            if (type == ForestType.SWAMP && s.riparian() > 0.55 && patch(seed, x, z, 5.0, 15) > 0.62) {
                return MUD;
            }
            return NO_CHANGE;
        }

        private static double smooth(double a, double b, double v) {
            double t = Math.max(0.0, Math.min(1.0, (v - a) / (b - a)));
            return t * t * (3.0 - 2.0 * t);
        }

        private static BlockState choose(ForestSample s, double canopy, int x, int z, long seed, double amount, BlockState ground) {
            ForestType type = s.type();
            double u = unit(seed, x, z, 1) / amount;
            double moist = Math.max(0.0, Math.min(1.0, (s.moisture() + 0.4) / 0.9));
            double edge = 1.0 - Math.min(1.0, s.density() / 0.5);
            boolean snowy = s.temperature() < -0.45;
            double clump = 0.35 + 1.3 * patch(seed, x, z, 6.0, 2);
            // Deep shade under a closed canopy keeps the floor sparse.
            double thin = canopy >= 1.0 && s.density() > 0.8 ? 0.55 : 1.0;
            // Mushrooms come up in groups, more of them on podzol.
            double fungi = (0.25 + 3.0 * smooth(0.62, 0.85, patch(seed, x, z, 5.0, 14))) * (ground.is(Blocks.PODZOL) ? 2.0 : 1.0);
            if (snowy) {
                return u < 0.035 * clump * thin ? Blocks.FERN.defaultBlockState() : null;
            }
            switch (type) {
                case TEMPERATE, FLOWER, BIRCH, OLD_BIRCH, DARK, SWAMP -> {
                    double litter = (0.05 + 0.42 * canopy) * (type == ForestType.SWAMP ? 0.3 : 1.0);
                    if (u < litter * (0.6 + 0.8 * patch(seed, x, z, 4.0, 4))) {
                        long h = hash(seed, x, z, 5);
                        return Blocks.LEAF_LITTER.defaultBlockState().setValue(LeafLitterBlock.FACING, HORIZONTAL[(int) (h & 3)])
                                .setValue(BlockStateProperties.SEGMENT_AMOUNT, 1 + (int) ((h >>> 2) & 3));
                    }
                    u -= litter;
                    double fern = (0.02 + 0.07 * moist * canopy) * clump * thin;
                    if (u < fern) {
                        return Blocks.FERN.defaultBlockState();
                    }
                    u -= fern;
                    double mushroom = (type == ForestType.DARK ? 0.02 : 0.006) * canopy * thin * fungi;
                    if (u < mushroom && canopy >= 0.75) {
                        return (hash(seed, x, z, 6) & 1) == 0 ? Blocks.BROWN_MUSHROOM.defaultBlockState() : Blocks.RED_MUSHROOM.defaultBlockState();
                    }
                    u -= mushroom;
                    double grass = (0.03 + 0.12 * (1.0 - canopy) + 0.08 * edge) * clump;
                    if (u < grass) {
                        return (hash(seed, x, z, 7) % 5) == 0 ? Blocks.TALL_GRASS.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState();
                    }
                    u -= grass;
                    double bush = 0.006 + 0.03 * edge;
                    return u < bush ? Blocks.BUSH.defaultBlockState() : null;
                }
                case BOREAL, OLD_SPRUCE, OLD_PINE, GROVE, HIGHLAND -> {
                    double fern = (0.05 + 0.05 * s.old() + 0.04 * moist) * clump * thin;
                    if (u < fern) {
                        return (hash(seed, x, z, 7) % 3) == 0 || s.old() > 0.5 && (hash(seed, x, z, 7) & 1) == 0
                                ? Blocks.LARGE_FERN.defaultBlockState() : Blocks.FERN.defaultBlockState();
                    }
                    u -= fern;
                    double grass = (0.02 + 0.06 * (1.0 - canopy)) * clump;
                    if (u < grass) {
                        return Blocks.SHORT_GRASS.defaultBlockState();
                    }
                    u -= grass;
                    double mushroom = 0.004 * canopy * fungi;
                    return u < mushroom && canopy >= 0.75 ? Blocks.BROWN_MUSHROOM.defaultBlockState() : null;
                }
                case JUNGLE, SPARSE_JUNGLE -> {
                    double fern = 0.05 * clump * thin;
                    if (u < fern) {
                        return (hash(seed, x, z, 7) % 3) == 0 ? Blocks.LARGE_FERN.defaultBlockState() : Blocks.FERN.defaultBlockState();
                    }
                    u -= fern;
                    return u < 0.03 * clump ? Blocks.SHORT_GRASS.defaultBlockState() : null;
                }
                case SAVANNA, DRY -> {
                    if (s.temperature() > 0.4 && u < 0.05 * clump) {
                        return (hash(seed, x, z, 7) % 4) == 0 ? Blocks.TALL_DRY_GRASS.defaultBlockState() : Blocks.SHORT_DRY_GRASS.defaultBlockState();
                    }
                    return null;
                }
                case GRASSLAND -> {
                    if (canopy > 0.3 && u < 0.05 * canopy * clump) {
                        return (hash(seed, x, z, 7) & 1) == 0 ? Blocks.FERN.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState();
                    }
                    return null;
                }
                case DUSK -> {
                    // A shaded, damp floor: ferns, mushrooms in groups (more on podzol), grass in the gaps.
                    double fern = (0.04 + 0.08 * moist * canopy) * clump * thin;
                    if (u < fern) {
                        return (hash(seed, x, z, 7) % 4) == 0 ? Blocks.LARGE_FERN.defaultBlockState() : Blocks.FERN.defaultBlockState();
                    }
                    u -= fern;
                    double mushroom = 0.03 * canopy * thin * fungi;
                    if (u < mushroom && canopy >= 0.5) {
                        return (hash(seed, x, z, 6) % 3) == 0 ? Blocks.RED_MUSHROOM.defaultBlockState() : Blocks.BROWN_MUSHROOM.defaultBlockState();
                    }
                    u -= mushroom;
                    double grass = (0.02 + 0.1 * (1.0 - canopy) + 0.06 * edge) * clump;
                    return u < grass ? Blocks.SHORT_GRASS.defaultBlockState() : null;
                }
                case CHARRED -> {
                    // Burnt ground: a dead bush here and there.
                    return u < 0.006 * clump ? Blocks.DEAD_BUSH.defaultBlockState() : null;
                }
                default -> {
                    return null;
                }
            }
        }
    }

    static boolean isWaterSource(BlockState state) {
        return state.getFluidState().is(Fluids.WATER) && state.getFluidState().isSource();
    }
}
