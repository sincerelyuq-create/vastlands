package com.sincerelyuq.vastlands.tools;

import com.sincerelyuq.vastlands.worldgen.WorldHeight;
import com.sincerelyuq.vastlands.worldgen.forest.ForestModel;
import com.sincerelyuq.vastlands.worldgen.forest.ForestSample;
import com.sincerelyuq.vastlands.worldgen.forest.ForestType;
import com.sincerelyuq.vastlands.worldgen.forest.TreeBlocks;
import com.sincerelyuq.vastlands.worldgen.forest.TreeForm;
import com.sincerelyuq.vastlands.worldgen.forest.TreeShape;
import com.sincerelyuq.vastlands.worldgen.forest.TreeSpec;
import com.sincerelyuq.vastlands.worldgen.forest.VastlandsForests;
import com.sincerelyuq.vastlands.worldgen.forest.Wood;
import com.sincerelyuq.vastlands.worldgen.hydrology.WaterColumn;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSetup;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSystems;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.imageio.ImageIO;

/**
 * Forest previews without the game ({@code ./gradlew forestPreview --args="..."}):
 * <ul>
 *     <li>{@code --gallery FILE}: every tree form at three sizes, isometric;</li>
 *     <li>{@code --view X Z SIZE FILE}: an isometric view of a square of terrain, water and trees;</li>
 *     <li>{@code --map X Z SIZE SCALE FILE}: an aerial map (crowns by wood over hillshaded ground);</li>
 *     <li>{@code --stats X Z SIZE}: tree counts by community, form and wood, heights, spacing,
 *     canopy cover, clearings and the checks of every trunk against water, caves and slopes;</li>
 *     <li>{@code --find TYPE N}: places of a community (dense forest) within N blocks of 0,0;</li>
 *     <li>{@code --find-river N}, {@code --find-treeline N}: riverbanks in forest, treelines;</li>
 *     <li>{@code --bench X Z CHUNKS}: time of the model and the shapes per chunk;</li>
 *     <li>{@code --trees X Z R}: every tree within R blocks (form, wood, root, size, bounds);</li>
 *     <li>{@code --reach X Z SIZE}: the largest radius of each form in a square, against its reach;</li>
 *     <li>{@code --order X Z CHUNKS}: the trees of each chunk queried in two orders on two threads,
 *     chunks that differ;</li>
 *     <li>{@code --explain LAYER I J X Z N}: why a lattice cell has or lacks a tree (site checks,
 *     yield to neighbours), for chunks found by {@code --order}.</li>
 * </ul>
 * Options: {@code --seed S}, {@code --gallery-seed S}, {@code --gallery-kind trees|mushrooms|deadwood}. Biomes come from vanilla's table on the
 * router's climate parameters ({@link PreviewForestBiomes}).
 */
public final class ForestPreview {
    private final GenerationSystems systems;
    private final VastlandsForests forests;
    private final ForestModel model;
    private final PreviewForestBiomes biomes;
    long gallerySeed;
    /** {@code trees} (every form), {@code mushrooms} (every cap style) or {@code deadwood}. */
    String galleryKind = "trees";

    ForestPreview(long seed) {
        GenerationSetup setup = new GenerationSetup(seed, WorldHeight.MIN_Y, WorldHeight.HEIGHT, 63);
        this.systems = GenerationSystems.create(setup);
        this.forests = (VastlandsForests) this.systems.forests();
        this.biomes = new PreviewForestBiomes(this.systems);
        this.forests.bind(this, this.biomes);
        this.model = this.forests.model();
    }

    public static void main(String[] args) throws IOException {
        long seed = 20261018L;
        long gallery = 0L;
        String galleryKind = "trees";
        List<String[]> jobs = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--seed" -> seed = Long.parseLong(args[++i]);
                case "--gallery-seed" -> gallery = Long.parseLong(args[++i]);
                case "--gallery-kind" -> galleryKind = args[++i];
                case "--gallery" -> jobs.add(new String[] {"gallery", args[++i]});
                case "--view" -> jobs.add(new String[] {"view", args[++i], args[++i], args[++i], args[++i]});
                case "--map" -> jobs.add(new String[] {"map", args[++i], args[++i], args[++i], args[++i], args[++i]});
                case "--stats" -> jobs.add(new String[] {"stats", args[++i], args[++i], args[++i]});
                case "--find" -> jobs.add(new String[] {"find", args[++i], args[++i]});
                case "--find-river" -> jobs.add(new String[] {"findRiver", args[++i]});
                case "--find-treeline" -> jobs.add(new String[] {"findTreeline", args[++i]});
                case "--bench" -> jobs.add(new String[] {"bench", args[++i], args[++i], args[++i]});
                case "--trees" -> jobs.add(new String[] {"trees", args[++i], args[++i], args[++i]});
                case "--reach" -> jobs.add(new String[] {"reach", args[++i], args[++i], args[++i]});
                case "--order" -> jobs.add(new String[] {"order", args[++i], args[++i], args[++i]});
                case "--explain" -> jobs.add(new String[] {"explain", args[++i], args[++i], args[++i], args[++i], args[++i], args[++i]});
                default -> throw new IllegalArgumentException("unknown argument " + args[i]);
            }
        }
        ForestPreview preview = new ForestPreview(seed);
        preview.gallerySeed = gallery;
        preview.galleryKind = galleryKind;
        for (String[] job : jobs) {
            long t0 = System.nanoTime();
            switch (job[0]) {
                case "gallery" -> preview.gallery(new File(job[1]));
                case "view" -> preview.view(Integer.parseInt(job[1]), Integer.parseInt(job[2]), Integer.parseInt(job[3]), new File(job[4]));
                case "map" -> preview.map(Integer.parseInt(job[1]), Integer.parseInt(job[2]), Integer.parseInt(job[3]), Integer.parseInt(job[4]), new File(job[5]));
                case "stats" -> preview.stats(Integer.parseInt(job[1]), Integer.parseInt(job[2]), Integer.parseInt(job[3]));
                case "find" -> preview.find(ForestType.valueOf(job[1].toUpperCase(Locale.ROOT)), Integer.parseInt(job[2]));
                case "findRiver" -> preview.findRiver(Integer.parseInt(job[1]));
                case "findTreeline" -> preview.findTreeline(Integer.parseInt(job[1]));
                case "bench" -> preview.bench(Integer.parseInt(job[1]), Integer.parseInt(job[2]), Integer.parseInt(job[3]));
                case "trees" -> preview.list(Integer.parseInt(job[1]), Integer.parseInt(job[2]), Integer.parseInt(job[3]));
                case "reach" -> preview.reach(Integer.parseInt(job[1]), Integer.parseInt(job[2]), Integer.parseInt(job[3]));
                case "order" -> orderCheck(seed, Integer.parseInt(job[1]), Integer.parseInt(job[2]), Integer.parseInt(job[3]));
                case "explain" -> {
                    // A cell on a fresh model, then on another one after a sweep over a square of chunks in rows.
                    int layer = Integer.parseInt(job[1]);
                    long ci = Long.parseLong(job[2]);
                    long cj = Long.parseLong(job[3]);
                    System.out.println("fresh: " + new ForestPreview(seed).model.explain(layer, ci, cj));
                    ForestPreview swept = new ForestPreview(seed);
                    int sx = Integer.parseInt(job[4]) >> 4;
                    int sz = Integer.parseInt(job[5]) >> 4;
                    int n = Integer.parseInt(job[6]);
                    for (int a = sx - n / 2; a < sx + n / 2; a++) {
                        for (int b = sz - n / 2; b < sz + n / 2; b++) {
                            swept.signature(a, b);
                        }
                    }
                    System.out.println("swept: " + swept.model.explain(layer, ci, cj));
                }
                default -> throw new IllegalStateException();
            }
            System.out.printf(Locale.ROOT, "%s: %.1f s%n", job[0], (System.nanoTime() - t0) / 1e9);
        }
    }

    // ------------------------------------------------------------------ colours

    static int logColor(Wood wood) {
        return switch (wood) {
            case OAK -> 0x6B5434;
            case BIRCH -> 0xD5D3C8;
            case SPRUCE -> 0x3D2B17;
            case DARK_OAK -> 0x3A2915;
            case JUNGLE -> 0x5A4A1E;
            case ACACIA -> 0x6A645A;
            case MUSHROOM -> 0xCFC9B9;
            case DUSK -> 0x3B3047;
            case CHARRED -> 0x262222;
        };
    }

    static int leafColor(Wood wood) {
        return switch (wood) {
            case OAK -> 0x3F7D24;
            case BIRCH -> 0x6A9447;
            case SPRUCE -> 0x355738;
            case DARK_OAK -> 0x2D5C1A;
            case JUNGLE -> 0x3B8A1E;
            case ACACIA -> 0x5B7E22;
            case MUSHROOM -> 0x92684A;
            case DUSK -> 0x6E4C9E;
            case CHARRED -> 0x3A3434;
        };
    }

    static int color(int code, long jitter) {
        int kind = TreeBlocks.kind(code);
        Wood wood = TreeBlocks.wood(code);
        int base = switch (kind) {
            case TreeBlocks.LOG, TreeBlocks.WOOD -> logColor(wood);
            case TreeBlocks.LEAVES -> leafColor(wood);
            case TreeBlocks.VINE -> 0x2E6516;
            case TreeBlocks.COCOA -> 0x8A5A2B;
            case TreeBlocks.BEE_NEST -> 0xD9A63B;
            case TreeBlocks.STEM -> 0xCFC9B9;
            case TreeBlocks.BROWN_CAP -> 0x92684A;
            case TreeBlocks.RED_CAP -> 0xB02222;
            case TreeBlocks.MOSS_CARPET -> 0x597A2E;
            case TreeBlocks.MUSHROOM -> TreeBlocks.extra(code) == 0 ? 0x9A7556 : 0xC03030;
            case TreeBlocks.STRIPPED -> IsoRenderer.shade(logColor(wood), 1.35);
            case TreeBlocks.ROOT -> 0x4C3B2A;
            case TreeBlocks.EARTH -> TreeBlocks.extra(code) == 0 ? 0x906850 : (TreeBlocks.extra(code) == 1 ? 0x775639 : 0x866043);
            case TreeBlocks.HANGING_ROOTS -> 0xA08868;
            default -> 0xFF00FF;
        };
        double f = 0.9 + 0.2 * ((jitter >>> 40) & 255) / 255.0;
        return IsoRenderer.shade(base, f);
    }

    private static long hash(int x, int y, int z) {
        return com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise.mix(x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L);
    }

    private void stamp(IsoRenderer r, TreeSpec spec, TreeShape shape) {
        for (int k = 0; k < shape.size(); k++) {
            int p = shape.positions[k];
            int x = spec.x() + TreeShape.dx(p);
            int y = spec.y() + TreeShape.dy(p);
            int z = spec.z() + TreeShape.dz(p);
            r.put(x, y, z, color(shape.codes[k], hash(x, y, z)));
        }
    }

    // ------------------------------------------------------------------ gallery

    void gallery(File file) throws IOException {
        Object[][] forms = {
                {TreeForm.BROADLEAF, Wood.OAK, 5.5, 2.4}, {TreeForm.BROADLEAF, Wood.OAK, 9.0, 3.8}, {TreeForm.BROADLEAF, Wood.OAK, 13.5, 5.6},
                {TreeForm.ANCIENT, Wood.OAK, 20.0, 10.0}, {TreeForm.ANCIENT, Wood.OAK, 26.0, 12.5},
                {TreeForm.BIRCH, Wood.BIRCH, 6.0, 1.6}, {TreeForm.BIRCH, Wood.BIRCH, 10.0, 2.1}, {TreeForm.BIRCH, Wood.BIRCH, 17.0, 2.5},
                {TreeForm.CONIFER, Wood.SPRUCE, 4.0, 1.5}, {TreeForm.CONIFER, Wood.SPRUCE, 10.0, 2.8}, {TreeForm.CONIFER, Wood.SPRUCE, 17.0, 3.9},
                {TreeForm.PINE, Wood.SPRUCE, 14.0, 2.6}, {TreeForm.MEGA_CONIFER, Wood.SPRUCE, 30.0, 5.8}, {TreeForm.MEGA_PINE, Wood.SPRUCE, 28.0, 4.4},
                {TreeForm.KRUMMHOLZ, Wood.SPRUCE, 3.0, 2.2}, {TreeForm.DARK_OAK, Wood.DARK_OAK, 8.5, 4.3}, {TreeForm.DARK_OAK, Wood.DARK_OAK, 13.0, 6.0},
                {TreeForm.TALL_JUNGLE, Wood.JUNGLE, 16.0, 2.8}, {TreeForm.MEGA_JUNGLE, Wood.JUNGLE, 27.0, 7.0}, {TreeForm.ACACIA, Wood.ACACIA, 7.5, 3.6},
                {TreeForm.ACACIA, Wood.ACACIA, 10.5, 5.0}, {TreeForm.SWAMP, Wood.OAK, 7.0, 4.0}, {TreeForm.BUSH, Wood.OAK, 2.0, 1.8},
                {TreeForm.BUSH, Wood.JUNGLE, 3.0, 2.2}, {TreeForm.SNAG, Wood.OAK, 7.0, 1.0}, {TreeForm.FALLEN, Wood.BIRCH, 6.0, 1.0},
                {TreeForm.HUGE_BROWN_MUSHROOM, Wood.MUSHROOM, 6.0, 3.0}, {TreeForm.HUGE_RED_MUSHROOM, Wood.MUSHROOM, 6.0, 2.0},
        };
        if (this.galleryKind.equals("mushrooms")) {
            int s = TreeSpec.STYLE_SHIFT;
            forms = new Object[][] {
                    {TreeForm.HUGE_BROWN_MUSHROOM, Wood.MUSHROOM, 6.0, 3.0, 0 << s}, {TreeForm.HUGE_BROWN_MUSHROOM, Wood.MUSHROOM, 7.0, 3.8, 1 << s},
                    {TreeForm.HUGE_BROWN_MUSHROOM, Wood.MUSHROOM, 5.0, 5.0, 2 << s}, {TreeForm.HUGE_BROWN_MUSHROOM, Wood.MUSHROOM, 10.0, 4.2, 3 << s},
                    {TreeForm.HUGE_BROWN_MUSHROOM, Wood.MUSHROOM, 6.0, 3.8, 4 << s}, {TreeForm.HUGE_RED_MUSHROOM, Wood.MUSHROOM, 6.0, 2.3, 0 << s},
                    {TreeForm.HUGE_RED_MUSHROOM, Wood.MUSHROOM, 11.0, 1.9, 1 << s}, {TreeForm.HUGE_RED_MUSHROOM, Wood.MUSHROOM, 6.0, 4.0, 2 << s},
                    {TreeForm.HUGE_RED_MUSHROOM, Wood.MUSHROOM, 7.0, 3.0, 4 << s}, {TreeForm.GIANT_MUSHROOM, Wood.MUSHROOM, 13.0, 6.2, 3 << s},
                    {TreeForm.GIANT_MUSHROOM, Wood.MUSHROOM, 12.0, 6.0, 1 << s}, {TreeForm.GIANT_MUSHROOM, Wood.MUSHROOM, 12.0, 5.6, TreeSpec.RED | 3 << s},
                    {TreeForm.GIANT_MUSHROOM, Wood.MUSHROOM, 12.0, 5.0, TreeSpec.RED}, {TreeForm.HUGE_BROWN_MUSHROOM, Wood.MUSHROOM, 8.0, 3.2, 0 << s},
                    {TreeForm.HUGE_RED_MUSHROOM, Wood.MUSHROOM, 8.0, 2.6, 0 << s}, {TreeForm.HUGE_BROWN_MUSHROOM, Wood.MUSHROOM, 4.0, 4.4, 2 << s},
            };
        } else if (this.galleryKind.equals("deadwood")) {
            int s = TreeSpec.STYLE_SHIFT;
            forms = new Object[][] {
                    {TreeForm.FALLEN, Wood.OAK, 6.0, 1.0, TreeSpec.MOSS}, {TreeForm.FALLEN, Wood.SPRUCE, 7.0, 1.0, 1 << s | 1 << TreeSpec.STEP_SHIFT},
                    {TreeForm.FALLEN, Wood.DARK_OAK, 7.0, 1.0, 2 << s | TreeSpec.ROT | TreeSpec.MOSS}, {TreeForm.FALLEN, Wood.SPRUCE, 8.0, 1.0, 3 << s | TreeSpec.MOSS},
                    {TreeForm.STUMP, Wood.OAK, 2.0, 1.0, TreeSpec.MOSS}, {TreeForm.STUMP, Wood.BIRCH, 3.0, 1.0, TreeSpec.ROT},
                    {TreeForm.UPROOTED, Wood.SPRUCE, 6.0, 3.0, TreeSpec.MOSS}, {TreeForm.DEBRIS, Wood.OAK, 2.0, 2.0, 0},
            };
        }
        int columns = 4;
        int rowsPerSheet = 2;
        int tile = 640;
        int perSheet = columns * rowsPerSheet;
        for (int sheetIndex = 0; sheetIndex * perSheet < forms.length; sheetIndex++) {
            BufferedImage sheet = new BufferedImage(columns * tile, rowsPerSheet * tile, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = sheet.createGraphics();
            g.setColor(new java.awt.Color(0xC8D8E8));
            g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
            for (int m = 0; m < perSheet && sheetIndex * perSheet + m < forms.length; m++) {
                int n = sheetIndex * perSheet + m;
                Object[] f = forms[n];
                TreeForm form = (TreeForm) f[0];
                int flags = f.length > 4 ? (int) f[4]
                        : (form == TreeForm.TALL_JUNGLE ? TreeSpec.COCOA : (form == TreeForm.BROADLEAF ? TreeSpec.BEES : TreeSpec.MOSS));
                double lean = form.mushroom() ? 1.3 : 0.4;
                TreeSpec spec = new TreeSpec(n + this.gallerySeed * 1000L, 0, 0, 0, form, (Wood) f[1], (double) f[2], (double) f[3], lean, -0.75 * lean, 1.0, 0.0,
                        form == TreeForm.TALL_JUNGLE || form == TreeForm.MEGA_JUNGLE || form == TreeForm.SWAMP ? 0.7 : 0.0,
                        flags, ForestType.TEMPERATE, 1234L + n * 7919L + this.gallerySeed * 104729L);
                TreeShape shape = this.model.shape(spec);
                IsoRenderer r = new IsoRenderer();
                int ground = Math.max(4, shape.radius() + 1);
                for (int x = -ground; x <= ground; x++) {
                    for (int z = -ground; z <= ground; z++) {
                        r.put(x, -1, z, 0x6D9A3F);
                    }
                }
                this.stamp(r, spec, shape);
                BufferedImage image = r.render(14, 0xC8D8E8);
                double fit = Math.min(1.0, Math.min((tile - 10.0) / image.getWidth(), (tile - 24.0) / image.getHeight()));
                int w = (int) (image.getWidth() * fit);
                int h = (int) (image.getHeight() * fit);
                int ox = (m % columns) * tile + (tile - w) / 2;
                int oy = (m / columns) * tile + (tile - h);
                g.drawImage(image, ox, oy - 5, w, h, null);
                g.setColor(java.awt.Color.BLACK);
                g.drawString(String.format(Locale.ROOT, "%s %s h%.0f r%.1f: %d logs %d leaves", form, f[1], spec.height(), spec.crown(), shape.logs, shape.leaves),
                        (m % columns) * tile + 6, (m / columns) * tile + 14);
            }
            g.dispose();
            File out = new File(file.getPath().replace(".png", "-" + sheetIndex + ".png"));
            write(sheet, out);
            System.out.println("wrote " + out);
        }
    }

    // ------------------------------------------------------------------ views

    private int groundColor(int x, int z, int top) {
        WaterColumn water = this.systems.hydrology().water(x, z);
        String biome = this.biomes.biome(x, top, z).location().getPath();
        if (biome.contains("snowy") || biome.contains("frozen") || biome.contains("ice") || biome.equals("grove")) {
            return 0xEEF2F4;
        }
        if (biome.contains("desert") || biome.contains("beach")) {
            return 0xD8CB8A;
        }
        if (biome.contains("badlands")) {
            return 0xB0603A;
        }
        if (biome.contains("peaks") || biome.contains("stony")) {
            return 0x8A8A8A;
        }
        double slope = Math.abs(this.systems.surfaceHeight(x + 1, z) - this.systems.surfaceHeight(x - 1, z))
                + Math.abs(this.systems.surfaceHeight(x, z + 1) - this.systems.surfaceHeight(x, z - 1));
        if (slope > 3.5) {
            return 0x808080;
        }
        if (water.wet()) {
            return 0x8B7D5A;
        }
        if (biome.contains("old_growth") && (biome.contains("taiga"))) {
            return 0x6A5030;
        }
        return switch (biome) {
            case "jungle", "sparse_jungle", "bamboo_jungle" -> 0x5DA03A;
            case "dark_forest" -> 0x4F7A30;
            case "swamp" -> 0x5A6A35;
            case "savanna", "savanna_plateau", "windswept_savanna" -> 0x9AA350;
            case "taiga" -> 0x5F8A4A;
            default -> 0x6D9A3F;
        };
    }

    void view(int cx, int cz, int size, File file) throws IOException {
        IsoRenderer r = new IsoRenderer();
        int x0 = cx - size / 2;
        int z0 = cz - size / 2;
        int[][] tops = new int[size + 2][size + 2];
        for (int i = -1; i <= size; i++) {
            for (int j = -1; j <= size; j++) {
                tops[i + 1][j + 1] = (int) Math.ceil(this.systems.surfaceHeight(x0 + i, z0 + j)) - 1;
            }
        }
        int low = Integer.MAX_VALUE;
        for (int[] row : tops) {
            for (int t : row) {
                low = Math.min(low, t);
            }
        }
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                int x = x0 + i;
                int z = z0 + j;
                int top = tops[i + 1][j + 1];
                int below = Math.min(Math.min(tops[i][j + 1], tops[i + 2][j + 1]), Math.min(tops[i + 1][j], tops[i + 1][j + 2]));
                int bottom = Math.max(low - 2, Math.min(top - 1, below));
                if (i == size - 1 || j == size - 1) {
                    bottom = low - 3;
                }
                int ground = this.groundColor(x, z, top);
                for (int y = bottom; y <= top; y++) {
                    r.put(x, y, z, y == top ? IsoRenderer.shade(ground, 0.94 + 0.12 * ((hash(x, y, z) >>> 40) & 255) / 255.0) : (y > top - 3 ? 0x7A5638 : 0x7F7F7F));
                }
                WaterColumn water = this.systems.hydrology().water(x, z);
                if (water.wet()) {
                    for (int y = water.groundTop() + 1; y <= water.waterTop(); y++) {
                        r.put(x, y, z, 0x3A5FCB);
                    }
                }
            }
        }
        int[] count = new int[1];
        this.model.trees(x0, z0, x0 + size - 1, z0 + size - 1, spec -> {
            TreeShape shape = this.model.shape(spec);
            for (int k = 0; k < shape.size(); k++) {
                int p = shape.positions[k];
                int x = spec.x() + TreeShape.dx(p);
                int y = spec.y() + TreeShape.dy(p);
                int z = spec.z() + TreeShape.dz(p);
                if (x >= x0 && x < x0 + size && z >= z0 && z < z0 + size) {
                    r.put(x, y, z, color(shape.codes[k], hash(x, y, z)));
                }
            }
            count[0]++;
        });
        BufferedImage image = r.render(size > 200 ? 2 : (size > 110 ? 3 : 4), 0xB8CCE0);
        write(image, file);
        System.out.printf(Locale.ROOT, "wrote %s: %d trees, %d voxels%n", file, count[0], r.size());
    }

    void map(int cx, int cz, int size, int scale, File file) throws IOException {
        int n = size / scale;
        int x0 = cx - size / 2;
        int z0 = cz - size / 2;
        int[] top = new int[size * size];
        int[] col = new int[size * size];
        for (int j = 0; j < size; j += scale) {
            for (int i = 0; i < size; i += scale) {
                int x = x0 + i;
                int z = z0 + j;
                int g = (int) Math.ceil(this.systems.surfaceHeight(x, z)) - 1;
                WaterColumn water = this.systems.hydrology().water(x, z);
                top[j * size + i] = water.wet() ? water.waterTop() : g;
                col[j * size + i] = water.wet() ? 0x3A5FCB : this.groundColor(x, z, g);
            }
        }
        if (scale == 1) {
            this.model.trees(x0, z0, x0 + size - 1, z0 + size - 1, spec -> {
                TreeShape shape = this.model.shape(spec);
                for (int k = 0; k < shape.size(); k++) {
                    int p = shape.positions[k];
                    int i = spec.x() + TreeShape.dx(p) - x0;
                    int j = spec.z() + TreeShape.dz(p) - z0;
                    int y = spec.y() + TreeShape.dy(p);
                    if (i >= 0 && i < size && j >= 0 && j < size && y >= top[j * size + i]) {
                        top[j * size + i] = y;
                        col[j * size + i] = color(shape.codes[k], hash(i, y, j));
                    }
                }
            });
        } else {
            for (int j = 0; j < size; j += scale) {
                for (int i = 0; i < size; i += scale) {
                    double d = this.model.density(x0 + i, z0 + j);
                    if (d > 0.0) {
                        int c = col[j * size + i];
                        col[j * size + i] = blend(c, 0x1E4A14, Math.min(1.0, d * 1.1));
                    }
                }
            }
        }
        BufferedImage image = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                int a = j * scale * size + i * scale;
                int west = i > 0 ? top[a - scale] : top[a];
                int north = j > 0 ? top[a - scale * size] : top[a];
                double shade = 1.0 + 0.07 * ((top[a] - west) + (top[a] - north)) / scale;
                image.setRGB(i, j, IsoRenderer.shade(col[a], Math.max(0.55, Math.min(1.25, shade))));
            }
        }
        write(image, file);
        System.out.println("wrote " + file);
    }

    private static int blend(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    // ------------------------------------------------------------------ statistics

    void stats(int cx, int cz, int size) {
        int x0 = cx - size / 2;
        int z0 = cz - size / 2;
        List<TreeSpec> trees = new ArrayList<>();
        this.model.trees(x0, z0, x0 + size - 1, z0 + size - 1, spec -> {
            if (spec.x() >= x0 && spec.x() < x0 + size && spec.z() >= z0 && spec.z() < z0 + size) {
                trees.add(spec);
            }
        });
        Map<ForestType, int[]> byType = new EnumMap<>(ForestType.class);
        Map<String, Integer> byForm = new TreeMap<>();
        int[] heights = new int[60];
        int invalid = 0;
        int giants = 0;
        int standing = 0;
        boolean[] canopy = new boolean[size * size];
        int maxReach = 0;
        for (TreeSpec t : trees) {
            byType.computeIfAbsent(t.type(), k -> new int[1])[0]++;
            byForm.merge(t.form() + " " + t.wood(), 1, Integer::sum);
            TreeShape shape = this.model.shape(t);
            maxReach = Math.max(maxReach, shape.radius());
            if (!t.form().lowGrowth() && t.form() != TreeForm.SNAG) {
                standing++;
                heights[Math.min(59, shape.height)]++;
                if (shape.height >= 22) {
                    giants++;
                }
            }
            for (int k = 0; k < shape.shadeColumns.length; k++) {
                int i = t.x() + TreeShape.dx(shape.shadeColumns[k]) - x0;
                int j = t.z() + TreeShape.dz(shape.shadeColumns[k]) - z0;
                if (i >= 0 && i < size && j >= 0 && j < size) {
                    canopy[j * size + i] = true;
                }
            }
            // Checks: trunk on dry ground at the model's surface, no cave under it.
            int w = t.form().wide() ? 2 : 1;
            for (int a = 0; a < w; a++) {
                for (int b = 0; b < w; b++) {
                    WaterColumn water = this.systems.hydrology().water(t.x() + a, t.z() + b);
                    boolean wet = water.kind() != WaterColumn.Kind.DRY && !(t.type() == ForestType.SWAMP && water.kind() == WaterColumn.Kind.SEA);
                    var caves = (com.sincerelyuq.vastlands.worldgen.caves.VastlandsCaves) this.systems.caves();
                    if (wet || caves.isCave(t.x() + a, t.y() - 1, t.z() + b)) {
                        invalid++;
                    }
                }
            }
        }
        int forestColumns = 0;
        int covered = 0;
        int open = 0;
        double densitySum = 0;
        for (int j = 0; j < size; j += 4) {
            for (int i = 0; i < size; i += 4) {
                ForestSample s = this.model.sample(x0 + i, z0 + j);
                if (s.share() > 0.5 && s.type() != null && s.type().closed()) {
                    forestColumns++;
                    densitySum += s.density();
                    covered += canopy[j * size + i] ? 1 : 0;
                    open += s.clearing() > 0.5 ? 1 : 0;
                }
            }
        }
        int coveredAll = 0;
        for (boolean c : canopy) {
            coveredAll += c ? 1 : 0;
        }
        // Spacing: nearest standing neighbour of each standing tree.
        List<TreeSpec> stems = trees.stream().filter(t -> !t.form().lowGrowth()).toList();
        double[] nearest = new double[stems.size()];
        for (int a = 0; a < stems.size(); a++) {
            double best = Double.POSITIVE_INFINITY;
            for (int b = 0; b < stems.size(); b++) {
                if (a != b) {
                    double d = Math.hypot(stems.get(a).x() - stems.get(b).x(), stems.get(a).z() - stems.get(b).z());
                    best = Math.min(best, d);
                }
            }
            nearest[a] = best;
        }
        java.util.Arrays.sort(nearest);
        double km2 = size * (double) size / 1e6;
        System.out.printf(Locale.ROOT, "square %d,%d size %d: %d objects (%.0f per km^2), %d standing trees (%.1f per chunk), %d of 22+ blocks, max reach %d%n",
                cx, cz, size, trees.size(), trees.size() / km2, standing, standing * 256.0 / (size * (double) size), giants, maxReach);
        System.out.print("by community:");
        byType.forEach((k, v) -> System.out.printf(Locale.ROOT, " %s %d", k.tagPath(), v[0]));
        System.out.println();
        System.out.print("by form:");
        byForm.forEach((k, v) -> System.out.printf(Locale.ROOT, " [%s %d]", k, v));
        System.out.println();
        System.out.print("heights (standing):");
        for (int h = 0; h < 60; h += 4) {
            int c = 0;
            for (int k = h; k < h + 4; k++) {
                c += heights[k];
            }
            if (c > 0) {
                System.out.printf(Locale.ROOT, " %d-%d: %d", h, h + 3, c);
            }
        }
        System.out.println();
        if (nearest.length > 0) {
            System.out.printf(Locale.ROOT, "nearest standing neighbour: min %.1f p10 %.1f median %.1f p90 %.1f%n", nearest[0],
                    nearest[nearest.length / 10], nearest[nearest.length / 2], nearest[nearest.length * 9 / 10]);
        }
        System.out.printf(Locale.ROOT, "canopy over %.1f%% of the square; closed-forest columns %d: mean density %.2f, canopy %.1f%%, clearings %.1f%%%n",
                100.0 * coveredAll / canopy.length, forestColumns, forestColumns == 0 ? 0 : densitySum / forestColumns,
                forestColumns == 0 ? 0 : 100.0 * covered / forestColumns, forestColumns == 0 ? 0 : 100.0 * open / forestColumns);
        System.out.printf(Locale.ROOT, "trunk columns on water or over caves: %d%n", invalid);
    }

    void find(ForestType type, int radius) {
        int found = 0;
        for (int r = 0; r <= radius && found < 12; r += 512) {
            for (int a = 0; a < Math.max(1, r / 128) && found < 12; a++) {
                double ang = a * 2 * Math.PI / Math.max(1, r / 128);
                int x = (int) (Math.cos(ang) * r);
                int z = (int) (Math.sin(ang) * r);
                ForestSample s = this.model.sample(x, z);
                if (s.type() == type && s.share() > 0.9 && s.density() > 0.4) {
                    System.out.printf(Locale.ROOT, "%s at %d,%d: density %.2f age %.2f ground %.0f%n", type.tagPath(), x, z, s.density(), s.age(),
                            this.systems.surfaceHeight(x, z));
                    found++;
                }
            }
        }
    }

    private static void write(BufferedImage image, File file) throws IOException {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        ImageIO.write(image, "png", file);
    }

    /**
     * Two fresh systems: one queries the chunks of a square in rows on one thread, the other in a
     * shuffled order on four threads; every chunk must get the same trees and shapes.
     */
    static void orderCheck(long seed, int cx, int cz, int chunks) {
        ForestPreview a = new ForestPreview(seed);
        ForestPreview b = new ForestPreview(seed);
        int c0x = (cx >> 4) - chunks / 2;
        int c0z = (cz >> 4) - chunks / 2;
        java.util.List<int[]> order = new java.util.ArrayList<>();
        for (int i = 0; i < chunks; i++) {
            for (int j = 0; j < chunks; j++) {
                order.add(new int[] {c0x + i, c0z + j});
            }
        }
        java.util.Map<Long, String> first = new java.util.concurrent.ConcurrentHashMap<>();
        for (int[] c : order) {
            first.put(((long) c[0] << 32) ^ (c[1] & 0xFFFFFFFFL), a.signature(c[0], c[1]));
        }
        java.util.Collections.shuffle(order, new java.util.Random(7));
        java.util.concurrent.atomic.AtomicInteger differ = new java.util.concurrent.atomic.AtomicInteger();
        order.parallelStream().forEach(c -> {
            String sig = b.signature(c[0], c[1]);
            if (!sig.equals(first.get(((long) c[0] << 32) ^ (c[1] & 0xFFFFFFFFL)))) {
                if (differ.incrementAndGet() <= 3) {
                    System.out.println("chunk " + c[0] + "," + c[1] + " differs:\n  " + first.get(((long) c[0] << 32) ^ (c[1] & 0xFFFFFFFFL)) + "\n  " + sig);
                }
            }
        });
        System.out.printf(Locale.ROOT, "order check: %d of %d chunks differ%n", differ.get(), order.size());
    }

    /** Everything a chunk would build: each reaching tree's spec and a hash of its shape. */
    String signature(int chunkX, int chunkZ) {
        StringBuilder out = new StringBuilder();
        this.model.trees(chunkX << 4, chunkZ << 4, (chunkX << 4) + 15, (chunkZ << 4) + 15, spec -> {
            TreeShape shape = this.model.shape(spec);
            out.append(spec.key()).append(':').append(spec.x()).append(',').append(spec.y()).append(',').append(spec.z()).append(spec.form())
                    .append(java.util.Arrays.hashCode(shape.positions) * 31 + java.util.Arrays.hashCode(shape.codes)).append(' ');
        });
        return out.toString();
    }

    /** The trees reaching a square around a point, with their shapes' bounds. */
    void list(int cx, int cz, int r) {
        this.model.trees(cx - r, cz - r, cx + r, cz + r, spec -> {
            TreeShape shape = this.model.shape(spec);
            System.out.printf(Locale.ROOT, "%s %s at %d %d %d h %.1f r %.1f type %s: x %d..%d y %d..%d z %d..%d, %d blocks%n", spec.form(), spec.wood(),
                    spec.x(), spec.y(), spec.z(), spec.height(), spec.crown(), spec.type(), spec.x() + shape.minX, spec.x() + shape.maxX,
                    spec.y() + shape.minY, spec.y() + shape.maxY, spec.z() + shape.minZ, spec.z() + shape.maxZ, shape.size());
        });
    }

    /** The horizontal radius of every tree of a square by form, against the form's reach. */
    void reach(int cx, int cz, int size) {
        java.util.Map<TreeForm, int[]> radii = new java.util.EnumMap<>(TreeForm.class);
        java.util.Map<TreeForm, int[]> counts = new java.util.EnumMap<>(TreeForm.class);
        this.model.trees(cx - size / 2, cz - size / 2, cx + size / 2, cz + size / 2, spec -> {
            int r = this.model.shape(spec).radius();
            radii.computeIfAbsent(spec.form(), f -> new int[17])[Math.min(16, r)]++;
            counts.computeIfAbsent(spec.form(), f -> new int[1])[0]++;
        });
        for (java.util.Map.Entry<TreeForm, int[]> e : radii.entrySet()) {
            int[] h = e.getValue();
            int max = 0;
            for (int r = 0; r <= 16; r++) {
                if (h[r] > 0) {
                    max = r;
                }
            }
            System.out.printf(Locale.ROOT, "%-20s %6d trees, radius max %2d (reach %2d), at the reach %d%n", e.getKey(), counts.get(e.getKey())[0], max,
                    e.getKey().reach, h[Math.min(16, e.getKey().reach)]);
        }
    }

    /** Rivers and lakes 8+ blocks wide inside closed forest, on a 64-block grid. */
    void findRiver(int radius) {
        int found = 0;
        for (int x = -radius; x <= radius && found < 16; x += 64) {
            for (int z = -radius; z <= radius && found < 16; z += 64) {
                WaterColumn w = this.systems.hydrology().water(x, z);
                if (!w.placed() || w.waterTop() < 64) {
                    continue;
                }
                ForestSample a = this.model.sample(x + 24, z, false);
                ForestSample b = this.model.sample(x - 24, z, false);
                if (a.type() != null && b.type() != null && a.type().closed() && b.type().closed() && a.density() > 0.45 && b.density() > 0.45) {
                    System.out.printf(Locale.ROOT, "%s in %s/%s forest at %d,%d: water top %d, density %.2f/%.2f%n", w.kind(), a.type().tagPath(),
                            b.type().tagPath(), x, z, w.waterTop(), a.density(), b.density());
                    found++;
                    x += 512;
                }
            }
        }
    }

    /** Places where forest gives way to cold high ground: dense forest within 300 blocks of treeless cold slopes. */
    void findTreeline(int radius) {
        int found = 0;
        for (int x = -radius; x <= radius && found < 10; x += 256) {
            for (int z = -radius; z <= radius && found < 10; z += 256) {
                ForestSample s = this.model.sample(x, z, false);
                if (s.type() != null && s.treeline() > 0.05 && s.treeline() < 0.7 && this.systems.surfaceHeight(x, z) > 170) {
                    System.out.printf(Locale.ROOT, "treeline at %d,%d: %s treeline %.2f density %.2f ground %.0f temperature %.2f%n", x, z,
                            s.type().tagPath(), s.treeline(), s.density(), this.systems.surfaceHeight(x, z), s.temperature());
                    found++;
                }
            }
        }
    }

    void bench(int cx, int cz, int chunks) {
        for (int round = 0; round < 2; round++) {
            long t0 = System.nanoTime();
            long blocks = 0;
            int trees = 0;
            for (int c = 0; c < chunks; c++) {
                int x = (cx >> 4) + c << 4;
                int z = cz >> 4 << 4;
                List<TreeSpec> list = new ArrayList<>();
                this.model.trees(x, z, x + 15, z + 15, list::add);
                for (TreeSpec spec : list) {
                    blocks += this.model.shape(spec).size();
                }
                trees += list.size();
            }
            double ms = (System.nanoTime() - t0) / 1e6;
            System.out.printf(Locale.ROOT, "round %d: %d chunks, %.2f ms per chunk, %.1f trees reaching each chunk, %.0f shape blocks per chunk%n",
                    round, chunks, ms / chunks, trees / (double) chunks, blocks / (double) chunks);
        }
    }
}
