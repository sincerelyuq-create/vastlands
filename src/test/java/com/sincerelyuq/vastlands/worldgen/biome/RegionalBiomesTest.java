package com.sincerelyuq.vastlands.worldgen.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sincerelyuq.vastlands.Vastlands;
import com.sincerelyuq.vastlands.worldgen.WorldHeight;
import com.sincerelyuq.vastlands.worldgen.context.GenerationContext;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSetup;
import com.sincerelyuq.vastlands.worldgen.terrain.TerrainColumn;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * The regional surface biomes (Agent 7) and the dunes of the dune sea without the game: the real
 * terrain, rivers and caves of seed 20261018. Every region exists somewhere in a square of
 * ~200 km, holds only the land it is meant for, and is the same from any instance and thread; the
 * dunes stay within their height, never jump between neighbouring columns (cell borders
 * included) and vanish at the region's border.
 */
class RegionalBiomesTest {
    private static final long SEED = 20261018L;
    private static final int ALL = (1 << RegionalBiomes.Region.VALUES.length) - 1;
    private static final int SPACING = 768;
    private static final int GRID = 256;

    private static GenerationContext context() {
        return new GenerationContext(new GenerationSetup(SEED, WorldHeight.MIN_Y, WorldHeight.HEIGHT, 63));
    }

    @Test
    void regionsHoldTheLandTheyAreMeantFor() {
        GenerationContext context = context();
        RegionalBiomes regions = context.regions();
        Map<RegionalBiomes.Region, int[]> counts = new EnumMap<>(RegionalBiomes.Region.class);
        Map<RegionalBiomes.Region, double[]> sums = new EnumMap<>(RegionalBiomes.Region.class);
        int land = 0;
        int half = GRID / 2;
        for (int j = -half; j < half; j++) {
            for (int i = -half; i < half; i++) {
                int x = i * SPACING + 37;
                int z = j * SPACING - 11;
                TerrainColumn column = context.systems().terrain().column(x, z);
                if (column.geography().continentalness() < 0.0) {
                    continue;
                }
                land++;
                RegionalBiomes.Region r = regions.region(x, z, ALL);
                if (r == null) {
                    continue;
                }
                counts.computeIfAbsent(r, k -> new int[1])[0]++;
                double[] s = sums.computeIfAbsent(r, k -> new double[4]);
                s[0] += column.height();
                s[1] += column.slope();
                s[2] += column.climateTemperature();
                s[3] += column.climateHumidity();
                switch (r) {
                    case VOLCANIC_WASTE -> assertTrue(regions.land().volcanic(x, z) > 0.45, "volcanic waste off the volcanic rock at " + x + " " + z);
                    case DUNE_SEA -> assertTrue(column.climateTemperature() > 0.4 && column.climateHumidity() < -0.25,
                            "dune sea outside a hot, arid climate at " + x + " " + z);
                    default -> {
                    }
                }
            }
        }
        StringBuilder report = new StringBuilder();
        for (RegionalBiomes.Region r : RegionalBiomes.Region.VALUES) {
            int n = counts.getOrDefault(r, new int[1])[0];
            double[] s = sums.getOrDefault(r, new double[4]);
            report.append(String.format(Locale.ROOT, "%n  %s: %d of %d land samples (%.2f%%), mean height %.0f, slope %.2f, temperature %.2f, humidity %.2f",
                    r.tagPath(), n, land, 100.0 * n / land, s[0] / Math.max(1, n), s[1] / Math.max(1, n), s[2] / Math.max(1, n), s[3] / Math.max(1, n)));
            assertTrue(n > 0, r + " never appears");
            assertTrue(n < land * 0.12, r + " covers too much: " + n + " of " + land);
        }
        Vastlands.LOGGER.info("RegionalBiomesTest: regions{}", report);
        double[] painted = sums.get(RegionalBiomes.Region.PAINTED_RANGE);
        double[] wetland = sums.get(RegionalBiomes.Region.WETLAND);
        double[] dunes = sums.get(RegionalBiomes.Region.DUNE_SEA);
        assertTrue(painted[0] / counts.get(RegionalBiomes.Region.PAINTED_RANGE)[0] > 63 + 60, "painted ranges are not mountains");
        assertTrue(wetland[0] / counts.get(RegionalBiomes.Region.WETLAND)[0] < 63 + 30, "wetlands are not lowland");
        assertTrue(dunes[1] / counts.get(RegionalBiomes.Region.DUNE_SEA)[0] < 0.25, "the dune sea is not flat");
    }

    @Test
    void sameRegionsFromAnyInstanceAndThread() {
        RegionalBiomes a = context().regions();
        RegionalBiomes b = context().regions();
        int[] mismatches = IntStream.range(0, 4096).parallel().map(k -> {
            int x = (k % 64) * 911 - 30000;
            int z = (k / 64) * 877 - 28000;
            boolean same = a.region(x, z, ALL) == b.region(x, z, ALL)
                    && a.dunes().height(x, z) == b.dunes().height(x, z)
                    && a.dunes().amplitude(x, z, ALL) == b.dunes().amplitude(x, z, ALL);
            return same ? 0 : 1;
        }).toArray();
        assertEquals(0, IntStream.of(mismatches).sum(), "regions or dunes differ between instances");
        // Without the region's biomes (no other mods), nothing is ever a region and no dune rises.
        for (int k = 0; k < 256; k++) {
            assertEquals(null, a.region(k * 1301, k * -977, 0));
            assertEquals(0.0, a.dunes().amplitude(k * 1301, k * -977, ALL & ~(1 << RegionalBiomes.Region.DUNE_SEA.ordinal())));
        }
    }

    @Test
    void dunesAreSmoothAndVanishAtTheBorder() {
        GenerationContext context = context();
        RegionalBiomes regions = context.regions();
        DuneField dunes = regions.dunes();
        double maxStep = 0.0;
        double maxHeight = 0.0;
        long steps = 0;
        long bigSteps = 0;
        // A strip across a cell border (x = 2048) and a square inside a cell.
        int[][] squares = {{DuneField.CELL - 96, 300, 192}, {-5000, 7000, 160}};
        for (int[] sq : squares) {
            for (int z = sq[1]; z < sq[1] + sq[2]; z++) {
                double last = dunes.height(sq[0], z);
                for (int x = sq[0] + 1; x < sq[0] + sq[2]; x++) {
                    double h = dunes.height(x, z);
                    double up = dunes.height(x, z - 1);
                    assertTrue(h >= 0.0 && h <= DuneField.MAX_HEIGHT + 0.5, "dune height " + h + " at " + x + " " + z);
                    double step = Math.max(Math.abs(h - last), Math.abs(h - up));
                    maxStep = Math.max(maxStep, step);
                    maxHeight = Math.max(maxHeight, h);
                    steps++;
                    if (step > 1.5) {
                        bigSteps++;
                    }
                    last = h;
                }
            }
        }
        Vastlands.LOGGER.info("RegionalBiomesTest: dunes up to {} blocks, steepest step {} ({} of {} steps over 1.5)",
                String.format(Locale.ROOT, "%.1f", maxHeight), String.format(Locale.ROOT, "%.2f", maxStep), bigSteps, steps);
        assertTrue(maxHeight > DuneField.MAX_HEIGHT * 0.5, "no dune of any size: " + maxHeight);
        assertTrue(maxStep < 2.6, "a dune steps " + maxStep + " blocks between neighbouring columns");
        assertTrue(bigSteps < steps / 50, "too many steep steps: " + bigSteps + " of " + steps);
        // The amplitude: 0..1, and 0 wherever the region's margin is not positive.
        int dune = 1 << RegionalBiomes.Region.DUNE_SEA.ordinal();
        for (int k = 0; k < 4096; k++) {
            int x = (k % 64) * 1531 - 49000;
            int z = (k / 64) * 1499 - 47000;
            double a = dunes.amplitude(x, z, dune);
            assertTrue(a >= 0.0 && a <= 1.0, "amplitude " + a);
            if (regions.margin(RegionalBiomes.Region.DUNE_SEA, x, z) <= 0.0) {
                assertEquals(0.0, a, "dunes outside the dune sea at " + x + " " + z);
            }
        }
    }
}
