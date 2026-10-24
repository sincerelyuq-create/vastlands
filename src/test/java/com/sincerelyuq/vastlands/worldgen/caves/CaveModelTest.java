package com.sincerelyuq.vastlands.worldgen.caves;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sincerelyuq.vastlands.Vastlands;
import com.sincerelyuq.vastlands.worldgen.WorldHeight;
import com.sincerelyuq.vastlands.worldgen.hydrology.WaterColumn;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSetup;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSystems;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The cave model without the game: determinism, fluids that vanilla's rules leave in place, river
 * and lake beds kept intact, sea caves, deep lava, volume and the rarity of large features.
 * Places are from seed 20261018 (found with {@code ./gradlew cavePreview}).
 */
class CaveModelTest {
    private static final long SEED = 20261018L;
    private static GenerationSystems systems;
    private static VastlandsCaves caves;
    private static CaveVolume volume;

    @BeforeAll
    static void setUp() {
        systems = GenerationSystems.create(new GenerationSetup(SEED, WorldHeight.MIN_Y, WorldHeight.HEIGHT, 63));
        caves = (VastlandsCaves) systems.caves();
        volume = new CaveVolume(systems, caves);
    }

    /** Two worlds of the same seed agree block for block, whatever thread and order asks. */
    @Test
    void sameSeedSameCavesFromAnyThreadAndOrder() {
        GenerationSystems other = GenerationSystems.create(new GenerationSetup(SEED, WorldHeight.MIN_Y, WorldHeight.HEIGHT, 63));
        VastlandsCaves second = (VastlandsCaves) other.caves();
        Random random = new Random(7);
        List<int[]> points = new ArrayList<>();
        for (int i = 0; i < 20000; i++) {
            int x = random.nextInt(4096) - 2048;
            int z = random.nextInt(4096) - 2048;
            points.add(new int[] {x, -240 + random.nextInt(400), z});
        }
        int[] expected = points.stream().mapToInt(p -> code(caves, p)).toArray();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            order.add(i);
        }
        Collections.shuffle(order, new Random(11));
        int[] actual = new int[points.size()];
        order.parallelStream().forEach(i -> actual[i] = code(second, points.get(i)));
        int open = 0;
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], "differs at " + points.get(i)[0] + " " + points.get(i)[1] + " " + points.get(i)[2]);
            open += expected[i] > 0 ? 1 : 0;
        }
        assertTrue(open > 100, "too few cave blocks to compare: " + open);
    }

    private static int code(VastlandsCaves caves, int[] p) {
        if (caves.isRoof(p[0], p[1], p[2])) {
            return -1;
        }
        return caves.isCave(p[0], p[1], p[2]) ? 1 + caves.caveFluid(p[0], p[1], p[2]).ordinal() : 0;
    }

    /**
     * No cave fluid that vanilla would move: no water or lava beside or above open air, no water
     * touching lava, no sea or river water touching dry cave space. On land, at sea, in a lava
     * region, on a coast, at a natural bridge, at a swallet and at a natural tunnel.
     */
    @Test
    void cavesHoldTheirFluids() {
        int[][] places = {{0, 0}, {330, -210}, {250, 450}, {430, 120}, {-300, 70}, {-319, 3106}, {-6584, 608}};
        long water = 0;
        long lava = 0;
        long air = 0;
        for (int[] place : places) {
            CaveVolume.Check check = volume.check(place[0] - 48, place[1] - 48, 96, 96);
            Vastlands.LOGGER.info("CaveModelTest: {} at {},{}", check, place[0], place[1]);
            assertEquals(0, check.unstable(), "unstable fluids at " + place[0] + "," + place[1] + ": " + check);
            water += check.caveWater();
            lava += check.caveLava();
            air += check.caveAir();
        }
        assertTrue(water > 10000 && lava > 1000 && air > 50000, "too little cave space to judge: water " + water + " lava " + lava + " air " + air);
    }

    /** No cave space under the water of a river or lake within its clearance (swallets aside). */
    @Test
    void riverAndLakeBedsStayIntact() {
        int checked = 0;
        for (int[] place : new int[][] {{-200, 2700}, {-800, 3330}, {600, 1180}}) {
            for (int dx = -96; dx < 96; dx++) {
                for (int dz = -96; dz < 96; dz++) {
                    int x = place[0] + dx;
                    int z = place[1] + dz;
                    WaterColumn water = systems.hydrology().water(x, z);
                    if (!water.placed() || !"NONE".equals(caves.swalletPart(x, z))) {
                        continue;
                    }
                    checked++;
                    for (int y = water.groundTop() - CaveModel.RIVER_CLEARANCE + 1; y <= water.waterTop(); y++) {
                        assertFalse(caves.isCave(x, y, z), "cave under river water at " + x + " " + y + " " + z);
                        assertFalse(caves.isRoof(x, y, z), "roof in river water at " + x + " " + y + " " + z);
                    }
                }
            }
        }
        assertTrue(checked > 3000, "too few river columns: " + checked);
    }

    /** Coastal caves are flooded to sea level and some open into the sea; deep lava only. */
    @Test
    void seaCavesAndDeepLava() {
        long openings = 0;
        long lavaAboveLimit = 0;
        for (int[] place : new int[][] {{330, -210}, {430, 120}}) {
            int x0 = place[0] - 48;
            int z0 = place[1] - 48;
            for (int i = 1; i < 95; i++) {
                for (int j = 1; j < 95; j++) {
                    int x = x0 + i;
                    int z = z0 + j;
                    double surface = volume.surface(x, z);
                    for (int y = -60; y < Math.min(62, surface); y++) {
                        if (volume.block(x, y, z, Integer.MIN_VALUE) != CaveVolume.Block.CAVE_WATER) {
                            continue;
                        }
                        if (volume.block(x + 1, y, z, Integer.MIN_VALUE) == CaveVolume.Block.SEA
                                || volume.block(x - 1, y, z, Integer.MIN_VALUE) == CaveVolume.Block.SEA
                                || volume.block(x, y, z + 1, Integer.MIN_VALUE) == CaveVolume.Block.SEA
                                || volume.block(x, y, z - 1, Integer.MIN_VALUE) == CaveVolume.Block.SEA
                                || volume.block(x, y + 1, z, Integer.MIN_VALUE) == CaveVolume.Block.SEA) {
                            openings++;
                        }
                    }
                }
            }
        }
        for (int x = -2048; x < 2048; x += 37) {
            for (int z = -2048; z < 2048; z += 41) {
                for (int y = -54; y < 140; y += 2) {
                    if (caves.isCave(x, y, z) && caves.caveFluid(x, y, z) == CaveFluid.LAVA) {
                        lavaAboveLimit++;
                    }
                }
            }
        }
        Vastlands.LOGGER.info("CaveModelTest: {} cave water blocks open to the sea, {} lava blocks above Y -54", openings, lavaAboveLimit);
        assertTrue(openings > 20, "no flooded caves open into the sea: " + openings);
        assertEquals(0, lavaAboveLimit, "cave lava above Y -54");
    }

    /**
     * Caves fill a small share of the rock (with solid regions between), large chambers are rare
     * and enormous caverns rarer, and every kind of piece exists.
     */
    @Test
    void volumeAndRarity() {
        int columns = 4000;
        long[] counts = new long[2];
        long[] quietRegions = new long[1];
        Random random = new Random(3);
        int[][] samples = new int[columns][2];
        for (int i = 0; i < columns; i++) {
            samples[i][0] = random.nextInt(32768) - 16384;
            samples[i][1] = random.nextInt(32768) - 16384;
        }
        long[] perColumn = new long[columns];
        IntStream.range(0, columns).parallel().forEach(i -> {
            int x = samples[i][0];
            int z = samples[i][1];
            double surface = systems.surfaceHeight(x, z);
            long open = 0;
            for (int y = -240; y < surface - 6; y++) {
                open += caves.isCave(x, y, z) ? 1 : 0;
            }
            perColumn[i] = open;
            synchronized (counts) {
                counts[0] += open;
                counts[1] += (long) Math.max(0, surface - 6 + 240);
            }
        });
        for (long open : perColumn) {
            quietRegions[0] += open == 0 ? 1 : 0;
        }
        double share = (double) counts[0] / counts[1];
        Vastlands.LOGGER.info("CaveModelTest: cave share {} of the rock, {} of {} columns without caves", share, quietRegions[0], columns);
        assertTrue(share > 0.012 && share < 0.08, "cave share " + share);
        assertTrue(quietRegions[0] > columns / 5, "too few columns of solid rock: " + quietRegions[0]);

        // Pieces of the network over 8 x 8 cells of 64 blocks around several places.
        int chambers = 0;
        int large = 0;
        int adits = 0;
        int funnels = 0;
        int segments = 0;
        for (int[] place : new int[][] {{0, 0}, {8000, -3000}, {-6000, 9000}, {15000, 15000}}) {
            for (int i = -4; i < 4; i++) {
                for (int j = -4; j < 4; j++) {
                    CaveNetwork.Cell cell = caves.model().network.cell(place[0] / 64 + i, place[1] / 64 + j);
                    for (CavePrimitive p : cell.primitives) {
                        if (p.type == CavePrimitive.CHAMBER) {
                            chambers++;
                            large += p.ra >= 30.0 ? 1 : 0;
                        } else if (p.funnelTop == p.funnelTop) {
                            funnels++;
                        } else if (p.ignoresCover) {
                            adits++;
                        } else {
                            segments++;
                        }
                    }
                }
            }
        }
        Vastlands.LOGGER.info("CaveModelTest: 256 cells: {} tunnel pieces, {} chambers ({} of radius 30+), {} adits, {} dolines", segments, chambers, large, adits, funnels);
        assertTrue(segments > 500, "too few tunnels: " + segments);
        assertTrue(chambers > 10 && large * 5 < chambers, "chambers " + chambers + ", large " + large);
        assertTrue(adits > 0, "no entrances");
        // Enormous caverns: some, but in under half of the 640-block cells.
        int caverns = 0;
        int cells = 0;
        Caverns.Section section = new Caverns.Section();
        for (int i = -10; i < 10; i++) {
            for (int j = -10; j < 10; j++) {
                cells++;
                boolean found = false;
                for (int k = 0; k < 9 && !found; k++) {
                    caves.model().caverns.section(i * 640 + 320 + (k % 3 - 1) * 60, j * 640 + 320 + (k / 3 - 1) * 60, section);
                    found = section.present();
                }
                caverns += found ? 1 : 0;
            }
        }
        Vastlands.LOGGER.info("CaveModelTest: enormous caverns found in {} of {} cells", caverns, cells);
        assertTrue(caverns > 0 && caverns < cells / 2, "caverns " + caverns + " of " + cells);
    }

    /** River connections exist near the origin's region and keep the river water untouched. */
    @Test
    void riverConnections() {
        assertTrue(caves.roofStrength(-309, 77) > 0.5 || caves.roofStrength(-300, 70) > 0.5, "the natural bridge near -300,70 is gone");
        List<int[]> swallets = caves.swalletsNear(-319, 3106);
        assertFalse(swallets.isEmpty(), "the swallet near -319,3106 is gone");
        for (int[] s : swallets) {
            // The shaft reaches the cave water below the river.
            assertTrue(s[3] < s[2] - 10, "shallow swallet " + s[2] + " -> " + s[3]);
        }
    }
}
