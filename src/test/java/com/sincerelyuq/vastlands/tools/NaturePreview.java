package com.sincerelyuq.vastlands.tools;

import com.sincerelyuq.vastlands.worldgen.WorldHeight;
import com.sincerelyuq.vastlands.worldgen.caves.VastlandsCaves;
import com.sincerelyuq.vastlands.worldgen.forest.ForestModel;
import com.sincerelyuq.vastlands.worldgen.forest.TreeShape;
import com.sincerelyuq.vastlands.worldgen.forest.VastlandsForests;
import com.sincerelyuq.vastlands.worldgen.hydrology.WaterColumn;
import com.sincerelyuq.vastlands.worldgen.nature.FormationBlocks;
import com.sincerelyuq.vastlands.worldgen.nature.FormationKind;
import com.sincerelyuq.vastlands.worldgen.nature.FormationShape;
import com.sincerelyuq.vastlands.worldgen.nature.FormationSpec;
import com.sincerelyuq.vastlands.worldgen.nature.NatureModel;
import com.sincerelyuq.vastlands.worldgen.nature.NatureTesting;
import com.sincerelyuq.vastlands.worldgen.nature.Rock;
import com.sincerelyuq.vastlands.worldgen.nature.VastlandsNature;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSetup;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSystems;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

/**
 * Previews of the natural formations without the game ({@code ./gradlew naturePreview --args="..."}):
 * <ul>
 *     <li>{@code --gallery FILE}: every formation kind on flat ground or in a box cave, isometric;</li>
 *     <li>{@code --view X Z SIZE FILE}: a square of terrain, water, trees and surface formations;</li>
 *     <li>{@code --cave X Y Z SIZE HEIGHT FILE}: the caves of a box with their ceilings cut away:
 *     floors and walls by depth, water, and the cave formations;</li>
 *     <li>{@code --stats X Z SIZE}: formations by kind at the surface and in the caves, per km²;</li>
 *     <li>{@code --find KIND N}: formations of a kind within N blocks of 0,0;</li>
 *     <li>{@code --bench X Z CHUNKS}: the model and the shapes per chunk, surface and caves;</li>
 *     <li>{@code --order X Z CHUNKS}: the formations of every chunk from two systems queried in
 *     two orders on parallel threads, chunks that differ.</li>
 * </ul>
 * Options: {@code --seed S}. Biomes: {@link PreviewNatureBiomes}.
 */
public final class NaturePreview {
    private final GenerationSystems systems;
    private final NatureModel model;
    private final ForestModel forest;
    private final PreviewNatureBiomes biomes;

    NaturePreview(long seed) {
        this.systems = GenerationSystems.create(new GenerationSetup(seed, WorldHeight.MIN_Y, WorldHeight.HEIGHT, 63));
        VastlandsNature nature = (VastlandsNature) this.systems.nature();
        this.biomes = new PreviewNatureBiomes(this.systems);
        nature.bind(this, this.biomes);
        this.model = nature.model();
        VastlandsForests forests = (VastlandsForests) this.systems.forests();
        forests.bind(this, new PreviewForestBiomes(this.systems));
        this.forest = forests.model();
    }

    public static void main(String[] args) throws IOException {
        long seed = 20261018L;
        List<String[]> jobs = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--seed" -> seed = Long.parseLong(args[++i]);
                case "--gallery" -> jobs.add(new String[] {"gallery", args[++i]});
                case "--view" -> jobs.add(new String[] {"view", args[++i], args[++i], args[++i], args[++i]});
                case "--cave" -> jobs.add(new String[] {"cave", args[++i], args[++i], args[++i], args[++i], args[++i], args[++i]});
                case "--stats" -> jobs.add(new String[] {"stats", args[++i], args[++i], args[++i]});
                case "--find" -> jobs.add(new String[] {"find", args[++i], args[++i]});
                case "--bench" -> jobs.add(new String[] {"bench", args[++i], args[++i], args[++i]});
                case "--order" -> jobs.add(new String[] {"order", args[++i], args[++i], args[++i]});
                default -> throw new IllegalArgumentException("unknown argument " + args[i]);
            }
        }
        NaturePreview preview = new NaturePreview(seed);
        for (String[] job : jobs) {
            long t0 = System.nanoTime();
            switch (job[0]) {
                case "gallery" -> gallery(new File(job[1]));
                case "view" -> preview.view(Integer.parseInt(job[1]), Integer.parseInt(job[2]), Integer.parseInt(job[3]), new File(job[4]));
                case "cave" -> preview.cave(Integer.parseInt(job[1]), Integer.parseInt(job[2]), Integer.parseInt(job[3]), Integer.parseInt(job[4]),
                        Integer.parseInt(job[5]), new File(job[6]));
                case "stats" -> preview.stats(Integer.parseInt(job[1]), Integer.parseInt(job[2]), Integer.parseInt(job[3]));
                case "find" -> preview.find(FormationKind.valueOf(job[1].toUpperCase(Locale.ROOT)), Integer.parseInt(job[2]));
                case "bench" -> preview.bench(Integer.parseInt(job[1]), Integer.parseInt(job[2]), Integer.parseInt(job[3]));
                case "order" -> orderCheck(seed, Integer.parseInt(job[1]), Integer.parseInt(job[2]), Integer.parseInt(job[3]));
                default -> throw new IllegalStateException(job[0]);
            }
            System.out.printf(Locale.ROOT, "%s: %.1f s%n", job[0], (System.nanoTime() - t0) / 1e9);
        }
    }

    // ------------------------------------------------------------------ colours

    private static long hash(int x, int y, int z) {
        return com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise.mix(x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L);
    }

    static int rockColor(Rock rock, int y) {
        return switch (rock) {
            case STONE -> 0x7F7F7F;
            case GRANITE -> 0x9A6B57;
            case LIMESTONE -> 0xBDBDB2;
            case SANDSTONE -> 0xD8CB8A;
            case TERRACOTTA -> new int[] {0xA1543A, 0xB0603A, 0x8E3B2E, 0xBA8523, 0xD1B2A1, 0x876B62}[Math.floorMod(y / 2, 6)];
            case BASALT -> 0x505055;
            case DEEPSLATE -> 0x4D4D52;
            case DRIPSTONE -> 0x86705E;
            case RUBBLE -> 0x737373;
            case DEEP_RUBBLE -> 0x555558;
            case SHORE -> 0x8A8A8A;
            case CALCITE -> 0xDFDFDC;
            case PALE -> 0xBCBCBC;
        };
    }

    static int color(int code, int x, int y, int z) {
        int kind = FormationBlocks.kind(code);
        int extra = FormationBlocks.extra(code);
        int base = switch (kind) {
            case FormationBlocks.ROCK -> {
                boolean exposed = FormationBlocks.has(code, FormationBlocks.EXPOSED);
                boolean top = FormationBlocks.has(code, FormationBlocks.TOP);
                double c = ((hash(x, y, z) >>> 20) & 1023) / 1023.0;
                if (exposed && (extra & FormationBlocks.SCULKY) != 0 && c < (top ? 0.8 : 0.55)) {
                    yield 0x0F2A33;
                }
                if (exposed && (extra & FormationBlocks.LUSHY) != 0 && c < (top ? 0.9 : 0.62)) {
                    yield 0x596E2D;
                }
                if (exposed && (extra & FormationBlocks.MOSSY) != 0 && c < (top ? 0.62 : 0.2)) {
                    yield 0x6B7A4A;
                }
                yield rockColor(FormationBlocks.palette(code), y);
            }
            case FormationBlocks.POINTED -> 0x9A7A64;
            case FormationBlocks.DRIPSTONE -> 0x86705E;
            case FormationBlocks.MOSS_BLOCK -> 0x596E2D;
            case FormationBlocks.MOSS_CARPET -> 0x5D7232;
            case FormationBlocks.ROOTED_DIRT -> 0x906850;
            case FormationBlocks.HANGING_ROOTS -> 0xA08868;
            case FormationBlocks.CAVE_VINES -> (extra & 1) != 0 ? 0xF5A030 : 0x5A7A2A;
            case FormationBlocks.AZALEA -> (extra & 1) != 0 ? 0xC070B0 : 0x5E7A2E;
            case FormationBlocks.AZALEA_LEAVES -> (extra & 1) != 0 ? 0xA06AA0 : 0x5A7A2A;
            case FormationBlocks.SCULK -> 0x0F2A33;
            case FormationBlocks.SCULK_VEIN -> 0x10404A;
            case FormationBlocks.LOG -> (extra & 32) != 0 ? 0xB89F7C : 0x6B5231;
            case FormationBlocks.GRAVEL -> 0x857F7C;
            case FormationBlocks.CLAY -> 0xA0A6B3;
            case FormationBlocks.CALCITE -> 0xDFDFDC;
            case FormationBlocks.AMETHYST_BLOCK -> 0x8661B8;
            case FormationBlocks.AMETHYST -> 0xA87FDB;
            case FormationBlocks.BASALT -> 0x4E4E52;
            case FormationBlocks.GLOW_LICHEN -> 0x7A9A80;
            case FormationBlocks.MAGMA -> 0x8E3A14;
            case FormationBlocks.COARSE_DIRT -> 0x775639;
            case FormationBlocks.MUD -> 0x3C3837;
            case FormationBlocks.SPORE_BLOSSOM -> 0xD55E9A;
            case FormationBlocks.SAND -> 0xDBCFA3;
            case FormationBlocks.PACKED_MUD -> 0x8E6B4F;
            default -> 0xFF00FF;
        };
        return IsoRenderer.shade(base, 0.92 + 0.16 * ((hash(x, y, z) >>> 40) & 255) / 255.0);
    }

    /** Stamps a formation's blocks (snapped stacks as they lie in the shape, KEEP markers left out). */
    private static int stamp(IsoRenderer r, FormationSpec spec, FormationShape shape, int x0, int z0, int x1, int z1) {
        int n = 0;
        for (int k = 0; k < shape.size(); k++) {
            int p = shape.positions[k];
            int code = shape.codes[k];
            if (FormationBlocks.kind(code) == FormationBlocks.KEEP) {
                continue;
            }
            int x = spec.x() + FormationShape.dx(p);
            int y = spec.y() + FormationShape.dy(p);
            int z = spec.z() + FormationShape.dz(p);
            if (x >= x0 && x <= x1 && z >= z0 && z <= z1) {
                r.put(x, y, z, color(code, x, y, z));
                n++;
            }
        }
        return n;
    }

    static void write(BufferedImage image, File file) throws IOException {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        ImageIO.write(image, "png", file);
    }

    // ------------------------------------------------------------------ gallery

    /** Every kind on flat ground (water beyond x = 3 for the kinds of the shore) or in a box cave 24 blocks high. */
    static void gallery(File file) throws IOException {
        Object[][] entries = {
                {FormationKind.BOULDER, 1.6, 2.2, Rock.STONE, FormationSpec.MOSSY}, {FormationKind.BOULDER, 2.6, 3.4, Rock.GRANITE, 0},
                {FormationKind.ROCK_CLUSTER, 1.0, 1.8, Rock.SANDSTONE, 0}, {FormationKind.TALUS, 2.0, 11.0, Rock.RUBBLE, 0},
                {FormationKind.OUTCROP, 7.0, 4.5, Rock.GRANITE, 0}, {FormationKind.SPIRE, 12.0, 2.2, Rock.TERRACOTTA, 0},
                {FormationKind.SPIRE, 22.0, 3.4, Rock.STONE, 0}, {FormationKind.ARCH, 9.0, 8.0, Rock.SANDSTONE, 0},
                {FormationKind.DRIFTWOOD, 6.0, 1.0, Rock.SHORE, 0}, {FormationKind.SHORE_STONES, 2.0, 2.0, Rock.SHORE, 0},
                {FormationKind.ISLET, 2.0, 4.0, Rock.SHORE, FormationSpec.FLOODED}, {FormationKind.BALANCED_ROCK, 5.0, 3.2, Rock.SANDSTONE, 0},
                {FormationKind.GREAT_ARCH, 22.0, 18.0, Rock.TERRACOTTA, 0}, {FormationKind.GREAT_SPIRE, 50.0, 7.0, Rock.GRANITE, 0},
                {FormationKind.STONE_FOREST, 24.0, 22.0, Rock.LIMESTONE, FormationSpec.MOSSY},
                {FormationKind.RUBBLE, 0.0, 2.0, Rock.RUBBLE, 0}, {FormationKind.CAVE_BOULDER, 1.8, 2.4, Rock.STONE, 0},
                {FormationKind.STALAGMITE, 10.0, 2.2, Rock.DRIPSTONE, FormationSpec.DRIPSTONE},
                {FormationKind.STALACTITE, 12.0, 2.4, Rock.DRIPSTONE, FormationSpec.DRIPSTONE | FormationSpec.CEILING},
                {FormationKind.COLUMN, 24.0, 2.8, Rock.DRIPSTONE, FormationSpec.DRIPSTONE},
                {FormationKind.POINTED_GROUP, 0.0, 2.0, Rock.DRIPSTONE, FormationSpec.DRIPSTONE},
                {FormationKind.PENDANT, 5.0, 2.6, Rock.STONE, 0}, {FormationKind.ROCK_PILLAR, 24.0, 2.0, Rock.DEEPSLATE, 0},
                {FormationKind.COLLAPSE, 5.0, 8.0, Rock.RUBBLE, 0}, {FormationKind.ROOT_CURTAIN, 0.0, 2.6, Rock.STONE, FormationSpec.LUSH},
                {FormationKind.MOSS_GARDEN, 0.0, 5.0, Rock.STONE, FormationSpec.LUSH}, {FormationKind.MOSSY_PILLAR, 24.0, 2.2, Rock.STONE, FormationSpec.LUSH},
                {FormationKind.SCULK_MOUND, 2.4, 3.4, Rock.DEEPSLATE, FormationSpec.SCULK}, {FormationKind.SCULK_PILLAR, 24.0, 2.2, Rock.DEEPSLATE, FormationSpec.SCULK},
                {FormationKind.SCULK_GROWTH, 0.0, 3.0, Rock.DEEPSLATE, FormationSpec.SCULK}, {FormationKind.CRYSTALS, 2.4, 2.8, Rock.CALCITE, 0},
                {FormationKind.BASALT_COLUMNS, 0.0, 3.0, Rock.BASALT, 0}, {FormationKind.SUBMERGED_ROCK, 6.0, 2.4, Rock.STONE, FormationSpec.FLOODED},
                {FormationKind.CAVE_ISLET, 2.0, 5.0, Rock.STONE, FormationSpec.FLOODED | FormationSpec.MOSSY},
                {FormationKind.GIANT_DRIPSTONE, 30.0, 6.0, Rock.DRIPSTONE, FormationSpec.DRIPSTONE},
                {FormationKind.SPIKE_FIELD, 14.0, 11.0, Rock.DRIPSTONE, FormationSpec.DRIPSTONE},
                {FormationKind.GIANT_COLUMN, 24.0, 6.0, Rock.DEEPSLATE, FormationSpec.SCULK},
        };
        int columns = 4;
        int rows = 2;
        int tile = 640;
        int per = columns * rows;
        for (int sheet = 0; sheet * per < entries.length; sheet++) {
            BufferedImage image = new BufferedImage(columns * tile, rows * tile, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setColor(new java.awt.Color(0xC8D8E8));
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            for (int m = 0; m < per && sheet * per + m < entries.length; m++) {
                int n = sheet * per + m;
                Object[] e = entries[n];
                FormationKind kind = (FormationKind) e[0];
                int span = kind.surface ? 0 : 24;
                int water = kind == FormationKind.CAVE_ISLET ? 3 : (kind == FormationKind.SUBMERGED_ROCK ? 9 : -1);
                int flags = (int) e[4];
                FormationSpec spec = new FormationSpec(1000L + n, kind, 0, 0, 0, (double) e[1], (double) e[2],
                        kind == FormationKind.CAVE_ISLET ? 2 : (kind == FormationKind.DRIFTWOOD ? 2 : span),
                        kind == FormationKind.ARCH || kind == FormationKind.GREAT_ARCH ? -Math.PI / 4 : 0.6, (Rock) e[3], flags, 4242L + n * 7919L);
                boolean shore = kind == FormationKind.DRIFTWOOD || kind == FormationKind.SHORE_STONES || kind == FormationKind.ISLET;
                FormationShape shape = NatureTesting.shape(spec, kind.surface, shore, span);
                IsoRenderer r = new IsoRenderer();
                int ground = Math.max(5, shape.radius() + 2);
                for (int x = -ground; x <= ground; x++) {
                    for (int z = -ground; z <= ground; z++) {
                        boolean wet = shore && x >= 3 || kind == FormationKind.ISLET;
                        int top = wet ? -3 : -1;
                        r.put(x, top, z, kind.surface ? (wet ? 0x8B7D5A : 0x6D9A3F) : 0x5A5A5A);
                        if (wet) {
                            r.put(x, -1, z, 0x3A5FCB);
                        }
                        if (water > 0 && !kind.surface) {
                            r.put(x, water - 1, z, 0x3A5FCB);
                        }
                    }
                }
                int blocks = stamp(r, spec, shape, -64, -64, 64, 64);
                BufferedImage img = r.render(12, 0xC8D8E8);
                double fit = Math.min(1.0, Math.min((tile - 10.0) / img.getWidth(), (tile - 24.0) / img.getHeight()));
                int w = (int) (img.getWidth() * fit);
                int h = (int) (img.getHeight() * fit);
                g.drawImage(img, (m % columns) * tile + (tile - w) / 2, (m / columns) * tile + (tile - h) - 5, w, h, null);
                g.setColor(java.awt.Color.BLACK);
                g.drawString(String.format(Locale.ROOT, "%s %s h%.0f r%.1f: %d blocks", kind, e[3], (double) e[1], (double) e[2], blocks),
                        (m % columns) * tile + 6, (m / columns) * tile + 14);
            }
            g.dispose();
            File out = new File(file.getPath().replace(".png", "-" + sheet + ".png"));
            write(image, out);
            System.out.println("wrote " + out);
        }
    }

    // ------------------------------------------------------------------ views

    void view(int cx, int cz, int size, File file) throws IOException {
        IsoRenderer r = new IsoRenderer();
        int x0 = cx - size / 2;
        int z0 = cz - size / 2;
        int[][] tops = new int[size + 2][size + 2];
        int low = Integer.MAX_VALUE;
        for (int i = -1; i <= size; i++) {
            for (int j = -1; j <= size; j++) {
                tops[i + 1][j + 1] = (int) Math.ceil(this.systems.surfaceHeight(x0 + i, z0 + j)) - 1;
                low = Math.min(low, tops[i + 1][j + 1]);
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
                String biome = this.biomes.biomeName(x, top, z);
                int ground = biome.contains("desert") || biome.contains("beach") ? 0xD8CB8A : (biome.contains("badlands") ? 0xB0603A
                        : (biome.contains("snowy") || biome.contains("peaks") || biome.contains("slopes") || biome.contains("grove") ? 0xEEF2F4 : 0x6D9A3F));
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
        int[] counts = new int[2];
        this.forest.trees(x0, z0, x0 + size - 1, z0 + size - 1, spec -> {
            TreeShape shape = this.forest.shape(spec);
            for (int k = 0; k < shape.size(); k++) {
                int p = shape.positions[k];
                int x = spec.x() + TreeShape.dx(p);
                int y = spec.y() + TreeShape.dy(p);
                int z = spec.z() + TreeShape.dz(p);
                if (x >= x0 && x < x0 + size && z >= z0 && z < z0 + size) {
                    r.put(x, y, z, ForestPreview.color(shape.codes[k], hash(x, y, z)));
                }
            }
            counts[0]++;
        });
        this.model.formations(x0, z0, x0 + size - 1, z0 + size - 1, true, spec -> {
            stamp(r, spec, this.model.shape(spec, this.model.world()), x0, z0, x0 + size - 1, z0 + size - 1);
            counts[1]++;
        });
        write(r.render(size > 200 ? 2 : (size > 110 ? 3 : 4), 0xB8CCE0), file);
        System.out.printf(Locale.ROOT, "wrote %s: %d trees, %d surface formations%n", file, counts[0], counts[1]);
    }

    /** The caves of a box seen from above with the ceilings cut away: floors by depth, water, formations. */
    void cave(int cx, int cy, int cz, int size, int height, File file) throws IOException {
        VastlandsCaves caves = (VastlandsCaves) this.systems.caves();
        IsoRenderer r = new IsoRenderer();
        int x0 = cx - size / 2;
        int z0 = cz - size / 2;
        int y0 = cy - height / 2;
        int y1 = cy + height / 2;
        for (int x = x0; x < x0 + size; x++) {
            for (int z = z0; z < z0 + size; z++) {
                boolean below = false;
                for (int y = y0; y <= y1; y++) {
                    boolean open = caves.isCave(x, y, z);
                    if (open) {
                        if (!below && y > y0) {
                            int shadeY = (int) (0x50 + 0x50 * (y - y0) / (double) height);
                            r.put(x, y - 1, z, shadeY << 16 | shadeY << 8 | shadeY);
                        }
                        if (caves.caveFluid(x, y, z) == com.sincerelyuq.vastlands.worldgen.caves.CaveFluid.WATER
                                && !caves.isCave(x, y + 1, z) | caves.caveFluid(x, y + 1, z) != com.sincerelyuq.vastlands.worldgen.caves.CaveFluid.WATER) {
                            r.put(x, y, z, 0x3A5FCB);
                        }
                        if (caves.caveFluid(x, y, z) == com.sincerelyuq.vastlands.worldgen.caves.CaveFluid.LAVA
                                && caves.caveFluid(x, y + 1, z) != com.sincerelyuq.vastlands.worldgen.caves.CaveFluid.LAVA) {
                            r.put(x, y, z, 0xE06A10);
                        }
                    }
                    below = open;
                }
            }
        }
        int[] count = new int[1];
        this.model.formations(x0, z0, x0 + size - 1, z0 + size - 1, false, spec -> {
            if (spec.y() + spec.span() >= y0 && spec.y() <= y1) {
                stamp(r, spec, this.model.shape(spec, this.model.world()), x0, z0, x0 + size - 1, z0 + size - 1);
                count[0]++;
            }
        });
        write(r.render(size > 200 ? 2 : (size > 110 ? 3 : 4), 0x101418), file);
        System.out.printf(Locale.ROOT, "wrote %s: %d cave formations%n", file, count[0]);
    }

    // ------------------------------------------------------------------ numbers

    void stats(int cx, int cz, int size) {
        Map<FormationKind, int[]> kinds = new EnumMap<>(FormationKind.class);
        int x0 = cx - size / 2;
        int z0 = cz - size / 2;
        for (boolean surface : new boolean[] {true, false}) {
            this.model.formations(x0, z0, x0 + size - 1, z0 + size - 1, surface, spec -> {
                if (spec.x() < x0 || spec.x() >= x0 + size || spec.z() < z0 || spec.z() >= z0 + size) {
                    return;
                }
                FormationShape shape = this.model.shape(spec, this.model.world());
                int[] acc = kinds.computeIfAbsent(spec.kind(), k -> new int[3]);
                acc[0]++;
                acc[1] += shape.size();
                acc[2] = Math.max(acc[2], shape.maxY - shape.minY + 1);
            });
        }
        double km2 = size * (double) size / 1e6;
        System.out.printf(Locale.ROOT, "formations in %d x %d blocks around %d %d:%n", size, size, cx, cz);
        for (Map.Entry<FormationKind, int[]> e : kinds.entrySet()) {
            int[] a = e.getValue();
            System.out.printf(Locale.ROOT, "  %-16s %6d (%7.1f per km²), %6.0f blocks each, tallest %d%n", e.getKey(), a[0], a[0] / km2, a[1] / (double) a[0], a[2]);
        }
    }

    void find(FormationKind kind, int radius) {
        int found = 0;
        for (int x = -radius; x < radius && found < 20; x += 256) {
            for (int z = -radius; z < radius && found < 20; z += 256) {
                List<FormationSpec> list = new ArrayList<>();
                this.model.formations(x, z, x + 255, z + 255, kind.surface, s -> {
                    if (s.kind() == kind) {
                        list.add(s);
                    }
                });
                for (FormationSpec s : list) {
                    if (s.x() >= x && s.x() < x + 256 && s.z() >= z && s.z() < z + 256 && found < 20) {
                        System.out.printf(Locale.ROOT, "  %s at %d %d %d (h %.1f r %.1f span %d %s flags %d) biome %s%n", kind, s.x(), s.y(), s.z(), s.height(),
                                s.radius(), s.span(), s.rock(), s.flags(), this.biomes.biomeName(s.x(), s.y(), s.z()));
                        found++;
                    }
                }
            }
        }
    }

    void bench(int cx, int cz, int chunks) {
        for (int round = 0; round < 2; round++) {
            long surface = 0;
            long caves = 0;
            int formations = 0;
            int blocks = 0;
            int n = 0;
            int bx = (cx >> 4) + round * chunks;
            for (int i = 0; i < chunks; i++) {
                for (int j = 0; j < 4; j++) {
                    int x = (bx + i) << 4;
                    int z = ((cz >> 4) + j) << 4;
                    int[] acc = new int[2];
                    long t0 = System.nanoTime();
                    this.model.formations(x, z, x + 15, z + 15, true, s -> {
                        acc[0]++;
                        acc[1] += this.model.shape(s, this.model.world()).size();
                    });
                    long t1 = System.nanoTime();
                    this.model.formations(x, z, x + 15, z + 15, false, s -> {
                        acc[0]++;
                        acc[1] += this.model.shape(s, this.model.world()).size();
                    });
                    long t2 = System.nanoTime();
                    if (round == 1 || i >= 2) {
                        surface += t1 - t0;
                        caves += t2 - t1;
                        n++;
                        formations += acc[0];
                        blocks += acc[1];
                    }
                }
            }
            System.out.printf(Locale.ROOT, "round %d: %d chunks, surface %.2f ms, caves %.2f ms per chunk, %.1f formations and %.0f shape blocks reaching a chunk%n",
                    round, n, surface / 1e6 / n, caves / 1e6 / n, formations / (double) n, blocks / (double) n);
        }
    }

    private String signature(int chunkX, int chunkZ) {
        StringBuilder out = new StringBuilder();
        for (boolean surface : new boolean[] {true, false}) {
            this.model.formations(chunkX << 4, chunkZ << 4, (chunkX << 4) + 15, (chunkZ << 4) + 15, surface, s -> {
                FormationShape shape = this.model.shape(s, this.model.world());
                out.append(s).append(Arrays.hashCode(shape.positions) * 31 + Arrays.hashCode(shape.codes)).append(';');
            });
        }
        return out.toString();
    }

    static void orderCheck(long seed, int cx, int cz, int chunks) {
        NaturePreview a = new NaturePreview(seed);
        NaturePreview b = new NaturePreview(seed);
        List<int[]> list = new ArrayList<>();
        for (int i = 0; i < chunks; i++) {
            for (int j = 0; j < chunks; j++) {
                list.add(new int[] {(cx >> 4) + i, (cz >> 4) + j});
            }
        }
        Map<Long, String> first = new ConcurrentHashMap<>();
        for (int[] c : list) {
            first.put((long) c[0] << 32 ^ (c[1] & 0xFFFFFFFFL), a.signature(c[0], c[1]));
        }
        Collections.shuffle(list, new Random(7));
        int[] differ = new int[1];
        list.parallelStream().forEach(c -> {
            if (!first.get((long) c[0] << 32 ^ (c[1] & 0xFFFFFFFFL)).equals(b.signature(c[0], c[1]))) {
                synchronized (differ) {
                    differ[0]++;
                }
            }
        });
        System.out.printf(Locale.ROOT, "order check: %d chunks, %d differ%n", list.size(), differ[0]);
    }
}
