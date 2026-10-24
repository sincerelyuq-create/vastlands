package com.sincerelyuq.vastlands.worldgen.hydrology;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sincerelyuq.vastlands.Vastlands;
import com.sincerelyuq.vastlands.worldgen.WorldHeight;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSetup;
import com.sincerelyuq.vastlands.worldgen.terrain.VastlandsTerrain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * The river network on its own: determinism and order independence, rivers running downhill to
 * the sea or a lake, water levels, widths, falls, and water that stays in its channels.
 */
class HydrologyModelTest {
    private static final long SEED = 20261018L;
    private static final GenerationSetup SETUP = new GenerationSetup(SEED, WorldHeight.MIN_Y, WorldHeight.HEIGHT, 63);
    /** Places measured with the preview: a 50-block fall off a plateau, broad lowland rivers, lakes. */
    private static final int[][] PLACES = {{-5925, 14778}, {-13312, 5888}, {512, 1536}, {25000, -25000}};

    private static VastlandsHydrology hydrology() {
        return new VastlandsHydrology(SETUP, new VastlandsTerrain(SETUP));
    }

    /** Distinct basins with rivers near the test places. */
    private static List<BasinRivers> basins(HydrologyModel model) {
        Set<BasinRivers> basins = new LinkedHashSet<>();
        for (int[] place : PLACES) {
            for (int dx = -2048; dx <= 2048; dx += 512) {
                for (int dz = -2048; dz <= 2048; dz += 512) {
                    WaterIndex index = model.index(Math.floorDiv(place[0] + dx, WaterIndex.SIZE), Math.floorDiv(place[1] + dz, WaterIndex.SIZE));
                    basins.addAll(List.of(index.owners));
                }
            }
        }
        return new ArrayList<>(basins);
    }

    /**
     * Same seed, same rivers, whatever order tiles and regions are computed in and from which
     * threads: a basin is computed by the tile that owns it, from pure functions of the seed.
     */
    @Test
    void waterIsIndependentOfOrderAndThreads() {
        VastlandsHydrology a = hydrology();
        VastlandsHydrology b = hydrology();
        // b computes the neighbouring tiles first, in reverse, in parallel.
        IntStream.rangeClosed(-2, 2).parallel().forEach(i -> b.model().domain(-i, 1 - i));
        Random random = new Random(1);
        int[][] columns = new int[3000][];
        for (int i = 0; i < columns.length; i++) {
            int[] place = PLACES[i % PLACES.length];
            columns[i] = new int[] {place[0] + random.nextInt(1024) - 512, place[1] + random.nextInt(1024) - 512};
        }
        double[] carvedA = new double[columns.length];
        int[] topsA = new int[columns.length];
        for (int i = 0; i < columns.length; i++) {
            carvedA[i] = a.carvedHeight(columns[i][0], columns[i][1]);
            topsA[i] = a.water(columns[i][0], columns[i][1]).waterTop();
        }
        double[] carvedB = new double[columns.length];
        int[] topsB = new int[columns.length];
        IntStream.range(0, columns.length).parallel().map(i -> columns.length - 1 - i).forEach(i -> {
            carvedB[i] = b.carvedHeight(columns[i][0], columns[i][1]);
            topsB[i] = b.water(columns[i][0], columns[i][1]).waterTop();
        });
        assertArrayEquals(carvedA, carvedB);
        assertArrayEquals(topsA, topsB);
    }

    /**
     * Water runs downhill: along every reach and across every confluence the water top never
     * rises, and every water top is the sea's or an even number of blocks above it.
     */
    @Test
    void riversRunDownhillInEvenSteps() {
        HydrologyModel model = hydrology().model();
        int seaTop = model.seaTop();
        int segments = 0;
        int steps = 0;
        for (BasinRivers rivers : basins(model)) {
            Map<String, List<Integer>> starts = new HashMap<>();
            for (int k = 0; k < rivers.segments; k++) {
                starts.computeIfAbsent(rivers.ax[k] + "," + rivers.az[k], key -> new ArrayList<>()).add(k);
                int top = rivers.waterTop[k];
                assertTrue(top >= seaTop && (top - seaTop) % 2 == 0, "water top " + top);
            }
            for (int k = 0; k < rivers.segments; k++) {
                for (int next : starts.getOrDefault(rivers.bx[k] + "," + rivers.bz[k], List.of())) {
                    assertTrue(rivers.waterTop[next] <= rivers.waterTop[k],
                            "water rises from " + rivers.waterTop[k] + " to " + rivers.waterTop[next] + " at " + rivers.bx[k] + ", " + rivers.bz[k]);
                    steps += rivers.waterTop[next] < rivers.waterTop[k] ? 1 : 0;
                }
            }
            for (int top : rivers.lakeTops.values()) {
                assertTrue(top >= seaTop && (top - seaTop) % 2 == 0, "lake top " + top);
            }
            segments += rivers.segments;
        }
        Vastlands.LOGGER.info("HydrologyModelTest: {} segments, {} steps", segments, steps);
        assertTrue(segments > 5000, "too few rivers to judge: " + segments);
    }

    /** Every river cell drains into another river cell, the sea, a terminal lake, or ends in a lake. */
    @Test
    void riversEndInTheSeaOrALake() {
        HydrologyModel model = hydrology().model();
        int ocean = 0;
        int lakes = 0;
        for (int tx = -1; tx <= 0; tx++) {
            for (int tz = -1; tz <= 0; tz++) {
                DrainageDomain domain = model.domain(tx, tz);
                for (int r = 0; r < domain.cellKeys.length; r++) {
                    switch (domain.downKinds[r]) {
                        case DrainageDomain.DOWN_RIVER -> assertTrue(domain.cellIndex.containsKey(domain.downKeys[r]), "river flows into nothing");
                        case DrainageDomain.DOWN_OCEAN -> ocean++;
                        case DrainageDomain.DOWN_LAKE -> lakes++;
                        default -> assertTrue(domain.lakeOf[r] >= 0, "a river ends outside a lake");
                    }
                }
            }
        }
        Vastlands.LOGGER.info("HydrologyModelTest: {} river mouths, {} rivers ending in terminal lakes", ocean, lakes);
        assertTrue(ocean > 50, "too few river mouths: " + ocean);
    }

    /** Broad rivers, rare big ones up to ~100 blocks wide (plunge pools aside), falls in steep places. */
    @Test
    void widthsAndFalls() {
        HydrologyModel model = hydrology().model();
        double widest = 0.0;
        int tallFalls = 0;
        double tallest = 0.0;
        for (BasinRivers rivers : basins(model)) {
            for (int k = 0; k < rivers.segments; k++) {
                if ((rivers.flags[k] & BasinRivers.POOL) == 0) {
                    widest = Math.max(widest, 2.0 * Math.max(rivers.rA[k], rivers.rB[k]));
                }
            }
            tallFalls += rivers.tallFalls;
            tallest = Math.max(tallest, rivers.tallestFall);
        }
        Vastlands.LOGGER.info("HydrologyModelTest: widest river {} blocks, {} falls of 8+ blocks, tallest {}", widest, tallFalls, tallest);
        assertTrue(widest > 50.0 && widest <= 110.0, "widest river " + widest);
        assertTrue(tallFalls > 0 && tallest >= 30.0, "falls: " + tallFalls + ", tallest " + tallest);
    }

    /**
     * Water stays in its channels at block level. For every river or lake column, each neighbour
     * must hold water at the same level, belong to a fall (a level at least 2 blocks apart), or be
     * ground at least as high as the water. The few exceptions are raised by the block step
     * (one-block levees); the density must keep them rare.
     */
    @Test
    void waterIsContainedByBanks() {
        VastlandsHydrology hydrology = hydrology();
        int shore = 0;
        int levees = 0;
        List<String> examples = new ArrayList<>();
        int falls = 0;
        int wet = 0;
        for (int[] place : PLACES) {
            int n = 384;
            int x0 = place[0] - n / 2;
            int z0 = place[1] - n / 2;
            WaterColumn[] grid = new WaterColumn[n * n];
            IntStream.range(0, n).parallel().forEach(j -> {
                for (int i = 0; i < n; i++) {
                    grid[j * n + i] = hydrology.water(x0 + i, z0 + j);
                }
            });
            for (int j = 1; j < n - 1; j++) {
                for (int i = 1; i < n - 1; i++) {
                    WaterColumn c = grid[j * n + i];
                    if (!c.placed()) {
                        continue;
                    }
                    wet++;
                    for (int[] o : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        WaterColumn m = grid[(j + o[1]) * n + i + o[0]];
                        if (m.wet()) {
                            int difference = Math.abs(m.waterTop() - c.waterTop());
                            assertTrue(difference != 1, "water surfaces 1 block apart at " + (x0 + i) + ", " + (z0 + j));
                            falls += difference >= 2 ? 1 : 0;
                        } else {
                            shore++;
                            if (m.groundTop() < c.waterTop()) {
                                levees++;
                                if (examples.size() < 40) {
                                    examples.add((x0 + i + o[0]) + "," + (z0 + j + o[1]) + " " + c.kind() + " top " + c.waterTop() + " ground " + m.groundTop());
                                }
                            }
                        }
                    }
                }
            }
        }
        Vastlands.LOGGER.info("HydrologyModelTest: {} river and lake columns, {} shore edges, {} need a levee, {} fall edges; levees e.g. {}", wet, shore, levees, falls, examples);
        assertTrue(wet > 5000, "too little water to judge: " + wet);
        assertTrue(levees < 0.02 * shore, "levees needed on " + levees + " of " + shore + " shore edges, e.g. " + examples);
    }

    /** Carving follows the channels: off the rivers the surface is the terrain's. */
    @Test
    void terrainIsUnchangedAwayFromWater() {
        VastlandsHydrology hydrology = hydrology();
        VastlandsTerrain terrain = new VastlandsTerrain(SETUP);
        Random random = new Random(7);
        int changed = 0;
        int raised = 0;
        int samples = 4000;
        for (int i = 0; i < samples; i++) {
            int[] place = PLACES[i % PLACES.length];
            int x = (place[0] + random.nextInt(4096) - 2048) & ~3;
            int z = (place[1] + random.nextInt(4096) - 2048) & ~3;
            double before = terrain.column(x, z).height();
            double after = hydrology.carvedHeight(x, z);
            if (after != before) {
                changed++;
                raised += after > before + 0.01 ? 1 : 0;
            }
        }
        Vastlands.LOGGER.info("HydrologyModelTest: {} of {} grid columns reshaped, {} raised", changed, samples, raised);
        assertTrue(changed < 0.25 * samples, "hydrology reshapes too much: " + changed);
        assertTrue(raised < 0.01 * samples, "too many levees: " + raised);
    }

    /** The quick check block steps use to skip chunks never misses river or lake water. */
    @Test
    void quickCheckNeverMissesWater() {
        VastlandsHydrology hydrology = hydrology();
        int skipped = 0;
        int boxes = 0;
        for (int[] place : PLACES) {
            for (int bx = place[0] - 512; bx < place[0] + 512; bx += 20) {
                for (int bz = place[1] - 512; bz < place[1] + 512; bz += 20) {
                    boxes++;
                    if (hydrology.mayHoldWater(bx, bz, bx + 19, bz + 19)) {
                        continue;
                    }
                    skipped++;
                    for (int x = bx; x < bx + 20; x++) {
                        for (int z = bz; z < bz + 20; z++) {
                            assertTrue(!hydrology.water(x, z).placed(), "water missed at " + x + ", " + z);
                        }
                    }
                }
            }
        }
        Vastlands.LOGGER.info("HydrologyModelTest: {} of {} boxes skipped", skipped, boxes);
        assertTrue(skipped > boxes / 4, "the quick check skips too little: " + skipped + " of " + boxes);
    }

    @Test
    void levelsAreQuantizedAboveTheSea() {
        HydrologyModel model = hydrology().model();
        assertEquals(62, model.seaTop());
        assertEquals(62, model.quantizeLevel(40.0));
        assertEquals(62, model.quantizeLevel(63.9));
        assertEquals(64, model.quantizeLevel(64.0));
        assertEquals(64, model.quantizeLevel(65.99));
        assertEquals(100, model.quantizeLevel(101.5));
    }
}
