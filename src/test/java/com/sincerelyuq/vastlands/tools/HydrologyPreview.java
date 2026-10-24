package com.sincerelyuq.vastlands.tools;

import com.sincerelyuq.vastlands.worldgen.WorldHeight;
import com.sincerelyuq.vastlands.worldgen.hydrology.HydrologyModel;
import com.sincerelyuq.vastlands.worldgen.hydrology.VastlandsHydrology;
import com.sincerelyuq.vastlands.worldgen.hydrology.WaterColumn;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSetup;
import com.sincerelyuq.vastlands.worldgen.terrain.TerrainColumn;
import com.sincerelyuq.vastlands.worldgen.terrain.VastlandsTerrain;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import net.minecraft.world.level.biome.Climate;

/**
 * Renders the rivers and lakes of a seed over the carved terrain, without the game.
 *
 * <pre>
 * ./gradlew hydrologyPreview --args="--seed 20261018 --center 0 0 --size 16384 --scale 8 --out build/hydrology/overview"
 * </pre>
 *
 * Writes {@code <out>.png} (hillshaded relief of the carved surface, rivers and lakes in blue,
 * frozen ones (in snowy or frozen biomes, by vanilla's biome table) pale, the sea dark, falls of 8
 * blocks or more white) and prints the time the drainage took, river statistics, the tallest falls
 * and the widest rivers of the square. {@code --probe X Z} prints the columns and segments around a
 * point; {@code --water FILE} exports the expected water of the square for
 * {@code tools/worldcheck/rivercheck.py --expected}.
 */
public final class HydrologyPreview {
    private static final double SEA = 63.0;

    private HydrologyPreview() {
    }

    /**
     * Expected water of a square at 1 block per pixel, row-major (z then x), for
     * {@code tools/worldcheck/rivercheck.py --expected}: int16 ground tops, then int16 water tops,
     * then one byte per column (0 dry, 1 sea, 2 river, 3 lake); little-endian. Uses the game's
     * hydrology system, so it is exactly what the world should contain.
     */
    private static void writeWater(long seed, String path, int x0, int z0, int n) throws IOException {
        GenerationSetup setup = new GenerationSetup(seed, WorldHeight.MIN_Y, WorldHeight.HEIGHT, (int) SEA);
        VastlandsTerrain terrain = new VastlandsTerrain(setup);
        VastlandsHydrology hydrology = new VastlandsHydrology(setup, terrain);
        short[] grounds = new short[n * n];
        short[] tops = new short[n * n];
        byte[] kinds = new byte[n * n];
        IntStream.range(0, n).parallel().forEach(j -> {
            for (int i = 0; i < n; i++) {
                WaterColumn column = hydrology.water(x0 + i, z0 + j);
                grounds[j * n + i] = (short) column.groundTop();
                tops[j * n + i] = (short) (column.wet() ? column.waterTop() : -32768);
                kinds[j * n + i] = (byte) column.kind().ordinal();
            }
        });
        ByteBuffer buffer = ByteBuffer.allocate(n * n * 5).order(ByteOrder.LITTLE_ENDIAN);
        for (short g : grounds) {
            buffer.putShort(g);
        }
        for (short t : tops) {
            buffer.putShort(t);
        }
        buffer.put(kinds);
        File file = new File(path);
        file.getAbsoluteFile().getParentFile().mkdirs();
        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(buffer.array());
        }
        System.out.printf(Locale.ROOT, "wrote %dx%d water columns from x %d z %d to %s%n", n, n, x0, z0, file);
    }

    /**
     * Cost of the hydrology per chunk once the drainage of the area is known: the node columns the
     * density reads (5 × 5 per chunk) and the water columns the block step reads (20 × 20 per
     * chunk, twice), single-threaded, chunk by chunk over the square.
     */
    private static void benchmark(long seed, int centerX, int centerZ, int size) {
        GenerationSetup setup = new GenerationSetup(seed, WorldHeight.MIN_Y, WorldHeight.HEIGHT, (int) SEA);
        VastlandsTerrain terrain = new VastlandsTerrain(setup);
        VastlandsHydrology hydrology = new VastlandsHydrology(setup, terrain);
        long start = System.nanoTime();
        hydrology.water(centerX, centerZ);
        System.out.printf(Locale.ROOT, "drainage and rivers around the centre: %.2f s (%s)%n", (System.nanoTime() - start) / 1e9, HydrologyModel.COUNTERS);
        int chunks = size / 16;
        int x0 = centerX - size / 2;
        int z0 = centerZ - size / 2;
        for (int pass = 0; pass < 2; pass++) {
            long terrainNanos = 0;
            long nodeNanos = 0;
            long waterNanos = 0;
            int placed = 0;
            double sink = 0;
            for (int cz = 0; cz < chunks; cz++) {
                for (int cx = 0; cx < chunks; cx++) {
                    int bx = x0 + cx * 16;
                    int bz = z0 + cz * 16;
                    long t0 = System.nanoTime();
                    for (int i = 0; i <= 16; i += 4) {
                        for (int j = 0; j <= 16; j += 4) {
                            sink += terrain.column(bx + i, bz + j).height();
                        }
                    }
                    long t1 = System.nanoTime();
                    for (int i = 0; i <= 16; i += 4) {
                        for (int j = 0; j <= 16; j += 4) {
                            sink += hydrology.carvedHeight(bx + i, bz + j);
                        }
                    }
                    long t2 = System.nanoTime();
                    for (int k = 0; k < 2 && hydrology.mayHoldWater(bx - 2, bz - 2, bx + 17, bz + 17); k++) {
                        for (int i = -2; i < 18; i++) {
                            for (int j = -2; j < 18; j++) {
                                WaterColumn column = hydrology.water(bx + i, bz + j);
                                placed += column.placed() ? 1 : 0;
                            }
                        }
                    }
                    long t3 = System.nanoTime();
                    terrainNanos += t1 - t0;
                    nodeNanos += t2 - t1;
                    waterNanos += t3 - t2;
                }
            }
            int n = chunks * chunks;
            System.out.printf(Locale.ROOT, "pass %d, %d chunks: terrain grid %.0f us, hydrology nodes %.0f us, water columns (2 x 400) %.0f us per chunk; %d placed water columns (%.0f)%n",
                    pass, n, terrainNanos / 1e3 / n, nodeNanos / 1e3 / n, waterNanos / 1e3 / n, placed / 2, sink * 0);
        }
    }

    public static void main(String[] args) throws IOException {
        long seed = 20261018L;
        double centerX = 0;
        double centerZ = 0;
        int size = 16384;
        double scale = 8;
        String out = "build/hydrology/preview";
        int[] probe = null;
        String waterFile = null;
        boolean bench = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--seed" -> seed = Long.parseLong(args[++i]);
                case "--center" -> {
                    centerX = Double.parseDouble(args[++i]);
                    centerZ = Double.parseDouble(args[++i]);
                }
                case "--size" -> size = Integer.parseInt(args[++i]);
                case "--scale" -> scale = Double.parseDouble(args[++i]);
                case "--out" -> out = args[++i];
                case "--water" -> waterFile = args[++i];
                case "--bench" -> bench = true;
                case "--probe" -> {
                    probe = new int[] {Integer.parseInt(args[++i]), Integer.parseInt(args[++i])};
                }
                default -> throw new IllegalArgumentException("unknown argument " + args[i]);
            }
        }
        VastlandsTerrain terrain = new VastlandsTerrain(new GenerationSetup(seed, WorldHeight.MIN_Y, WorldHeight.HEIGHT, (int) SEA));
        HydrologyModel hydrology = new HydrologyModel(terrain.geography(), (x, z) -> terrain.sample(x, z).height());
        if (bench) {
            benchmark(seed, (int) centerX, (int) centerZ, size);
            return;
        }
        if (waterFile != null) {
            writeWater(seed, waterFile, (int) Math.floor(centerX - size / 2.0), (int) Math.floor(centerZ - size / 2.0), size);
            return;
        }
        if (probe != null) {
            for (int dx = -8; dx <= 8; dx += 2) {
                HydrologyModel.Column c = new HydrologyModel.Column();
                double h = terrain.column(probe[0] + dx, probe[1]).height();
                hydrology.sample(probe[0] + dx, probe[1], h, c);
                StringBuilder tops = new StringBuilder();
                for (int k = 0; k < c.topCount; k++) {
                    tops.append(' ').append(c.tops[k]);
                }
                System.out.printf(Locale.ROOT, "probe %d,%d terrain %.1f carved %.1f tops[%s] river w %.1f top %d edge %.1f lake %d%n",
                        probe[0] + dx, probe[1], h, c.carved, tops, c.riverWidth, c.riverTop, c.riverEdge, c.lakeTop);
            }
            System.out.println(hydrology.describe(probe[0], probe[1]));
            return;
        }
        int n = (int) Math.round(size / scale);
        double x0 = centerX - size / 2.0;
        double z0 = centerZ - size / 2.0;
        long start = System.nanoTime();
        hydrology.sample((int) centerX, (int) centerZ, 70.0, new HydrologyModel.Column());
        System.out.printf(Locale.ROOT, "first column (drainage of the surrounding tiles): %.2f s%n", (System.nanoTime() - start) / 1e9);

        double[][] carved = new double[n][n];
        int[][] water = new int[n][n];
        boolean[][] lake = new boolean[n][n];
        boolean[][] frozen = new boolean[n][n];
        double finalScale = scale;
        start = System.nanoTime();
        IntStream.range(0, n).parallel().forEach(j -> {
            HydrologyModel.Column column = new HydrologyModel.Column();
            for (int i = 0; i < n; i++) {
                int x = (int) Math.floor(x0 + i * finalScale);
                int z = (int) Math.floor(z0 + j * finalScale);
                double h = terrain.column(x, z).height();
                hydrology.sample(x, z, h, column);
                carved[j][i] = column.carved;
                int top = Integer.MIN_VALUE;
                for (int k = 0; k < column.topCount; k++) {
                    if (column.carved <= column.tops[k]) {
                        top = column.tops[k];
                        break;
                    }
                }
                if (top < SEA && h < SEA) {
                    // A channel that runs on into the sea at its level is sea.
                    top = Integer.MIN_VALUE;
                }
                water[j][i] = top;
                lake[j][i] = top != Integer.MIN_VALUE && top == column.lakeTop;
                if (top != Integer.MIN_VALUE) {
                    // The surface biome vanilla's table picks: frozen rivers and lakes in polar climates.
                    TerrainColumn grid = terrain.column(x & ~3, z & ~3);
                    float weirdness = column.riverBiome ? 0.0F : (float) grid.climateWeirdness();
                    Climate.TargetPoint target = Climate.target((float) grid.climateTemperature(), (float) grid.climateHumidity(),
                            (float) grid.climateContinentalness(), (float) grid.climateErosion(), 0.0F, weirdness);
                    String biome = TerrainPreview.BIOME_TABLE.findValue(target).location().getPath();
                    frozen[j][i] = biome.startsWith("frozen") || biome.startsWith("snowy") || biome.equals("ice_spikes");
                }
            }
        });
        System.out.printf(Locale.ROOT, "%d columns in %.2f s%n", n * n, (System.nanoTime() - start) / 1e9);
        // Places worth a look: the widest rivers and the tallest falls of the square.
        java.util.List<double[]> widest = new java.util.ArrayList<>();
        java.util.Set<String> seenFalls = new java.util.HashSet<>();
        java.util.List<double[]> falls = new java.util.ArrayList<>();
        HydrologyModel.Column probeColumn = new HydrologyModel.Column();
        for (int j = 0; j < n; j += Math.max(1, (int) (256 / scale))) {
            for (int i = 0; i < n; i += Math.max(1, (int) (256 / scale))) {
                int x = (int) Math.floor(x0 + i * scale);
                int z = (int) Math.floor(z0 + j * scale);
                for (double[] f : hydrology.falls(x, z)) {
                    if (seenFalls.add((int) f[0] + "," + (int) f[1]) && f[0] >= x0 && f[0] < x0 + size && f[1] >= z0 && f[1] < z0 + size) {
                        falls.add(f);
                    }
                }
                hydrology.sample(x, z, terrain.column(x, z).height(), probeColumn);
                if (probeColumn.riverEdge < 0) {
                    widest.add(new double[] {x, z, probeColumn.riverWidth, probeColumn.riverTop});
                }
            }
        }
        falls.sort((a, b) -> Double.compare(b[2] - b[3], a[2] - a[3]));
        widest.sort((a, b) -> Double.compare(b[2], a[2]));
        StringBuilder places = new StringBuilder("tallest falls:");
        for (int k = 0; k < Math.min(8, falls.size()); k++) {
            double[] f = falls.get(k);
            places.append(String.format(Locale.ROOT, " %.0f,%.0f (%d->%d)", f[0], f[1], (int) f[2], (int) f[3]));
        }
        places.append(String.format(Locale.ROOT, "%nwidest rivers sampled:"));
        for (int k = 0; k < Math.min(8, widest.size()); k++) {
            double[] w = widest.get(k);
            places.append(String.format(Locale.ROOT, " %.0f,%.0f (%.0f wide, top %d)", w[0], w[1], w[2], (int) w[3]));
        }
        System.out.println(places);
        HydrologyModel.Stats stats = hydrology.stats((int) centerX, (int) centerZ);
        System.out.printf(Locale.ROOT, "around the centre: %d river segments, %d steps, %d falls of 8+ blocks, tallest %.0f%n",
                stats.segments(), stats.falls(), stats.tallFalls(), stats.tallestFall());

        int wet = 0;
        int land = 0;
        double[][] shade = TerrainPreview.shade(carved, scale);
        BufferedImage image = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                double h = carved[j][i];
                int[] c;
                if (water[j][i] != Integer.MIN_VALUE) {
                    c = frozen[j][i] ? new int[] {175, 205, 240} : (lake[j][i] ? new int[] {70, 120, 200} : new int[] {40, 90, 225});
                    boolean fall = false;
                    for (int[] o : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int ii = i + o[0];
                        int jj = j + o[1];
                        if (ii >= 0 && jj >= 0 && ii < n && jj < n && water[jj][ii] != Integer.MIN_VALUE && water[j][i] - water[jj][ii] >= 8) {
                            fall = true;
                        }
                    }
                    if (fall) {
                        c = new int[] {255, 255, 255};
                    }
                    wet++;
                } else {
                    c = TerrainPreview.color(h);
                }
                if (h >= SEA) {
                    land++;
                }
                double s = water[j][i] != Integer.MIN_VALUE ? 0.85 + 0.15 * shade[j][i] : shade[j][i];
                image.setRGB(i, j, (int) Math.min(255, c[0] * s) << 16 | (int) Math.min(255, c[1] * s) << 8 | (int) Math.min(255, c[2] * s));
            }
        }
        System.out.println("work: " + HydrologyModel.COUNTERS);
        System.out.printf(Locale.ROOT, "rivers and lakes above sea level: %.2f%% of the land%n", 100.0 * wet / Math.max(1, land));
        File file = new File(out + ".png");
        file.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(image, "png", file);
        System.out.println("wrote " + file);
    }
}
