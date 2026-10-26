package com.sincerelyuq.vastlands.worldgen.forest;

import java.util.Arrays;

/**
 * The blocks of one tree, relative to its root (the lowest trunk block, standing on the ground).
 * Immutable. Built by {@link Builder} from logs, leaves and decorations; every leaf carries the
 * distance vanilla's leaves would compute from the tree's own logs, and leaves that would be more
 * than 6 steps from a log are never kept, so nothing decays.
 *
 * <p>Positions are packed as {@code dx & 0xFF | (dz & 0xFF) << 8 | (dy & 0x3FF) << 16} (see
 * {@link #dx}, {@link #dy}, {@link #dz}); every block lies within {@link #REACH} blocks of the root
 * horizontally, so a tree only ever reaches the chunks next to the one holding its root.
 */
public final class TreeShape {
    /** Largest horizontal offset of any block from the root. */
    public static final int REACH = 16;

    /** Packed positions and block codes ({@link TreeBlocks}), in a fixed order. */
    public final int[] positions;
    public final int[] codes;
    /** Bounds of the blocks relative to the root. */
    public final int minX, maxX, minY, maxY, minZ, maxZ;
    /** Trunk columns standing on the ground (packed {@code dx & 0xFF | (dz & 0xFF) << 8}): extended down onto solid ground. */
    public final int[] trunk;
    /** Ground changes around the root: packed column and a ground code ({@code TreeBlocks.GROUND_*}). */
    public final int[] groundColumns;
    public final int[] groundCodes;
    /** Columns shaded by the crown (packed column) and the number of leaves above them. */
    public final int[] shadeColumns;
    public final int[] shadeCounts;
    public final int logs;
    public final int leaves;
    /** Height of the highest block above the root plus one. */
    public final int height;

    private TreeShape(int[] positions, int[] codes, int minX, int maxX, int minY, int maxY, int minZ, int maxZ, int[] trunk,
                      int[] groundColumns, int[] groundCodes, int[] shadeColumns, int[] shadeCounts, int logs, int leaves) {
        this.positions = positions;
        this.codes = codes;
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.trunk = trunk;
        this.groundColumns = groundColumns;
        this.groundCodes = groundCodes;
        this.shadeColumns = shadeColumns;
        this.shadeCounts = shadeCounts;
        this.logs = logs;
        this.leaves = leaves;
        this.height = maxY + 1;
    }

    public int size() {
        return this.positions.length;
    }

    public static int dx(int packed) {
        return (byte) packed;
    }

    public static int dz(int packed) {
        return (byte) (packed >> 8);
    }

    public static int dy(int packed) {
        return (packed << 6) >> 22;
    }

    public static int pack(int dx, int dy, int dz) {
        return dx & 0xFF | (dz & 0xFF) << 8 | (dy & 0x3FF) << 16;
    }

    public static int packColumn(int dx, int dz) {
        return dx & 0xFF | (dz & 0xFF) << 8;
    }

    /** Horizontal radius of the shape (largest offset from the root). */
    public int radius() {
        return Math.max(Math.max(-this.minX, this.maxX), Math.max(-this.minZ, this.maxZ));
    }

    /**
     * A reusable scratch volume for building shapes (one per thread). Blocks are written with
     * priorities: logs over everything, leaves and decorations only into empty cells; vines and
     * cocoa are dropped at the end if nothing holds them up.
     */
    public static final class Builder {
        static final int R = REACH;
        static final int SIDE = 2 * R + 1;
        static final int MIN_Y = -8;
        static final int MAX_Y = 63;
        static final int LAYERS = MAX_Y - MIN_Y + 1;
        private final int[] cells = new int[SIDE * SIDE * LAYERS];
        private final byte[] distance = new byte[SIDE * SIDE * LAYERS];
        private int[] touched = new int[4096];
        private int count;
        private int[] queue = new int[4096];
        private final int[] groundColumns = new int[512];
        private final int[] groundCodes = new int[512];
        private int groundCount;
        /** Horizontal limit of the shape being built (its form's reach): blocks beyond it are dropped. */
        private int reach = R;

        public Builder() {
            Arrays.fill(this.distance, (byte) 127);
        }

        private static int index(int dx, int dy, int dz) {
            return ((dy - MIN_Y) * SIDE + (dz + R)) * SIDE + (dx + R);
        }

        private boolean inside(int dx, int dy, int dz) {
            int r = this.reach;
            return dx >= -r && dx <= r && dz >= -r && dz <= r && dy >= MIN_Y && dy <= MAX_Y;
        }

        int get(int dx, int dy, int dz) {
            return inside(dx, dy, dz) ? this.cells[index(dx, dy, dz)] : 0;
        }

        boolean isEmpty(int dx, int dy, int dz) {
            return inside(dx, dy, dz) && this.cells[index(dx, dy, dz)] == 0;
        }

        private void put(int i, int code) {
            if (this.cells[i] == 0) {
                if (this.count == this.touched.length) {
                    this.touched = Arrays.copyOf(this.touched, this.count * 2);
                }
                this.touched[this.count++] = i;
            }
            this.cells[i] = code;
        }

        /** A log (or wood): replaces leaves and decorations, never another log. */
        void log(int dx, int dy, int dz, int code) {
            if (!inside(dx, dy, dz)) {
                return;
            }
            int i = index(dx, dy, dz);
            int old = this.cells[i];
            if (old == 0 || !TreeBlocks.isLog(old)) {
                this.put(i, code);
            }
        }

        /** Any block that only goes into an empty cell (leaves, vines, cocoa, caps). */
        void soft(int dx, int dy, int dz, int code) {
            if (inside(dx, dy, dz)) {
                int i = index(dx, dy, dz);
                if (this.cells[i] == 0) {
                    this.put(i, code);
                }
            }
        }

        /** Overwrites whatever is there (huge mushroom caps over their stem's top). */
        void force(int dx, int dy, int dz, int code) {
            if (inside(dx, dy, dz)) {
                this.put(index(dx, dy, dz), code);
            }
        }

        void ground(int dx, int dz, int code) {
            if (this.groundCount < this.groundColumns.length && Math.abs(dx) <= this.reach && Math.abs(dz) <= this.reach) {
                this.groundColumns[this.groundCount] = packColumn(dx, dz);
                this.groundCodes[this.groundCount] = code;
                this.groundCount++;
            }
        }

        /**
         * The finished shape. Leaf distances are counted from the logs through leaves (6
         * neighbours, as vanilla's leaves do); leaves further than 6 steps are dropped, then vines
         * and cocoa without support. Clears the scratch volume for the next tree.
         */
        public TreeShape build() {
            // Leaf distances: breadth-first from every log through leaves.
            int head = 0;
            int tail = 0;
            if (this.queue.length < this.count) {
                this.queue = new int[this.count];
            }
            for (int k = 0; k < this.count; k++) {
                int i = this.touched[k];
                if (TreeBlocks.isLog(this.cells[i])) {
                    this.distance[i] = 0;
                    this.queue[tail++] = i;
                }
            }
            int[] steps = {1, -1, SIDE, -SIDE, SIDE * SIDE, -SIDE * SIDE};
            while (head < tail) {
                int i = this.queue[head++];
                int d = this.distance[i];
                if (d >= 6) {
                    continue;
                }
                int x = i % SIDE;
                int z = (i / SIDE) % SIDE;
                int y = i / (SIDE * SIDE);
                for (int s = 0; s < 6; s++) {
                    if ((s == 0 && x == SIDE - 1) || (s == 1 && x == 0) || (s == 2 && z == SIDE - 1) || (s == 3 && z == 0)
                            || (s == 4 && y == LAYERS - 1) || (s == 5 && y == 0)) {
                        continue;
                    }
                    int j = i + steps[s];
                    if (TreeBlocks.kind(this.cells[j]) == TreeBlocks.LEAVES && this.distance[j] > d + 1) {
                        this.distance[j] = (byte) (d + 1);
                        this.queue[tail++] = j;
                    }
                }
            }
            for (int k = 0; k < this.count; k++) {
                int i = this.touched[k];
                int code = this.cells[i];
                if (TreeBlocks.kind(code) == TreeBlocks.LEAVES) {
                    int d = this.distance[i];
                    this.cells[i] = d <= 6 ? (code & 0x1FF) | d << 9 : 0;
                }
            }
            // Decorations need something to hang on: vines a solid block on one of their faces or a
            // vine above, cocoa its log. From the top down, so hanging vines follow their support.
            int[] order = Arrays.copyOf(this.touched, this.count);
            Arrays.sort(order);
            for (int k = order.length - 1; k >= 0; k--) {
                int i = order[k];
                int code = this.cells[i];
                int kind = TreeBlocks.kind(code);
                if (kind != TreeBlocks.VINE && kind != TreeBlocks.COCOA) {
                    continue;
                }
                int x = i % SIDE - R;
                int z = (i / SIDE) % SIDE - R;
                int y = i / (SIDE * SIDE) + MIN_Y;
                if (kind == TreeBlocks.VINE) {
                    int faces = TreeBlocks.extra(code);
                    int kept = 0;
                    int above = this.get(x, y + 1, z);
                    int aboveFaces = TreeBlocks.kind(above) == TreeBlocks.VINE ? TreeBlocks.extra(above) : 0;
                    for (int f = 0; f < 4; f++) {
                        int bit = 1 << f;
                        if ((faces & bit) != 0 && (TreeBlocks.isSolid(this.get(x + FACE_X[f], y, z + FACE_Z[f])) || (aboveFaces & bit) != 0)) {
                            kept |= bit;
                        }
                    }
                    this.cells[i] = kept == 0 ? 0 : (code & 0x1FF) | kept << 9;
                } else {
                    int f = TreeBlocks.extra(code) & 3;
                    if (!TreeBlocks.isLog(this.get(x + FACE_X[f], y, z + FACE_Z[f]))) {
                        this.cells[i] = 0;
                    }
                }
            }

            int n = 0;
            for (int k = 0; k < order.length; k++) {
                if (this.cells[order[k]] != 0) {
                    n++;
                }
            }
            int[] positions = new int[n];
            int[] codes = new int[n];
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            int logs = 0;
            int leaves = 0;
            int lowestLog = Integer.MAX_VALUE;
            int m = 0;
            int[] shade = new int[SIDE * SIDE];
            for (int i : order) {
                int code = this.cells[i];
                if (code == 0) {
                    continue;
                }
                int x = i % SIDE - R;
                int z = (i / SIDE) % SIDE - R;
                int y = i / (SIDE * SIDE) + MIN_Y;
                positions[m] = pack(x, y, z);
                codes[m] = code;
                m++;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
                int kind = TreeBlocks.kind(code);
                if (TreeBlocks.isLog(code) || kind == TreeBlocks.STEM) {
                    logs++;
                    if (TreeBlocks.extra(code) == TreeBlocks.AXIS_Y || kind != TreeBlocks.LOG) {
                        lowestLog = Math.min(lowestLog, y);
                    }
                } else if (kind == TreeBlocks.LEAVES || kind == TreeBlocks.BROWN_CAP || kind == TreeBlocks.RED_CAP) {
                    leaves++;
                    if (y > 1) {
                        shade[(z + R) * SIDE + (x + R)]++;
                    }
                }
            }
            // Trunk columns: the lowest vertical logs at the root's level stand on the ground.
            int[] trunk = new int[16];
            int trunkCount = 0;
            for (int k = 0; k < n && trunkCount < trunk.length; k++) {
                int code = codes[k];
                if (dy(positions[k]) == 0 && (TreeBlocks.kind(code) == TreeBlocks.STEM
                        || (TreeBlocks.isLog(code) && (TreeBlocks.kind(code) == TreeBlocks.WOOD || TreeBlocks.extra(code) == TreeBlocks.AXIS_Y)))) {
                    trunk[trunkCount++] = packColumn(dx(positions[k]), dz(positions[k]));
                }
            }
            int shadeCount = 0;
            for (int c : shade) {
                shadeCount += c > 0 ? 1 : 0;
            }
            int[] shadeColumns = new int[shadeCount];
            int[] shadeCounts = new int[shadeCount];
            int s = 0;
            for (int i = 0; i < shade.length; i++) {
                if (shade[i] > 0) {
                    shadeColumns[s] = packColumn(i % SIDE - R, i / SIDE - R);
                    shadeCounts[s] = shade[i];
                    s++;
                }
            }
            TreeShape shape = new TreeShape(positions, codes, n == 0 ? 0 : minX, n == 0 ? 0 : maxX, n == 0 ? 0 : minY, n == 0 ? 0 : maxY,
                    n == 0 ? 0 : minZ, n == 0 ? 0 : maxZ, Arrays.copyOf(trunk, trunkCount),
                    Arrays.copyOf(this.groundColumns, this.groundCount), Arrays.copyOf(this.groundCodes, this.groundCount),
                    shadeColumns, shadeCounts, logs, leaves);
            this.clear(R);
            return shape;
        }

        /** Empties the volume for the next shape, whose blocks may lie at most {@code reach} from the root. */
        void clear(int reach) {
            for (int k = 0; k < this.count; k++) {
                int i = this.touched[k];
                this.cells[i] = 0;
                this.distance[i] = 127;
            }
            this.count = 0;
            this.groundCount = 0;
            this.reach = Math.min(R, reach);
        }

        static final int[] FACE_X = {0, 1, 0, -1};
        static final int[] FACE_Z = {-1, 0, 1, 0};
    }
}
