package com.sincerelyuq.vastlands.worldgen.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** The height field on its own: determinism, continuity, bounds and the shape of its height distribution. */
class TerrainModelTest {
    private static final double SEA = 63.0;
    private static final double MAX = VastlandsTerrain.MAX_SURFACE;
    /** The noise settings' top slide begins here: the surface must stay below it. */
    private static final double TOP_SLIDE = 488.0;
    private static final long[] SEEDS = {1L, 42L, 20261018L, -7L};

    @Test
    void sameSeedSameTerrain() {
        TerrainModel a = new TerrainModel(1234L, SEA, MAX);
        TerrainModel b = new TerrainModel(1234L, SEA, MAX);
        Random random = new Random(1);
        for (int i = 0; i < 2000; i++) {
            int x = random.nextInt(2_000_000) - 1_000_000;
            int z = random.nextInt(2_000_000) - 1_000_000;
            assertEquals(a.sample(x, z), b.sample(x, z));
        }
    }

    @Test
    void differentSeedsDifferentTerrain() {
        TerrainModel a = new TerrainModel(1L, SEA, MAX);
        TerrainModel b = new TerrainModel(2L, SEA, MAX);
        int differ = 0;
        for (int i = 0; i < 100; i++) {
            if (Math.abs(a.sample(i * 997, i * -613).height() - b.sample(i * 997, i * -613).height()) > 1.0) {
                differ++;
            }
        }
        assertTrue(differ > 80, "only " + differ + " of 100 columns differ");
    }

    /** Results depend only on position: sampling in parallel, in any order, gives the same values. */
    @Test
    void threadSafeAndOrderIndependent() {
        TerrainModel model = new TerrainModel(99L, SEA, MAX);
        double[] sequential = new double[4000];
        for (int i = 0; i < sequential.length; i++) {
            sequential[i] = model.sample(i * 37 - 70000, i * -53 + 9000).height();
        }
        double[] parallel = new double[sequential.length];
        IntStream.range(0, parallel.length).parallel().map(i -> parallel.length - 1 - i)
                .forEach(i -> parallel[i] = model.sample(i * 37 - 70000, i * -53 + 9000).height());
        for (int i = 0; i < sequential.length; i++) {
            assertEquals(sequential[i], parallel[i]);
        }
    }

    /**
     * No spikes or seams: neighbouring columns never differ by more than a steep cliff, and the
     * terrain is continuous at lattice and chunk boundaries (the model has no notion of chunks).
     */
    @Test
    void continuousWithoutSpikes() {
        for (long seed : SEEDS) {
            TerrainModel model = new TerrainModel(seed, SEA, MAX);
            Random random = new Random(seed);
            double maxStep = 0.0;
            double sum = 0.0;
            int n = 0;
            for (int i = 0; i < 6000; i++) {
                int x = random.nextInt(400_000) - 200_000;
                int z = random.nextInt(400_000) - 200_000;
                double h = model.sample(x, z).height();
                double step = Math.max(Math.abs(h - model.sample(x + 1, z).height()), Math.abs(h - model.sample(x, z + 1).height()));
                maxStep = Math.max(maxStep, step);
                sum += step;
                n++;
            }
            assertTrue(maxStep < 12.0, "seed " + seed + ": step of " + maxStep + " blocks between neighbouring columns");
            assertTrue(sum / n < 0.4, "seed " + seed + ": mean step " + sum / n);
        }
    }

    /**
     * No discontinuities anywhere: over 1/20 of a block the height changes no more than the
     * steepest slope allows. A jump in any layer (masks switching on, cell windows, gully strength)
     * shows up here as a step of several blocks.
     */
    @Test
    void noDiscontinuities() {
        for (long seed : SEEDS) {
            TerrainModel model = new TerrainModel(seed, SEA, MAX);
            Random random = new Random(seed ^ 0xD15C);
            for (int i = 0; i < 15000; i++) {
                double x = random.nextDouble() * 400_000 - 200_000;
                double z = random.nextDouble() * 400_000 - 200_000;
                double h = model.sample(x, z).height();
                double step = Math.max(Math.abs(model.sample(x + 0.05, z).height() - h), Math.abs(model.sample(x, z + 0.05).height() - h));
                assertTrue(step < 1.0, "seed " + seed + ": height jumps by " + step + " within 0.05 blocks at " + x + ", " + z);
            }
        }
    }

    /**
     * Geography in numbers, over a 400 000-block square per seed: mostly land, mostly low, with
     * expansive oceans; mountains common, high mountains exceptional; never close to the top of the
     * world; trenches as the only ground far below the abyssal plains.
     */
    @Test
    void heightDistributionIsPlausible() {
        for (long seed : SEEDS) {
            TerrainModel model = new TerrainModel(seed, SEA, MAX);
            Random random = new Random(seed * 31 + 7);
            int n = 20000;
            int ocean = 0;
            int lowland = 0;
            int above200 = 0;
            int above300 = 0;
            double max = Double.NEGATIVE_INFINITY;
            double min = Double.POSITIVE_INFINITY;
            for (int i = 0; i < n; i++) {
                TerrainColumn column = model.sample(random.nextInt(400_000) - 200_000, random.nextInt(400_000) - 200_000);
                double h = column.height();
                max = Math.max(max, h);
                min = Math.min(min, h);
                ocean += h < SEA ? 1 : 0;
                lowland += h >= SEA && h < 130 ? 1 : 0;
                above200 += h > 200 ? 1 : 0;
                above300 += h > 300 ? 1 : 0;
            }
            String where = "seed " + seed + ": ";
            assertTrue(ocean > 0.25 * n && ocean < 0.50 * n, where + "ocean " + ocean);
            assertTrue(lowland > 0.38 * n, where + "lowland " + lowland);
            assertTrue(above200 > 0.005 * n && above200 < 0.08 * n, where + "above 200: " + above200);
            assertTrue(above300 < 0.015 * n, where + "above 300: " + above300);
            assertTrue(above300 < above200 / 2, where + "high mountains must be rarer");
            assertTrue(max < TOP_SLIDE, where + "max " + max);
            assertTrue(min > -200.0, where + "min " + min);
        }
    }

    /** Climate parameters stay in vanilla's ranges and never select river biomes (Hydrology owns rivers). */
    @Test
    void climateParametersAreInRange() {
        TerrainModel model = new TerrainModel(5L, SEA, MAX);
        Random random = new Random(5);
        for (int i = 0; i < 20000; i++) {
            TerrainColumn column = model.sample(random.nextInt(400_000) - 200_000, random.nextInt(400_000) - 200_000);
            assertTrue(column.climateContinentalness() >= -1.05 && column.climateContinentalness() <= 1.0, column.toString());
            assertTrue(column.climateErosion() >= -1.0 && column.climateErosion() <= 1.0, column.toString());
            assertTrue(Math.abs(column.climateWeirdness()) >= 0.05 && Math.abs(column.climateWeirdness()) <= 1.0, column.toString());
            if (column.height() < SEA - 1.0) {
                assertTrue(column.climateContinentalness() < -0.19, "underwater column must read as ocean: " + column);
            }
            if (column.height() > SEA + 1.0) {
                assertTrue(column.climateContinentalness() >= -0.19, "dry column must not read as ocean: " + column);
            }
        }
    }

    /**
     * The summits of an exceptional mountain system stand highest, at about Y 475, those of other
     * great ranges lower (Y 455..474) instead of every great summit at one flattened height, and
     * none comes near the top slide. Six summits of seed 20261018 found over 262 144 blocks (raw
     * heights Y 570..670 before the cap): two of an exceptional system, four of other great ranges.
     */
    @Test
    void exceptionalSystemsStandAboveTheGreatRanges() {
        TerrainModel model = new TerrainModel(20261018L, SEA, MAX);
        int[][] exceptional = {{48475, 50461}, {129466, -49580}};
        int[][] great = {{58945, 10193}, {-21368, 5812}, {-45386, -89724}, {-135, 90095}};
        double lowestExceptional = Double.POSITIVE_INFINITY;
        for (int[] summit : exceptional) {
            double h = model.sample(summit[0], summit[1]).height();
            assertTrue(h > 472.0 && h < TOP_SLIDE - 6.0, "summit of an exceptional system at " + summit[0] + "," + summit[1] + ": " + h);
            lowestExceptional = Math.min(lowestExceptional, h);
        }
        double lowestGreat = Double.POSITIVE_INFINITY;
        for (int[] summit : great) {
            double h = model.sample(summit[0], summit[1]).height();
            assertTrue(h > 455.0 && h < lowestExceptional, "summit of a great range at " + summit[0] + "," + summit[1] + ": " + h);
            lowestGreat = Math.min(lowestGreat, h);
        }
        assertTrue(lowestExceptional - lowestGreat > 6.0, "summit heights spread, not one flattened level: " + lowestExceptional + " vs " + lowestGreat);
    }

    @Test
    void differentPositionsDifferentValues() {
        TerrainModel model = new TerrainModel(3L, SEA, MAX);
        assertNotEquals(model.sample(0, 0).height(), model.sample(5000, 5000).height());
    }
}
