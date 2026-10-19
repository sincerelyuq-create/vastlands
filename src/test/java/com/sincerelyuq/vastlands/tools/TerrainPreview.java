package com.sincerelyuq.vastlands.tools;

import com.sincerelyuq.vastlands.worldgen.WorldHeight;
import com.sincerelyuq.vastlands.worldgen.geography.FrozenSea;
import com.sincerelyuq.vastlands.worldgen.geography.GeographySample;
import com.sincerelyuq.vastlands.worldgen.geography.GeographySettings;
import com.sincerelyuq.vastlands.worldgen.geography.Landform;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSetup;
import com.sincerelyuq.vastlands.worldgen.terrain.TerrainColumn;
import com.sincerelyuq.vastlands.worldgen.terrain.VastlandsTerrain;
import java.awt.image.BufferedImage;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;

/**
 * Renders the Vastlands terrain and geography of a seed without starting the game, and exports
 * expected surface heights for comparison with generated worlds ({@code tools/worldcheck/heightmap.py --expected}).
 *
 * <pre>
 * ./gradlew terrainPreview --args="--seed 20261018 --center 0 0 --size 16384 --scale 16 --out build/terrain-preview/overview"
 * ./gradlew terrainPreview --args="--seed 20261018 --size 65536 --scale 64 --maps relief,landform,biome,temperature,moisture --out build/p/world"
 * ./gradlew terrainPreview --args="--seed 20261018 --center 0 0 --size 512 --scale 1 --heights run/expected.bin"
 * </pre>
 *
 * Maps ({@code --maps}, default {@code relief}): {@code relief} (hillshaded hypsometric tint, plus
 * a perspective view {@code <out>-view.png} from the south edge or {@code --camera x z yaw altitude}),
 * {@code landform} (regional landform classes), {@code biome} (the surface biome vanilla's table
 * picks from the terrain's climate parameters), {@code temperature}, {@code moisture} (climate
 * bands) and {@code ice} (the polar ice field cover). Each map is written to {@code <out>-<map>.png}
 * ({@code <out>.png} for relief). It prints the height distribution, landform, climate and biome
 * shares, the mean length of biome runs along rows, and the sampling cost.
 * {@code --region-scale F} renders with all macro wavelengths multiplied by F.
 * {@code --heights} writes the terrain surface heights (float32 little-endian, row-major, z then x)
 * of the square at 1 block/pixel, {@code --cover} the expected sea ice cover (see {@link #writeCover}).
 * {@code --describe-ice X Z} prints the ice field's intermediate values at five columns from X - 2.
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
        String cover = null;
        double[] camera = null;
        List<String> maps = List.of("relief");
        GeographySettings settings = GeographySettings.DEFAULT;
        double[] iceCenter = null;
        String find = null;
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
                case "--cover" -> cover = args[++i];
                case "--maps" -> maps = List.of(args[++i].split(","));
                case "--find" -> find = args[++i];
                case "--describe-ice" -> {
                    VastlandsTerrain t = new VastlandsTerrain(new GenerationSetup(seed, WorldHeight.MIN_Y, WorldHeight.HEIGHT, (int) SEA), settings);
                    double dx = Double.parseDouble(args[++i]);
                    double dz = Double.parseDouble(args[++i]);
                    for (int k = -2; k <= 2; k++) {
                        double strength = FrozenSea.strength(t.geography().seaLevelTemperature(dx + k, dz));
                        System.out.println("ICE " + (dx + k) + "," + dz + " strength " + strength + " " + t.geography().frozenSea().describe(dx + k, dz, strength));
                    }
                    return;
                }
                case "--ice-center" -> iceCenter = new double[] {Double.parseDouble(args[++i]), Double.parseDouble(args[++i])};
                case "--region-scale" -> settings = settings.withRegionScale(Double.parseDouble(args[++i]));
                case "--camera" -> camera = new double[] {
                        Double.parseDouble(args[++i]), Double.parseDouble(args[++i]), Double.parseDouble(args[++i]), Double.parseDouble(args[++i])};
                default -> throw new IllegalArgumentException("unknown argument " + args[i]);
            }
        }
        VastlandsTerrain terrain = new VastlandsTerrain(new GenerationSetup(seed, WorldHeight.MIN_Y, WorldHeight.HEIGHT, (int) SEA), settings);

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
        if (cover != null) {
            writeCover(terrain, cover, (int) Math.floor(centerX - size / 2.0), (int) Math.floor(centerZ - size / 2.0), size);
        }
        if (out == null) {
            return;
        }

        int n = (int) Math.round(size / scale);
        double x0 = centerX - size / 2.0;
        double z0 = centerZ - size / 2.0;
        double[][] h = new double[n][n];
        TerrainColumn[][] columns = new TerrainColumn[n][n];
        double finalScale = scale;
        long start = System.nanoTime();
        IntStream.range(0, n).parallel().forEach(j -> {
            for (int i = 0; i < n; i++) {
                TerrainColumn column = terrain.column((int) Math.floor(x0 + i * finalScale), (int) Math.floor(z0 + j * finalScale));
                columns[j][i] = column;
                h[j][i] = column.height();
            }
        });
        double seconds = (System.nanoTime() - start) / 1e9;
        int threads = Runtime.getRuntime().availableProcessors();
        System.out.printf(Locale.ROOT, "seed %d: %d columns in %.2f s on %d threads, %.1f us per column per thread%n",
                seed, n * n, seconds, threads, seconds * 1e6 * threads / ((double) n * n));
        printDistribution(h);
        printPeaks(h, x0, z0, scale);
        printGeography(columns);
        String[][] biomes = biomes(columns);
        printBiomes(biomes, scale);
        if (find != null) {
            printLocations(find, biomes, columns, x0, z0, scale);
        }

        File base = new File(out + ".png");
        base.getAbsoluteFile().getParentFile().mkdirs();
        double[][] shade = shade(h, scale);
        for (String map : maps) {
            switch (map) {
                case "relief" -> {
                    ImageIO.write(paint(n, (i, j) -> color(h[j][i]), shade), "png", base);
                    double[] cam = camera == null ? new double[] {n / 2.0, n - 1.0, 0.0, 140.0}
                            : new double[] {(camera[0] - x0) / scale, (camera[1] - z0) / scale, camera[2], camera[3]};
                    ImageIO.write(perspective(h, scale, cam), "png", new File(out + "-view.png"));
                }
                case "landform" -> ImageIO.write(paint(n, (i, j) -> LANDFORM_COLORS.get(columns[j][i].geography().landform()), shade), "png", file(out, map));
                case "biome" -> ImageIO.write(paint(n, (i, j) -> biomeColor(biomes[j][i]), shade), "png", file(out, map));
                case "temperature" -> ImageIO.write(paint(n, (i, j) -> band(columns[j][i].climateTemperature(), TEMPERATURE_BANDS, TEMPERATURE_COLORS), shade), "png", file(out, map));
                case "moisture" -> ImageIO.write(paint(n, (i, j) -> band(columns[j][i].climateHumidity(), HUMIDITY_BANDS, HUMIDITY_COLORS), shade), "png", file(out, map));
                case "ice" -> ImageIO.write(ice(terrain, x0, z0, n, scale, iceCenter), "png", file(out, map));
                default -> throw new IllegalArgumentException("unknown map " + map);
            }
            System.out.println("wrote " + (map.equals("relief") ? base + " and " + out + "-view.png" : file(out, map)));
        }
    }

    /**
     * Expected sea surface cover of the square, one byte per column (row-major, z then x): 0 land or
     * shallow ground, 1 open water, 2 ice, 3 thick ice, 4 pressure ridge, 5 ridge crest; for
     * {@code tools/worldcheck/oceancheck.py --cover}.
     */
    private static void writeCover(VastlandsTerrain terrain, String path, int x0, int z0, int n) throws IOException {
        byte[] values = new byte[n * n];
        FrozenSea sea = terrain.geography().frozenSea();
        IntStream.range(0, n).parallel().forEach(j -> {
            for (int i = 0; i < n; i++) {
                int x = x0 + i;
                int z = z0 + j;
                if (terrain.surfaceHeight(x, z) > SEA - 1.5) {
                    continue;
                }
                double strength = FrozenSea.strength(terrain.geography().seaLevelTemperature(x, z));
                values[j * n + i] = (byte) (sea.cover(x, z, strength).ordinal() + 1);
            }
        });
        File file = new File(path);
        file.getAbsoluteFile().getParentFile().mkdirs();
        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(values);
        }
        System.out.printf(Locale.ROOT, "wrote %dx%d sea cover classes from x %d z %d to %s%n", n, n, x0, z0, file);
    }

    private static File file(String out, String map) {
        return new File(out + "-" + map + ".png");
    }

    private static void printDistribution(double[][] h) {
        double[] all = Arrays.stream(h).flatMapToDouble(Arrays::stream).sorted().toArray();
        int[] limits = {-256, -100, -50, 0, 30, 50, 63, 100, 150, 200, 250, 300, 350, 400, 450, 512};
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

    /** The highest points of the square, at least 3 000 blocks apart: places worth a closer look. */
    private static void printPeaks(double[][] h, double x0, double z0, double scale) {
        int n = h.length;
        Integer[] order = IntStream.range(0, n * n).boxed().toArray(Integer[]::new);
        Arrays.sort(order, (a, b) -> Double.compare(h[b / n][b % n], h[a / n][a % n]));
        StringBuilder text = new StringBuilder("highest:");
        List<double[]> found = new java.util.ArrayList<>();
        for (int index : order) {
            double x = x0 + (index % n) * scale;
            double z = z0 + (index / n) * scale;
            if (found.stream().anyMatch(p -> Math.hypot(p[0] - x, p[1] - z) < 3000.0)) {
                continue;
            }
            found.add(new double[] {x, z});
            text.append(String.format(Locale.ROOT, " %.0f,%.0f (Y %.0f)", x, z, h[index / n][index % n]));
            if (found.size() == 6) {
                break;
            }
        }
        System.out.println(text);
    }

    private static void printGeography(TerrainColumn[][] columns) {
        Map<Landform, Integer> landforms = new EnumMap<>(Landform.class);
        int[] temperature = new int[5];
        int[] humidity = new int[5];
        int land = 0;
        int total = 0;
        for (TerrainColumn[] row : columns) {
            for (TerrainColumn column : row) {
                GeographySample g = column.geography();
                landforms.merge(g.landform(), 1, Integer::sum);
                total++;
                if (column.height() >= SEA) {
                    land++;
                    temperature[index(column.climateTemperature(), TEMPERATURE_BANDS)]++;
                    humidity[index(column.climateHumidity(), HUMIDITY_BANDS)]++;
                }
            }
        }
        StringBuilder text = new StringBuilder("landforms (share of all columns):");
        for (Map.Entry<Landform, Integer> entry : landforms.entrySet()) {
            text.append(String.format(Locale.ROOT, " %s %.1f%%", entry.getKey(), 100.0 * entry.getValue() / total));
        }
        int dry = land;
        text.append(String.format(Locale.ROOT, "%nland temperature bands T0..T4:"));
        for (int count : temperature) {
            text.append(String.format(Locale.ROOT, " %.1f%%", 100.0 * count / Math.max(1, dry)));
        }
        text.append(" | humidity bands H0..H4:");
        for (int count : humidity) {
            text.append(String.format(Locale.ROOT, " %.1f%%", 100.0 * count / Math.max(1, dry)));
        }
        System.out.println(text);
    }

    private static final Climate.ParameterList<ResourceKey<Biome>> BIOME_TABLE = overworldBiomeTable();

    /** Vanilla's overworld biome table keyed by biome; the generating method is package-private. */
    @SuppressWarnings("unchecked")
    private static Climate.ParameterList<ResourceKey<Biome>> overworldBiomeTable() {
        try {
            Method generate = MultiNoiseBiomeSourceParameterList.Preset.class.getDeclaredMethod("generateOverworldBiomes", Function.class);
            generate.setAccessible(true);
            return (Climate.ParameterList<ResourceKey<Biome>>) generate.invoke(null, Function.<ResourceKey<Biome>>identity());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot build vanilla's overworld biome table", e);
        }
    }

    /** The surface biome vanilla's overworld table picks from each column's climate parameters. */
    private static String[][] biomes(TerrainColumn[][] columns) {
        int n = columns.length;
        String[][] names = new String[n][n];
        IntStream.range(0, n).parallel().forEach(j -> {
            for (int i = 0; i < n; i++) {
                TerrainColumn c = columns[j][i];
                Climate.TargetPoint target = Climate.target((float) c.climateTemperature(), (float) c.climateHumidity(),
                        (float) c.climateContinentalness(), (float) c.climateErosion(), 0.0F, (float) c.climateWeirdness());
                names[j][i] = BIOME_TABLE.findValue(target).location().getPath();
            }
        });
        return names;
    }

    private static void printBiomes(String[][] biomes, double scale) {
        Map<String, Integer> counts = new HashMap<>();
        long runs = 0;
        long cells = 0;
        for (String[] row : biomes) {
            for (int i = 0; i < row.length; i++) {
                counts.merge(row[i], 1, Integer::sum);
                cells++;
                if (i == 0 || !row[i].equals(row[i - 1])) {
                    runs++;
                }
            }
        }
        long total = cells;
        StringBuilder text = new StringBuilder("biomes:");
        new TreeMap<>(counts).entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(e -> text.append(String.format(Locale.ROOT, " %s %.1f%%", e.getKey(), 100.0 * e.getValue() / total)));
        System.out.println(text);
        System.out.printf(Locale.ROOT, "mean biome run along rows: %.0f blocks (sampled every %.0f blocks)%n", scale * cells / runs, scale);
    }

    /** Up to eight places of a biome or landform (by name), at least 2 000 blocks apart. */
    private static void printLocations(String name, String[][] biomes, TerrainColumn[][] columns, double x0, double z0, double scale) {
        StringBuilder text = new StringBuilder(name + ":");
        List<double[]> found = new java.util.ArrayList<>();
        for (int j = 0; j < biomes.length && found.size() < 8; j++) {
            for (int i = 0; i < biomes.length && found.size() < 8; i++) {
                if (!biomes[j][i].equals(name) && !columns[j][i].geography().landform().name().equalsIgnoreCase(name)) {
                    continue;
                }
                double x = x0 + i * scale;
                double z = z0 + j * scale;
                if (found.stream().noneMatch(p -> Math.hypot(p[0] - x, p[1] - z) < 2000.0)) {
                    found.add(new double[] {x, z});
                    text.append(String.format(Locale.ROOT, " %.0f,%.0f (Y %.0f %s)", x, z, columns[j][i].height(), biomes[j][i]));
                }
            }
        }
        System.out.println(text);
    }

    /**
     * One block per pixel of polar ice cover around {@code center}, or around the coldest sea column
     * found in the square.
     */
    private static BufferedImage ice(VastlandsTerrain terrain, double x0, double z0, int n, double scale, double[] center) {
        FrozenSea sea = terrain.geography().frozenSea();
        // Around a given centre the map covers the square at its scale; otherwise 1 024 blocks at 1 block per pixel.
        int pixels = center == null ? 1024 : n;
        double step = center == null ? 1.0 : scale;
        double[] coldest = center == null ? new double[] {Double.POSITIVE_INFINITY, x0, z0}
                : new double[] {terrain.geography().seaLevelTemperature(center[0], center[1]), center[0], center[1]};
        for (int j = 0; center == null && j < n; j += 2) {
            for (int i = 0; i < n; i += 2) {
                double x = x0 + i * scale;
                double z = z0 + j * scale;
                double t = terrain.geography().seaLevelTemperature(x, z);
                if (t < coldest[0] && terrain.column((int) x, (int) z).height() < SEA - 6) {
                    coldest[0] = t;
                    coldest[1] = x;
                    coldest[2] = z;
                }
            }
        }
        int cx = (int) (coldest[1] - pixels * step / 2);
        int cz = (int) (coldest[2] - pixels * step / 2);
        System.out.printf(Locale.ROOT, "ice map: %.0f x %.0f blocks from x %d z %d (sea temperature %.2f at the centre)%n",
                pixels * step, pixels * step, cx, cz, coldest[0]);
        BufferedImage image = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_RGB);
        IntStream.range(0, pixels).parallel().forEach(j -> {
            for (int i = 0; i < pixels; i++) {
                int x = (int) (cx + i * step);
                int z = (int) (cz + j * step);
                int rgb;
                if (terrain.surfaceHeight(x, z) > SEA - 1.5) {
                    rgb = 0x8C8C78;
                } else {
                    double strength = FrozenSea.strength(terrain.geography().seaLevelTemperature(x, z));
                    rgb = switch (sea.cover(x, z, strength)) {
                        case OPEN_WATER -> 0x1E3250;
                        case ICE -> 0xB4D2F0;
                        case THICK_ICE -> 0x8CAADC;
                        case PRESSURE_RIDGE -> 0xE6F0FA;
                        case HIGH_RIDGE -> 0xFFFFFF;
                    };
                }
                image.setRGB(i, j, rgb);
            }
        });
        return image;
    }

    private interface Painter {
        int[] color(int i, int j);
    }

    private static BufferedImage paint(int n, Painter painter, double[][] shade) {
        BufferedImage image = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                image.setRGB(i, j, rgb(painter.color(i, j), shade[j][i]));
            }
        }
        return image;
    }

    private static final double[] TEMPERATURE_BANDS = {-0.45, -0.15, 0.2, 0.55};
    private static final int[][] TEMPERATURE_COLORS = {{210, 225, 250}, {120, 170, 220}, {120, 190, 110}, {225, 190, 90}, {225, 120, 70}};
    private static final double[] HUMIDITY_BANDS = {-0.35, -0.1, 0.1, 0.3};
    private static final int[][] HUMIDITY_COLORS = {{220, 200, 140}, {200, 205, 130}, {140, 190, 110}, {80, 160, 100}, {40, 120, 90}};

    private static int index(double v, double[] bands) {
        int i = 0;
        while (i < bands.length && v >= bands[i]) {
            i++;
        }
        return i;
    }

    private static int[] band(double v, double[] bands, int[][] colors) {
        return colors[index(v, bands)];
    }

    private static final Map<Landform, int[]> LANDFORM_COLORS = new EnumMap<>(Map.ofEntries(
            Map.entry(Landform.SHELF, new int[] {110, 170, 215}),
            Map.entry(Landform.CONTINENTAL_SLOPE, new int[] {60, 110, 180}),
            Map.entry(Landform.ABYSSAL_PLAIN, new int[] {25, 45, 110}),
            Map.entry(Landform.SUBMARINE_RIDGE, new int[] {40, 140, 150}),
            Map.entry(Landform.SEAMOUNT, new int[] {90, 200, 200}),
            Map.entry(Landform.TRENCH, new int[] {60, 10, 70}),
            Map.entry(Landform.COAST, new int[] {225, 210, 150}),
            Map.entry(Landform.PLAINS, new int[] {150, 200, 100}),
            Map.entry(Landform.HILLS, new int[] {90, 150, 70}),
            Map.entry(Landform.BASIN, new int[] {170, 170, 90}),
            Map.entry(Landform.PLATEAU, new int[] {200, 130, 70}),
            Map.entry(Landform.FOOTHILLS, new int[] {175, 155, 110}),
            Map.entry(Landform.MOUNTAINS, new int[] {130, 110, 100}),
            Map.entry(Landform.HIGH_MOUNTAINS, new int[] {235, 235, 240})));

    private static final Map<String, int[]> BIOME_COLORS = Map.ofEntries(
            Map.entry("ocean", new int[] {40, 70, 170}), Map.entry("deep_ocean", new int[] {25, 45, 120}),
            Map.entry("cold_ocean", new int[] {50, 70, 150}), Map.entry("deep_cold_ocean", new int[] {30, 45, 110}),
            Map.entry("lukewarm_ocean", new int[] {40, 100, 180}), Map.entry("deep_lukewarm_ocean", new int[] {30, 70, 140}),
            Map.entry("warm_ocean", new int[] {40, 140, 200}), Map.entry("frozen_ocean", new int[] {150, 170, 220}),
            Map.entry("deep_frozen_ocean", new int[] {90, 110, 170}), Map.entry("mushroom_fields", new int[] {200, 80, 200}),
            Map.entry("beach", new int[] {230, 215, 160}), Map.entry("snowy_beach", new int[] {235, 235, 220}),
            Map.entry("stony_shore", new int[] {140, 140, 140}), Map.entry("plains", new int[] {140, 190, 90}),
            Map.entry("sunflower_plains", new int[] {170, 200, 80}), Map.entry("snowy_plains", new int[] {240, 245, 250}),
            Map.entry("ice_spikes", new int[] {190, 220, 240}), Map.entry("desert", new int[] {230, 205, 130}),
            Map.entry("swamp", new int[] {70, 100, 70}), Map.entry("mangrove_swamp", new int[] {60, 110, 60}),
            Map.entry("forest", new int[] {50, 120, 50}), Map.entry("flower_forest", new int[] {110, 150, 60}),
            Map.entry("birch_forest", new int[] {90, 150, 80}), Map.entry("old_growth_birch_forest", new int[] {80, 140, 70}),
            Map.entry("dark_forest", new int[] {30, 70, 30}), Map.entry("pale_garden", new int[] {170, 175, 165}),
            Map.entry("taiga", new int[] {50, 100, 80}), Map.entry("snowy_taiga", new int[] {170, 200, 190}),
            Map.entry("old_growth_spruce_taiga", new int[] {60, 90, 60}), Map.entry("old_growth_pine_taiga", new int[] {70, 90, 60}),
            Map.entry("savanna", new int[] {190, 180, 90}), Map.entry("savanna_plateau", new int[] {170, 160, 90}),
            Map.entry("windswept_savanna", new int[] {160, 150, 100}), Map.entry("jungle", new int[] {40, 140, 30}),
            Map.entry("sparse_jungle", new int[] {80, 160, 50}), Map.entry("bamboo_jungle", new int[] {100, 170, 40}),
            Map.entry("badlands", new int[] {210, 110, 50}), Map.entry("eroded_badlands", new int[] {220, 90, 40}),
            Map.entry("wooded_badlands", new int[] {170, 110, 60}), Map.entry("meadow", new int[] {120, 200, 120}),
            Map.entry("cherry_grove", new int[] {240, 170, 200}), Map.entry("grove", new int[] {170, 210, 200}),
            Map.entry("snowy_slopes", new int[] {225, 235, 245}), Map.entry("frozen_peaks", new int[] {200, 220, 250}),
            Map.entry("jagged_peaks", new int[] {235, 240, 250}), Map.entry("stony_peaks", new int[] {150, 145, 140}),
            Map.entry("windswept_hills", new int[] {110, 130, 110}), Map.entry("windswept_gravelly_hills", new int[] {130, 130, 130}),
            Map.entry("windswept_forest", new int[] {70, 110, 80}), Map.entry("river", new int[] {60, 90, 220}),
            Map.entry("frozen_river", new int[] {160, 180, 240}));

    private static int[] biomeColor(String name) {
        return BIOME_COLORS.getOrDefault(name, new int[] {255, 0, 255});
    }

    static int[] color(double v) {
        if (v < SEA) {
            double d = Math.min(1, (SEA - v) / 100.0);
            return new int[] {(int) (70 - 60 * d), (int) (130 - 110 * d), (int) (190 - 120 * d)};
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

    /** Hillshading with light from the north-west; the sea floor is shaded more softly. */
    static double[][] shade(double[][] h, double scale) {
        int n = h.length;
        double[][] shade = new double[n][n];
        double lx = -0.5, ly = 0.6, lz = -0.62;
        double ll = Math.sqrt(lx * lx + ly * ly + lz * lz);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                double exaggerate = h[j][i] < SEA ? 0.3 : 1.0;
                double dx = exaggerate * (h[j][Math.min(n - 1, i + 1)] - h[j][Math.max(0, i - 1)]) / (2 * scale);
                double dz = exaggerate * (h[Math.min(n - 1, j + 1)][i] - h[Math.max(0, j - 1)][i]) / (2 * scale);
                double s = (-dx * lx + ly - dz * lz) / ll / Math.sqrt(dx * dx + 1 + dz * dz);
                shade[j][i] = 0.35 + 0.75 * Math.max(0, s);
            }
        }
        return shade;
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
