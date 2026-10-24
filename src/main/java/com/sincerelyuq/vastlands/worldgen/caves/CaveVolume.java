package com.sincerelyuq.vastlands.worldgen.caves;

import com.sincerelyuq.vastlands.worldgen.hydrology.WaterColumn;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSystems;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * What the generators put in a block, from the systems alone (no chunks): ground and cave space
 * from the density's surface and the cave model, fluids from the cave domains, the sea and the
 * hydrology's river and lake water. For previews and tests: the game itself places these blocks
 * from the router and the fluid picker.
 *
 * <p>{@link #check} counts the fluid blocks vanilla's fluid rules would move: water or lava with
 * open air below it or beside it at its own height, water touching lava, and outside water
 * touching dry cave space. Vanilla flows a fluid block sideways into open air and down into it;
 * a generated cave fluid must have neither.
 */
public final class CaveVolume {
    public enum Block {
        SKY,
        SEA,
        RIVER,
        ROCK,
        CAVE_AIR,
        CAVE_WATER,
        CAVE_LAVA
    }

    private final GenerationSystems systems;
    private final VastlandsCaves caves;
    private final int minY;
    private final int seaTop;

    public CaveVolume(GenerationSystems systems, VastlandsCaves caves) {
        this.systems = systems;
        this.caves = caves;
        this.minY = caves.model().minY();
        this.seaTop = caves.model().seaTop();
    }

    public double surface(int x, int z) {
        return this.systems.surfaceHeight(x, z);
    }

    public Block block(int x, int y, int z) {
        return this.block(x, y, z, this.systems.hydrology().surfaceHeight(x, z), this.riverTop(x, z));
    }

    /** Top of the river or lake water of a column, or {@link Integer#MIN_VALUE}. */
    public int riverTop(int x, int z) {
        WaterColumn water = this.systems.hydrology().water(x, z);
        return water.placed() ? water.waterTop() : Integer.MIN_VALUE;
    }

    /** {@link #block(int, int, int)} with the column's carved ground and river water known. */
    public Block block(int x, int y, int z, int riverTop) {
        return this.block(x, y, z, this.systems.hydrology().surfaceHeight(x, z), riverTop);
    }

    /** {@code surface}: the carved ground before caves (roofs over gorges are checked here). */
    private Block block(int x, int y, int z, double surface, int riverTop) {
        if (y >= surface) {
            if (this.caves.isRoof(x, y, z)) {
                return Block.ROCK;
            }
            if (y <= riverTop) {
                return Block.RIVER;
            }
            return y <= this.seaTop ? Block.SEA : Block.SKY;
        }
        if (y <= this.minY + 4 || !this.caves.isCave(x, y, z)) {
            return Block.ROCK;
        }
        return switch (this.caves.caveFluid(x, y, z)) {
            case AIR -> Block.CAVE_AIR;
            case WATER -> Block.CAVE_WATER;
            case LAVA -> Block.CAVE_LAVA;
        };
    }

    /** Result of {@link #check}. */
    public record Check(long blocks, long caveAir, long caveWater, long caveLava, long waterFlows, long lavaFlows,
                        long waterOnLava, long outsideLeaks, String example) {
        @Override
        public String toString() {
            return String.format(Locale.ROOT,
                    "%d blocks checked: cave air %d, cave water %d, cave lava %d; unstable: water %d, lava %d, water touching lava %d, outside water into dry caves %d%s",
                    this.blocks, this.caveAir, this.caveWater, this.caveLava, this.waterFlows, this.lavaFlows, this.waterOnLava, this.outsideLeaks,
                    this.example.isEmpty() ? "" : " (first at " + this.example + ")");
        }

        public long unstable() {
            return this.waterFlows + this.lavaFlows + this.waterOnLava + this.outsideLeaks;
        }
    }

    /** Builds the blocks of a box of columns (full height) and counts what fluids would flow. */
    public Check check(int x0, int z0, int width, int depth) {
        int top = this.minY;
        double[][] surfaces = new double[depth][width];
        int[][] rivers = new int[depth][width];
        for (int j = 0; j < depth; j++) {
            for (int i = 0; i < width; i++) {
                surfaces[j][i] = this.systems.hydrology().surfaceHeight(x0 + i, z0 + j);
                WaterColumn water = this.systems.hydrology().water(x0 + i, z0 + j);
                rivers[j][i] = water.placed() ? water.waterTop() : Integer.MIN_VALUE;
                top = Math.max(top, (int) Math.ceil(this.surface(x0 + i, z0 + j)) + 2);
            }
        }
        int height = top - this.minY + 1;
        Block[] values = Block.values();
        byte[] blocks = new byte[width * depth * height];
        IntStream.range(0, depth).parallel().forEach(j -> {
            for (int i = 0; i < width; i++) {
                for (int k = 0; k < height; k++) {
                    blocks[(j * width + i) * height + k] = (byte) this.block(x0 + i, this.minY + k, z0 + j, surfaces[j][i], rivers[j][i]).ordinal();
                }
            }
        });
        long air = 0, water = 0, lava = 0, waterFlows = 0, lavaFlows = 0, waterOnLava = 0, leaks = 0;
        String example = "";
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int j = 1; j < depth - 1; j++) {
            for (int i = 1; i < width - 1; i++) {
                for (int k = 1; k < height - 1; k++) {
                    Block b = values[blocks[(j * width + i) * height + k]];
                    if (b == Block.CAVE_AIR) {
                        air++;
                        continue;
                    }
                    boolean caveWater = b == Block.CAVE_WATER;
                    boolean outsideWater = b == Block.SEA || b == Block.RIVER;
                    boolean isLava = b == Block.CAVE_LAVA;
                    if (!caveWater && !outsideWater && !isLava) {
                        continue;
                    }
                    if (caveWater) {
                        water++;
                    } else if (isLava) {
                        lava++;
                    }
                    boolean flows = false;
                    boolean touchesOther = false;
                    boolean leak = false;
                    Block below = values[blocks[(j * width + i) * height + k - 1]];
                    Block above = values[blocks[(j * width + i) * height + k + 1]];
                    if (below == Block.CAVE_AIR || below == Block.SKY && !outsideWater) {
                        flows = true;
                    }
                    for (int[] side : sides) {
                        Block n = values[blocks[((j + side[1]) * width + i + side[0]) * height + k]];
                        if (n == Block.CAVE_AIR || n == Block.SKY && !outsideWater) {
                            flows = true;
                        }
                        if (isLava ? n == Block.CAVE_WATER || n == Block.SEA || n == Block.RIVER : n == Block.CAVE_LAVA) {
                            touchesOther = true;
                        }
                    }
                    if (isLava ? above == Block.CAVE_WATER || above == Block.SEA || above == Block.RIVER || below == Block.CAVE_WATER
                            : above == Block.CAVE_LAVA || below == Block.CAVE_LAVA) {
                        touchesOther = true;
                    }
                    if (outsideWater) {
                        leak = flows;
                        flows = false;
                    }
                    if (flows || touchesOther || leak) {
                        if (example.isEmpty()) {
                            example = String.format(Locale.ROOT, "%d %d %d %s", x0 + i, this.minY + k, z0 + j, b);
                        }
                        if (flows && caveWater) {
                            waterFlows++;
                        } else if (flows) {
                            lavaFlows++;
                        }
                        if (touchesOther && !isLava) {
                            waterOnLava++;
                        }
                        if (leak) {
                            leaks++;
                        }
                    }
                }
            }
        }
        return new Check((long) width * depth * height, air, water, lava, waterFlows, lavaFlows, waterOnLava, leaks, example);
    }
}
