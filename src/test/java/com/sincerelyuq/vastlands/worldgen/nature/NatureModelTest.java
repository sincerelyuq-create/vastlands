package com.sincerelyuq.vastlands.worldgen.nature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sincerelyuq.vastlands.Vastlands;
import com.sincerelyuq.vastlands.tools.PreviewForestBiomes;
import com.sincerelyuq.vastlands.tools.PreviewNatureBiomes;
import com.sincerelyuq.vastlands.worldgen.WorldHeight;
import com.sincerelyuq.vastlands.worldgen.forest.TreeShape;
import com.sincerelyuq.vastlands.worldgen.forest.VastlandsForests;
import com.sincerelyuq.vastlands.worldgen.hydrology.VastlandsHydrology;
import com.sincerelyuq.vastlands.worldgen.hydrology.WaterColumn;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSetup;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSystems;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Natural formations without the game: biomes from vanilla's table on the router's climate
 * ({@link PreviewNatureBiomes}), the real terrain, rivers and caves. Seed 20261018.
 */
class NatureModelTest {
    private static final long SEED = 20261018L;

    /**
     * Places: a dark forest by the sea, snowy peaks, badlands, a stony shore, a jungle river, the
     * dripstone caves under a plain.
     */
    private static final int[][] PLACES = {{288, 32}, {-11520, -10752}, {-12704, -11808}, {-8224, -8480}, {1728, 64}, {-2976, -1348}};

    private static final class World {
        final GenerationSystems systems;
        final NatureModel model;
        final VastlandsForests forests;

        World() {
            this.systems = GenerationSystems.create(new GenerationSetup(SEED, WorldHeight.MIN_Y, WorldHeight.HEIGHT, 63));
            VastlandsNature nature = (VastlandsNature) this.systems.nature();
            nature.bind(this, new PreviewNatureBiomes(this.systems));
            this.model = nature.model();
            this.forests = (VastlandsForests) this.systems.forests();
            this.forests.bind(this, new PreviewForestBiomes(this.systems));
        }

        List<FormationSpec> formations(int x0, int z0, int size, boolean surface) {
            List<FormationSpec> out = new ArrayList<>();
            this.model.formations(x0, z0, x0 + size - 1, z0 + size - 1, surface, s -> {
                if (s.x() >= x0 && s.x() < x0 + size && s.z() >= z0 && s.z() < z0 + size) {
                    out.add(s);
                }
            });
            return out;
        }
    }

    private static int layer(FormationSpec spec) {
        return (int) (spec.key() >>> 58) & 7;
    }

    private static String signature(NatureModel model, int cx, int cz) {
        StringBuilder out = new StringBuilder();
        for (boolean surface : new boolean[] {true, false}) {
            model.formations(cx << 4, cz << 4, (cx << 4) + 15, (cz << 4) + 15, surface, s -> {
                FormationShape shape = model.shape(s, model.world());
                out.append(s).append(Arrays.hashCode(shape.positions) * 31 + Arrays.hashCode(shape.codes)).append(Arrays.hashCode(shape.feet))
                        .append(Arrays.hashCode(shape.hangers)).append(';');
            });
        }
        return out.toString();
    }

    @Test
    void sameFormationsFromAnyThreadAndOrder() {
        World a = new World();
        World b = new World();
        List<int[]> chunks = new ArrayList<>();
        for (int[] p : PLACES) {
            for (int i = -4; i < 4; i++) {
                for (int j = -4; j < 4; j++) {
                    chunks.add(new int[] {(p[0] >> 4) + i, (p[1] >> 4) + j});
                }
            }
        }
        Map<String, String> first = new HashMap<>();
        for (int[] c : chunks) {
            first.put(c[0] + "," + c[1], signature(a.model, c[0], c[1]));
        }
        Collections.shuffle(chunks, new Random(7));
        AtomicInteger differ = new AtomicInteger();
        chunks.parallelStream().forEach(c -> differ.addAndGet(signature(b.model, c[0], c[1]).equals(first.get(c[0] + "," + c[1])) ? 0 : 1));
        assertEquals(0, differ.get(), "chunks whose formations depend on the query order or thread");
    }

    /**
     * What the block step may read: during features a region holds structure references 1 chunk
     * away and starts 8 away. Formations reaching at most 16 blocks read their root chunk's
     * references and starts 7 chunks from it; wider ones (root up to 2 chunks away) starts 6
     * chunks from it. A kind reaching beyond 32 blocks would need chunks no region has.
     */
    @Test
    void formationsStayWithinWhatADecoratingRegionCanRead() {
        for (FormationKind kind : FormationKind.values()) {
            assertTrue(kind.reach <= 32, kind + " reaches " + kind.reach + " blocks: its root could be 3 chunks from a chunk it reaches");
        }
        for (int layer = 0; layer < NatureModel.LAYERS; layer++) {
            assertTrue(NatureModel.reach(layer) <= 32, "layer " + layer + " reaches " + NatureModel.reach(layer));
        }
    }

    private static long key(int x, int y, int z) {
        return (long) x << 38 ^ (long) (z & 0x3FFFFFF) << 12 ^ (y & 0xFFF);
    }

    /**
     * Formations at real places stand on the ground or hang from the ceiling: every solid block
     * holds face to face, through the formation's own blocks, to natural rock (or to a foot or
     * hanger that reaches it); shapes stay within their kind's reach.
     */
    @Test
    void formationsRestOnTheGroundAndStayInReach() {
        World w = new World();
        FormationShapes.World world = w.model.world();
        int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        Map<FormationKind, int[]> byKind = new EnumMap<>(FormationKind.class);
        int formations = 0;
        int blocks = 0;
        int loose = 0;
        StringBuilder worst = new StringBuilder();
        for (int[] p : PLACES) {
            for (boolean surface : new boolean[] {true, false}) {
                for (FormationSpec spec : w.formations(p[0] - 96, p[1] - 96, 192, surface)) {
                    FormationShape shape = w.model.shape(spec, world);
                    if (shape.isEmpty()) {
                        continue;
                    }
                    formations++;
                    assertTrue(Math.max(Math.max(-shape.minX, shape.maxX), Math.max(-shape.minZ, shape.maxZ)) <= spec.kind().reach,
                            spec + ": reach " + shape.minX + ".." + shape.maxX + " x " + shape.minZ + ".." + shape.maxZ);
                    LongOpenHashSet solid = new LongOpenHashSet();
                    for (int k = 0; k < shape.size(); k++) {
                        if (FormationBlocks.isSolid(shape.codes[k]) && !FormationBlocks.has(shape.codes[k], FormationBlocks.SNAP_FLOOR)
                                && !FormationBlocks.has(shape.codes[k], FormationBlocks.SNAP_CEILING)) {
                            int q = shape.positions[k];
                            solid.add(key(spec.x() + FormationShape.dx(q), spec.y() + FormationShape.dy(q), spec.z() + FormationShape.dz(q)));
                        }
                    }
                    LongOpenHashSet held = new LongOpenHashSet();
                    ArrayDeque<int[]> queue = new ArrayDeque<>();
                    for (int k = 0; k < shape.size(); k++) {
                        int q = shape.positions[k];
                        int x = spec.x() + FormationShape.dx(q);
                        int y = spec.y() + FormationShape.dy(q);
                        int z = spec.z() + FormationShape.dz(q);
                        if (!solid.contains(key(x, y, z))) {
                            continue;
                        }
                        boolean anchored = rock(world, x, y, z);
                        for (int[] st : steps) {
                            anchored |= rock(world, x + st[0], y + st[1], z + st[2]) && !solid.contains(key(x + st[0], y + st[1], z + st[2]));
                        }
                        if (anchored && held.add(key(x, y, z))) {
                            queue.add(new int[] {x, y, z});
                        }
                    }
                    anchorEnds(spec, shape.feet, shape.footDepths, -1, world, solid, held, queue);
                    anchorEnds(spec, shape.hangers, shape.hangerHeights, 1, world, solid, held, queue);
                    while (!queue.isEmpty()) {
                        int[] c = queue.poll();
                        for (int[] st : steps) {
                            long n = key(c[0] + st[0], c[1] + st[1], c[2] + st[2]);
                            if (solid.contains(n) && held.add(n)) {
                                queue.add(new int[] {c[0] + st[0], c[1] + st[1], c[2] + st[2]});
                            }
                        }
                    }
                    int free = solid.size() - held.size();
                    int[] acc = byKind.computeIfAbsent(spec.kind(), k -> new int[3]);
                    acc[0]++;
                    acc[1] += solid.size();
                    acc[2] += free;
                    blocks += solid.size();
                    loose += free;
                    if (free > 0 && worst.length() < 1500) {
                        worst.append("\n  ").append(free).append(" of ").append(solid.size()).append(" loose: ").append(spec);
                    }
                }
            }
        }
        StringBuilder text = new StringBuilder();
        byKind.forEach((k, v) -> text.append(String.format(java.util.Locale.ROOT, " %s %d (%d blocks, %d loose)", k, v[0], v[1], v[2])));
        Vastlands.LOGGER.info("NatureModelTest: {} formations, {} solid blocks, {} not held:{}", formations, blocks, loose, text);
        assertTrue(formations > 1000, "too few formations: " + formations);
        assertEquals(0, loose, "solid blocks held by nothing:" + worst);
    }

    /** Natural rock: below the ground (the bed under water) and outside the caves. */
    private static boolean rock(FormationShapes.World world, int x, int y, int z) {
        return y <= world.groundTop(x, z) && !world.isCave(x, y, z);
    }

    /** Feet (hangers) whose extension reaches natural rock within their depth anchor their block. */
    private static void anchorEnds(FormationSpec spec, int[] at, int[] depths, int dir, FormationShapes.World world, LongOpenHashSet solid,
                                   LongOpenHashSet held, ArrayDeque<int[]> queue) {
        for (int n = 0; n < at.length; n++) {
            int x = spec.x() + FormationShape.dx(at[n]);
            int y = spec.y() + FormationShape.dy(at[n]);
            int z = spec.z() + FormationShape.dz(at[n]);
            if (!solid.contains(key(x, y, z))) {
                continue;
            }
            for (int k = 1; k <= depths[n] + 1; k++) {
                if (rock(world, x, y + dir * k, z)) {
                    if (held.add(key(x, y, z))) {
                        queue.add(new int[] {x, y, z});
                    }
                    break;
                }
            }
        }
    }

    /**
     * Surface formations keep off the water (but for shore stones, driftwood and islets), off cave
     * openings and away from waterfalls; cave formations stand on a cave floor (or hang from its
     * ceiling) with open space at their root.
     */
    @Test
    void formationsKeepOffWaterCavesAndFalls() {
        World w = new World();
        FormationShapes.World world = w.model.world();
        VastlandsHydrology hydrology = (VastlandsHydrology) w.systems.hydrology();
        int surface = 0;
        int caves = 0;
        for (int[] p : PLACES) {
            List<double[]> falls = new ArrayList<>();
            for (int rx = Math.floorDiv(p[0] - 512, 512); rx <= Math.floorDiv(p[0] + 512, 512); rx++) {
                for (int rz = Math.floorDiv(p[1] - 512, 512); rz <= Math.floorDiv(p[1] + 512, 512); rz++) {
                    falls.addAll(hydrology.model().falls(rx * 512, rz * 512));
                }
            }
            for (FormationSpec s : w.formations(p[0] - 256, p[1] - 256, 512, true)) {
                surface++;
                boolean shore = s.kind() == FormationKind.SHORE_STONES || s.kind() == FormationKind.DRIFTWOOD || s.kind() == FormationKind.ISLET;
                WaterColumn water = hydrology.water(s.x(), s.z());
                if (!shore) {
                    assertTrue(!water.wet(), s + " stands in " + water);
                }
                assertTrue(water.kind() != WaterColumn.Kind.RIVER || s.kind() != FormationKind.ISLET, s + " is an islet in a river");
                // Rock under the root (a cave may pass below, under a roof).
                assertTrue(!world.isCave(s.x(), s.y() - 1, s.z()) && !world.isCave(s.x(), s.y(), s.z()), s + " over a cave opening");
                for (double[] f : falls) {
                    assertTrue(Math.hypot(f[0] - s.x(), f[1] - s.z()) >= s.radius() + 8.0, s + " next to a fall at " + f[0] + " " + f[1]);
                }
            }
            for (FormationSpec s : w.formations(p[0] - 128, p[1] - 128, 256, false)) {
                caves++;
                int ceiling = s.y() + s.span() - 1;
                assertTrue(world.isCave(s.x(), s.y(), s.z()), s + ": root not in the cave");
                assertTrue(!world.isCave(s.x(), s.y() - 1, s.z()), s + ": no floor under the root");
                if (s.kind() != FormationKind.CAVE_ISLET) {
                    // (An islet's span is the depth of its pool.)
                    assertTrue(world.isCave(s.x(), ceiling, s.z()) && !world.isCave(s.x(), ceiling + 1, s.z()), s + ": span does not end at the ceiling");
                }
            }
        }
        Vastlands.LOGGER.info("NatureModelTest: {} surface and {} cave formations checked at {} places", surface, caves, PLACES.length);
        assertTrue(surface > 300 && caves > 1000, "too few formations: " + surface + " surface, " + caves + " in caves");
    }

    /** Small formations are common, medium ones fewer, large ones rare, wonders rarest. */
    @Test
    void rarityFollowsSize() {
        World w = new World();
        int[] surface = new int[4];
        int[] caves = new int[4];
        Map<FormationKind, Integer> wonders = new EnumMap<>(FormationKind.class);
        for (int x = -12288; x < 12288; x += 4096) {
            for (int z = -12288; z < 12288; z += 4096) {
                // Surface: 1 km squares on a 4 km grid over 24 x 24 km; caves: 256-block squares.
                for (FormationSpec s : w.formations(x, z, 1024, true)) {
                    surface[layer(s)]++;
                    if (layer(s) == NatureModel.S_WONDER) {
                        wonders.merge(s.kind(), 1, Integer::sum);
                    }
                }
                for (FormationSpec s : w.formations(x, z, 256, false)) {
                    caves[layer(s) - NatureModel.C_SMALL]++;
                }
            }
        }
        Vastlands.LOGGER.info("NatureModelTest: surface formations by size {}, caves {}, wonders {}", Arrays.toString(surface), Arrays.toString(caves), wonders);
        for (int k = 0; k < 3; k++) {
            assertTrue(surface[k] > surface[k + 1], "surface sizes out of order: " + Arrays.toString(surface));
            assertTrue(caves[k] > caves[k + 1], "cave sizes out of order: " + Arrays.toString(caves));
        }
        int sum = wonders.values().stream().mapToInt(Integer::intValue).sum();
        assertTrue(sum > 0 && sum <= 36, "wonders in 36 km2: " + wonders);
    }

    /** No tree grows into a surface formation: trunks keep clear of their solid blocks. */
    @Test
    void treesKeepClearOfFormations() {
        World w = new World();
        int trees = 0;
        int formations = 0;
        for (int[] p : PLACES) {
            int x0 = p[0] - 128;
            int z0 = p[1] - 128;
            LongOpenHashSet rock = new LongOpenHashSet();
            for (FormationSpec s : w.formations(x0 - 32, z0 - 32, 320, true)) {
                formations++;
                FormationShape shape = w.model.shape(s, w.model.world());
                for (int k = 0; k < shape.size(); k++) {
                    if (FormationBlocks.isSolid(shape.codes[k])) {
                        int q = shape.positions[k];
                        rock.add(key(s.x() + FormationShape.dx(q), s.y() + FormationShape.dy(q), s.z() + FormationShape.dz(q)));
                    }
                }
            }
            int[] count = new int[1];
            w.forests.model().trees(x0, z0, x0 + 255, z0 + 255, t -> {
                TreeShape shape = w.forests.model().shape(t);
                for (int k = 0; k < shape.size(); k++) {
                    int q = shape.positions[k];
                    if (com.sincerelyuq.vastlands.worldgen.forest.TreeBlocks.isLog(shape.codes[k])) {
                        long at = key(t.x() + TreeShape.dx(q), t.y() + TreeShape.dy(q), t.z() + TreeShape.dz(q));
                        assertTrue(!rock.contains(at), t + " grows into a formation at " + (t.x() + TreeShape.dx(q)) + " " + (t.y() + TreeShape.dy(q)) + " "
                                + (t.z() + TreeShape.dz(q)));
                    }
                }
                count[0]++;
            });
            trees += count[0];
        }
        Vastlands.LOGGER.info("NatureModelTest: {} trees beside {} surface formations, none grown into one", trees, formations);
        assertTrue(trees > 2000, "too few trees: " + trees);
    }
}
