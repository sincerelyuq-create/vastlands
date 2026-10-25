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
import org.junit.jupiter.api.AfterAll;
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

    /** Lets the models and their caches go: the test JVM runs the other test classes after this one. */
    @AfterAll
    static void tearDown() {
        systems = null;
        caves = null;
        volume = null;
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
     * touching lava, no sea or river water touching dry cave space (designed spillways aside). On
     * land, at sea, in a lava region, on a coast, at a natural bridge, at a swallet and at a natural
     * tunnel; and (Agent 4.5) at megacaves holding lakes with cenotes open to the sky, a flooded
     * one under the coast, a lava megacave, a dry canyon, and underground rivers falling over dams.
     */
    @Test
    void cavesHoldTheirFluids() {
        int[][] places = {{0, 0}, {330, -210}, {250, 450}, {430, 120}, {-300, 70}, {-319, 3106}, {-6584, 608},
                {-2314, -2353}, {716, 448}, {-1794, 1648}, {-464, -1541}, {-236, -1832}, {-1343, -680}, {-704, 224}, {-500, 330}};
        long water = 0;
        long lava = 0;
        long air = 0;
        long spills = 0;
        for (int[] place : places) {
            CaveVolume.Check check = volume.check(place[0] - 48, place[1] - 48, 96, 96);
            Vastlands.LOGGER.info("CaveModelTest: {} at {},{}", check, place[0], place[1]);
            assertEquals(0, check.unstable(), "unstable fluids at " + place[0] + "," + place[1] + ": " + check);
            water += check.caveWater();
            lava += check.caveLava();
            air += check.caveAir();
            spills += check.spillways();
        }
        assertTrue(water > 100000 && lava > 10000 && air > 1000000, "too little cave space to judge: water " + water + " lava " + lava + " air " + air);
        assertTrue(spills > 20, "underground rivers should fall over spillways: " + spills);
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

    /**
     * Sea caves: the entrances of the network with their mouth at the sea in a square of coast and
     * shelf (sea-cave adits and blue holes) open into the sea and hold their water. Deep lava only.
     */
    @Test
    void seaCavesAndDeepLava() {
        List<CaveVolume.SeaCave> seaCaves = volume.seaCaves(512, -2048, 1024);
        int open = 0;
        int sinkholes = 0;
        for (CaveVolume.SeaCave c : seaCaves) {
            assertEquals(0, c.check().unstable(), "unstable fluids at the sea cave at " + c.x() + "," + c.z() + ": " + c.check());
            open += c.check().seaOpenings() > 0 ? 1 : 0;
            sinkholes += c.sinkhole() ? 1 : 0;
        }
        long lavaAboveLimit = 0;
        for (int x = -2048; x < 2048; x += 37) {
            for (int z = -2048; z < 2048; z += 41) {
                for (int y = -54; y < 140; y += 2) {
                    if (caves.isCave(x, y, z) && caves.caveFluid(x, y, z) == CaveFluid.LAVA) {
                        lavaAboveLimit++;
                    }
                }
            }
        }
        Vastlands.LOGGER.info("CaveModelTest: {} sea caves with a mouth at the sea ({} blue holes), {} open to the sea; {} lava blocks above Y -54",
                seaCaves.size(), sinkholes, open, lavaAboveLimit);
        assertTrue(seaCaves.size() >= 10 && sinkholes > 0 && sinkholes < seaCaves.size(), "sea caves: " + seaCaves.size() + ", blue holes " + sinkholes);
        assertTrue(open * 5 >= seaCaves.size() * 4, "sea caves open to the sea: " + open + " of " + seaCaves.size());
        assertEquals(0, lavaAboveLimit, "cave lava above Y -54");
    }

    /**
     * Caves fill a share of the rock several times Agent 4's (with solid regions between), large
     * chambers are uncommon and megacaves rarer, galleries are wide, and every kind of piece exists.
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
        // Agent 4 measured 1.56% here; the overhaul aims at several times that, with solid regions left.
        assertTrue(share > 0.04 && share < 0.16, "cave share " + share);
        assertTrue(quietRegions[0] > columns / 10, "too few columns of solid rock: " + quietRegions[0]);

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
        assertTrue(segments > 1000, "too few tunnels: " + segments);
        assertTrue(chambers > 30 && large * 3 < chambers && large > 0, "chambers " + chambers + ", large " + large);
        assertTrue(adits > 0, "no entrances");
        // Megacaves: some in each zone, but in under two thirds of the 1 024-block cells.
        int[] perZone = new int[2];
        int cells = 400;
        for (Megacaves.Megacave m : caves.model().megacaves.near(-10 * 1024, -10 * 1024, 10 * 1024 - 1, 10 * 1024 - 1)) {
            perZone[m.zone]++;
        }
        Vastlands.LOGGER.info("CaveModelTest: megacaves around 20 x 20 cells of 1 024 blocks: {} upper, {} deep", perZone[0], perZone[1]);
        assertTrue(perZone[0] > 20 && perZone[0] < cells * 2 / 3 + 40, "upper megacaves " + perZone[0]);
        assertTrue(perZone[1] > 20 && perZone[1] < cells * 2 / 3 + 40, "deep megacaves " + perZone[1]);
        // Galleries: large passages, major corridors and some colossal ones.
        java.util.Map<String, Integer> census = new java.util.TreeMap<>();
        for (int[] place : new int[][] {{0, 0}, {8000, -3000}, {-6000, 9000}, {15000, 15000}}) {
            caves.census(place[0] - 1536, place[1] - 1536, place[0] + 1535, place[1] + 1535).forEach((k, v) -> census.merge(k, v, Integer::sum));
        }
        Vastlands.LOGGER.info("CaveModelTest: pieces over four 3 072-block squares: {}", census);
        assertTrue(census.getOrDefault("gallery pieces r<30", 0) > 200, "large galleries: " + census);
        assertTrue(census.getOrDefault("gallery pieces r30-55", 0) > 50, "major corridors: " + census);
        assertTrue(census.getOrDefault("gallery pieces r55+", 0) > 0, "colossal galleries: " + census);
        assertTrue(census.getOrDefault("river gallery pieces", 0) > 10, "underground rivers: " + census);
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
