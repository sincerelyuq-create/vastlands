package com.sincerelyuq.vastlands.tools;

import com.sincerelyuq.vastlands.worldgen.WorldHeight;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSetup;
import com.sincerelyuq.vastlands.worldgen.terrain.TerrainColumn;
import com.sincerelyuq.vastlands.worldgen.terrain.VastlandsTerrain;
import java.awt.image.BufferedImage;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;

/**
 * Renders the Vastlands terrain of a seed without starting the game, and exports expected surface
 * heights for comparison with generated worlds ({@code tools/worldcheck/heightmap.py --expected}).
 *
 * <pre>
 * ./gradlew terrainPreview --args="--seed 20261018 --center 0 0 --size 16384 --scale 16 --out build/terrain-preview/overview"
 * ./gradlew terrainPreview --args="--seed 20261018 --center 0 0 --size 512 --scale 1 --heights run/expected.bin"
 * </pre>
 *
 * Writes {@code <out>.png} (hillshaded relief map, north up), {@code <out>-view.png} (perspective
 * view from the south edge looking north, or from {@code --camera x z yawDegrees altitude}) and
 * prints the height distribution and the sampling cost. {@code --heights} writes the terrain
 * surface heights (float32 little-endian, row-major, z then x) of the square at 1 block/pixel.
 */
public final class TerrainPreview {
    private static final double SEA = 63.0;

    private TerrainPreview() {
    }

    public static void main(String[] args) throws IOException {
        long seed = 20261018L;
        double centerX = 0;
        double centerZ = 0;
        int size = 16384;
        double scale = 16;
        String out = null;
        String heights = null;
        double[] camera = null;
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
                case "--heights" -> heights = args[++i];
                case "--camera" -> camera = new double[] {
                        Double.parseDouble(args[++i]), Double.parseDouble(args[++i]), Double.parseDouble(args[++i]), Double.parseDouble(args[++i])};
                default -> throw new IllegalArgumentException("unknown argument " + args[i]);
            }
        }
        VastlandsTerrain terrain = new VastlandsTerrain(new GenerationSetup(seed, WorldHeight.MIN_Y, WorldHeight.HEIGHT, (int) SEA));

        if (heights != null) {
            int x0 = (int) Math.floor(centerX - size / 2.0);
            int z0 = (int) Math.floor(centerZ - size / 2.0);
            float[] values = new float[size * size];
            int n = size;
            IntStream.range(0, n).parallel().forEach(j -> {
                for (int i = 0; i < n; i++) {
                    values[j * n + i] = (float) terrain.surfaceHeight(x0 + i, z0 + j);
                }
            });
            ByteBuffer buffer = ByteBuffer.allocate(values.length * 4).order(ByteOrder.LITTLE_ENDIAN);
            buffer.asFloatBuffer().put(values);
            File file = new File(heights);
            file.getAbsoluteFile().getParentFile().mkdirs();
            try (DataOutputStream stream = new DataOutputStream(new FileOutputStream(file))) {
                stream.write(buffer.array());
            }
            System.out.printf(Locale.ROOT, "wrote %dx%d surface heights from x %d z %d to %s%n", n, n, x0, z0, file);
        }
        if (out == null) {
            return;
        }

        int n = (int) Math.round(size / scale);
        double x0 = centerX - size / 2.0;
        double z0 = centerZ - size / 2.0;
        double[][] h = new double[n][n];
        double finalScale = scale;
        long start = System.nanoTime();
        IntStream.range(0, n).parallel().forEach(j -> {
            for (int i = 0; i < n; i++) {
                TerrainColumn column = terrain.column((int) Math.floor(x0 + i * finalScale), (int) Math.floor(z0 + j * finalScale));
                h[j][i] = column.height();
            }
        });
        double seconds = (System.nanoTime() - start) / 1e9;
        int threads = Runtime.getRuntime().availableProcessors();
        System.out.printf(Locale.ROOT, "seed %d: %d columns in %.2f s on %d threads, %.1f us per column per thread%n",
                seed, n * n, seconds, threads, seconds * 1e6 * threads / ((double) n * n));
        printDistribution(h);

        File png = new File(out + ".png");
        png.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(relief(h, scale), "png", png);
        double[] cam = camera == null ? new double[] {n / 2.0, n - 1.0, 0.0, 140.0}
                : new double[] {(camera[0] - x0) / scale, (camera[1] - z0) / scale, camera[2], camera[3]};
        ImageIO.write(perspective(h, scale, cam), "png", new File(out + "-view.png"));
        System.out.println("wrote " + png + " and " + out + "-view.png");
    }

    private static void printDistribution(double[][] h) {
        double[] all = Arrays.stream(h).flatMapToDouble(Arrays::stream).sorted().toArray();
        int[] limits = {-50, 0, 63, 100, 150, 200, 250, 300, 350, 400, 450, 512};
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT, "min %.1f, median %.1f, max %.1f%n", all[0], all[all.length / 2], all[all.length - 1]));
        for (int i = 0; i < limits.length - 1; i++) {
            int lo = limits[i];
            int hi = limits[i + 1];
            long count = Arrays.stream(all).filter(v -> v >= lo && v < hi).count();
            if (count > 0) {
                text.append(String.format(Locale.ROOT, "  Y %4d..%-4d %6.2f%%%n", lo, hi, 100.0 * count / all.length));
            }
        }
        System.out.print(text);
    }

    static int[] color(double v) {
        if (v < SEA) {
            double d = Math.min(1, (SEA - v) / 70.0);
            return new int[] {(int) (70 - 55 * d), (int) (130 - 95 * d), (int) (190 - 90 * d)};
        }
        double[][] stops = {
                {63, 196, 190, 140}, {66, 104, 156, 82}, {90, 88, 140, 66}, {120, 132, 156, 84},
                {160, 160, 146, 98}, {210, 140, 116, 90}, {270, 128, 120, 116}, {330, 200, 200, 204}, {420, 250, 250, 255}};
        for (int i = 0; i < stops.length - 1; i++) {
            if (v < stops[i + 1][0]) {
                double t = (v - stops[i][0]) / (stops[i + 1][0] - stops[i][0]);
                return new int[] {
                        (int) (stops[i][1] + t * (stops[i + 1][1] - stops[i][1])),
                        (int) (stops[i][2] + t * (stops[i + 1][2] - stops[i][2])),
                        (int) (stops[i][3] + t * (stops[i + 1][3] - stops[i][3]))};
            }
        }
        return new int[] {250, 250, 255};
    }

    /** Hypsometric tint with hillshading, light from the north-west. */
    static BufferedImage relief(double[][] h, double scale) {
        int n = h.length;
        BufferedImage image = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        double lx = -0.5, ly = 0.6, lz = -0.62;
        double ll = Math.sqrt(lx * lx + ly * ly + lz * lz);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                double v = h[j][i];
                double dx = v < SEA ? 0 : (h[j][Math.min(n - 1, i + 1)] - h[j][Math.max(0, i - 1)]) / (2 * scale);
                double dz = v < SEA ? 0 : (h[Math.min(n - 1, j + 1)][i] - h[Math.max(0, j - 1)][i]) / (2 * scale);
                double shade = (-dx * lx + ly - dz * lz) / ll / Math.sqrt(dx * dx + 1 + dz * dz);
                image.setRGB(i, j, rgb(color(v), 0.35 + 0.75 * Math.max(0, shade)));
            }
        }
        return image;
    }

    private static int rgb(int[] c, double s) {
        return (int) Math.min(255, c[0] * s) << 16 | (int) Math.min(255, c[1] * s) << 8 | (int) Math.min(255, c[2] * s);
    }

    private static double bilinear(double[][] h, double x, double z) {
        int n = h.length;
        x = Math.max(0, Math.min(n - 1.001, x));
        z = Math.max(0, Math.min(n - 1.001, z));
        int i = (int) x;
        int j = (int) z;
        double fx = x - i;
        double fz = z - j;
        return (h[j][i] * (1 - fx) + h[j][i + 1] * fx) * (1 - fz) + (h[j + 1][i] * (1 - fx) + h[j + 1][i + 1] * fx) * fz;
    }

    /** Height-field ray marching ("voxel space"); camera = grid x, grid z, yaw (0 = north), altitude in blocks. */
    static BufferedImage perspective(double[][] h, double scale, double[] camera) {
        int n = h.length;
        int width = 1600;
        int height = 900;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            double t = (double) y / height;
            int sky = (int) (150 + 60 * t) << 16 | (int) (185 + 40 * t) << 8 | 235;
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, sky);
            }
        }
        int[] lowest = new int[width];
        Arrays.fill(lowest, height);
        double yaw = Math.toRadians(camera[2]);
        double fx = Math.sin(yaw), fz = -Math.cos(yaw), rx = Math.cos(yaw), rz = Math.sin(yaw);
        double eye = Math.max(bilinear(h, camera[0], camera[1]), SEA) + camera[3];
        double horizon = height * 0.35;
        double far = n * 1.5;
        for (double d = 1; d < far; d += 0.2 + d * 0.003) {
            double distance = d * scale;
            double fog = Math.min(1, distance / (far * scale * 0.9));
            for (int sx = 0; sx < width; sx++) {
                double lateral = (sx - width / 2.0) / (width / 2.0) * d * 0.75;
                double gx = camera[0] + fx * d + rx * lateral;
                double gz = camera[1] + fz * d + rz * lateral;
                if (gx < 1 || gx >= n - 2 || gz < 1 || gz >= n - 2) {
                    continue;
                }
                double v = bilinear(h, gx, gz);
                int sy = (int) ((eye - Math.max(v, SEA)) / distance * height + horizon);
                if (sy < lowest[sx]) {
                    double dx = (bilinear(h, gx + 1, gz) - bilinear(h, gx - 1, gz)) / (2 * scale);
                    double dz = (bilinear(h, gx, gz + 1) - bilinear(h, gx, gz - 1)) / (2 * scale);
                    double s = v < SEA ? 0.9 : 0.4 + 0.75 * Math.max(0, (dx * 0.55 + dz * 0.45 + 0.75) / Math.sqrt(dx * dx + dz * dz + 1));
                    int[] c = color(v);
                    int color = (int) (Math.min(255, c[0] * s) * (1 - fog) + 175 * fog) << 16
                            | (int) (Math.min(255, c[1] * s) * (1 - fog) + 200 * fog) << 8
                            | (int) (Math.min(255, c[2] * s) * (1 - fog) + 230 * fog);
                    for (int y = Math.max(0, sy); y < lowest[sx]; y++) {
                        image.setRGB(sx, y, color);
                    }
                    lowest[sx] = Math.max(0, sy);
                }
            }
        }
        return image;
    }
}
