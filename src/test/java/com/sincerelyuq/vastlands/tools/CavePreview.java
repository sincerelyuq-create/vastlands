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
 * to open air at its own height or above air, water touching lava). {@code --sea} lists the
 * entrances of the square whose mouth lies at the sea, checks each of them and gives sea caves per
 * km of coastline. {@code --bench} times the cave stage per chunk as noise filling calls it.
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
        int[] probe = null;
        int[] order = null;
        boolean bench = false;
        int check = 0;
        boolean sea = false;
        int[] slices = null;
        int places = 0;
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
                case "--order" -> order = new int[] {Integer.parseInt(args[++i]), Integer.parseInt(args[++i]), Integer.parseInt(args[++i])};
                case "--probe" -> probe = new int[] {Integer.parseInt(args[++i]), Integer.parseInt(args[++i]), Integer.parseInt(args[++i])};
                case "--bench" -> bench = true;
                case "--check" -> check = Integer.parseInt(args[++i]);
                case "--sea" -> sea = true;
                case "--places" -> places = Integer.parseInt(args[++i]);
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
        if (!section && !stats && !bench && check == 0 && slices == null && plan == 0 && tunnels == 0 && !roofs && !sea) {
            section = true;
        }
        GenerationSetup setup = new GenerationSetup(seed, WorldHeight.MIN_Y, WorldHeight.HEIGHT, 63);
        GenerationSystems systems = GenerationSystems.create(setup);
        VastlandsCaves caves = (VastlandsCaves) systems.caves();
        CaveVolume volume = new CaveVolume(systems, caves);
        if (places > 0) {
            places(caves, cx, cz, places);
            return;
        }
        if (order != null) {
            // Same block from fresh systems: asked first, and after the 2048-block square around it.
            for (int run = 0; run < 3; run++) {
                GenerationSystems fresh = GenerationSystems.create(setup);
                VastlandsCaves c = (VastlandsCaves) fresh.caves();
                java.util.Random random = new java.util.Random(run);
                for (int k = 0; k < run * 3000; k++) {
                    int x = random.nextInt(4096) - 2048;
                    int z = random.nextInt(4096) - 2048;
                    c.isCave(x, -240 + random.nextInt(400), z);
                }
                System.out.println("run " + run + ": cave " + c.isCave(order[0], order[1], order[2]) + " fluid " + c.caveFluid(order[0], order[1], order[2])
                        + " | " + c.describe(order[0], order[1], order[2]) + " | " + c.debug(order[0], order[2]));
            }
            return;
        }
        if (probe != null) {
            probe(volume, caves, probe[0], probe[1], probe[2]);
            return;
        }
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
        if (sea) {
            sea(systems, volume, caves, cx, cz, size);
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

    /** Megacaves (with lakes, lava, cenotes) and underground river galleries within a radius of the centre. */
    private static void places(VastlandsCaves caves, int cx, int cz, int radius) {
        for (com.sincerelyuq.vastlands.worldgen.caves.MegacaveInfo m : caves.megacaves(cx - radius, cz - radius, cx + radius - 1, cz + radius - 1)) {
            System.out.printf(Locale.ROOT, "megacave %s %s at %.0f %.0f: %.0f x %.0f, floor %.0f, vault %.0f, %s %s%s%n", m.zone(), m.kind(), m.x(), m.z(),
                    2 * m.lengthRadius(), 2 * m.widthRadius(), m.floorY(), m.ceilingY(), m.fluid(), m.fluidTop() == Integer.MIN_VALUE ? "" : m.fluidTop(),
                    m.cenoteX() == m.cenoteX() ? String.format(Locale.ROOT, ", cenote at %.0f %.0f", m.cenoteX(), m.cenoteZ()) : "");
        }
        java.util.List<double[]> rivers = caves.riverGalleries(cx - radius, cz - radius, cx + radius - 1, cz + radius - 1);
        System.out.printf(Locale.ROOT, "%d river gallery pieces%n", rivers.size());
        rivers.stream().limit(20).forEach(r -> System.out.printf(Locale.ROOT, "  river gallery at %.0f %.0f %.0f%n", r[0], r[1], r[2]));
        // Spillway notches near the river galleries: underground falls.
        java.util.Map<Long, int[]> falls = new java.util.concurrent.ConcurrentHashMap<>();
        rivers.parallelStream().forEach(r -> {
            for (int dx = -40; dx <= 40; dx++) {
                for (int dz = -40; dz <= 40; dz++) {
                    int x = (int) r[0] + dx;
                    int z = (int) r[2] + dz;
                    int notch = caves.spillway(x, z);
                    if (notch != Integer.MIN_VALUE) {
                        falls.putIfAbsent((long) Math.floorDiv(x, 32) << 32 | (Math.floorDiv(z, 32) & 0xFFFFFFFFL), new int[] {x, notch, z});
                    }
                }
            }
        });
        System.out.printf(Locale.ROOT, "%d 32-block cells with spillway notches near river galleries%n", falls.size());
        falls.values().stream().limit(12).forEach(f -> System.out.printf(Locale.ROOT, "  notch at %d %d %d%n", f[0], f[1], f[2]));
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

    /** Prints the blocks around a point (x and z ±2, y ±3) and the cave diagnostics of its column. */
    private static void probe(CaveVolume volume, VastlandsCaves caves, int px, int py, int pz) {
        System.out.println(caves.describe(px, py, pz));
        for (int dx = -2; dx <= 2; dx++) {
            System.out.println("  x " + (px + dx) + ": " + caves.debug(px + dx, pz));
        }
        for (int y = py + 3; y >= py - 3; y--) {
            StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "y %4d: ", y));
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    CaveVolume.Block b = volume.block(px + dx, y, pz + dz);
                    line.append(switch (b) {
                        case SKY -> '.';
                        case SEA -> '~';
                        case RIVER -> 'r';
                        case ROCK -> '#';
                        case CAVE_AIR -> 'o';
                        case CAVE_WATER -> 'w';
                        case CAVE_LAVA -> 'L';
                    });
                }
                line.append(' ');
            }
            System.out.println(line);
        }
        System.out.println("water tops / ground (every 2 blocks, x right, z down; G grotto, . dry):");
        for (int dz = -24; dz <= 24; dz += 2) {
            StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "%5d ", pz + dz));
            for (int dx = -24; dx <= 24; dx += 2) {
                int top = volume.riverTop(px + dx, pz + dz);
                boolean grotto = false;
                for (int y = py - 6; y <= py + 6 && !grotto; y++) {
                    grotto = caves.isGrotto(px + dx, y, pz + dz);
                }
                line.append(grotto ? "  GG" : top == Integer.MIN_VALUE ? String.format(Locale.ROOT, "%4d", (int) volume.surface(px + dx, pz + dz)).replace(' ', '.') : String.format(Locale.ROOT, "%4d", top));
            }
            System.out.println(line);
        }
        for (int dz = -2; dz <= 2; dz++) {
            StringBuilder line = new StringBuilder("surface: ");
            for (int dx = -2; dx <= 2; dx++) {
                line.append(String.format(Locale.ROOT, "%7.2f/%4d ", volume.surface(px + dx, pz + dz), volume.riverTop(px + dx, pz + dz)));
            }
            System.out.println(line);
        }
    }

    /**
     * Sea caves of a square: every entrance of the network whose mouth lies at or in the sea (the
     * sea within its radius), checked block by block (cave water open to the sea beside or above
     * it), and the coastline length (land and sea columns side by side, sampled every 8 blocks).
     */
    private static void sea(GenerationSystems systems, CaveVolume volume, VastlandsCaves caves, int cx, int cz, int size) {
        int x0 = cx - size / 2;
        int z0 = cz - size / 2;
        int n = size / 8;
        boolean[][] wet = new boolean[n][n];
        IntStream.range(0, n).parallel().forEach(j -> {
            for (int i = 0; i < n; i++) {
                wet[j][i] = systems.hydrology().water(x0 + i * 8, z0 + j * 8).kind() == com.sincerelyuq.vastlands.worldgen.hydrology.WaterColumn.Kind.SEA;
            }
        });
        long edges = 0;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                edges += i + 1 < n && wet[j][i] != wet[j][i + 1] ? 1 : 0;
                edges += j + 1 < n && wet[j][i] != wet[j + 1][i] ? 1 : 0;
            }
        }
        double coast = edges * 8.0 * Math.PI / 4.0;
        int adits = 0;
        int sinkholes = 0;
        for (double[] e : caves.entrances(x0, z0, x0 + size - 1, z0 + size - 1)) {
            boolean sinkhole = e[7] == 1;
            double mx = sinkhole ? e[0] : e[3];
            double mz = sinkhole ? e[2] : e[5];
            if (mx >= x0 && mx < x0 + size && mz >= z0 && mz < z0 + size) {
                adits += sinkhole ? 0 : 1;
                sinkholes += sinkhole ? 1 : 0;
            }
        }
        java.util.List<CaveVolume.SeaCave> found = volume.seaCaves(x0, z0, size);
        System.out.printf(Locale.ROOT, "%d x %d blocks: coastline ~%.1f km; %d adits and %d sinkholes, %d with a mouth at the sea%n",
                size, size, coast / 1000.0, adits, sinkholes, found.size());
        int open = 0;
        for (CaveVolume.SeaCave c : found) {
            open += c.check().seaOpenings() > 0 ? 1 : 0;
            System.out.printf(Locale.ROOT, "  %s at %.0f %.0f (r %.1f): %d cave water blocks open to the sea, unstable %d%n",
                    c.sinkhole() ? "sinkhole" : "adit", c.x(), c.z(), c.radius(), c.check().seaOpenings(), c.check().unstable());
        }
        System.out.printf(Locale.ROOT, "%d sea caves open to the sea: %.2f per km of coastline%n", open, open / Math.max(0.001, coast / 1000.0));
    }

    /** Lists the river tunnels of a square: 256-block cells with roofed river columns. */
    private static void tunnels(VastlandsCaves caves, int cx, int cz, int size, int step) {
        int n = size / step;
        java.util.Map<Long, int[]> cells = new java.util.concurrent.ConcurrentHashMap<>();
        java.util.Set<Long> swallets = java.util.concurrent.ConcurrentHashMap.newKeySet();
        java.util.Map<Long, int[]> swalletSamples = new java.util.concurrent.ConcurrentHashMap<>();
        java.util.Map<Long, int[]> grottoes = new java.util.concurrent.ConcurrentHashMap<>();
        IntStream.range(0, n).parallel().forEach(j -> {
            for (int i = 0; i < n; i++) {
                int x = cx - size / 2 + i * step;
                int z = cz - size / 2 + j * step;
                double b = caves.roofStrength(x, z);
                if (i % 64 == 0 && j % 64 == 0) {
                    for (int[] g : caves.grottoesNear(x, z)) {
                        grottoes.putIfAbsent((long) g[0] << 32 | (g[1] & 0xFFFFFFFFL), g);
                    }
                }
                if (i % 4 == 0 && j % 4 == 0) {
                    for (int[] sw : caves.swalletsNear(x, z)) {
                        long key = (long) sw[0] << 32 | (sw[1] & 0xFFFFFFFFL);
                        if (swallets.add(key)) {
                            swalletSamples.put(key, sw);
                        }
                    }
                }
                if (b > 0.5) {
                    long key = (long) Math.floorDiv(x, 256) << 32 | (Math.floorDiv(z, 256) & 0xFFFFFFFFL);
                    cells.compute(key, (k, v) -> v == null ? new int[] {1, x, z} : new int[] {v[0] + 1, v[1], v[2]});
                }
            }
        });
        System.out.printf(Locale.ROOT, "%d 256-block cells with roofed river columns (b > 0.5, sampled every %d blocks) in %d x %d around %d,%d%n",
                cells.size(), step, size, size, cx, cz);
        System.out.printf(Locale.ROOT, "%d grottoes behind falls%n", grottoes.size());
        grottoes.values().stream().limit(10).forEach(g -> System.out.printf(Locale.ROOT, "  grotto at %d %d, floor %d ceiling %d%n", g[0], g[1], g[2], g[3]));
        System.out.printf(Locale.ROOT, "%d swallets%n", swallets.size());
        swalletSamples.values().stream().limit(15).forEach(v -> System.out.printf(Locale.ROOT, "  swallet shaft at %d %d, river water top %d, shaft bottom %d%n", v[0], v[1], v[2], v[3]));
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
