package com.sincerelyuq.vastlands.tools;

import com.sincerelyuq.vastlands.worldgen.WorldHeight;
import com.sincerelyuq.vastlands.worldgen.caves.CaveFluid;
import com.sincerelyuq.vastlands.worldgen.caves.CaveVolume;
import com.sincerelyuq.vastlands.worldgen.caves.VastlandsCaves;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSetup;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSystems;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;

/**
 * Renders the Vastlands caves of a seed without the game: vertical sections and horizontal
 * slices of rock, cave air, cave water and lava, the statistics of a sampled area, a check of the
 * fluids against vanilla's flow rules, and the cost per chunk.
 *
 * <pre>
 * ./gradlew cavePreview --args="--seed 20261018 --center 0 0 --size 1024 --out build/caves/s"
 * </pre>
 *
 * {@code --section} writes {@code <out>-x.png} and {@code <out>-z.png}: sections through the
 * centre along x and along z, one pixel per block, Y -256 at the bottom (sky, sea and river water
 * outside; rock shaded by depth, cave air black, cave water blue, lava orange). {@code --slices
 * Y,Y,...} writes top-down slices. {@code --stats} prints cave volume, water and lava shares by
 * height band over random columns of the square. {@code --check N} builds an N × N column box
 * around the centre and counts fluid blocks vanilla's fluid rules would move (water or lava next
 * to open air at its own height or above air, water touching lava). {@code --bench} times the
 * cave stage per chunk as noise filling calls it.
 */
public final class CavePreview {
    private CavePreview() {
    }

    public static void main(String[] args) throws IOException {
        long seed = 20261018L;
        int cx = 0;
        int cz = 0;
        int size = 1024;
        String out = "build/caves/preview";
        boolean section = false;
        int plan = 0;
        boolean stats = false;
        int tunnels = 0;
        boolean roofs = false;
        boolean bench = false;
        int check = 0;
        int[] slices = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--seed" -> seed = Long.parseLong(args[++i]);
                case "--center" -> {
                    cx = Integer.parseInt(args[++i]);
                    cz = Integer.parseInt(args[++i]);
                }
                case "--size" -> size = Integer.parseInt(args[++i]);
                case "--out" -> out = args[++i];
                case "--section" -> section = true;
                case "--plan" -> plan = Integer.parseInt(args[++i]);
                case "--stats" -> stats = true;
                case "--tunnels" -> tunnels = Integer.parseInt(args[++i]);
                case "--roofs" -> roofs = true;
                case "--bench" -> bench = true;
                case "--check" -> check = Integer.parseInt(args[++i]);
                case "--slices" -> {
                    String[] parts = args[++i].split(",");
                    slices = new int[parts.length];
                    for (int k = 0; k < parts.length; k++) {
                        slices[k] = Integer.parseInt(parts[k].trim());
                    }
                }
                default -> throw new IllegalArgumentException("unknown argument " + args[i]);
            }
        }
        if (!section && !stats && !bench && check == 0 && slices == null && plan == 0 && tunnels == 0 && !roofs) {
            section = true;
        }
        GenerationSetup setup = new GenerationSetup(seed, WorldHeight.MIN_Y, WorldHeight.HEIGHT, 63);
        GenerationSystems systems = GenerationSystems.create(setup);
        VastlandsCaves caves = (VastlandsCaves) systems.caves();
        CaveVolume volume = new CaveVolume(systems, caves);
        long start = System.nanoTime();
        systems.surfaceHeight(cx, cz);
        System.out.printf(Locale.ROOT, "first column (drainage around the centre): %.2f s%n", (System.nanoTime() - start) / 1e9);
        if (section) {
            section(volume, cx, cz, size, true, out + "-x.png");
            section(volume, cx, cz, size, false, out + "-z.png");
        }
        if (plan > 0) {
            plan(volume, cx, cz, size, plan, out + "-plan.png");
        }
        if (slices != null) {
            for (int y : slices) {
                slice(volume, cx, cz, size, y, out + "-y" + y + ".png");
            }
        }
        if (stats) {
            stats(volume, cx, cz, size);
        }
        if (check > 0) {
            CaveVolume.Check result = volume.check(cx - check / 2, cz - check / 2, check, check);
            System.out.println(result);
        }
        if (bench) {
            bench(systems, caves, cx, cz, size);
        }
        if (tunnels > 0) {
            tunnels(caves, cx, cz, size, tunnels);
        }
        if (roofs) {
            roofs(volume, caves, cx, cz, size, out + "-roofs.png");
        }
    }

    private static void section(CaveVolume volume, int cx, int cz, int size, boolean alongX, String path) throws IOException {
        int top = WorldHeight.MIN_Y;
        int[] surfaces = new int[size];
        for (int i = 0; i < size; i++) {
            int x = alongX ? cx - size / 2 + i : cx;
            int z = alongX ? cz : cz - size / 2 + i;
            surfaces[i] = (int) Math.ceil(volume.surface(x, z));
            top = Math.max(top, surfaces[i]);
        }
        top = Math.min(WorldHeight.MAX_Y, top + 24);
        int height = top - WorldHeight.MIN_Y + 1;
        BufferedImage image = new BufferedImage(size, height, BufferedImage.TYPE_INT_RGB);
        final int topY = top;
        IntStream.range(0, size).parallel().forEach(i -> {
            int x = alongX ? cx - size / 2 + i : cx;
            int z = alongX ? cz : cz - size / 2 + i;
            int river = volume.riverTop(x, z);
            for (int y = WorldHeight.MIN_Y; y <= topY; y++) {
                image.setRGB(i, topY - y, color(volume.block(x, y, z, river), y, surfaces[i]));
            }
        });
        File file = new File(path);
        file.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(image, "png", file);
        System.out.printf(Locale.ROOT, "wrote %s (%s through %d,%d, %d blocks, Y %d..%d)%n", file, alongX ? "x" : "z", cx, cz, size, WorldHeight.MIN_Y, top);
    }

    /**
     * Top-down map: every column with cave space, coloured by the height of its highest cave block
     * (deep red through yellow and green to white near the surface), blue where that block is
     * water and orange where it is lava; land grey, sea dark blue.
     */
    private static void plan(CaveVolume volume, int cx, int cz, int size, int scale, String path) throws IOException {
        int n = size / scale;
        BufferedImage image = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        IntStream.range(0, n).parallel().forEach(j -> {
            for (int i = 0; i < n; i++) {
                int x = cx - size / 2 + i * scale;
                int z = cz - size / 2 + j * scale;
                double surface = volume.surface(x, z);
                int rgb = surface < 63 ? 0x1E3A66 : 0x9A9A9A;
                for (int y = (int) Math.ceil(surface) - 1; y > WorldHeight.MIN_Y + 4; y--) {
                    CaveVolume.Block b = volume.block(x, y, z);
                    if (b == CaveVolume.Block.CAVE_AIR || b == CaveVolume.Block.CAVE_WATER || b == CaveVolume.Block.CAVE_LAVA) {
                        double t = Math.max(0.0, Math.min(1.0, (y + 240.0) / 400.0));
                        java.awt.Color c = java.awt.Color.getHSBColor((float) (0.0 + 0.33 * t), 0.85f, (float) (0.45 + 0.55 * t));
                        rgb = c.getRGB() & 0xFFFFFF;
                        if (b == CaveVolume.Block.CAVE_WATER) {
                            rgb = (rgb & 0xFEFEFE) / 2 + 0x1E3C7F;
                        } else if (b == CaveVolume.Block.CAVE_LAVA) {
                            rgb = 0xFF6A00;
                        }
                        break;
                    }
                }
                image.setRGB(i, j, rgb);
            }
        });
        File file = new File(path);
        file.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(image, "png", file);
        System.out.printf(Locale.ROOT, "wrote %s (plan, %d blocks per pixel)%n", file, scale);
    }

    private static void slice(CaveVolume volume, int cx, int cz, int size, int y, String path) throws IOException {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        IntStream.range(0, size).parallel().forEach(j -> {
            for (int i = 0; i < size; i++) {
                int x = cx - size / 2 + i;
                int z = cz - size / 2 + j;
                image.setRGB(i, j, color(volume.block(x, y, z), y, (int) Math.ceil(volume.surface(x, z))));
            }
        });
        File file = new File(path);
        file.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(image, "png", file);
        System.out.printf(Locale.ROOT, "wrote %s (Y %d)%n", file, y);
    }

    private static int color(CaveVolume.Block block, int y, int surface) {
        return switch (block) {
            case SKY -> 0xBFD9F2;
            case SEA -> 0x2B4F8C;
            case RIVER -> 0x3F7FD9;
            case ROCK -> {
                double depth = Math.min(1.0, Math.max(0.0, (surface - y) / 300.0));
                int g = (int) (150 - 80 * depth);
                yield (g << 16) | (g << 8) | g;
            }
            case CAVE_AIR -> 0x101010;
            case CAVE_WATER -> 0x2E86FF;
            case CAVE_LAVA -> 0xFF7A10;
        };
    }

    private static void stats(CaveVolume volume, int cx, int cz, int size) {
        int bands = WorldHeight.HEIGHT / 32;
        long[] rock = new long[bands];
        long[] air = new long[bands];
        long[] water = new long[bands];
        long[] lava = new long[bands];
        java.util.Random random = new java.util.Random(1);
        int columns = 3000;
        for (int n = 0; n < columns; n++) {
            int x = cx - size / 2 + random.nextInt(size);
            int z = cz - size / 2 + random.nextInt(size);
            double surface = volume.surface(x, z);
            for (int y = WorldHeight.MIN_Y; y < surface; y++) {
                int b = (y - WorldHeight.MIN_Y) / 32;
                switch (volume.block(x, y, z)) {
                    case ROCK -> rock[b]++;
                    case CAVE_AIR -> air[b]++;
                    case CAVE_WATER -> water[b]++;
                    case CAVE_LAVA -> lava[b]++;
                    default -> {
                    }
                }
            }
        }
        System.out.printf(Locale.ROOT, "%d random columns of %d x %d around %d,%d; share of the underground volume:%n", columns, size, size, cx, cz);
        long allCave = 0;
        long all = 0;
        for (int b = bands - 1; b >= 0; b--) {
            long total = rock[b] + air[b] + water[b] + lava[b];
            if (total == 0) {
                continue;
            }
            long cave = air[b] + water[b] + lava[b];
            allCave += cave;
            all += total;
            System.out.printf(Locale.ROOT, "  Y %4d..%4d  cave %5.2f%%  (air %5.2f%%, water %5.2f%%, lava %5.2f%%)  of %d blocks%n",
                    WorldHeight.MIN_Y + 32 * b, WorldHeight.MIN_Y + 32 * b + 31, 100.0 * cave / total, 100.0 * air[b] / total,
                    100.0 * water[b] / total, 100.0 * lava[b] / total, total);
        }
        System.out.printf(Locale.ROOT, "  all bands: cave %.2f%%%n", 100.0 * allCave / Math.max(1, all));
    }

    /**
     * Plan of river tunnels at one block per pixel: relief of the generated ground (roofs included)
     * shaded grey, river water blue, roofed river columns (rock over the water) red, the vault
     * over the water dark red, skylights (vault open to the sky inside a roofed stretch) yellow.
     */
    private static void roofs(CaveVolume volume, VastlandsCaves caves, int cx, int cz, int size, String path) throws IOException {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        IntStream.range(0, size).parallel().forEach(j -> {
            for (int i = 0; i < size; i++) {
                int x = cx - size / 2 + i;
                int z = cz - size / 2 + j;
                double h = volume.surface(x, z);
                double hx = volume.surface(x + 1, z);
                double shade = Math.max(0.0, Math.min(1.0, 0.6 + 0.12 * (h - hx)));
                int g = (int) (90 + 120 * shade);
                int rgb = (g << 16) | (g << 8) | g;
                int river = volume.riverTop(x, z);
                double b = caves.roofStrength(x, z);
                if (river != Integer.MIN_VALUE) {
                    rgb = b > 0.0 ? (caves.isRoof(x, river + 1, z) ? 0xC81E1E : 0x7A0F0F) : 0x3F7FD9;
                    if (b <= 0.0 && caves.roofStrength(x + 6, z) + caves.roofStrength(x - 6, z) + caves.roofStrength(x, z + 6) + caves.roofStrength(x, z - 6) > 0.0) {
                        rgb = 0xE8D020;
                    }
                } else if (b > 0.0) {
                    int r = (int) (g * (1.0 - 0.5 * b) + 255 * 0.5 * b);
                    rgb = (r << 16) | ((int) (g * (1 - 0.5 * b)) << 8) | (int) (g * (1 - 0.5 * b));
                }
                image.setRGB(i, j, rgb);
            }
        });
        File file = new File(path);
        file.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(image, "png", file);
        System.out.printf(Locale.ROOT, "wrote %s (river tunnels)%n", file);
    }

    /** Lists the river tunnels of a square: 256-block cells with roofed river columns. */
    private static void tunnels(VastlandsCaves caves, int cx, int cz, int size, int step) {
        int n = size / step;
        java.util.Map<Long, int[]> cells = new java.util.concurrent.ConcurrentHashMap<>();
        IntStream.range(0, n).parallel().forEach(j -> {
            for (int i = 0; i < n; i++) {
                int x = cx - size / 2 + i * step;
                int z = cz - size / 2 + j * step;
                double b = caves.roofStrength(x, z);
                if (b > 0.5) {
                    long key = (long) Math.floorDiv(x, 256) << 32 | (Math.floorDiv(z, 256) & 0xFFFFFFFFL);
                    cells.compute(key, (k, v) -> v == null ? new int[] {1, x, z} : new int[] {v[0] + 1, v[1], v[2]});
                }
            }
        });
        System.out.printf(Locale.ROOT, "%d 256-block cells with roofed river columns (b > 0.5, sampled every %d blocks) in %d x %d around %d,%d%n",
                cells.size(), step, size, size, cx, cz);
        cells.values().stream().sorted((a, b) -> b[0] - a[0]).limit(25)
                .forEach(v -> System.out.printf(Locale.ROOT, "  %d samples, e.g. %d %d%n", v[0], v[1], v[2]));
    }

    private static void bench(GenerationSystems systems, VastlandsCaves caves, int cx, int cz, int size) {
        int chunks = Math.max(1, size / 16);
        for (int pass = 0; pass < 3; pass++) {
            long columnsNanos = 0;
            long blockNanos = 0;
            long fluidNanos = 0;
            long open = 0;
            double sink = 0;
            for (int c = 0; c < chunks; c++) {
                // Pass 1 runs on a fresh square (cold caches), pass 2 on the same one again.
                int bx = cx - size / 2 + c * 16 + Math.min(pass, 1) * size * 2;
                int bz = cz;
                long t0 = System.nanoTime();
                for (int i = 0; i < 16; i++) {
                    for (int j = 0; j < 16; j++) {
                        sink += caves.apply(bx + i, 0, bz + j, 0.3);
                    }
                }
                double[] grounds = new double[256];
                for (int i = 0; i < 16; i++) {
                    for (int j = 0; j < 16; j++) {
                        grounds[i * 16 + j] = systems.surfaceHeight(bx + i, bz + j);
                    }
                }
                long t1 = System.nanoTime();
                for (int y = WorldHeight.MIN_Y; y <= WorldHeight.MAX_Y; y++) {
                    for (int i = 0; i < 16; i++) {
                        for (int j = 0; j < 16; j++) {
                            double density = y < grounds[i * 16 + j] ? 0.45 : -0.46;
                            double d = caves.apply(bx + i, y, bz + j, density);
                            if (d <= 0.0) {
                                open++;
                            }
                        }
                    }
                }
                long t2 = System.nanoTime();
                for (int y = WorldHeight.MIN_Y; y <= WorldHeight.MAX_Y; y++) {
                    for (int i = 0; i < 16; i++) {
                        for (int j = 0; j < 16; j++) {
                            sink += caves.fluidCode(bx + i, y, bz + j);
                        }
                    }
                }
                long t3 = System.nanoTime();
                columnsNanos += t1 - t0;
                blockNanos += t2 - t1;
                fluidNanos += t3 - t2;
            }
            System.out.printf(Locale.ROOT, "pass %d, %d chunks: columns %.2f ms, density for 768 x 256 blocks %.2f ms, fluid picker %.2f ms per chunk; %.1f%% open (%.0f)%n",
                    pass, chunks, columnsNanos / 1e6 / chunks, blockNanos / 1e6 / chunks, fluidNanos / 1e6 / chunks,
                    100.0 * open / (chunks * 256.0 * WorldHeight.HEIGHT), sink * 0);
        }
    }
}
