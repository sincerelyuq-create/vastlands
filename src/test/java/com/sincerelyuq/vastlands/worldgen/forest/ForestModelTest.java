package com.sincerelyuq.vastlands.worldgen.forest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sincerelyuq.vastlands.Vastlands;
import com.sincerelyuq.vastlands.tools.PreviewForestBiomes;
import com.sincerelyuq.vastlands.worldgen.WorldHeight;
import com.sincerelyuq.vastlands.worldgen.caves.VastlandsCaves;
import com.sincerelyuq.vastlands.worldgen.hydrology.VastlandsHydrology;
import com.sincerelyuq.vastlands.worldgen.hydrology.WaterColumn;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSetup;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSystems;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * The forest model without the game: biomes from vanilla's table on the router's climate
 * ({@link PreviewForestBiomes}), the real terrain, rivers and caves. Seed 20261018.
 */
class ForestModelTest {
    private static final long SEED = 20261018L;

    private static final class World {
        final GenerationSystems systems;
        final VastlandsForests forests;
        final ForestModel model;

        World() {
            this.systems = GenerationSystems.create(new GenerationSetup(SEED, WorldHeight.MIN_Y, WorldHeight.HEIGHT, 63));
            this.forests = (VastlandsForests) this.systems.forests();
            this.forests.bind(this, new PreviewForestBiomes(this.systems));
            this.model = this.forests.model();
        }

        List<TreeSpec> trees(int x0, int z0, int size) {
            List<TreeSpec> out = new ArrayList<>();
            this.model.trees(x0, z0, x0 + size - 1, z0 + size - 1, t -> {
                if (t.x() >= x0 && t.x() < x0 + size && t.z() >= z0 && t.z() < z0 + size) {
                    out.add(t);
                }
            });
            return out;
        }
    }

    private static String signature(ForestModel model, int cx, int cz) {
        StringBuilder out = new StringBuilder();
        model.trees(cx << 4, cz << 4, (cx << 4) + 15, (cz << 4) + 15, t -> {
            TreeShape shape = model.shape(t);
            out.append(t).append(Arrays.hashCode(shape.positions) * 31 + Arrays.hashCode(shape.codes)).append(';');
        });
        return out.toString();
    }

    /** Places: a dark forest by old growth taiga and the sea, a mixed forest at a lake, a jungle river, a taiga river. */
    private static final int[][] PLACES = {{288, 32}, {-1700, -800}, {-768, 2432}, {-2368, 768}};

    @Test
    void sameTreesFromAnyThreadAndOrder() {
        World a = new World();
        World b = new World();
        List<int[]> chunks = new ArrayList<>();
        for (int[] p : PLACES) {
            for (int i = -6; i < 6; i++) {
                for (int j = -6; j < 6; j++) {
                    chunks.add(new int[] {(p[0] >> 4) + i, (p[1] >> 4) + j});
                }
            }
        }
        Map<String, String> first = new HashMap<>();
        for (int[] c : chunks) {
            first.put(c[0] + "," + c[1], signature(a.model, c[0], c[1]));
        }
        Collections.shuffle(chunks, new Random(5));
        AtomicInteger differ = new AtomicInteger();
        chunks.parallelStream().forEach(c -> differ.addAndGet(signature(b.model, c[0], c[1]).equals(first.get(c[0] + "," + c[1])) ? 0 : 1));
        assertEquals(0, differ.get(), "chunks whose trees depend on the query order or thread");
    }

    /** Leaf distances recomputed from the shape's own logs (6 neighbours through leaves), up to 7. */
    private static Map<Integer, Integer> distances(TreeShape shape) {
        Map<Integer, Integer> code = new HashMap<>();
        for (int k = 0; k < shape.size(); k++) {
            code.put(shape.positions[k], shape.codes[k]);
        }
        Map<Integer, Integer> d = new HashMap<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int k = 0; k < shape.size(); k++) {
            if (TreeBlocks.isLog(shape.codes[k])) {
                d.put(shape.positions[k], 0);
                queue.add(shape.positions[k]);
            }
        }
        int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            int p = queue.poll();
            int dp = d.get(p);
            if (dp >= 6) {
                continue;
            }
            for (int[] s : steps) {
                int n = TreeShape.pack(TreeShape.dx(p) + s[0], TreeShape.dy(p) + s[1], TreeShape.dz(p) + s[2]);
                Integer c = code.get(n);
                if (c != null && TreeBlocks.kind(c) == TreeBlocks.LEAVES && !d.containsKey(n)) {
                    d.put(n, dp + 1);
                    queue.add(n);
                }
            }
        }
        return d;
    }

    @Test
    void shapesKeepTheirLeavesStayInReachAndHoldTheirDecorations() {
        World w = new World();
        int shapes = 0;
        int leaves = 0;
        for (TreeForm form : TreeForm.values()) {
            for (int n = 0; n < 40; n++) {
                Random r = new Random(n * 31L + form.ordinal());
                Wood wood = switch (form) {
                    case BIRCH -> Wood.BIRCH;
                    case CONIFER, PINE, MEGA_CONIFER, MEGA_PINE, KRUMMHOLZ -> Wood.SPRUCE;
                    case DARK_OAK -> Wood.DARK_OAK;
                    case TALL_JUNGLE, MEGA_JUNGLE -> Wood.JUNGLE;
                    case ACACIA -> Wood.ACACIA;
                    case HUGE_BROWN_MUSHROOM, HUGE_RED_MUSHROOM -> Wood.MUSHROOM;
                    default -> Wood.values()[r.nextInt(6)];
                };
                double height = switch (form) {
                    case ANCIENT -> 17 + r.nextDouble() * 12;
                    case MEGA_CONIFER, MEGA_PINE, MEGA_JUNGLE -> 20 + r.nextDouble() * 16;
                    case BUSH, KRUMMHOLZ -> 2 + r.nextDouble() * 2;
                    case FALLEN -> 3 + r.nextInt(6);
                    default -> 4 + r.nextDouble() * 16;
                };
                double crown = form == TreeForm.ANCIENT ? 8.5 + r.nextDouble() * 4.5 : (form.wide() ? 3.5 + r.nextDouble() * 4.5 : 1.2 + r.nextDouble() * 4.5);
                TreeSpec spec = new TreeSpec(1_000_000L + shapes, 0, 0, 0, form, wood, height, crown, r.nextDouble() * 3 - 1.5, r.nextDouble() * 3 - 1.5,
                        1, 0, r.nextDouble(), TreeSpec.COCOA | TreeSpec.BEES | TreeSpec.MOSS | TreeSpec.PODZOL | TreeSpec.ROOTS, ForestType.TEMPERATE, r.nextLong());
                TreeShape shape = w.model.shape(spec);
                shapes++;
                assertTrue(shape.radius() <= ForestModel.reach(spec), form + ": reach " + shape.radius());
                assertTrue(shape.logs > 0, form + " without logs");
                if (!form.lowGrowth() || form == TreeForm.BUSH || form == TreeForm.KRUMMHOLZ) {
                    assertTrue(shape.trunk.length > 0, form + " without a trunk on the ground");
                }
                Map<Integer, Integer> d = distances(shape);
                Map<Integer, Integer> code = new HashMap<>();
                for (int k = 0; k < shape.size(); k++) {
                    code.put(shape.positions[k], shape.codes[k]);
                }
                for (int k = 0; k < shape.size(); k++) {
                    int c = shape.codes[k];
                    int p = shape.positions[k];
                    int kind = TreeBlocks.kind(c);
                    if (kind == TreeBlocks.LEAVES) {
                        leaves++;
                        int stored = TreeBlocks.extra(c);
                        assertTrue(stored >= 1 && stored <= 6, form + ": leaf distance " + stored);
                        assertEquals(d.getOrDefault(p, 7), stored, form + ": stored leaf distance differs from the recomputed one");
                    } else if (kind == TreeBlocks.VINE) {
                        int faces = TreeBlocks.extra(c);
                        assertTrue(faces != 0, "vine without faces");
                        for (int f = 0; f < 4; f++) {
                            if ((faces & 1 << f) == 0) {
                                continue;
                            }
                            Integer side = code.get(TreeShape.pack(TreeShape.dx(p) + TreeShape.Builder.FACE_X[f], TreeShape.dy(p), TreeShape.dz(p) + TreeShape.Builder.FACE_Z[f]));
                            Integer above = code.get(TreeShape.pack(TreeShape.dx(p), TreeShape.dy(p) + 1, TreeShape.dz(p)));
                            boolean held = side != null && TreeBlocks.isSolid(side)
                                    || above != null && TreeBlocks.kind(above) == TreeBlocks.VINE && (TreeBlocks.extra(above) & 1 << f) != 0;
                            assertTrue(held, "vine face without support");
                        }
                    } else if (kind == TreeBlocks.COCOA) {
                        int f = TreeBlocks.extra(c) & 3;
                        Integer log = code.get(TreeShape.pack(TreeShape.dx(p) + TreeShape.Builder.FACE_X[f], TreeShape.dy(p), TreeShape.dz(p) + TreeShape.Builder.FACE_Z[f]));
                        assertTrue(log != null && TreeBlocks.kind(log) == TreeBlocks.LOG && TreeBlocks.wood(log) == Wood.JUNGLE, "cocoa without its jungle log");
                    } else if (kind == TreeBlocks.BEE_NEST) {
                        assertEquals(null, code.get(TreeShape.pack(TreeShape.dx(p), TreeShape.dy(p), TreeShape.dz(p) + 1)), "bee nest without room in front");
                    }
                }
            }
        }
        Vastlands.LOGGER.info("ForestModelTest: {} shapes, {} leaves, all within reach and at their recomputed distance", shapes, leaves);
    }

    @Test
    void treesStandOnDryGroundAwayFromCavesFallsAndSteepSlopes() {
        World w = new World();
        VastlandsCaves caves = (VastlandsCaves) w.systems.caves();
        VastlandsHydrology hydrology = (VastlandsHydrology) w.systems.hydrology();
        int checked = 0;
        int nearWater = 0;
        for (int[] p : PLACES) {
            List<double[]> falls = new ArrayList<>();
            for (int rx = Math.floorDiv(p[0] - 256, 512); rx <= Math.floorDiv(p[0] + 256, 512); rx++) {
                for (int rz = Math.floorDiv(p[1] - 256, 512); rz <= Math.floorDiv(p[1] + 256, 512); rz++) {
                    falls.addAll(hydrology.model().falls(rx * 512, rz * 512));
                }
            }
            PreviewForestBiomes biomes = new PreviewForestBiomes(w.systems);
            for (TreeSpec t : w.trees(p[0] - 192, p[1] - 192, 384)) {
                assertTrue(biomes.category(t.x(), t.y() - 1, t.z()) != ForestModel.NONE, t + " rooted in an unmanaged biome");
                int span = t.form().wide() ? 2 : 1;
                for (int a = 0; a < span; a++) {
                    for (int b = 0; b < span; b++) {
                        int x = t.x() + a;
                        int z = t.z() + b;
                        WaterColumn water = hydrology.water(x, z);
                        boolean shallowSwamp = t.type() == ForestType.SWAMP && water.kind() == WaterColumn.Kind.SEA && water.waterTop() - water.groundTop() <= 1;
                        assertTrue(water.kind() == WaterColumn.Kind.DRY || shallowSwamp, t + " stands in " + water);
                        int ground = shallowSwamp ? water.groundTop() : (int) Math.ceil(w.systems.surfaceHeight(x, z)) - 1;
                        assertTrue(ground >= t.y() - 1 && ground <= t.y(), t + ": ground " + ground);
                        for (int dy : new int[] {0, 1, 3, 6}) {
                            assertTrue(!caves.isCave(x, t.y() - 1 - dy, z), t + " over a cave opening");
                        }
                    }
                }
                if (t.form() != TreeForm.FALLEN && t.form() != TreeForm.BUSH) {
                    for (double[] f : falls) {
                        assertTrue(Math.hypot(f[0] - t.x(), f[1] - t.z()) >= t.crown() + 8.0, t + " next to a fall");
                    }
                }
                double slope = Math.hypot(w.systems.surfaceHeight(t.x() + 2, t.z()) - w.systems.surfaceHeight(t.x() - 2, t.z()),
                        w.systems.surfaceHeight(t.x(), t.z() + 2) - w.systems.surfaceHeight(t.x(), t.z() - 2)) / 4.0;
                assertTrue(slope <= 1.26, t + " on a slope of " + slope);
                ForestSample s = w.model.sample(t.x(), t.z(), false);
                nearWater += s.riparian() > 0.5 ? 1 : 0;
                checked++;
            }
        }
        Vastlands.LOGGER.info("ForestModelTest: {} trees checked at {} places, {} beside rivers and lakes", checked, PLACES.length, nearWater);
        assertTrue(checked > 3000, "too few trees: " + checked);
        assertTrue(nearWater > 50, "too few riverside trees: " + nearWater);
    }

    @Test
    void spacingSizesAndCommunitiesFollowTheForest() {
        World w = new World();
        Map<ForestType, Map<Wood, Integer>> woods = new EnumMap<>(ForestType.class);
        int standing = 0;
        int giants = 0;
        double closest = Double.POSITIVE_INFINITY;
        for (int[] p : PLACES) {
            List<TreeSpec> trees = w.trees(p[0] - 160, p[1] - 160, 320);
            // The spacing rule holds between trees of the main lattice (shrubs keep their own, smaller distances).
            List<TreeSpec> stems = trees.stream().filter(t -> !t.form().lowGrowth() && t.key() >>> 58 == 2).toList();
            for (TreeSpec t : trees) {
                woods.computeIfAbsent(t.type(), k -> new EnumMap<>(Wood.class)).merge(t.wood(), 1, Integer::sum);
            }
            for (int a = 0; a < stems.size(); a++) {
                TreeSpec t = stems.get(a);
                standing++;
                giants += w.model.shape(t).height >= 22 ? 1 : 0;
                for (int b = a + 1; b < stems.size(); b++) {
                    TreeSpec u = stems.get(b);
                    double d = Math.hypot(t.x() - u.x(), t.z() - u.z());
                    closest = Math.min(closest, d);
                    assertTrue(d >= 0.5 * (t.form().spacing + u.form().spacing) - 1e-9, t + " too close to " + u);
                }
            }
        }
        Vastlands.LOGGER.info("ForestModelTest: {} standing trees, {} of 22+ blocks, closest pair {}; woods by community {}", standing, giants, closest, woods);
        assertTrue(giants > 0 && giants < standing / 5, "giants " + giants + " of " + standing);
        // Communities keep their character.
        Map<Wood, Integer> dark = woods.getOrDefault(ForestType.DARK, Map.of());
        assertTrue(dark.getOrDefault(Wood.DARK_OAK, 0) > dark.values().stream().mapToInt(Integer::intValue).sum() / 2, "dark forest " + dark);
        Map<Wood, Integer> spruce = woods.getOrDefault(ForestType.OLD_SPRUCE, Map.of());
        assertTrue(spruce.getOrDefault(Wood.SPRUCE, 0) > spruce.values().stream().mapToInt(Integer::intValue).sum() * 0.9, "old growth taiga " + spruce);
        Map<Wood, Integer> jungle = woods.getOrDefault(ForestType.JUNGLE, Map.of());
        assertTrue(jungle.getOrDefault(Wood.JUNGLE, 0) > jungle.values().stream().mapToInt(Integer::intValue).sum() / 2, "jungle " + jungle);
    }

    @Test
    void densityFollowsBiomesClimateAndWater() {
        World w = new World();
        PreviewForestBiomes biomes = new PreviewForestBiomes(w.systems);
        Map<String, double[]> byBiome = new ConcurrentHashMap<>();
        int unmanagedWithTrees = 0;
        int cold = 0;
        for (int x = -8000; x <= 8000; x += 320) {
            for (int z = -8000; z <= 8000; z += 320) {
                int ground = (int) Math.ceil(w.systems.surfaceHeight(x, z)) - 1;
                if (ground < 63) {
                    continue;
                }
                String biome = biomes.biome(x, ground, z).location().getPath();
                ForestSample s = w.model.sample(x, z, false);
                double[] acc = byBiome.computeIfAbsent(biome, k -> new double[2]);
                acc[0] += s.density();
                acc[1]++;
                if (biomes.category(x, ground, z) == ForestModel.NONE && s.share() <= 0.0) {
                    // Deep inside unmanaged biomes (deserts, peaks, oceans' coasts) there is no forest at all.
                    unmanagedWithTrees += s.density() > 0.0 ? 1 : 0;
                }
                cold += s.treeline() < 0.01 && s.density() > 0.0 ? 1 : 0;
            }
        }
        StringBuilder text = new StringBuilder();
        byBiome.entrySet().stream().sorted((a, b) -> Double.compare(b.getValue()[1], a.getValue()[1])).limit(24)
                .forEach(e -> text.append(String.format(java.util.Locale.ROOT, " %s %.2f (%d)", e.getKey(), e.getValue()[0] / e.getValue()[1], (int) e.getValue()[1])));
        Vastlands.LOGGER.info("ForestModelTest: mean density by surface biome:{}", text);
        assertEquals(0, unmanagedWithTrees, "forest density inside unmanaged biomes");
        assertEquals(0, cold, "forest beyond the treeline");
        double forest = mean(byBiome, "forest");
        double dark = mean(byBiome, "dark_forest");
        double plains = mean(byBiome, "plains");
        double taiga = mean(byBiome, "taiga");
        assertTrue(dark > 0.4 && forest > 0.35 && taiga > 0.35, "closed forests too thin: dark " + dark + " forest " + forest + " taiga " + taiga);
        assertTrue(plains < 0.15, "plains too wooded: " + plains);
        // Deserts get no forest of their own: only a little density near borders with forest biomes (no tree roots there).
        assertTrue(mean(byBiome, "desert") < 0.03, "forest in deserts: " + mean(byBiome, "desert"));

        // Riparian woods: open land is more wooded right beside rivers than away from them.
        double[] near = new double[2];
        double[] far = new double[2];
        for (int x = -4096; x <= 4096; x += 23) {
            for (int z = -4096; z <= 4096; z += 211) {
                ForestSample s = w.model.sample(x, z, false);
                if (s.type() == ForestType.GRASSLAND || s.type() == ForestType.SAVANNA) {
                    double[] acc = s.riparian() > 0.5 ? near : far;
                    acc[0] += s.density();
                    acc[1]++;
                }
            }
        }
        Vastlands.LOGGER.info("ForestModelTest: open land density beside water {} ({}), away {} ({})", near[0] / near[1], (int) near[1], far[0] / far[1], (int) far[1]);
        assertTrue(near[1] > 10 && near[0] / near[1] > 2.0 * far[0] / far[1], "no riparian woods on open land");
    }

    private static double mean(Map<String, double[]> byBiome, String biome) {
        double[] acc = byBiome.get(biome);
        return acc == null || acc[1] == 0 ? 0.0 : acc[0] / acc[1];
    }
}
