package com.sincerelyuq.vastlands.tools;

import com.sincerelyuq.vastlands.worldgen.WorldHeight;
import com.sincerelyuq.vastlands.worldgen.caves.CaveVolume;
import com.sincerelyuq.vastlands.worldgen.caves.VastlandsCaves;
import com.sincerelyuq.vastlands.worldgen.geography.GeographySample;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSetup;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSystems;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.stream.IntStream;

/**
 * Quantitative survey of the caves of a square, from the cave system's public queries alone (so the
 * same tool measures any version of the caves):
 * <ul>
 *     <li>cave share of the underground (rock below the ground) per height band and per depth below
 *     the ground;</li>
 *     <li>passage widths: the local thickness (diameter of the largest disc inside the cave that
 *     covers a block) on every horizontal slice, volume-weighted, in the size classes of the cave
 *     design (under 4, 4..10, 10..25, 25..60, 60..120, 120+ blocks); cave heights from the open runs
 *     of the columns;</li>
 *     <li>large spaces: 3D connected components of the blocks whose local width is at least 24, 60
 *     and 120 blocks (wide passages and halls, large chambers, megacaves), with their extents;</li>
 *     <li>connectivity: 3D connected components of all cave space (6-neighbours);</li>
 *     <li>water: connected bodies of cave water by size, and how much cave space is flooded;</li>
 *     <li>openings: columns where cave space reaches the ground (entrances);</li>
 *     <li>cave biomes: the biomes of random cave blocks (see {@link CaveSurveyBiomes}).</li>
 * </ul>
 *
 * <pre>
 * ./gradlew caveSurvey --args="--seed 20261018 --center 0 0 --size 768"
 * </pre>
 *
 * Blocks outside the square count as rock, so passages crossing its edge are cut there.
 */
public final class CaveSurvey {
    private static final int MIN_Y = WorldHeight.MIN_Y;
    private static final int WORDS = WorldHeight.HEIGHT / 64;
    private static final int[] WIDTH_BINS = {4, 10, 25, 60, 120};
    private static final String[] WIDTH_NAMES = {"<4", "4-10", "10-25", "25-60", "60-120", "120+"};
    private static final int[] CORE_WIDTHS = {24, 60, 120};

    private final int size;
    private final int x0;
    private final int z0;
    /** Per column (index z * size + x), bit y - MIN_Y: open cave space (air, water or lava). */
    private final long[] open;
    private final long[] water;
    private final long[][] cores = new long[CORE_WIDTHS.length][];
    private final short[] surface;

    private CaveSurvey(int x0, int z0, int size) {
        this.size = size;
        this.x0 = x0;
        this.z0 = z0;
        this.open = new long[size * size * WORDS];
        this.water = new long[size * size * WORDS];
        for (int k = 0; k < CORE_WIDTHS.length; k++) {
            this.cores[k] = new long[size * size * WORDS];
        }
        this.surface = new short[size * size];
    }

    public static void main(String[] args) {
        long seed = 20261018L;
        int cx = 0;
        int cz = 0;
        int size = 768;
        int biomeSamples = 40000;
        boolean locate = false;
        int list = 0;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--locate" -> locate = true;
                case "--megacaves" -> list = Integer.parseInt(args[++i]);
                case "--seed" -> seed = Long.parseLong(args[++i]);
                case "--center" -> {
                    cx = Integer.parseInt(args[++i]);
                    cz = Integer.parseInt(args[++i]);
                }
                case "--size" -> size = Integer.parseInt(args[++i]);
                case "--biomes" -> biomeSamples = Integer.parseInt(args[++i]);
                default -> throw new IllegalArgumentException("unknown argument " + args[i]);
            }
        }
        GenerationSystems systems = GenerationSystems.create(new GenerationSetup(seed, WorldHeight.MIN_Y, WorldHeight.HEIGHT, 63));
        VastlandsCaves caves = (VastlandsCaves) systems.caves();
        CaveVolume volume = new CaveVolume(systems, caves);
        if (locate) {
            locate(systems, cx, cz);
            return;
        }
        if (list > 0) {
            List<com.sincerelyuq.vastlands.worldgen.caves.MegacaveInfo> all = caves.megacaves(cx - list, cz - list, cx + list - 1, cz + list - 1);
            Map<String, Integer> kinds = new TreeMap<>();
            for (com.sincerelyuq.vastlands.worldgen.caves.MegacaveInfo m : all) {
                kinds.merge(m.zone() + " " + m.kind() + " " + m.fluid(), 1, Integer::sum);
                System.out.printf(Locale.ROOT, "  %s %s at %.0f %.0f: %.0f x %.0f, floor %.0f, vault %.0f, %s %s%n", m.zone(), m.kind(), m.x(), m.z(),
                        2 * m.lengthRadius(), 2 * m.widthRadius(), m.floorY(), m.ceilingY(), m.fluid(), m.fluidTop() == Integer.MIN_VALUE ? "" : m.fluidTop());
            }
            System.out.printf(Locale.ROOT, "%d megacaves in %d x %d blocks (%.2f per km2): %s%n", all.size(), 2 * list, 2 * list, all.size() / (4.0 * list * list / 1e6), kinds);
            return;
        }
        int x0 = cx - size / 2;
        int z0 = cz - size / 2;
        System.out.printf(Locale.ROOT, "cave survey: seed %d, %d x %d blocks around %d,%d%n", seed, size, size, cx, cz);
        CaveSurvey survey = new CaveSurvey(x0, z0, size);
        long t0 = System.nanoTime();
        survey.fill(systems, volume);
        long t1 = System.nanoTime();
        System.out.printf(Locale.ROOT, "  blocks built in %.1f s%n", (t1 - t0) / 1e9);
        survey.ground(systems);
        census(caves, x0, z0, size);
        survey.shares();
        long t2 = System.nanoTime();
        survey.widths();
        long t3 = System.nanoTime();
        survey.heights();
        survey.spaces();
        survey.connectivity();
        survey.waters();
        survey.openings();
        long t4 = System.nanoTime();
        survey.biomes(systems, biomeSamples, seed);
        System.out.printf(Locale.ROOT, "  survey done in %.1f s (widths %.1f s, components %.1f s, biomes %.1f s)%n", (System.nanoTime() - t0) / 1e9,
                (t3 - t2) / 1e9, (t4 - t3) / 1e9, (System.nanoTime() - t4) / 1e9);
    }

    /** Pieces of the caves over the square, and its megacaves, where the cave system says (newer versions). */
    private static void census(VastlandsCaves caves, int x0, int z0, int size) {
        try {
            Object census = VastlandsCaves.class.getMethod("census", int.class, int.class, int.class, int.class).invoke(caves, x0, z0, x0 + size - 1, z0 + size - 1);
            System.out.println("  pieces: " + census);
            List<?> megacaves = (List<?>) VastlandsCaves.class.getMethod("megacaves", int.class, int.class, int.class, int.class)
                    .invoke(caves, x0, z0, x0 + size - 1, z0 + size - 1);
            System.out.println("  megacaves reaching the square: " + megacaves.size());
            for (Object m : megacaves) {
                System.out.println("    " + m);
            }
        } catch (ReflectiveOperationException e) {
            System.out.println("  pieces: (not reported by this version of the caves)");
        }
    }

    /** Nearest places of each landform on a 768-block grid around a point, with their ground height. */
    private static void locate(GenerationSystems systems, int cx, int cz) {
        Map<String, List<String>> places = new TreeMap<>();
        for (int r = 0; r <= 24; r++) {
            for (int j = -r; j <= r; j++) {
                for (int i = -r; i <= r; i++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != r) {
                        continue;
                    }
                    int x = cx + i * 768;
                    int z = cz + j * 768;
                    String landform = systems.terrain().column(x, z).geography().landform().name().toLowerCase(Locale.ROOT);
                    List<String> list = places.computeIfAbsent(landform, k -> new ArrayList<>());
                    if (list.size() < 4) {
                        list.add(String.format(Locale.ROOT, "%d,%d (Y %.0f)", x, z, systems.surfaceHeight(x, z)));
                    }
                }
            }
        }
        places.forEach((k, v) -> System.out.println("  " + k + ": " + v));
    }

    private int column(int i, int j) {
        return j * this.size + i;
    }

    private static boolean get(long[] bits, int column, int y) {
        int b = y - MIN_Y;
        return (bits[column * WORDS + (b >> 6)] >>> (b & 63) & 1L) != 0;
    }

    private static void set(long[] bits, int column, int y) {
        int b = y - MIN_Y;
        bits[column * WORDS + (b >> 6)] |= 1L << (b & 63);
    }

    private void fill(GenerationSystems systems, CaveVolume volume) {
        IntStream.range(0, this.size).parallel().forEach(j -> {
            for (int i = 0; i < this.size; i++) {
                int x = this.x0 + i;
                int z = this.z0 + j;
                int c = this.column(i, j);
                double ground = volume.surface(x, z);
                this.surface[c] = (short) Math.ceil(ground);
                int river = volume.riverTop(x, z);
                int top = Math.min(WorldHeight.MAX_Y, (int) Math.ceil(ground) + 2);
                for (int y = MIN_Y + 5; y <= top; y++) {
                    CaveVolume.Block b = volume.block(x, y, z, river);
                    if (b == CaveVolume.Block.CAVE_AIR || b == CaveVolume.Block.CAVE_WATER || b == CaveVolume.Block.CAVE_LAVA) {
                        set(this.open, c, y);
                        if (b == CaveVolume.Block.CAVE_WATER) {
                            set(this.water, c, y);
                        }
                    }
                }
            }
        });
    }

    private void ground(GenerationSystems systems) {
        double sum = 0;
        int sea = 0;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (short s : this.surface) {
            sum += s;
            sea += s < 63 ? 1 : 0;
            min = Math.min(min, s);
            max = Math.max(max, s);
        }
        Map<String, Integer> landforms = new TreeMap<>();
        for (int j = 0; j < this.size; j += 32) {
            for (int i = 0; i < this.size; i += 32) {
                GeographySample g = systems.terrain().column(this.x0 + i, this.z0 + j).geography();
                landforms.merge(g.landform().name().toLowerCase(Locale.ROOT), 1, Integer::sum);
            }
        }
        System.out.printf(Locale.ROOT, "  ground: mean Y %.0f, %d..%d, %.0f%% under sea level; landforms %s%n",
                sum / this.surface.length, min, max, 100.0 * sea / this.surface.length, landforms);
    }

    /** Cave share of the rock below the ground, per 64-block height band and per depth below the ground. */
    private void shares() {
        int bands = WorldHeight.HEIGHT / 64;
        long[] rock = new long[bands];
        long[] cave = new long[bands];
        long[] wet = new long[bands];
        int[] depthEdges = {0, 32, 64, 128, 256, 1000};
        long[] dRock = new long[depthEdges.length - 1];
        long[] dCave = new long[depthEdges.length - 1];
        long cavernousColumns = 0;
        for (int c = 0; c < this.surface.length; c++) {
            int top = this.surface[c];
            boolean any = false;
            for (int y = MIN_Y + 8; y < top; y++) {
                int band = (y - MIN_Y) / 64;
                rock[band]++;
                int depth = top - y;
                int d = 0;
                while (d < dRock.length - 1 && depth >= depthEdges[d + 1]) {
                    d++;
                }
                dRock[d]++;
                if (get(this.open, c, y)) {
                    cave[band]++;
                    dCave[d]++;
                    any = true;
                    if (get(this.water, c, y)) {
                        wet[band]++;
                    }
                }
            }
            cavernousColumns += any ? 1 : 0;
        }
        long allRock = Arrays.stream(rock).sum();
        long allCave = Arrays.stream(cave).sum();
        long allWet = Arrays.stream(wet).sum();
        System.out.printf(Locale.ROOT, "  cave share of the underground: %.2f%% (%d blocks; flooded %.1f%% of it); columns with caves %.1f%%%n",
                100.0 * allCave / Math.max(1, allRock), allCave, 100.0 * allWet / Math.max(1, allCave), 100.0 * cavernousColumns / this.surface.length);
        StringBuilder line = new StringBuilder("  by height:");
        for (int b = bands - 1; b >= 0; b--) {
            if (rock[b] > 0) {
                line.append(String.format(Locale.ROOT, " Y%d..%d %.1f%%", MIN_Y + 64 * b, MIN_Y + 64 * b + 63, 100.0 * cave[b] / rock[b]));
            }
        }
        System.out.println(line);
        line = new StringBuilder("  by depth below the ground:");
        for (int d = 0; d < dRock.length; d++) {
            if (dRock[d] > 0) {
                line.append(String.format(Locale.ROOT, " %d..%d %.1f%%", depthEdges[d], depthEdges[d + 1], 100.0 * dCave[d] / dRock[d]));
            }
        }
        System.out.println(line);
    }

    /**
     * Local thickness on every horizontal slice: the 2D Euclidean distance transform of the cave
     * space, then each distance ridge paints its disc with its diameter. Volume-weighted classes.
     */
    private void widths() {
        int lo = MIN_Y + 5;
        int hi = 0;
        for (short s : this.surface) {
            hi = Math.max(hi, s);
        }
        int height = hi - lo + 1;
        AtomicLongArray bins = new AtomicLongArray(WIDTH_NAMES.length);
        AtomicLongArray upperBins = new AtomicLongArray(WIDTH_NAMES.length);
        AtomicLong total = new AtomicLong();
        int[] histogram = new int[1024];
        int top = hi;
        IntStream.range(0, height).parallel().forEach(k -> {
            int y = lo + k;
            int n = this.size;
            boolean[] cave = new boolean[n * n];
            boolean any = false;
            for (int c = 0; c < n * n; c++) {
                cave[c] = get(this.open, c, y);
                any |= cave[c];
            }
            if (!any) {
                return;
            }
            float[] thickness = localThickness(cave, n);
            int[] local = new int[1024];
            long[] counts = new long[WIDTH_NAMES.length];
            for (int c = 0; c < n * n; c++) {
                if (!cave[c]) {
                    continue;
                }
                double w = thickness[c];
                int bin = 0;
                while (bin < WIDTH_BINS.length && w >= WIDTH_BINS[bin]) {
                    bin++;
                }
                counts[bin]++;
                local[Math.min(1023, (int) w)]++;
                for (int q = 0; q < CORE_WIDTHS.length; q++) {
                    if (w >= CORE_WIDTHS[q]) {
                        set(this.cores[q], c, y);
                    }
                }
            }
            long sum = 0;
            for (int b = 0; b < counts.length; b++) {
                bins.addAndGet(b, counts[b]);
                if (y >= -60) {
                    upperBins.addAndGet(b, counts[b]);
                }
                sum += counts[b];
            }
            total.addAndGet(sum);
            synchronized (histogram) {
                for (int i = 0; i < 1024; i++) {
                    histogram[i] += local[i];
                }
            }
        });
        StringBuilder line = new StringBuilder("  passage widths (local thickness, share of cave volume):");
        for (int b = 0; b < WIDTH_NAMES.length; b++) {
            line.append(String.format(Locale.ROOT, " %s %.1f%%", WIDTH_NAMES[b], 100.0 * bins.get(b) / Math.max(1, total.get())));
        }
        System.out.println(line);
        long upper = 0;
        for (int b = 0; b < WIDTH_NAMES.length; b++) {
            upper += upperBins.get(b);
        }
        line = new StringBuilder("    above Y -60:");
        for (int b = 0; b < WIDTH_NAMES.length; b++) {
            line.append(String.format(Locale.ROOT, " %s %.1f%%", WIDTH_NAMES[b], 100.0 * upperBins.get(b) / Math.max(1, upper)));
        }
        System.out.println(line);
        System.out.printf(Locale.ROOT, "    volume-weighted width: median %d, p25 %d, p75 %d, p90 %d, p99 %d blocks (slices Y %d..%d)%n",
                quantile(histogram, 0.5), quantile(histogram, 0.25), quantile(histogram, 0.75), quantile(histogram, 0.9), quantile(histogram, 0.99), lo, top);
    }

    private static int quantile(int[] histogram, double q) {
        long total = 0;
        for (int h : histogram) {
            total += h;
        }
        long target = (long) Math.ceil(q * total);
        long run = 0;
        for (int i = 0; i < histogram.length; i++) {
            run += histogram[i];
            if (run >= target && total > 0) {
                return i;
            }
        }
        return histogram.length - 1;
    }

    /** Local thickness (diameter, blocks) of every cave pixel of an n × n slice; outside the slice is rock. */
    static float[] localThickness(boolean[] cave, int n) {
        float[] d2 = edt(cave, n);
        float[] out = new float[n * n];
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                int c = j * n + i;
                float v = d2[c];
                if (v <= 0.0F) {
                    continue;
                }
                // A ridge of the distance map: no neighbour farther from the rock.
                boolean ridge = true;
                for (int dj = -1; dj <= 1 && ridge; dj++) {
                    for (int di = -1; di <= 1; di++) {
                        int a = i + di;
                        int b = j + dj;
                        if ((di != 0 || dj != 0) && a >= 0 && a < n && b >= 0 && b < n && d2[b * n + a] > v) {
                            ridge = false;
                            break;
                        }
                    }
                }
                if (!ridge) {
                    continue;
                }
                float r = (float) Math.sqrt(v);
                float diameter = 2.0F * r - 1.0F;
                int ri = (int) Math.ceil(r);
                float r2 = v;
                for (int b = Math.max(0, j - ri); b <= Math.min(n - 1, j + ri); b++) {
                    int dz = b - j;
                    for (int a = Math.max(0, i - ri); a <= Math.min(n - 1, i + ri); a++) {
                        int dx = a - i;
                        if (dx * dx + dz * dz < r2) {
                            int q = b * n + a;
                            if (out[q] < diameter) {
                                out[q] = diameter;
                            }
                        }
                    }
                }
            }
        }
        for (int c = 0; c < n * n; c++) {
            if (cave[c] && out[c] < 1.0F) {
                out[c] = 1.0F;
            }
        }
        return out;
    }

    /** Squared Euclidean distance from each cave pixel to the nearest rock (Felzenszwalb and Huttenlocher). */
    private static float[] edt(boolean[] cave, int n) {
        float inf = 1e12F;
        float[] g = new float[n * n];
        float[] f = new float[n + 2];
        float[] d = new float[n + 2];
        int[] v = new int[n + 2];
        float[] zz = new float[n + 3];
        // Pass along x, with rock beyond both ends.
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n + 2; i++) {
                f[i] = i == 0 || i == n + 1 || !cave[j * n + i - 1] ? 0.0F : inf;
            }
            transform(f, n + 2, d, v, zz);
            for (int i = 0; i < n; i++) {
                g[j * n + i] = d[i + 1];
            }
        }
        float[] out = new float[n * n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n + 2; j++) {
                f[j] = j == 0 || j == n + 1 ? 0.0F : g[(j - 1) * n + i];
            }
            transform(f, n + 2, d, v, zz);
            for (int j = 0; j < n; j++) {
                out[j * n + i] = d[j + 1];
            }
        }
        return out;
    }

    private static void transform(float[] f, int n, float[] d, int[] v, float[] z) {
        int k = 0;
        v[0] = 0;
        z[0] = Float.NEGATIVE_INFINITY;
        z[1] = Float.POSITIVE_INFINITY;
        for (int q = 1; q < n; q++) {
            float s = ((f[q] + q * q) - (f[v[k]] + v[k] * v[k])) / (2.0F * q - 2.0F * v[k]);
            while (s <= z[k]) {
                k--;
                s = ((f[q] + q * q) - (f[v[k]] + v[k] * v[k])) / (2.0F * q - 2.0F * v[k]);
            }
            k++;
            v[k] = q;
            z[k] = s;
            z[k + 1] = Float.POSITIVE_INFINITY;
        }
        k = 0;
        for (int q = 0; q < n; q++) {
            while (z[k + 1] < q) {
                k++;
            }
            float dq = q - v[k];
            d[q] = dq * dq + f[v[k]];
        }
    }

    /** Heights of the open runs of the columns (how tall the caves are), weighted by run length. */
    private void heights() {
        long[] bins = new long[WIDTH_NAMES.length];
        long total = 0;
        int[] histogram = new int[1024];
        for (int c = 0; c < this.surface.length; c++) {
            int run = 0;
            for (int y = MIN_Y + 5; y <= this.surface[c] + 1; y++) {
                if (get(this.open, c, y)) {
                    run++;
                } else if (run > 0) {
                    int bin = 0;
                    while (bin < WIDTH_BINS.length && run >= WIDTH_BINS[bin]) {
                        bin++;
                    }
                    bins[bin] += run;
                    total += run;
                    histogram[Math.min(1023, run)] += run;
                    run = 0;
                }
            }
        }
        StringBuilder line = new StringBuilder("  cave heights (open runs of the columns, share of cave volume):");
        for (int b = 0; b < WIDTH_NAMES.length; b++) {
            line.append(String.format(Locale.ROOT, " %s %.1f%%", WIDTH_NAMES[b], 100.0 * bins[b] / Math.max(1, total)));
        }
        System.out.println(line);
        System.out.printf(Locale.ROOT, "    volume-weighted height: median %d, p75 %d, p90 %d, p99 %d blocks%n",
                quantile(histogram, 0.5), quantile(histogram, 0.75), quantile(histogram, 0.9), quantile(histogram, 0.99));
    }

    /** Wide spaces: connected components of blocks at least 24, 60 and 120 blocks wide. */
    private void spaces() {
        double km2 = this.size * (double) this.size / 1e6;
        int[] minVolume = {3000, 30000, 150000};
        for (int q = 0; q < CORE_WIDTHS.length; q++) {
            List<Component> components = this.components(this.cores[q]);
            int least = minVolume[q];
            components.removeIf(c -> c.volume < least);
            components.sort((a, b) -> Long.compare(b.volume, a.volume));
            long volume = components.stream().mapToLong(c -> c.volume).sum();
            System.out.printf(Locale.ROOT, "  spaces %d+ blocks wide (over %d blocks): %d (%.1f per km2), %d blocks%n",
                    CORE_WIDTHS[q], minVolume[q], components.size(), components.size() / km2, volume);
            for (Component c : components.subList(0, Math.min(components.size(), 6))) {
                System.out.printf(Locale.ROOT, "    %s%n", c);
            }
        }
    }

    /** All cave space: connected components (6-neighbours). */
    private void connectivity() {
        List<Component> components = this.components(this.open);
        long total = components.stream().mapToLong(c -> c.volume).sum();
        components.sort((a, b) -> Long.compare(b.volume, a.volume));
        long big = components.stream().filter(c -> c.volume >= 10000).mapToLong(c -> c.volume).sum();
        long tiny = components.stream().filter(c -> c.volume < 200).count();
        double km2 = this.size * (double) this.size / 1e6;
        System.out.printf(Locale.ROOT, "  connectivity: %d cave systems (%d under 200 blocks); %d of 10 000+ blocks (%.1f per km2) hold %.1f%% of the cave volume; largest %.1f%%%n",
                components.size(), tiny, components.stream().filter(c -> c.volume >= 10000).count(), components.stream().filter(c -> c.volume >= 10000).count() / km2,
                100.0 * big / Math.max(1, total), components.isEmpty() ? 0.0 : 100.0 * components.get(0).volume / Math.max(1, total));
        for (Component c : components.subList(0, Math.min(components.size(), 3))) {
            System.out.printf(Locale.ROOT, "    %s%n", c);
        }
    }

    /** Cave water: connected bodies by size; air above water in the same system (partly flooded). */
    private void waters() {
        List<Component> bodies = this.components(this.water);
        long[] classes = new long[4];
        long[] volumes = new long[4];
        long[] limits = {1000, 30000, 1000000};
        for (Component c : bodies) {
            int k = 0;
            while (k < limits.length && c.volume >= limits[k]) {
                k++;
            }
            classes[k]++;
            volumes[k] += c.volume;
        }
        bodies.sort((a, b) -> Long.compare(b.volume, a.volume));
        // Surface area of the water bodies: water blocks with cave air right above them.
        long surfaceArea = 0;
        for (int c = 0; c < this.surface.length; c++) {
            for (int w = 0; w < WORDS; w++) {
                long wet = this.water[c * WORDS + w];
                if (wet == 0) {
                    continue;
                }
                long above = (this.open[c * WORDS + w] & ~this.water[c * WORDS + w]) >>> 1;
                if (w + 1 < WORDS) {
                    long next = this.open[c * WORDS + w + 1] & ~this.water[c * WORDS + w + 1];
                    above |= (next & 1L) << 63;
                }
                surfaceArea += Long.bitCount(wet & above);
            }
        }
        System.out.printf(Locale.ROOT, "  cave water bodies: %d ponds (<1k blocks), %d lakes (1k..30k), %d large lakes (30k..1M), %d seas (1M+); open water surface %d blocks (%.2f%% of the area)%n",
                classes[0], classes[1], classes[2], classes[3], surfaceArea, 100.0 * surfaceArea / this.surface.length);
        for (Component c : bodies.subList(0, Math.min(bodies.size(), 3))) {
            System.out.printf(Locale.ROOT, "    %s%n", c);
        }
    }

    /** Entrances: columns where cave space comes within two blocks of the ground. */
    private void openings() {
        long mouths = 0;
        for (int c = 0; c < this.surface.length; c++) {
            int top = this.surface[c];
            for (int y = top - 2; y <= top + 1; y++) {
                if (y > MIN_Y && y < WorldHeight.MAX_Y && get(this.open, c, y)) {
                    mouths++;
                    break;
                }
            }
        }
        System.out.printf(Locale.ROOT, "  entrances: %d columns where caves reach the ground (%.0f per km2)%n", mouths, mouths / (this.size * (double) this.size / 1e6));
    }

    /** A connected component and its bounds. */
    private record Component(long volume, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%d blocks, %d x %d x %d (x %d..%d, y %d..%d, z %d..%d)", this.volume,
                    this.maxX - this.minX + 1, this.maxZ - this.minZ + 1, this.maxY - this.minY + 1,
                    this.minX, this.maxX, this.minY, this.maxY, this.minZ, this.maxZ);
        }
    }

    /** Connected components of a bit volume: open runs per column joined with overlapping runs of the -x and -z neighbours. */
    private List<Component> components(long[] bits) {
        int n = this.size;
        int[] runStart = new int[n * n + 1];
        List<int[]> runs = new ArrayList<>();
        for (int c = 0; c < n * n; c++) {
            runStart[c] = runs.size();
            int start = Integer.MIN_VALUE;
            for (int w = 0; w < WORDS; w++) {
                long word = bits[c * WORDS + w];
                if (word == 0 && start == Integer.MIN_VALUE) {
                    continue;
                }
                for (int b = 0; b < 64; b++) {
                    boolean on = (word >>> b & 1L) != 0;
                    int y = MIN_Y + w * 64 + b;
                    if (on && start == Integer.MIN_VALUE) {
                        start = y;
                    } else if (!on && start != Integer.MIN_VALUE) {
                        runs.add(new int[] {start, y - 1});
                        start = Integer.MIN_VALUE;
                    }
                }
            }
            if (start != Integer.MIN_VALUE) {
                runs.add(new int[] {start, MIN_Y + WORDS * 64 - 1});
            }
        }
        runStart[n * n] = runs.size();
        int[] parent = new int[runs.size()];
        for (int r = 0; r < parent.length; r++) {
            parent[r] = r;
        }
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                int c = j * n + i;
                if (i > 0) {
                    this.join(runs, parent, runStart, c, c - 1);
                }
                if (j > 0) {
                    this.join(runs, parent, runStart, c, c - n);
                }
            }
        }
        Map<Integer, long[]> stats = new HashMap<>();
        for (int c = 0; c < n * n; c++) {
            int i = c % n;
            int j = c / n;
            for (int r = runStart[c]; r < runStart[c + 1]; r++) {
                int root = find(parent, r);
                int[] run = runs.get(r);
                long[] s = stats.computeIfAbsent(root, k -> new long[] {0, Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE});
                s[0] += run[1] - run[0] + 1;
                s[1] = Math.min(s[1], i);
                s[2] = Math.max(s[2], i);
                s[3] = Math.min(s[3], run[0]);
                s[4] = Math.max(s[4], run[1]);
                s[5] = Math.min(s[5], j);
                s[6] = Math.max(s[6], j);
            }
        }
        List<Component> out = new ArrayList<>();
        for (long[] s : stats.values()) {
            out.add(new Component(s[0], this.x0 + (int) s[1], this.x0 + (int) s[2], (int) s[3], (int) s[4], this.z0 + (int) s[5], this.z0 + (int) s[6]));
        }
        return out;
    }

    private void join(List<int[]> runs, int[] parent, int[] runStart, int c, int other) {
        int a = runStart[c];
        int b = runStart[other];
        int aEnd = runStart[c + 1];
        int bEnd = runStart[other + 1];
        while (a < aEnd && b < bEnd) {
            int[] ra = runs.get(a);
            int[] rb = runs.get(b);
            if (ra[1] >= rb[0] && rb[1] >= ra[0]) {
                int pa = find(parent, a);
                int pb = find(parent, b);
                if (pa != pb) {
                    parent[pa] = pb;
                }
            }
            if (ra[1] < rb[1]) {
                a++;
            } else {
                b++;
            }
        }
    }

    private static int find(int[] parent, int r) {
        while (parent[r] != r) {
            parent[r] = parent[parent[r]];
            r = parent[r];
        }
        return r;
    }

    /** Biomes of random cave blocks of the square (drawn from the surveyed cave space). */
    private void biomes(GenerationSystems systems, int samples, long seed) {
        if (samples <= 0) {
            return;
        }
        Random random = new Random(seed ^ 0xB10E5L);
        Map<String, Integer> counts = new TreeMap<>();
        Map<String, Integer> deep = new TreeMap<>();
        int found = 0;
        int deepFound = 0;
        for (int n = 0; n < samples * 200 && found < samples; n++) {
            int i = random.nextInt(this.size);
            int j = random.nextInt(this.size);
            int c = this.column(i, j);
            int top = this.surface[c] - 4;
            if (top <= MIN_Y + 10) {
                continue;
            }
            int y = MIN_Y + 8 + random.nextInt(top - MIN_Y - 8);
            if (!get(this.open, c, y)) {
                continue;
            }
            String biome = CaveSurveyBiomes.biome(systems, this.x0 + i, y, this.z0 + j);
            counts.merge(biome, 1, Integer::sum);
            found++;
            if (y < -66) {
                deep.merge(biome, 1, Integer::sum);
                deepFound++;
            }
        }
        System.out.printf(Locale.ROOT, "  biomes of %d random cave blocks: %s%n", found, shares(counts, found));
        System.out.printf(Locale.ROOT, "    below Y -66 (%d): %s%n", deepFound, shares(deep, deepFound));
    }

    private static String shares(Map<String, Integer> counts, int total) {
        StringBuilder text = new StringBuilder();
        counts.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(16)
                .forEach(e -> text.append(String.format(Locale.ROOT, "%s %.1f%%, ", e.getKey(), 100.0 * e.getValue() / Math.max(1, total))));
        return text.toString();
    }
}
