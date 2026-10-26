package com.sincerelyuq.vastlands.worldgen.nature;

import static com.sincerelyuq.vastlands.worldgen.nature.FormationBlocks.EMBED;
import static com.sincerelyuq.vastlands.worldgen.nature.FormationBlocks.EXPOSED;
import static com.sincerelyuq.vastlands.worldgen.nature.FormationBlocks.SNAP_CEILING;
import static com.sincerelyuq.vastlands.worldgen.nature.FormationBlocks.SNAP_FLOOR;
import static com.sincerelyuq.vastlands.worldgen.nature.FormationBlocks.TOP;
import static com.sincerelyuq.vastlands.worldgen.nature.FormationBlocks.WET;

import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * The block step of the natural formations: each chunk builds its part of every formation that
 * reaches it. A formation is a pure function of the model, so the chunks it spans each place
 * exactly their own blocks of the same shape: formations cross chunk borders whole, nothing is
 * written outside the chunk being decorated, and the result does not depend on generation order.
 *
 * <ul>
 *     <li>Rock and other solid blocks go into open space: air, plants, snow layers; into water
 *     only where the formation stands in it ({@code WET}: never lava, except basalt columns
 *     rising out of volcanic lava); into natural ground only where they are sunk into it
 *     ({@code EMBED}: stone, deepslate, dirt, sand, gravel, terracotta ... never anything built).
 *     Decorations go into air only (waterloggable ones into still water, waterlogged).</li>
 *     <li>Feet and hangers: the lowest block of a footed column extends down to the ground, the
 *     highest of a hanging one up to the ceiling, through open space of that column only.</li>
 *     <li>Snapped stacks (plants, carpets, veins, rubble on cave floors and ceilings) find the
 *     floor or ceiling of their own column near their height and move there together.</li>
 *     <li>Structures: a formation is left out whole if its footprint meets a piece of any
 *     structure starting within 7 chunks of the formation's own chunk (6 for the wonders that
 *     reach beyond the neighbouring chunks: villages, ruins, ancient cities, mineshafts, trial
 *     chambers, other mods' and datapacks' structures); surface ones by their horizontal extent
 *     (some pieces move up and down when placed), cave ones in 3D.</li>
 * </ul>
 * Rock is picked from the formation's palette by position (smooth patches of each stone, moss on
 * the exposed faces of weathered rock, sculk on deep dark rock), so every chunk picks the same.
 * Blocks are written without neighbour updates, like vanilla's features; light comes later.
 */
public final class NatureBlocks {
    private static final Map<Integer, BlockState> STATES = new ConcurrentHashMap<>();
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final Direction[] FACES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    /** Badlands' terracotta bands, bottom to top (repeating). */
    private static final BlockState[] BANDS = {
            Blocks.TERRACOTTA.defaultBlockState(), Blocks.ORANGE_TERRACOTTA.defaultBlockState(), Blocks.ORANGE_TERRACOTTA.defaultBlockState(),
            Blocks.RED_TERRACOTTA.defaultBlockState(), Blocks.YELLOW_TERRACOTTA.defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState(),
            Blocks.WHITE_TERRACOTTA.defaultBlockState(), Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState(), Blocks.ORANGE_TERRACOTTA.defaultBlockState(),
            Blocks.BROWN_TERRACOTTA.defaultBlockState(), Blocks.RED_TERRACOTTA.defaultBlockState(), Blocks.ORANGE_TERRACOTTA.defaultBlockState(),
    };

    private NatureBlocks() {
    }

    /** Counts of what one chunk received (tests and diagnostics). */
    public static final class Stats {
        public int formations;
        public int skippedForStructures;
        public int blocks;
    }

    /**
     * Builds the chunk's part of every surface ({@code surface}) or cave formation reaching it;
     * {@code density} below 1 leaves out a share of them (by their identity, so the same in every
     * chunk).
     */
    public static Stats decorate(WorldGenLevel level, ChunkAccess chunk, VastlandsNature nature, boolean surface, double density) {
        Stats stats = new Stats();
        NatureModel model = nature.model();
        if (model == null) {
            return stats;
        }
        ChunkPos pos = chunk.getPos();
        int minX = pos.getMinBlockX();
        int minZ = pos.getMinBlockZ();
        int maxX = minX + 15;
        int maxZ = minZ + 15;
        List<FormationSpec> specs = new ArrayList<>();
        model.formations(minX, minZ, maxX, maxZ, surface, specs::add);
        if (specs.isEmpty()) {
            return stats;
        }
        FormationShapes.World world = model.world();
        StructureGuard guard = new StructureGuard(level, surface);
        Writer writer = new Writer(level, chunk, stats);
        for (FormationSpec spec : specs) {
            if (density < 1.0 && (SimplexNoise.mix(spec.key() ^ 0x44656E73L) >>> 11) * 0x1.0p-53 >= density) {
                continue;
            }
            FormationShape shape = model.shape(spec, world);
            if (shape.isEmpty() || spec.x() + shape.maxX < minX || spec.x() + shape.minX > maxX || spec.z() + shape.maxZ < minZ
                    || spec.z() + shape.minZ > maxZ) {
                continue;
            }
            if (guard.blocked(spec, shape)) {
                stats.skippedForStructures++;
                continue;
            }
            stats.formations++;
            writer.write(spec, shape);
        }
        return stats;
    }

    // ------------------------------------------------------------------ writing

    private static final class Writer {
        final WorldGenLevel level;
        final ChunkAccess chunk;
        final Stats stats;
        final int minX;
        final int minZ;
        final int bottom;
        final int top;
        final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        /** Columns (and their Y range) whose pointed dripstone changed while writing a formation. */
        private final Map<Integer, int[]> tips = new HashMap<>();

        Writer(WorldGenLevel level, ChunkAccess chunk, Stats stats) {
            this.level = level;
            this.chunk = chunk;
            this.stats = stats;
            this.minX = chunk.getPos().getMinBlockX();
            this.minZ = chunk.getPos().getMinBlockZ();
            this.bottom = chunk.getMinY();
            this.top = chunk.getMaxY();
        }

        boolean inside(int x, int y, int z) {
            return x >= this.minX && x < this.minX + 16 && z >= this.minZ && z < this.minZ + 16 && y >= this.bottom && y <= this.top;
        }

        void write(FormationSpec spec, FormationShape shape) {
            // Snapped stacks by column (within the chunk), the rest in two passes: solids, then decorations.
            Map<Integer, List<int[]>> floors = new HashMap<>();
            Map<Integer, List<int[]>> ceilings = new HashMap<>();
            List<int[]> held = new ArrayList<>();
            Map<Integer, int[]> tips = this.tips;
            tips.clear();
            for (int pass = 0; pass < 2; pass++) {
                for (int k = 0; k < shape.size(); k++) {
                    int p = shape.positions[k];
                    int code = shape.codes[k];
                    int x = spec.x() + FormationShape.dx(p);
                    int z = spec.z() + FormationShape.dz(p);
                    int y = spec.y() + FormationShape.dy(p);
                    if (!this.inside(x, y, z)) {
                        continue;
                    }
                    if (FormationBlocks.has(code, SNAP_FLOOR) || FormationBlocks.has(code, SNAP_CEILING)) {
                        if (pass == 0) {
                            Map<Integer, List<int[]>> stacks = FormationBlocks.has(code, SNAP_FLOOR) ? floors : ceilings;
                            stacks.computeIfAbsent((z - this.minZ) << 4 | (x - this.minX), c -> new ArrayList<>()).add(new int[] {y, code});
                        }
                        continue;
                    }
                    boolean solid = FormationBlocks.isSolid(code);
                    if (solid == (pass == 0)) {
                        if (!solid && heldVertically(code)) {
                            held.add(new int[] {x, y, z, code});
                        } else {
                            this.place(x, y, z, code, false);
                        }
                    }
                }
                if (pass == 0) {
                    this.extend(spec, shape.feet, shape.footCodes, shape.footDepths, -1);
                    this.extend(spec, shape.hangers, shape.hangerCodes, shape.hangerHeights, 1);
                }
            }
            // Decorations held by the block above or below them (tips, vines, roots, carpets): from
            // their support outwards, each only where it holds (a body block that stayed out, under
            // water for example, leaves no tip hanging from nothing). Their support lies in their own
            // column, so this reads only the chunk's own blocks.
            held.sort((a, b) -> {
                boolean ha = hangs(a[3]);
                boolean hb = hangs(b[3]);
                if (ha != hb) {
                    return ha ? -1 : 1;
                }
                return ha ? Integer.compare(b[1], a[1]) : Integer.compare(a[1], b[1]);
            });
            for (int[] cell : held) {
                if (this.place(cell[0], cell[1], cell[2], cell[3], true) && FormationBlocks.kind(cell[3]) == FormationBlocks.POINTED) {
                    this.tipped(tips, cell[0], cell[1], cell[2]);
                }
            }
            for (Map.Entry<Integer, List<int[]>> e : floors.entrySet()) {
                this.snap(e.getKey(), e.getValue(), true, tips);
            }
            for (Map.Entry<Integer, List<int[]>> e : ceilings.entrySet()) {
                this.snap(e.getKey(), e.getValue(), false, tips);
            }
            for (Map.Entry<Integer, int[]> e : tips.entrySet()) {
                this.retip(this.minX + (e.getKey() & 15), this.minZ + (e.getKey() >> 4), e.getValue()[0], e.getValue()[1]);
            }
        }

        /** Remembers the Y range of the pointed dripstone placed in a column. */
        private void tipped(Map<Integer, int[]> tips, int x, int y, int z) {
            int[] range = tips.computeIfAbsent((z - this.minZ) << 4 | (x - this.minX), k -> new int[] {y, y});
            range[0] = Math.min(range[0], y);
            range[1] = Math.max(range[1], y);
        }

        /**
         * The thickness of the pointed dripstone of a column as vanilla computes it from its
         * neighbours ({@code PointedDripstoneBlock.calculateDripstoneThickness}): a stack cut short
         * still ends in a tip, and nothing changes at the first block update.
         */
        private void retip(int x, int z, int y0, int y1) {
            while (y0 - 1 >= this.bottom && this.chunk.getBlockState(this.m.set(x, y0 - 1, z)).is(Blocks.POINTED_DRIPSTONE)) {
                y0--;
            }
            while (y1 + 1 <= this.top && this.chunk.getBlockState(this.m.set(x, y1 + 1, z)).is(Blocks.POINTED_DRIPSTONE)) {
                y1++;
            }
            // Each block's thickness follows from the next one towards its tip: tips first.
            for (int y = y0; y <= y1; y++) {
                this.thickness(x, y, z, Direction.DOWN);
            }
            for (int y = y1; y >= y0; y--) {
                this.thickness(x, y, z, Direction.UP);
            }
        }

        private void thickness(int x, int y, int z, Direction dir) {
            BlockState state = this.chunk.getBlockState(this.m.set(x, y, z));
            if (!pointed(state, dir)) {
                return;
            }
            BlockState next = this.stateAt(x, y + dir.getStepY(), z);
            DripstoneThickness t;
            if (pointed(next, dir.getOpposite())) {
                t = state.getValue(PointedDripstoneBlock.THICKNESS) == DripstoneThickness.TIP_MERGE
                        || next.getValue(PointedDripstoneBlock.THICKNESS) == DripstoneThickness.TIP_MERGE ? DripstoneThickness.TIP_MERGE : DripstoneThickness.TIP;
            } else if (!pointed(next, dir)) {
                t = DripstoneThickness.TIP;
            } else {
                DripstoneThickness n = next.getValue(PointedDripstoneBlock.THICKNESS);
                if (n != DripstoneThickness.TIP && n != DripstoneThickness.TIP_MERGE) {
                    t = pointed(this.stateAt(x, y - dir.getStepY(), z), dir) ? DripstoneThickness.MIDDLE : DripstoneThickness.BASE;
                } else {
                    t = DripstoneThickness.FRUSTUM;
                }
            }
            if (state.getValue(PointedDripstoneBlock.THICKNESS) != t) {
                this.chunk.setBlockState(this.m.set(x, y, z), state.setValue(PointedDripstoneBlock.THICKNESS, t), Block.UPDATE_CLIENTS);
            }
        }

        private BlockState stateAt(int x, int y, int z) {
            return y < this.bottom || y > this.top ? Blocks.AIR.defaultBlockState() : this.chunk.getBlockState(this.m.set(x, y, z));
        }

        /** Feet (dir -1) down to the ground, hangers (dir 1) up to the ceiling, through open space of their column. */
        private void extend(FormationSpec spec, int[] at, int[] codes, int[] depths, int dir) {
            for (int n = 0; n < at.length; n++) {
                int x = spec.x() + FormationShape.dx(at[n]);
                int z = spec.z() + FormationShape.dz(at[n]);
                int y = spec.y() + FormationShape.dy(at[n]);
                if (!this.inside(x, y, z)) {
                    continue;
                }
                // Only under (over) a block the formation actually placed there.
                BlockState end = this.chunk.getBlockState(this.m.set(x, y, z));
                if (end.isAir() || !end.getFluidState().isEmpty()) {
                    continue;
                }
                int code = codes[n] & ~EMBED;
                for (int k = 1; k <= depths[n]; k++) {
                    int yy = y + dir * k;
                    if (yy < this.bottom || yy > this.top) {
                        break;
                    }
                    BlockState old = this.chunk.getBlockState(this.m.set(x, yy, z));
                    boolean water = old.is(Blocks.WATER) && old.getFluidState().isSource();
                    if (!(open(old) || water && FormationBlocks.has(code, WET))) {
                        break;
                    }
                    this.set(x, yy, z, state(code, x, yy, z, water));
                }
            }
        }

        /**
         * A snapped stack in one column: its lowest (highest) cell lands on the floor (ceiling) block
         * near its height; the stack moves there whole and its cells are written bottom-up
         * (top-down), the anchor cell replacing that block if it is natural ground.
         */
        private void snap(int column, List<int[]> cells, boolean floor, Map<Integer, int[]> tips) {
            int x = this.minX + (column & 15);
            int z = this.minZ + (column >> 4);
            cells.sort((a, b) -> floor ? Integer.compare(a[0], b[0]) : Integer.compare(b[0], a[0]));
            int anchorY = cells.get(0)[0];
            boolean wet = false;
            for (int[] cell : cells) {
                wet |= FormationBlocks.has(cell[1], WET);
            }
            int found = this.findSurface(x, anchorY, z, floor, wet);
            if (found == Integer.MIN_VALUE) {
                return;
            }
            int shift = found - anchorY;
            for (int i = 0; i < cells.size(); i++) {
                int y = cells.get(i)[0] + shift;
                int code = cells.get(i)[1];
                if (y < this.bottom || y > this.top) {
                    return;
                }
                if (FormationBlocks.kind(code) == FormationBlocks.KEEP) {
                    continue;
                }
                boolean placed = this.place(x, y, z, i == 0 ? code | EMBED : code, true);
                if (!placed) {
                    // A floor or ceiling block that may not change, or a plant that cannot stand, ends the stack.
                    return;
                }
                if (FormationBlocks.kind(code) == FormationBlocks.POINTED) {
                    this.tipped(tips, x, y, z);
                }
            }
        }

        /** The floor (ceiling) block of a column near a height: solid, with open space above (below) it. */
        private int findSurface(int x, int y, int z, boolean floor, boolean wet) {
            int dir = floor ? 1 : -1;
            for (int k = 0; k <= 6; k++) {
                for (int s = -1; s <= 1; s += 2) {
                    if (k == 0 && s == 1) {
                        continue;
                    }
                    int yy = y + s * k;
                    if (yy - 1 < this.bottom || yy + 1 > this.top) {
                        continue;
                    }
                    BlockState here = this.chunk.getBlockState(this.m.set(x, yy, z));
                    BlockState next = this.chunk.getBlockState(this.m.set(x, yy + dir, z));
                    if (sturdy(here) && (open(next) && next.getFluidState().isEmpty() || wet && next.is(Blocks.WATER))) {
                        return yy;
                    }
                }
            }
            return Integer.MIN_VALUE;
        }

        /** Writes one cell if the block there may take it; returns whether it did. */
        boolean place(int x, int y, int z, int code, boolean check) {
            BlockState old = this.chunk.getBlockState(this.m.set(x, y, z));
            int kind = FormationBlocks.kind(code);
            boolean water = old.is(Blocks.WATER);
            boolean source = water && old.getFluidState().isSource();
            if (FormationBlocks.isSolid(code)) {
                // Into water only where it stands still: never into falls, springs or spillways.
                boolean ok = open(old) && old.getFluidState().isEmpty()
                        || source && FormationBlocks.has(code, WET)
                        || old.is(Blocks.LAVA) && FormationBlocks.has(code, WET) && (kind == FormationBlocks.BASALT || kind == FormationBlocks.MAGMA)
                        || FormationBlocks.has(code, EMBED) && natural(old);
                if (!ok) {
                    return false;
                }
                if (old.is(Blocks.POINTED_DRIPSTONE)) {
                    // A cave formation through vanilla's dripstone: what is left of it hangs on the new block.
                    this.tipped(this.tips, x, y, z);
                }
                this.set(x, y, z, state(code, x, y, z, false));
                return true;
            }
            BlockState state;
            if (old.isAir()) {
                state = state(code, x, y, z, false);
            } else if (source && waterloggable(kind)) {
                state = state(code, x, y, z, true);
            } else {
                return false;
            }
            if (check && needsSupport(kind)) {
                this.set(x, y, z, state);
                if (!state.canSurvive(this.level, new BlockPos(x, y, z))) {
                    this.set(x, y, z, old);
                    this.stats.blocks--;
                    return false;
                }
                return true;
            }
            this.set(x, y, z, state);
            return true;
        }

        void set(int x, int y, int z, BlockState state) {
            this.chunk.setBlockState(this.m.set(x, y, z), state, Block.UPDATE_CLIENTS);
            this.stats.blocks++;
        }
    }

    /**
     * Air, plants, snow layers, leaf litter, vanilla's pointed dripstone (what is left of it stays
     * attached to the formation): what formations may replace (no fluids, no leaves, nothing solid).
     */
    static boolean open(BlockState state) {
        if (state.isAir()) {
            return true;
        }
        if (!state.getFluidState().isEmpty() || state.getBlock() instanceof LeavesBlock || state.hasBlockEntity()) {
            return false;
        }
        return state.is(BlockTags.REPLACEABLE_BY_TREES) || state.getBlock() instanceof VegetationBlock || state.is(Blocks.SNOW)
                || state.is(Blocks.MOSS_CARPET) || state.is(Blocks.LEAF_LITTER) || state.is(Blocks.GLOW_LICHEN) || state.is(Blocks.SCULK_VEIN)
                || state.is(Blocks.POINTED_DRIPSTONE);
    }

    /** The natural ground and rock a sunk block may replace. Nothing built, nothing with a block entity, no ores of value. */
    static boolean natural(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) {
            return false;
        }
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.TERRACOTTA)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE)
                || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.CALCITE) || state.is(Blocks.DRIPSTONE_BLOCK) || state.is(Blocks.BASALT)
                || state.is(Blocks.SMOOTH_BASALT) || state.is(Blocks.BLACKSTONE) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.SCULK)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.PACKED_MUD);
    }

    /** A full block a stack can rest on (or hang from). */
    static boolean sturdy(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty() && state.isSolid() && !(state.getBlock() instanceof LeavesBlock);
    }

    private static boolean waterloggable(int kind) {
        return kind == FormationBlocks.POINTED || kind == FormationBlocks.HANGING_ROOTS || kind == FormationBlocks.SCULK_VEIN
                || kind == FormationBlocks.GLOW_LICHEN || kind == FormationBlocks.AMETHYST;
    }

    private static boolean needsSupport(int kind) {
        return kind == FormationBlocks.POINTED || kind == FormationBlocks.AZALEA || kind == FormationBlocks.CAVE_VINES || kind == FormationBlocks.HANGING_ROOTS
                || kind == FormationBlocks.SPORE_BLOSSOM || kind == FormationBlocks.MOSS_CARPET || kind == FormationBlocks.SCULK_VEIN
                || kind == FormationBlocks.AMETHYST || kind == FormationBlocks.GLOW_LICHEN;
    }

    /** Decorations held only by the block above or below them: checked where they are written. */
    private static boolean heldVertically(int code) {
        int kind = FormationBlocks.kind(code);
        return kind == FormationBlocks.POINTED || kind == FormationBlocks.CAVE_VINES || kind == FormationBlocks.HANGING_ROOTS
                || kind == FormationBlocks.SPORE_BLOSSOM || kind == FormationBlocks.MOSS_CARPET || kind == FormationBlocks.AZALEA;
    }

    /** Decorations held by the block above them (written top down). */
    private static boolean hangs(int code) {
        int kind = FormationBlocks.kind(code);
        return kind == FormationBlocks.POINTED ? (FormationBlocks.extra(code) & 1) == 0
                : kind == FormationBlocks.CAVE_VINES || kind == FormationBlocks.HANGING_ROOTS || kind == FormationBlocks.SPORE_BLOSSOM;
    }

    private static boolean pointed(BlockState state, Direction dir) {
        return state.is(Blocks.POINTED_DRIPSTONE) && state.getValue(PointedDripstoneBlock.TIP_DIRECTION) == dir;
    }

    // ------------------------------------------------------------------ block states

    private static double patch(int x, int y, int z, long salt, double scale) {
        return NatureRandom.value(0x5061746368L ^ salt, x, y, z, scale);
    }

    /** The block state of a code at a position (rock picked from its palette there). */
    static BlockState state(int code, int x, int y, int z, boolean waterlogged) {
        int kind = FormationBlocks.kind(code);
        if (kind == FormationBlocks.ROCK) {
            return rock(code, x, y, z);
        }
        int key = (code & ~(EMBED | WET | SNAP_FLOOR | SNAP_CEILING | EXPOSED | TOP)) << 1 | (waterlogged ? 1 : 0);
        return STATES.computeIfAbsent(key, k -> build(kind, FormationBlocks.extra(code), waterlogged));
    }

    private static BlockState build(int kind, int extra, boolean waterlogged) {
        return switch (kind) {
            case FormationBlocks.POINTED -> Blocks.POINTED_DRIPSTONE.defaultBlockState()
                    .setValue(PointedDripstoneBlock.TIP_DIRECTION, (extra & 1) != 0 ? Direction.UP : Direction.DOWN)
                    .setValue(PointedDripstoneBlock.THICKNESS, switch (extra >> 1) {
                        case 0 -> DripstoneThickness.TIP;
                        case 1 -> DripstoneThickness.FRUSTUM;
                        case 2 -> DripstoneThickness.MIDDLE;
                        case 4 -> DripstoneThickness.TIP_MERGE;
                        default -> DripstoneThickness.BASE;
                    }).setValue(PointedDripstoneBlock.WATERLOGGED, waterlogged);
            case FormationBlocks.DRIPSTONE -> Blocks.DRIPSTONE_BLOCK.defaultBlockState();
            case FormationBlocks.MOSS_BLOCK -> Blocks.MOSS_BLOCK.defaultBlockState();
            case FormationBlocks.MOSS_CARPET -> Blocks.MOSS_CARPET.defaultBlockState();
            case FormationBlocks.ROOTED_DIRT -> Blocks.ROOTED_DIRT.defaultBlockState();
            case FormationBlocks.HANGING_ROOTS -> Blocks.HANGING_ROOTS.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, waterlogged);
            case FormationBlocks.CAVE_VINES -> ((extra & 2) != 0
                    ? Blocks.CAVE_VINES.defaultBlockState().setValue(GrowingPlantHeadBlock.AGE, GrowingPlantHeadBlock.MAX_AGE)
                    : Blocks.CAVE_VINES_PLANT.defaultBlockState()).setValue(CaveVines.BERRIES, (extra & 1) != 0);
            case FormationBlocks.AZALEA -> (extra & 1) != 0 ? Blocks.FLOWERING_AZALEA.defaultBlockState() : Blocks.AZALEA.defaultBlockState();
            case FormationBlocks.AZALEA_LEAVES -> ((extra & 1) != 0 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES).defaultBlockState()
                    .setValue(LeavesBlock.PERSISTENT, true);
            case FormationBlocks.SCULK -> Blocks.SCULK.defaultBlockState();
            case FormationBlocks.SCULK_VEIN -> faces(Blocks.SCULK_VEIN.defaultBlockState(), extra).setValue(BlockStateProperties.WATERLOGGED, waterlogged);
            case FormationBlocks.GLOW_LICHEN -> faces(Blocks.GLOW_LICHEN.defaultBlockState(), extra).setValue(BlockStateProperties.WATERLOGGED, waterlogged);
            case FormationBlocks.LOG -> log(extra);
            case FormationBlocks.GRAVEL -> Blocks.GRAVEL.defaultBlockState();
            case FormationBlocks.CLAY -> Blocks.CLAY.defaultBlockState();
            case FormationBlocks.CALCITE -> Blocks.CALCITE.defaultBlockState();
            case FormationBlocks.AMETHYST_BLOCK -> Blocks.AMETHYST_BLOCK.defaultBlockState();
            case FormationBlocks.AMETHYST -> (switch ((extra >> 3) & 3) {
                case 0 -> Blocks.SMALL_AMETHYST_BUD;
                case 1 -> Blocks.MEDIUM_AMETHYST_BUD;
                case 2 -> Blocks.LARGE_AMETHYST_BUD;
                default -> Blocks.AMETHYST_CLUSTER;
            }).defaultBlockState().setValue(AmethystClusterBlock.FACING, FACES[Math.min(5, extra & 7)]).setValue(AmethystClusterBlock.WATERLOGGED, waterlogged);
            case FormationBlocks.BASALT -> Blocks.BASALT.defaultBlockState();
            case FormationBlocks.MAGMA -> Blocks.MAGMA_BLOCK.defaultBlockState();
            case FormationBlocks.COARSE_DIRT -> Blocks.COARSE_DIRT.defaultBlockState();
            case FormationBlocks.MUD -> Blocks.MUD.defaultBlockState();
            case FormationBlocks.SPORE_BLOSSOM -> Blocks.SPORE_BLOSSOM.defaultBlockState();
            case FormationBlocks.SAND -> Blocks.SAND.defaultBlockState();
            case FormationBlocks.PACKED_MUD -> Blocks.PACKED_MUD.defaultBlockState();
            default -> Blocks.AIR.defaultBlockState();
        };
    }

    private static BlockState faces(BlockState state, int faces) {
        for (int f = 0; f < 6; f++) {
            state = state.setValue(MultifaceBlock.getFaceProperty(FACES[f]), (faces & 1 << f) != 0);
        }
        return state;
    }

    private static BlockState log(int extra) {
        boolean stripped = (extra & 32) != 0;
        Block block = switch (extra & 7) {
            case 1 -> stripped ? Blocks.STRIPPED_BIRCH_LOG : Blocks.BIRCH_LOG;
            case 2 -> stripped ? Blocks.STRIPPED_SPRUCE_LOG : Blocks.SPRUCE_LOG;
            case 3 -> stripped ? Blocks.STRIPPED_DARK_OAK_LOG : Blocks.DARK_OAK_LOG;
            case 4 -> stripped ? Blocks.STRIPPED_JUNGLE_LOG : Blocks.JUNGLE_LOG;
            case 5 -> stripped ? Blocks.STRIPPED_ACACIA_LOG : Blocks.ACACIA_LOG;
            default -> stripped ? Blocks.STRIPPED_OAK_LOG : Blocks.OAK_LOG;
        };
        int axis = (extra >> 3) & 3;
        return block.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis == 1 ? Direction.Axis.X : (axis == 2 ? Direction.Axis.Z : Direction.Axis.Y));
    }

    /**
     * A rock block of a palette at a position: smooth patches of the palette's stones (each patch a
     * few blocks across), the accent and cap roles leaning to their stones, and the coats on
     * exposed faces: moss weathering (mossy cobblestone, mostly on top), moss blocks (lush caves),
     * sculk (the deep dark, patchy, thinning down the sides).
     */
    static BlockState rock(int code, int x, int y, int z) {
        Rock palette = FormationBlocks.palette(code);
        int role = FormationBlocks.role(code);
        int coat = FormationBlocks.extra(code);
        boolean exposed = FormationBlocks.has(code, EXPOSED);
        boolean top = FormationBlocks.has(code, TOP);
        if (exposed) {
            double c = patch(x, y, z, 0x436F6174L, 1.9);
            if ((coat & FormationBlocks.SCULKY) != 0 && c < (top ? 0.8 : 0.55)) {
                return Blocks.SCULK.defaultBlockState();
            }
            if ((coat & FormationBlocks.LUSHY) != 0 && c < (top ? 0.9 : 0.62)) {
                return Blocks.MOSS_BLOCK.defaultBlockState();
            }
            if ((coat & FormationBlocks.MOSSY) != 0 && c < (top ? 0.62 : 0.2)) {
                return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            }
        }
        double v = patch(x, y, z, palette.ordinal() * 0x9E37L, 2.8);
        if (role == FormationBlocks.ACCENT) {
            v = 0.75 + 0.25 * v;
        } else if (role == FormationBlocks.CAP) {
            v = 0.25 * v;
        }
        return switch (palette) {
            case STONE -> v < 0.55 ? Blocks.STONE.defaultBlockState() : (v < 0.8 ? Blocks.ANDESITE.defaultBlockState()
                    : (v < 0.92 ? Blocks.COBBLESTONE.defaultBlockState() : (y < 16 ? Blocks.TUFF.defaultBlockState() : Blocks.STONE.defaultBlockState())));
            // Mostly stone with granite in patches: a whole pink boulder field looks foreign in vanilla's grey mountains.
            case GRANITE -> v < 0.42 ? Blocks.STONE.defaultBlockState() : (v < 0.72 ? Blocks.GRANITE.defaultBlockState()
                    : (v < 0.93 ? Blocks.ANDESITE.defaultBlockState() : Blocks.DIORITE.defaultBlockState()));
            case LIMESTONE -> v < 0.4 ? Blocks.STONE.defaultBlockState() : (v < 0.7 ? Blocks.DIORITE.defaultBlockState()
                    : (v < 0.9 ? Blocks.CALCITE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState()));
            case SANDSTONE -> role == FormationBlocks.CAP || v < 0.2 ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState();
            case TERRACOTTA -> role == FormationBlocks.CAP ? Blocks.RED_SANDSTONE.defaultBlockState()
                    : BANDS[Math.floorMod((int) Math.floor((y + 3.0 * patch(x, 0, z, 0x42616E64L, 24.0)) / 2.0), BANDS.length)];
            case BASALT -> v < 0.45 ? Blocks.BASALT.defaultBlockState() : (v < 0.7 ? Blocks.SMOOTH_BASALT.defaultBlockState()
                    : (v < 0.9 ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.TUFF.defaultBlockState()));
            case DEEPSLATE -> v < 0.6 ? Blocks.DEEPSLATE.defaultBlockState() : (v < 0.85 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : Blocks.TUFF.defaultBlockState());
            case DRIPSTONE -> v < 0.88 ? Blocks.DRIPSTONE_BLOCK.defaultBlockState() : Blocks.CALCITE.defaultBlockState();
            case RUBBLE -> v < 0.45 ? Blocks.COBBLESTONE.defaultBlockState() : (v < 0.75 ? Blocks.STONE.defaultBlockState()
                    : (v < 0.9 ? Blocks.ANDESITE.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState()));
            case DEEP_RUBBLE -> v < 0.5 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : (v < 0.75 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.TUFF.defaultBlockState());
            case SHORE -> v < 0.4 ? Blocks.STONE.defaultBlockState() : (v < 0.7 ? Blocks.COBBLESTONE.defaultBlockState()
                    : (v < 0.9 ? Blocks.ANDESITE.defaultBlockState() : Blocks.GRANITE.defaultBlockState()));
            case CALCITE -> v < 0.85 ? Blocks.CALCITE.defaultBlockState() : Blocks.TUFF.defaultBlockState();
            case PALE -> v < 0.45 ? Blocks.DIORITE.defaultBlockState() : (v < 0.8 ? Blocks.STONE.defaultBlockState()
                    : (v < 0.9 ? Blocks.CALCITE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState()));
        };
    }

    // ------------------------------------------------------------------ structures

    /**
     * Keeps formations out of structures: a formation is left out whole if its blocks' extent (two
     * blocks wider; three for caves) meets a piece of any structure starting within 7 chunks of the
     * formation's own chunk (found through that chunk's structure references), or for a formation
     * reaching more than 16 blocks from its root within 6 chunks (every start there). Every chunk a
     * formation reaches sees the same structures (they are within what a decorating region can
     * read: references 1 chunk away, starts 8), so every chunk decides the same.
     */
    static final class StructureGuard {
        private static final int START_RANGE = 7;
        /**
         * Starts searched around the root of a formation that reaches beyond the neighbouring chunks
         * (a root up to 2 chunks from the decorated chunk): during features a region reads structure
         * starts up to 8 chunks away and structure references only 1 away, so every chunk such a
         * formation reaches can read the starts within 6 chunks of its root, and nothing more.
         */
        private static final int WIDE_RANGE = 6;
        private final WorldGenLevel level;
        private final boolean surface;
        private final Map<Long, List<BoundingBox>> pieces = new HashMap<>();
        private final Map<Long, List<BoundingBox>> widePieces = new HashMap<>();

        StructureGuard(WorldGenLevel level, boolean surface) {
            this.level = level;
            this.surface = surface;
        }

        boolean blocked(FormationSpec spec, FormationShape shape) {
            int cx = spec.x() >> 4;
            int cz = spec.z() >> 4;
            // A formation within 16 blocks of every chunk it reaches has its root chunk among their neighbours.
            boolean wide = spec.kind().reach > 16;
            List<BoundingBox> boxes = wide ? this.widePieces.computeIfAbsent(ChunkPos.asLong(cx, cz), k -> this.collectWide(cx, cz))
                    : this.pieces.computeIfAbsent(ChunkPos.asLong(cx, cz), k -> this.collect(cx, cz));
            if (boxes.isEmpty()) {
                return false;
            }
            int margin = this.surface ? 2 : 3;
            int x0 = spec.x() + shape.minX - margin;
            int x1 = spec.x() + shape.maxX + margin;
            int z0 = spec.z() + shape.minZ - margin;
            int z1 = spec.z() + shape.maxZ + margin;
            int y0 = spec.y() + shape.minY - margin;
            int y1 = spec.y() + shape.maxY + margin;
            for (BoundingBox b : boxes) {
                if (b.maxX() >= x0 && b.minX() <= x1 && b.maxZ() >= z0 && b.minZ() <= z1 && (this.surface || b.maxY() >= y0 && b.minY() <= y1)) {
                    return true;
                }
            }
            return false;
        }

        private List<BoundingBox> collect(int cx, int cz) {
            List<BoundingBox> boxes = new ArrayList<>();
            ChunkAccess root = this.level.getChunk(cx, cz, ChunkStatus.STRUCTURE_REFERENCES, false);
            if (root == null) {
                return boxes;
            }
            for (Map.Entry<Structure, LongSet> entry : root.getAllReferences().entrySet()) {
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
                        boxes.add(piece.getBoundingBox());
                    }
                }
            }
            return boxes;
        }

        /** The pieces of every structure starting within {@link #WIDE_RANGE} chunks of a root chunk. */
        private List<BoundingBox> collectWide(int cx, int cz) {
            List<BoundingBox> boxes = new ArrayList<>();
            for (int dz = -WIDE_RANGE; dz <= WIDE_RANGE; dz++) {
                for (int dx = -WIDE_RANGE; dx <= WIDE_RANGE; dx++) {
                    ChunkAccess holder = this.level.getChunk(cx + dx, cz + dz, ChunkStatus.STRUCTURE_STARTS, false);
                    if (holder == null) {
                        continue;
                    }
                    for (StructureStart s : holder.getAllStarts().values()) {
                        if (s.isValid()) {
                            for (StructurePiece piece : s.getPieces()) {
                                boxes.add(piece.getBoundingBox());
                            }
                        }
                    }
                }
            }
            return boxes;
        }
    }

    static Direction horizontal(int i) {
        return HORIZONTAL[i & 3];
    }
}
