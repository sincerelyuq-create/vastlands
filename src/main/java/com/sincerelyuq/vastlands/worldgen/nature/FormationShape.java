package com.sincerelyuq.vastlands.worldgen.nature;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.Arrays;

/**
 * The blocks of one natural formation relative to its root ({@code FormationSpec.x, y, z}).
 * Immutable. Positions are packed as {@code dx & 0xFF | (dz & 0xFF) << 8 | dy << 16} (see
 * {@link #dx}, {@link #dy}, {@link #dz}): up to 127 blocks sideways, 32 767 up or down, so tall
 * cave formations fit.
 *
 * <p>Besides its blocks a shape lists feet and hangers: columns whose lowest (highest) block is
 * extended down to the ground (up to the ceiling) through open space when it is written, up to a
 * depth, so a formation never floats over a dip in the ground or hangs under a bump in the ceiling
 * of a column it reaches. Those extensions read only the blocks of the column being written, which
 * lies whole in one chunk.
 */
public final class FormationShape {
    public final int[] positions;
    public final int[] codes;
    public final int minX, maxX, minY, maxY, minZ, maxZ;
    /** Feet: packed position of the lowest block, the code to extend with, the greatest depth. */
    public final int[] feet;
    public final int[] footCodes;
    public final int[] footDepths;
    /** Hangers: packed position of the highest block, the code, the greatest height. */
    public final int[] hangers;
    public final int[] hangerCodes;
    public final int[] hangerHeights;

    private FormationShape(int[] positions, int[] codes, int[] feet, int[] footCodes, int[] footDepths, int[] hangers, int[] hangerCodes,
                           int[] hangerHeights) {
        this.positions = positions;
        this.codes = codes;
        this.feet = feet;
        this.footCodes = footCodes;
        this.footDepths = footDepths;
        this.hangers = hangers;
        this.hangerCodes = hangerCodes;
        this.hangerHeights = hangerHeights;
        int x0 = 0, x1 = 0, y0 = 0, y1 = 0, z0 = 0, z1 = 0;
        for (int k = 0; k < positions.length; k++) {
            int p = positions[k];
            int x = dx(p);
            int y = dy(p);
            int z = dz(p);
            if (k == 0) {
                x0 = x1 = x;
                y0 = y1 = y;
                z0 = z1 = z;
            } else {
                x0 = Math.min(x0, x);
                x1 = Math.max(x1, x);
                y0 = Math.min(y0, y);
                y1 = Math.max(y1, y);
                z0 = Math.min(z0, z);
                z1 = Math.max(z1, z);
            }
        }
        this.minX = x0;
        this.maxX = x1;
        this.minY = y0;
        this.maxY = y1;
        this.minZ = z0;
        this.maxZ = z1;
    }

    public int size() {
        return this.positions.length;
    }

    public boolean isEmpty() {
        return this.positions.length == 0;
    }

    public static int dx(int packed) {
        return (byte) packed;
    }

    public static int dz(int packed) {
        return (byte) (packed >> 8);
    }

    public static int dy(int packed) {
        return packed >> 16;
    }

    public static int pack(int dx, int dy, int dz) {
        return dx & 0xFF | (dz & 0xFF) << 8 | dy << 16;
    }

    /** Horizontal radius (largest offset from the root). */
    public int radius() {
        return this.isEmpty() ? 0 : Math.max(Math.max(-this.minX, this.maxX), Math.max(-this.minZ, this.maxZ));
    }

    /**
     * Builds a shape: solid blocks win over decorations, the first solid block written to a cell
     * stays; decorations only fill empty cells. Blocks beyond the reach are dropped. One per thread.
     */
    public static final class Builder {
        private final Int2IntOpenHashMap cells = new Int2IntOpenHashMap();
        private final Int2IntOpenHashMap feet = new Int2IntOpenHashMap();
        private final Int2IntOpenHashMap footDepth = new Int2IntOpenHashMap();
        private final Int2IntOpenHashMap hangers = new Int2IntOpenHashMap();
        private final Int2IntOpenHashMap hangerHeight = new Int2IntOpenHashMap();
        private int reach = 16;
        /** Flags every solid block gets (a cave formation in a flooded run stands in its water). */
        private int solidFlags;

        void clear(int reach) {
            this.cells.clear();
            this.feet.clear();
            this.footDepth.clear();
            this.hangers.clear();
            this.hangerHeight.clear();
            this.reach = reach;
            this.solidFlags = 0;
        }

        /** Solid blocks written from now on may go into still water ({@link FormationBlocks#WET}). */
        void wetSolids() {
            this.solidFlags = FormationBlocks.WET;
        }

        int reach() {
            return this.reach;
        }

        private boolean inside(int dx, int dz) {
            return Math.abs(dx) <= this.reach && Math.abs(dz) <= this.reach;
        }

        int get(int dx, int dy, int dz) {
            return this.cells.get(pack(dx, dy, dz));
        }

        boolean isEmpty(int dx, int dy, int dz) {
            return this.inside(dx, dz) && !this.cells.containsKey(pack(dx, dy, dz));
        }

        boolean isSolid(int dx, int dy, int dz) {
            return FormationBlocks.isSolid(this.cells.get(pack(dx, dy, dz)));
        }

        /** A solid block: replaces a decoration, never another solid block. */
        void solid(int dx, int dy, int dz, int code) {
            if (!this.inside(dx, dz)) {
                return;
            }
            int p = pack(dx, dy, dz);
            int old = this.cells.get(p);
            if (old == 0 || !FormationBlocks.isSolid(old)) {
                this.cells.put(p, code | this.solidFlags);
            }
        }

        /** Overwrites whatever is there. */
        void force(int dx, int dy, int dz, int code) {
            if (this.inside(dx, dz)) {
                this.cells.put(pack(dx, dy, dz), FormationBlocks.isSolid(code) ? code | this.solidFlags : code);
            }
        }

        /** A decoration: only into an empty cell. */
        void decor(int dx, int dy, int dz, int code) {
            if (this.inside(dx, dz)) {
                this.cells.putIfAbsent(pack(dx, dy, dz), code);
            }
        }

        void remove(int dx, int dy, int dz) {
            this.cells.remove(pack(dx, dy, dz));
        }

        /** The lowest block of a column is extended down to the ground through open space, at most {@code depth} blocks. */
        void foot(int dx, int dz, int depth) {
            if (!this.inside(dx, dz)) {
                return;
            }
            int column = pack(dx, 0, dz);
            this.footDepth.put(column, Math.max(this.footDepth.get(column), depth));
        }

        /** The highest block of a column is extended up to the ceiling through open space, at most {@code height} blocks. */
        void hang(int dx, int dz, int height) {
            if (!this.inside(dx, dz)) {
                return;
            }
            int column = pack(dx, 0, dz);
            this.hangerHeight.put(column, Math.max(this.hangerHeight.get(column), height));
        }

        /** The finished shape: positions in a fixed order, exposed solid cells marked. */
        FormationShape build() {
            int[] positions = this.cells.keySet().toIntArray();
            Arrays.sort(positions);
            int[] codes = new int[positions.length];
            // Lowest and highest solid block of every column for its foot and hanger.
            Int2IntOpenHashMap lowest = new Int2IntOpenHashMap();
            Int2IntOpenHashMap highest = new Int2IntOpenHashMap();
            for (int k = 0; k < positions.length; k++) {
                int p = positions[k];
                int code = this.cells.get(p);
                if (FormationBlocks.isSolid(code)) {
                    int x = dx(p);
                    int y = dy(p);
                    int z = dz(p);
                    boolean exposed = !this.isSolid(x + 1, y, z) || !this.isSolid(x - 1, y, z) || !this.isSolid(x, y + 1, z)
                            || !this.isSolid(x, y - 1, z) || !this.isSolid(x, y, z + 1) || !this.isSolid(x, y, z - 1);
                    if (exposed) {
                        code |= FormationBlocks.EXPOSED;
                    }
                    if (!this.isSolid(x, y + 1, z)) {
                        code |= FormationBlocks.TOP;
                    }
                    int column = pack(x, 0, z);
                    if (!lowest.containsKey(column) || y < dy(lowest.get(column))) {
                        lowest.put(column, p);
                    }
                    if (!highest.containsKey(column) || y > dy(highest.get(column))) {
                        highest.put(column, p);
                    }
                }
                codes[k] = code;
            }
            int[][] f = extensions(this.footDepth, lowest);
            int[][] h = extensions(this.hangerHeight, highest);
            return new FormationShape(positions, codes, f[0], f[1], f[2], h[0], h[1], h[2]);
        }

        private int[][] extensions(Int2IntOpenHashMap depths, Int2IntOpenHashMap ends) {
            int[] columns = depths.keySet().toIntArray();
            Arrays.sort(columns);
            int n = 0;
            int[] at = new int[columns.length];
            int[] code = new int[columns.length];
            int[] depth = new int[columns.length];
            for (int column : columns) {
                if (!ends.containsKey(column)) {
                    continue;
                }
                int p = ends.get(column);
                at[n] = p;
                code[n] = this.cells.get(p) & ~(FormationBlocks.EXPOSED | FormationBlocks.TOP | FormationBlocks.SNAP_FLOOR | FormationBlocks.SNAP_CEILING);
                depth[n] = depths.get(column);
                n++;
            }
            return new int[][] {Arrays.copyOf(at, n), Arrays.copyOf(code, n), Arrays.copyOf(depth, n)};
        }
    }
}
