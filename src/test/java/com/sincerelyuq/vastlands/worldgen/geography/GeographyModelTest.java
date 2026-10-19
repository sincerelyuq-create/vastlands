package com.sincerelyuq.vastlands.worldgen.geography;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sincerelyuq.vastlands.worldgen.terrain.TerrainColumn;
import com.sincerelyuq.vastlands.worldgen.terrain.TerrainModel;
import java.util.Locale;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * The macro geography on its own: determinism, continuity, the scale of its regions, the shape of
 * the ocean floor, trenches and the climate. Pure Java, no game.
 */
class GeographyModelTest {
    private static final double SEA = 63.0;
    private static final long[] SEEDS = {1L, 42L, 20261018L, -7L};

    private static GeographyModel model(long seed) {
        return new GeographyModel(seed, SEA, GeographySettings.DEFAULT);
    }

    @Test
    void sameSeedSameGeographyFromAnyThread() {
        GeographyModel a = model(77L);
        GeographyModel b = model(77L);
        int n = 3000;
        GeographySample[] sequential = new GeographySample[n];
        for (int i = 0; i < n; i++) {
            sequential[i] = a.sample(i * 4111.0 - 6_000_000, i * -2903.0 + 4_000_000);
        }
        IntStream.range(0, n).parallel().map(i -> n - 1 - i)
                .forEach(i -> assertEquals(sequential[i], b.sample(i * 4111.0 - 6_000_000, i * -2903.0 + 4_000_000)));
    }

    /** Every continuous field changes by no more than its steepest slope allows over 1/20 of a block. */
    @Test
    void fieldsHaveNoDiscontinuities() {
        for (long seed : SEEDS) {
            GeographyModel model = model(seed);
            Random random = new Random(seed ^ 0x6E06);
            for (int i = 0; i < 8000; i++) {
                double x = random.nextDouble() * 400_000 - 200_000;
                double z = random.nextDouble() * 400_000 - 200_000;
                GeographySample g = model.sample(x, z);
                GeographySample gx = model.sample(x + 0.05, z);
                GeographySample gz = model.sample(x, z + 0.05);
                String where = "seed " + seed + " at " + x + ", " + z;
                assertTrue(Math.max(Math.abs(gx.elevation() - g.elevation()), Math.abs(gz.elevation() - g.elevation())) < 1.0, "elevation jumps " + where);
                assertTrue(Math.abs(model.smoothElevation(x + 0.05, z) - model.smoothElevation(x, z)) < 1.0, "smooth elevation jumps " + where);
                assertTrue(Math.max(Math.abs(gx.temperature() - g.temperature()), Math.abs(gz.temperature() - g.temperature())) < 0.01, "temperature jumps " + where);
                assertTrue(Math.max(Math.abs(gx.moisture() - g.moisture()), Math.abs(gz.moisture() - g.moisture())) < 0.01, "moisture jumps " + where);
                assertTrue(Math.abs(gx.mountain() - g.mountain()) < 0.02 && Math.abs(gx.plateau() - g.plateau()) < 0.02, "masks jump " + where);
            }
        }
    }

    /**
     * Regions are far larger than vanilla biome patches: along long transects, plains provinces,
     * climate zones (temperature bands at sea level) and stretches of open sea run for thousands of
     * blocks.
     */
    @Test
    void regionsAreLarge() {
        for (long seed : SEEDS) {
            GeographyModel model = model(seed);
            Random random = new Random(seed + 5);
            Runs plains = new Runs();
            Runs temperatureBand = new Runs();
            Runs sea = new Runs();
            int ocean = 0;
            int total = 0;
            for (int t = 0; t < 12; t++) {
                double x0 = random.nextDouble() * 400_000 - 200_000;
                double z0 = random.nextDouble() * 400_000 - 200_000;
                double angle = random.nextDouble() * Math.PI;
                plains.reset();
                temperatureBand.reset();
                sea.reset();
                for (double s = 0; s < 60_000; s += 64) {
                    GeographySample g = model.sample(x0 + s * Math.cos(angle), z0 + s * Math.sin(angle));
                    boolean water = g.elevation() < SEA;
                    ocean += water ? 1 : 0;
                    total++;
                    plains.add(water ? -1 : (g.plains() >= 0.5 ? 1 : 0));
                    temperatureBand.add(band(g.seaLevelTemperature()));
                    sea.add(water ? 1 : 0);
                }
            }
            String where = "seed " + seed + ": ";
            System.out.printf(Locale.ROOT, "%splains province runs %.0f, temperature band runs %.0f, longest open sea %.0f blocks, sea %.1f%%%n",
                    where, plains.mean(1) * 64, temperatureBand.meanAll() * 64, sea.longest(1) * 64.0, 100.0 * ocean / total);
            assertTrue(plains.mean(1) * 64 > 1500, where + "plains provinces too small: " + plains.mean(1) * 64);
            assertTrue(temperatureBand.meanAll() * 64 > 2500, where + "climate zones too small: " + temperatureBand.meanAll() * 64);
            assertTrue(sea.longest(1) * 64 > 5000, where + "no open sea wider than 5 000 blocks: " + sea.longest(1) * 64);
            assertTrue(ocean > 0.25 * total && ocean < 0.5 * total, where + "sea share " + (double) ocean / total);
        }
    }

    /** The sea floor falls from shelf over slope to the abyss, ridges and seamounts stay submerged. */
    @Test
    void seaFloorDeepensAwayFromTheCoast() {
        for (long seed : SEEDS) {
            GeographyModel model = model(seed);
            Random random = new Random(seed * 13);
            double shelfDepth = 0;
            int shelf = 0;
            double abyssDepth = 0;
            int abyss = 0;
            for (int i = 0; i < 40000; i++) {
                GeographySample g = model.sample(random.nextInt(400_000) - 200_000, random.nextInt(400_000) - 200_000);
                double c = g.continentalness();
                if (c < -0.35) {
                    assertTrue(g.elevation() < SEA - 5.9, "submarine feature breaks the surface: " + g);
                }
                if (c < 0.0 && c > -0.08) {
                    shelfDepth += SEA - g.elevation();
                    shelf++;
                } else if (c < -0.9 && g.trench() == 0.0) {
                    abyssDepth += SEA - g.elevation();
                    abyss++;
                }
                assertEquals(g.elevation() < SEA - 0.5, g.landform().isOcean(), "landform class vs water: " + g);
            }
            String where = "seed " + seed + ": ";
            assertTrue(shelf > 100 && abyss > 100, where + "too few samples " + shelf + " " + abyss);
            assertTrue(shelfDepth / shelf < 15.0, where + "shelf too deep: " + shelfDepth / shelf);
            assertTrue(abyssDepth / abyss > 65.0 && abyssDepth / abyss < 110.0, where + "abyssal depth " + abyssDepth / abyss);
        }
    }

    /** Trenches are exceptional: a tiny share of the sea floor, deep, never below the world's reach. */
    @Test
    void trenchesAreRareAndDeep() {
        int deep = 0;
        int sea = 0;
        double min = Double.POSITIVE_INFINITY;
        int n = 0;
        for (long seed : SEEDS) {
            GeographyModel model = model(seed);
            for (int x = -300_000; x < 300_000; x += 397) {
                for (int z = -300_000; z < 300_000; z += 4001) {
                    double h = model.smoothElevation(x, z);
                    n++;
                    sea += h < SEA ? 1 : 0;
                    if (h < -45.0) {
                        deep++;
                        assertTrue(model.sample(x, z).trench() > 0.0, "deeper than the abyss outside a trench at " + x + ", " + z);
                    }
                    min = Math.min(min, h);
                }
            }
        }
        double share = (double) deep / sea;
        System.out.printf(Locale.ROOT, "trench floor (below Y -45): %.3f%% of the sea floor, deepest Y %.1f%n", 100 * share, min);
        assertTrue(share > 0.0002 && share < 0.02, "trench share of the sea floor " + share);
        assertTrue(min < -100.0, "trenches should reach below Y -100: " + min);
        assertTrue(min > -200.0, "trench deeper than designed: " + min);
    }

    /** Temperature falls with elevation; rain shadows exist and dry the lee of ranges. */
    @Test
    void climateFollowsTheLandscape() {
        GeographyModel model = model(20261018L);
        Random random = new Random(9);
        int high = 0;
        int shadowed = 0;
        int land = 0;
        double shadowMoisture = 0;
        double openMoisture = 0;
        int open = 0;
        for (int i = 0; i < 60000; i++) {
            GeographySample g = model.sample(random.nextInt(400_000) - 200_000, random.nextInt(400_000) - 200_000);
            assertTrue(g.temperature() <= g.seaLevelTemperature() + 1e-12, "elevation must not warm: " + g);
            if (g.elevation() > 300 && g.seaLevelTemperature() > -0.4) {
                high++;
                assertTrue(g.seaLevelTemperature() - g.temperature() > 0.45, "high ground must be much colder: " + g);
            }
            if (g.elevation() >= SEA) {
                land++;
                if (g.rainShadow() > 0.5) {
                    shadowed++;
                    shadowMoisture += g.moisture();
                } else if (g.rainShadow() == 0.0) {
                    open++;
                    openMoisture += g.moisture();
                }
            }
            assertTrue(g.temperature() >= -1 && g.temperature() <= 1 && g.moisture() >= -1 && g.moisture() <= 1, g.toString());
        }
        assertTrue(high > 20, "too few high columns: " + high);
        assertTrue(shadowed > 0.005 * land && shadowed < 0.3 * land, "rain shadow share " + (double) shadowed / land);
        assertTrue(shadowMoisture / shadowed < openMoisture / open - 0.15, "lee sides must be drier");
    }

    /** The terrain model is built on the geography: same continents, same climate. */
    @Test
    void terrainUsesTheGeography() {
        TerrainModel terrain = new TerrainModel(5L, SEA, 470.0);
        GeographyModel geography = model(5L);
        Random random = new Random(5);
        for (int i = 0; i < 500; i++) {
            int x = random.nextInt(400_000) - 200_000;
            int z = random.nextInt(400_000) - 200_000;
            TerrainColumn column = terrain.sample(x, z);
            assertEquals(geography.sample(x, z), column.geography());
            assertEquals(column.geography().baseHeight(), column.baseHeight());
        }
    }

    private static int band(double temperature) {
        return temperature < -0.45 ? 0 : temperature < -0.15 ? 1 : temperature < 0.2 ? 2 : temperature < 0.55 ? 3 : 4;
    }

    /** Lengths of runs of equal values along a transect; value -1 is ignored (breaks a run). */
    private static final class Runs {
        private final java.util.Map<Integer, long[]> runs = new java.util.HashMap<>();
        private int value = Integer.MIN_VALUE;
        private int length;

        void reset() {
            this.close();
            this.value = Integer.MIN_VALUE;
            this.length = 0;
        }

        void add(int v) {
            if (v != this.value) {
                this.close();
                this.value = v;
                this.length = 0;
            }
            this.length++;
        }

        private void close() {
            if (this.value != Integer.MIN_VALUE && this.value != -1 && this.length > 0) {
                long[] r = this.runs.computeIfAbsent(this.value, k -> new long[3]);
                r[0]++;
                r[1] += this.length;
                r[2] = Math.max(r[2], this.length);
            }
        }

        double mean(int v) {
            this.close();
            this.length = 0;
            long[] r = this.runs.get(v);
            return r == null ? 0 : (double) r[1] / r[0];
        }

        double meanAll() {
            this.close();
            this.length = 0;
            long count = 0;
            long sum = 0;
            for (long[] r : this.runs.values()) {
                count += r[0];
                sum += r[1];
            }
            return count == 0 ? 0 : (double) sum / count;
        }

        long longest(int v) {
            this.close();
            this.length = 0;
            long[] r = this.runs.get(v);
            return r == null ? 0 : r[2];
        }
    }
}
