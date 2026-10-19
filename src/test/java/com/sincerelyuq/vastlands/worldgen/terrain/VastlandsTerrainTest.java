package com.sincerelyuq.vastlands.worldgen.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sincerelyuq.vastlands.worldgen.WorldHeight;
import com.sincerelyuq.vastlands.worldgen.system.DensityRange;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSetup;
import com.sincerelyuq.vastlands.worldgen.system.TerrainParameter;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** The terrain system as the router sees it: density, interpolation, caching and parameter bounds. */
class VastlandsTerrainTest {
    private static final GenerationSetup SETUP = new GenerationSetup(20261018L, WorldHeight.MIN_Y, WorldHeight.HEIGHT, 63);

    @Test
    void densityHasOneSurfacePerColumn() {
        VastlandsTerrain terrain = new VastlandsTerrain(SETUP);
        for (int i = 0; i < 200; i++) {
            int x = (i * 4517) & ~3;
            int z = (i * -2273) & ~3;
            double surface = terrain.column(x, z).height();
            double previous = Double.POSITIVE_INFINITY;
            for (int y = WorldHeight.MIN_Y; y <= WorldHeight.MAX_Y; y += 8) {
                double density = terrain.apply(x, y, z, 0.0);
                assertTrue(density <= previous, "density must not increase upwards");
                assertEquals(y < surface, density > 0.0, "solid exactly below the surface at " + x + "," + y + "," + z);
                previous = density;
            }
        }
    }

    /** The cache never changes results: cached columns equal a fresh model's, from any thread. */
    @Test
    void cachedColumnsEqualTheModel() {
        VastlandsTerrain terrain = new VastlandsTerrain(SETUP);
        VastlandsTerrain fresh = new VastlandsTerrain(SETUP);
        int[] xs = IntStream.range(0, 3000).map(i -> (i % 60) * 4 - 120 + (i / 60) * 4096).toArray();
        IntStream.range(0, xs.length).parallel().forEach(i -> {
            int z = (i * 12) % 4096 - 2048;
            assertEquals(fresh.column(xs[i], z), terrain.column(xs[i], z));
            assertEquals(fresh.column(xs[i], z), terrain.column(xs[i], z));
        });
    }

    @Test
    void surfaceHeightInterpolatesTheNoiseGrid() {
        VastlandsTerrain terrain = new VastlandsTerrain(SETUP);
        Random random = new Random(3);
        for (int i = 0; i < 2000; i++) {
            int x = random.nextInt(200_000) - 100_000;
            int z = random.nextInt(200_000) - 100_000;
            int x0 = x & ~3;
            int z0 = z & ~3;
            assertEquals(terrain.column(x0, z0).height(), terrain.surfaceHeight(x0, z0));
            double a = terrain.column(x0, z0).height();
            double b = terrain.column(x0 + 4, z0).height();
            double c = terrain.column(x0, z0 + 4).height();
            double d = terrain.column(x0 + 4, z0 + 4).height();
            double h = terrain.surfaceHeight(x, z);
            assertTrue(h >= Math.min(Math.min(a, b), Math.min(c, d)) - 1e-9 && h <= Math.max(Math.max(a, b), Math.max(c, d)) + 1e-9);
        }
    }

    @Test
    void outputsStayInsideTheirDeclaredRanges() {
        VastlandsTerrain terrain = new VastlandsTerrain(SETUP);
        DensityRange range = terrain.outputRange(new DensityRange(-1.0, 1.0));
        Random random = new Random(11);
        for (int i = 0; i < 5000; i++) {
            int x = random.nextInt(2_000_000) - 1_000_000;
            int z = random.nextInt(2_000_000) - 1_000_000;
            int y = random.nextInt(4000) - 2000;
            double input = random.nextDouble() * 2.0 - 1.0;
            double density = terrain.apply(x, y, z, input);
            assertTrue(density >= range.min() && density <= range.max(), "density " + density);
            for (TerrainParameter parameter : TerrainParameter.values()) {
                double value = terrain.parameter(parameter, x, y, z);
                assertTrue(value >= parameter.min() && value <= parameter.max(), parameter + " = " + value);
            }
        }
    }

    /** Aquifers and the preliminary surface must never see ground above the real surface. */
    @Test
    void initialDensityStaysBelowTheSurface() {
        VastlandsTerrain terrain = new VastlandsTerrain(SETUP);
        Random random = new Random(12);
        for (int i = 0; i < 3000; i++) {
            int x = random.nextInt(200_000) - 100_000;
            int z = random.nextInt(200_000) - 100_000;
            int x0 = x & ~3;
            int z0 = z & ~3;
            for (int dx = 0; dx <= 4; dx++) {
                for (int dz = 0; dz <= 4; dz++) {
                    int y = (int) Math.ceil(terrain.surfaceHeight(x0 + dx, z0 + dz));
                    assertTrue(terrain.parameter(TerrainParameter.INITIAL_DENSITY, x0, y, z0) <= 0.0);
                }
            }
        }
    }

    /** Cave biomes at vanilla distances below the surface; the deep dark only in vanilla's band. */
    @Test
    void depthFollowsTheSurfaceAndConfinesTheDeepDark() {
        assertEquals(0.0, VastlandsTerrain.depth(100.0, 100));
        assertEquals(0.5, VastlandsTerrain.depth(100.0, 36));
        assertEquals(-1.0, VastlandsTerrain.depth(100.0, 228));
        // 300 blocks under a peak: held below the deep dark point outside the band...
        assertEquals(VastlandsTerrain.DEEP_HOLD, VastlandsTerrain.depth(400.0, 100));
        assertEquals(VastlandsTerrain.DEEP_HOLD, VastlandsTerrain.depth(100.0, -200));
        // ...and deep (deep dark under low erosion) inside it, like vanilla's Y -64..0.
        assertEquals((400.0 + 30) / 128.0, VastlandsTerrain.depth(400.0, -30));
        assertTrue(VastlandsTerrain.depth(400.0, -30) > 1.1);
    }

    @Test
    void lowWorldsCapTheTerrainBelowTheirTop() {
        VastlandsTerrain terrain = new VastlandsTerrain(new GenerationSetup(1L, -64, 384, 63));
        Random random = new Random(13);
        for (int i = 0; i < 20000; i++) {
            assertTrue(terrain.column(random.nextInt(400_000) - 200_000, random.nextInt(400_000) - 200_000).height() < 319 - 41);
        }
    }
}
