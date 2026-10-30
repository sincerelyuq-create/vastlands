package com.sincerelyuq.vastlands.worldgen.nature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sincerelyuq.vastlands.Vastlands;
import com.sincerelyuq.vastlands.worldgen.WorldHeight;
import com.sincerelyuq.vastlands.worldgen.caves.CaveBiome;
import com.sincerelyuq.vastlands.worldgen.caves.VastlandsCaves;
import com.sincerelyuq.vastlands.worldgen.compat.ModBlock;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSetup;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSystems;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The bioluminescent depths as Agent 8 expanded them, in a world where Sincerelyuq's Biomes is
 * installed (expanded luminous caves; the mod's biome stands in the luminous provinces, the cave
 * slots elsewhere): lumen mushrooms of many sizes stand in groups, and every solid block of every
 * lumen formation is held face to face by rock, its foot or another block of it. No game classes:
 * the cave slots stand in for the biomes (the surface does not matter here). Places of seed
 * 20261018 rich in luminous caves.
 */
class LuminousNatureTest {
    private static final long SEED = 20261018L;
    private static final int[][] SQUARES = {{-6048, 3552}, {672, 6432}};
    private static final int SIZE = 256;
    private static final int[][] STEPS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    /** The mod's cave biomes in their slots, the bioluminescent depths in the luminous provinces. */
    private record ModCaves(VastlandsCaves caves) implements NatureModel.Biomes {
        @Override
        public int surfaceStyle(int x, int y, int z) {
            return SurfaceStyle.TEMPERATE.ordinal();
        }

        @Override
        public int caveProfile(int x, int y, int z) {
            CaveBiome slot = this.caves.caveBiome(x, y, z);
            if ((slot == CaveBiome.LUSH || slot == CaveBiome.DRIPSTONE) && this.caves.luminous(x, y, z)) {
                return CaveProfile.LUMINOUS.ordinal();
            }
            return switch (slot) {
                case CRYSTAL -> CaveProfile.AMETHYST.ordinal();
                case VOLCANIC -> CaveProfile.SMOLDERING.ordinal();
                case DRIPSTONE -> CaveProfile.DRIPSTONE.ordinal();
                case LUSH -> CaveProfile.LUSH.ordinal();
                case DEEP_DARK -> CaveProfile.DEEP_DARK.ordinal();
                default -> CaveProfile.PLAIN.ordinal();
            };
        }
    }

    @Test
    void lumenMushroomsStandInVariedGroupsAndHoldTogether() {
        GenerationSystems systems = GenerationSystems.create(new GenerationSetup(SEED, WorldHeight.MIN_Y, WorldHeight.HEIGHT, 63, true));
        VastlandsCaves caves = (VastlandsCaves) systems.caves();
        VastlandsNature nature = (VastlandsNature) systems.nature();
        nature.bind(this, new ModCaves(caves));
        NatureModel model = nature.model();
        FormationShapes.World world = model.world();
        Map<FormationKind, int[]> kinds = new EnumMap<>(FormationKind.class);
        List<Integer> heights = new ArrayList<>();
        int mushrooms = 0;
        int grouped = 0;
        int solids = 0;
        int loose = 0;
        for (int[] square : SQUARES) {
            int x0 = square[0] - SIZE / 2;
            int z0 = square[1] - SIZE / 2;
            List<FormationSpec> specs = new ArrayList<>();
            model.formations(x0, z0, x0 + SIZE - 1, z0 + SIZE - 1, false, spec -> {
                if (spec.x() >= x0 && spec.x() < x0 + SIZE && spec.z() >= z0 && spec.z() < z0 + SIZE
                        && (spec.kind().name().startsWith("LUMEN") || spec.kind() == FormationKind.POOL)) {
                    specs.add(spec);
                }
            });
            for (FormationSpec spec : specs) {
                FormationShape shape = model.shape(spec, world);
                if (shape.isEmpty()) {
                    continue;
                }
                kinds.computeIfAbsent(spec.kind(), k -> new int[1])[0]++;
                Set<Long> caps = new HashSet<>();
                Set<Long> solid = new HashSet<>();
                for (int k = 0; k < shape.size(); k++) {
                    int code = shape.codes[k];
                    int p = shape.positions[k];
                    long key = key(spec.x() + FormationShape.dx(p), spec.y() + FormationShape.dy(p), spec.z() + FormationShape.dz(p));
                    if (FormationBlocks.kind(code) == FormationBlocks.MOD
                            && (FormationBlocks.modBlock(code) == ModBlock.LUMEN_CAP || FormationBlocks.modBlock(code) == ModBlock.LUMEN_GILLS)) {
                        caps.add(key);
                    }
                    if (FormationBlocks.isSolid(code) && !FormationBlocks.has(code, FormationBlocks.SNAP_FLOOR)
                            && !FormationBlocks.has(code, FormationBlocks.SNAP_CEILING)) {
                        solid.add(key);
                    }
                }
                int count = 0;
                Set<Long> seen = new HashSet<>();
                for (long c0 : caps) {
                    if (!seen.add(c0)) {
                        continue;
                    }
                    count++;
                    int top = Integer.MIN_VALUE;
                    ArrayDeque<Long> queue = new ArrayDeque<>(List.of(c0));
                    while (!queue.isEmpty()) {
                        int[] v = unkey(queue.poll());
                        top = Math.max(top, v[1]);
                        for (int[] st : STEPS) {
                            long n = key(v[0] + st[0], v[1] + st[1], v[2] + st[2]);
                            if (caps.contains(n) && seen.add(n)) {
                                queue.add(n);
                            }
                        }
                    }
                    heights.add(top - spec.y());
                }
                if (spec.kind() == FormationKind.LUMEN_MUSHROOM) {
                    mushrooms++;
                    grouped += count >= 2 ? 1 : 0;
                }
                solids += solid.size();
                loose += solid.size() - held(spec, shape, world, solid);
            }
        }
        Collections.sort(heights);
        int p10 = heights.get(heights.size() / 10);
        int p90 = heights.get(heights.size() * 9 / 10);
        int max = heights.get(heights.size() - 1);
        StringBuilder report = new StringBuilder();
        kinds.forEach((k, n) -> report.append(k).append(' ').append(n[0]).append(", "));
        Vastlands.LOGGER.info("LuminousNatureTest: {}{} caps (top above the root p10 {} p90 {} max {}), {} of {} mushrooms in groups, {} solid blocks, {} not held",
                report, heights.size(), p10, p90, max, grouped, mushrooms, solids, loose);
        assertTrue(mushrooms >= 40, "lumen mushrooms: " + mushrooms);
        assertTrue(kinds.containsKey(FormationKind.LUMEN_COLONY) && kinds.containsKey(FormationKind.LUMEN_GROWTH) && kinds.containsKey(FormationKind.POOL),
                "every lumen kind: " + report);
        int giants = kinds.getOrDefault(FormationKind.LUMEN_GIANT, new int[1])[0];
        assertTrue(giants * 5 <= mushrooms, "giant lumen mushrooms stay rare: " + giants + " of " + mushrooms);
        assertTrue(grouped * 10 >= mushrooms * 3, "lumen mushrooms in groups (companions): " + grouped + " of " + mushrooms);
        assertTrue(heights.size() >= 3 * mushrooms, "an understory of small mushrooms: " + heights.size() + " caps for " + mushrooms + " mushrooms");
        assertTrue(p90 - p10 >= 3 && max >= 12, "mushrooms of many sizes: p10 " + p10 + " p90 " + p90 + " max " + max);
        assertEquals(0, loose, "solid blocks of lumen formations held by nothing");
    }

    /** Blocks of a formation held face to face, through its own blocks, by natural rock or its feet and hangers. */
    private static int held(FormationSpec spec, FormationShape shape, FormationShapes.World world, Set<Long> solid) {
        Set<Long> held = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        for (long s : solid) {
            int[] v = unkey(s);
            boolean anchored = rock(world, v[0], v[1], v[2]);
            for (int[] st : STEPS) {
                anchored |= rock(world, v[0] + st[0], v[1] + st[1], v[2] + st[2]) && !solid.contains(key(v[0] + st[0], v[1] + st[1], v[2] + st[2]));
            }
            if (anchored && held.add(s)) {
                queue.add(s);
            }
        }
        ends(spec, shape.feet, shape.footDepths, -1, world, solid, held, queue);
        ends(spec, shape.hangers, shape.hangerHeights, 1, world, solid, held, queue);
        while (!queue.isEmpty()) {
            int[] v = unkey(queue.poll());
            for (int[] st : STEPS) {
                long n = key(v[0] + st[0], v[1] + st[1], v[2] + st[2]);
                if (solid.contains(n) && held.add(n)) {
                    queue.add(n);
                }
            }
        }
        return held.size();
    }

    private static void ends(FormationSpec spec, int[] at, int[] depths, int dir, FormationShapes.World world, Set<Long> solid, Set<Long> held,
                             ArrayDeque<Long> queue) {
        for (int n = 0; n < at.length; n++) {
            int x = spec.x() + FormationShape.dx(at[n]);
            int y = spec.y() + FormationShape.dy(at[n]);
            int z = spec.z() + FormationShape.dz(at[n]);
            long key = key(x, y, z);
            if (!solid.contains(key)) {
                continue;
            }
            for (int k = 1; k <= depths[n] + 1; k++) {
                if (rock(world, x, y + dir * k, z)) {
                    if (held.add(key)) {
                        queue.add(key);
                    }
                    break;
                }
            }
        }
    }

    private static boolean rock(FormationShapes.World world, int x, int y, int z) {
        return y <= world.groundTop(x, z) && !world.isCave(x, y, z);
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private static int[] unkey(long k) {
        return new int[] {(int) (k >> 38) << 6 >> 6, (int) (k << 52 >> 52), (int) ((k >> 12) & 0x3FFFFFF) << 6 >> 6};
    }
}
