package com.sincerelyuq.vastlands.worldgen.nature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sincerelyuq.vastlands.Vastlands;
import com.sincerelyuq.vastlands.worldgen.compat.ModBlock;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The formations of Sincerelyuq's Biomes' biomes (Agent 7) in made-up caves and ground: lumen
 * mushrooms always fit their cave whole (cap included) or are left out, pools hold their fluid
 * (rock or more pool beside and under every fluid cell), fissures cut only into the ground with a
 * bottom under every cut, vents carry the eruptive block at their top only, crystals stay in the
 * open cave, and every kind gives the same shape twice.
 */
class IntegrationShapesTest {
    /** A cave room {@code height} blocks high over a floor at y 0 (open y 0..height-1), its ceiling waved by {@code wave}. */
    private record Room(int height, int wave, int half) implements FormationShapes.World {
        @Override
        public int groundTop(int x, int z) {
            return 63;
        }

        @Override
        public int waterTop(int x, int z) {
            return Integer.MIN_VALUE;
        }

        @Override
        public boolean isCave(int x, int y, int z) {
            if (Math.abs(x) > this.half || Math.abs(z) > this.half || y < 0) {
                return false;
            }
            int top = this.height + (int) Math.round(this.wave * Math.sin(x * 0.31) * Math.cos(z * 0.27));
            return y < top;
        }
    }

    private static FormationSpec spec(FormationKind kind, int y, double height, double radius, int span, int flags, Rock rock, long seed) {
        return new FormationSpec(seed * 31 + kind.ordinal(), kind, 0, 0, y, height, radius, span, 0.7, rock, flags, seed);
    }

    private static boolean isMod(int code, ModBlock block) {
        return FormationBlocks.kind(code) == FormationBlocks.MOD && FormationBlocks.modBlock(code) == block;
    }

    private static boolean snapped(int code) {
        return FormationBlocks.has(code, FormationBlocks.SNAP_FLOOR) || FormationBlocks.has(code, FormationBlocks.SNAP_CEILING);
    }

    @Test
    void lumenMushroomsFitTheirCaveOrAreLeftOut() {
        Random random = new Random(7);
        Map<FormationKind, int[]> built = new EnumMap<>(FormationKind.class);
        for (int k = 0; k < 600; k++) {
            FormationKind kind = k % 3 == 0 ? FormationKind.LUMEN_GIANT : (k % 3 == 1 ? FormationKind.LUMEN_MUSHROOM : FormationKind.LUMEN_COLONY);
            Room room = new Room(4 + random.nextInt(30), random.nextInt(4), 10 + random.nextInt(14));
            double height = kind == FormationKind.LUMEN_GIANT ? 10 + random.nextDouble() * 20 : 3 + random.nextDouble() * 10;
            double radius = kind == FormationKind.LUMEN_GIANT ? 5 + random.nextDouble() * 7 : 1.5 + random.nextDouble() * 4;
            FormationSpec spec = spec(kind, 0, height, radius, room.height(), 0, Rock.STONE, random.nextLong());
            FormationShape shape = FormationShapes.shape(spec, room);
            int stems = 0;
            for (int i = 0; i < shape.size(); i++) {
                int code = shape.codes[i];
                int p = shape.positions[i];
                if (snapped(code) || FormationBlocks.has(code, FormationBlocks.EMBED)) {
                    continue;
                }
                int dx = FormationShape.dx(p);
                int dy = FormationShape.dy(p);
                int dz = FormationShape.dz(p);
                boolean lumen = isMod(code, ModBlock.LUMEN_STEM) || isMod(code, ModBlock.LUMEN_CAP) || isMod(code, ModBlock.LUMEN_GILLS)
                        || isMod(code, ModBlock.LUMEN_BULB);
                if (lumen) {
                    assertTrue(room.isCave(dx, dy, dz), kind + " block outside its cave at " + dx + " " + dy + " " + dz + " (room " + room + ")");
                    assertTrue(Math.abs(dx) <= kind.reach && Math.abs(dz) <= kind.reach, kind + " beyond its reach");
                }
                stems += isMod(code, ModBlock.LUMEN_STEM) ? 1 : 0;
            }
            built.computeIfAbsent(kind, x -> new int[2])[stems > 0 ? 0 : 1]++;
            assertTrue(FormationShapes.shape(spec, room).size() == shape.size(), kind + " not the same twice");
        }
        Vastlands.LOGGER.info("IntegrationShapesTest: mushrooms built / left out {}", built.entrySet().stream()
                .map(e -> e.getKey() + " " + e.getValue()[0] + "/" + e.getValue()[1]).toList());
        built.forEach((kind, n) -> assertTrue(n[0] > n[1], kind + " left out too often: " + n[0] + " built, " + n[1] + " not"));
    }

    @Test
    void poolsHoldTheirFluid() {
        Random random = new Random(11);
        int pools = 0;
        for (int k = 0; k < 400; k++) {
            Room room = new Room(6 + random.nextInt(10), random.nextInt(3), 4 + random.nextInt(10));
            boolean spinel = k % 2 == 0;
            FormationSpec spec = spec(FormationKind.POOL, 0, 1 + random.nextInt(2), 2.5 + random.nextDouble() * 4, room.height(),
                    spinel ? FormationSpec.SPINEL : 0, spinel ? Rock.AMETHYST : Rock.STONE, random.nextLong());
            FormationShape shape = FormationShapes.shape(spec, room);
            IntOpenHashSet fluid = new IntOpenHashSet();
            for (int i = 0; i < shape.size(); i++) {
                if (FormationBlocks.kind(shape.codes[i]) == FormationBlocks.POOL) {
                    fluid.add(shape.positions[i]);
                }
            }
            if (fluid.isEmpty()) {
                continue;
            }
            pools++;
            for (int p : fluid) {
                int dx = FormationShape.dx(p);
                int dy = FormationShape.dy(p);
                int dz = FormationShape.dz(p);
                assertTrue(dy < 0, "pool fluid above the floor");
                assertFalse(room.isCave(dx, dy, dz), "pool fluid in open cave (it replaces ground only)");
                int[][] around = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};
                for (int[] n : around) {
                    int q = FormationShape.pack(dx + n[0], dy + n[1], dz + n[2]);
                    assertTrue(fluid.contains(q) || !room.isCave(dx + n[0], dy + n[1], dz + n[2]),
                            "pool spills at " + (dx + n[0]) + " " + (dy + n[1]) + " " + (dz + n[2]));
                }
            }
        }
        assertTrue(pools > 200, "too few pools: " + pools);
    }

    @Test
    void fissuresCutOnlyIntoTheGround() {
        Random random = new Random(13);
        Room flat = new Room(0, 0, 0);
        for (int k = 0; k < 300; k++) {
            FormationSpec spec = new FormationSpec(k, FormationKind.FISSURE, 0, 0, 64, 2 + random.nextDouble() * 2.5, 8 + random.nextDouble() * 5,
                    1 + random.nextInt(3), random.nextDouble() * Math.PI, Rock.BASALT, 0, random.nextLong());
            FormationShape shape = FormationShapes.shape(spec, flat);
            Map<Long, int[]> columns = new java.util.HashMap<>();
            int vents = 0;
            for (int i = 0; i < shape.size(); i++) {
                int code = shape.codes[i];
                int p = shape.positions[i];
                int dy = FormationShape.dy(p);
                long column = (long) FormationShape.dx(p) << 32 ^ (FormationShape.dz(p) & 0xFFFFFFFFL);
                if (FormationBlocks.kind(code) == FormationBlocks.CUT) {
                    assertTrue(dy <= -1, "a cut above the ground");
                    columns.computeIfAbsent(column, c -> new int[] {Integer.MAX_VALUE})[0] = Math.min(columns.get(column)[0], dy);
                }
                vents += isMod(code, ModBlock.ASH_VENT) ? 1 : 0;
            }
            assertTrue(!columns.isEmpty(), "a fissure without a cut");
            assertTrue(vents <= 3, "too many vents");
            // Under the lowest cut of every column: magma or blackstone, never nothing.
            for (Map.Entry<Long, int[]> e : columns.entrySet()) {
                int dx = (int) (e.getKey() >> 32);
                int dz = (int) (long) e.getKey();
                int below = FormationShape.pack(dx, e.getValue()[0] - 1, dz);
                boolean floored = false;
                for (int i = 0; i < shape.size(); i++) {
                    if (shape.positions[i] == below) {
                        int kind = FormationBlocks.kind(shape.codes[i]);
                        floored = kind == FormationBlocks.MAGMA || kind == FormationBlocks.BLACKSTONE;
                    }
                }
                assertTrue(floored, "a cut without a bottom at " + dx + " " + dz);
            }
        }
    }

    @Test
    void ventsAndCrystalsKeepTheirRules() {
        Random random = new Random(17);
        for (int k = 0; k < 300; k++) {
            Room room = new Room(12 + random.nextInt(50), random.nextInt(4), 14 + random.nextInt(10));
            FormationKind kind = switch (k % 4) {
                case 0 -> FormationKind.MAGMA_VENT;
                case 1 -> FormationKind.CRYSTAL_SPIRES;
                case 2 -> FormationKind.CRYSTAL_GARDEN;
                default -> FormationKind.GIANT_CRYSTAL;
            };
            double height = kind == FormationKind.GIANT_CRYSTAL ? 20 + random.nextDouble() * 28 : 2 + random.nextDouble() * 10;
            double radius = kind == FormationKind.GIANT_CRYSTAL ? 3.5 + random.nextDouble() * 3 : 2 + random.nextDouble() * 4;
            int flags = kind != FormationKind.MAGMA_VENT && random.nextBoolean() ? FormationSpec.CEILING : 0;
            Rock rock = kind == FormationKind.MAGMA_VENT ? Rock.MAGMATIC : Rock.AMETHYST;
            FormationSpec spec = spec(kind, 0, height, radius, room.height() - 1, flags, rock, random.nextLong());
            FormationShape shape = FormationShapes.shape(spec, room);
            int top = Integer.MIN_VALUE;
            int smoldering = 0;
            for (int i = 0; i < shape.size(); i++) {
                int code = shape.codes[i];
                int p = shape.positions[i];
                if (isMod(code, ModBlock.SMOLDERING_MAGMA)) {
                    smoldering++;
                }
                if (FormationBlocks.isSolid(code)) {
                    top = Math.max(top, FormationShape.dy(p));
                }
                if (isMod(code, ModBlock.AMETHYST_SPIKE) && !snapped(code)) {
                    assertTrue(room.isCave(FormationShape.dx(p), FormationShape.dy(p), FormationShape.dz(p)), kind + " spike in rock");
                }
            }
            if (kind == FormationKind.MAGMA_VENT) {
                assertEquals(1, smoldering, "a vent's smoldering magma");
                for (int i = 0; i < shape.size(); i++) {
                    if (isMod(shape.codes[i], ModBlock.SMOLDERING_MAGMA)) {
                        assertEquals(top, FormationShape.dy(shape.positions[i]), "smoldering magma below the vent's top");
                    }
                }
            } else {
                assertEquals(0, smoldering, kind + " with smoldering magma");
            }
        }
    }
}
