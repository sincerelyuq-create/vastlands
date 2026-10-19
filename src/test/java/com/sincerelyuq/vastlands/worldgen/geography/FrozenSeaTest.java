package com.sincerelyuq.vastlands.worldgen.geography;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The fracture pattern of polar ice fields: determinism, exact cell edges, a connected crack network, seamless chunks. */
class FrozenSeaTest {
    private static final int SIZE = 1024;

    @Test
    void coverDependsOnlyOnPosition() {
        FrozenSea a = new FrozenSea(11L);
        FrozenSea b = new FrozenSea(11L);
        FrozenSea other = new FrozenSea(12L);
        Random random = new Random(1);
        int differ = 0;
        for (int i = 0; i < 20000; i++) {
            double x = random.nextInt(2_000_000) - 1_000_000;
            double z = random.nextInt(2_000_000) - 1_000_000;
            double strength = random.nextDouble();
            assertEquals(a.cover(x, z, strength), b.cover(x, z, strength));
            differ += a.cover(x, z, 1.0) != other.cover(x, z, 1.0) ? 1 : 0;
        }
        assertTrue(differ > 500, "seeds must change the pattern: " + differ);
    }

    @Test
    void noIceOutsideTheFreezingBand() {
        FrozenSea sea = new FrozenSea(3L);
        assertEquals(0.0, FrozenSea.strength(FrozenSea.FREEZE_TEMPERATURE + 0.01));
        assertEquals(1.0, FrozenSea.strength(FrozenSea.SOLID_TEMPERATURE - 0.01));
        for (int i = 0; i < 1000; i++) {
            assertEquals(FrozenSea.Cover.OPEN_WATER, sea.cover(i * 37.0, i * -91.0, 0.0));
        }
    }

    /** The Voronoi edge distance equals a brute-force search over a much larger window, so cracks are continuous. */
    @Test
    void voronoiEdgesAreExact() {
        SimplexNoise points = new SimplexNoise(99L);
        Random random = new Random(2);
        double[] out = new double[2];
        for (int i = 0; i < 3000; i++) {
            double x = random.nextDouble() * 2000 - 1000;
            double z = random.nextDouble() * 2000 - 1000;
            FrozenSea.voronoi(points, x, z, out);
            long ix = (long) Math.floor(x);
            long iz = (long) Math.floor(z);
            double best = Double.POSITIVE_INFINITY;
            double bx = 0;
            double bz = 0;
            for (long ci = ix - 4; ci <= ix + 4; ci++) {
                for (long cj = iz - 4; cj <= iz + 4; cj++) {
                    double px = ci + points.hash01(ci, cj, 0);
                    double pz = cj + points.hash01(ci, cj, 1);
                    double d = (px - x) * (px - x) + (pz - z) * (pz - z);
                    if (d < best) {
                        best = d;
                        bx = px;
                        bz = pz;
                    }
                }
            }
            double edge = Double.POSITIVE_INFINITY;
            for (long ci = ix - 4; ci <= ix + 4; ci++) {
                for (long cj = iz - 4; cj <= iz + 4; cj++) {
                    double px = ci + points.hash01(ci, cj, 0);
                    double pz = cj + points.hash01(ci, cj, 1);
                    double nx = px - bx;
                    double nz = pz - bz;
                    double len = Math.sqrt(nx * nx + nz * nz);
                    if (len < 1e-9) {
                        continue;
                    }
                    edge = Math.min(edge, ((0.5 * (px + bx) - x) * nx + (0.5 * (pz + bz) - z) * nz) / len);
                }
            }
            assertEquals(edge, out[0], 1e-9, "edge distance at " + x + ", " + z);
        }
    }

    /**
     * A firm ice field is mostly ice, and its open water forms a connected network of long cracks
     * and leads rather than scattered holes.
     */
    @Test
    void firmIceFieldHasAConnectedCrackNetwork() {
        for (long seed : new long[] {1L, 2L, 3L}) {
            boolean[][] open = render(new FrozenSea(seed), seed * 100_000, -seed * 70_000, 1.0);
            int openCount = 0;
            for (boolean[] row : open) {
                for (boolean b : row) {
                    openCount += b ? 1 : 0;
                }
            }
            int[] components = components(open);
            double iceShare = 1.0 - (double) openCount / (SIZE * SIZE);
            double largest = (double) components[0] / openCount;
            double speckle = (double) components[1] / openCount;
            System.out.printf(Locale.ROOT, "seed %d: ice %.1f%%, largest crack network %.1f%% of open water, specks %.2f%%%n",
                    seed, 100 * iceShare, 100 * largest, 100 * speckle);
            assertTrue(iceShare > 0.8 && iceShare < 0.97, "ice share " + iceShare);
            assertTrue(largest > 0.4, "open water must form a network: largest component " + largest);
            assertTrue(speckle < 0.03, "isolated holes of under 10 blocks: " + speckle);
        }
    }

    /** Towards the warm edge the ice opens up step by step until little remains. */
    @Test
    void iceBreaksUpTowardsTheEdge() {
        FrozenSea sea = new FrozenSea(5L);
        double previous = 1.0;
        for (double strength : new double[] {1.0, 0.7, 0.45, 0.25, 0.08}) {
            boolean[][] open = render(sea, 40_000, 40_000, strength);
            int ice = 0;
            for (boolean[] row : open) {
                for (boolean b : row) {
                    ice += b ? 0 : 1;
                }
            }
            double share = (double) ice / (SIZE * SIZE);
            assertTrue(share < previous, "ice share must fall with strength: " + share + " at " + strength);
            previous = share;
        }
        assertTrue(previous < 0.2, "the outer edge must be mostly open water: " + previous);
    }

    /** The pattern has no notion of chunks: neighbours across chunk borders agree as often as inside chunks. */
    @Test
    void seamlessAcrossChunkBorders() {
        boolean[][] open = render(new FrozenSea(8L), -123_456, 98_765, 1.0);
        int borderPairs = 0;
        int borderDiffer = 0;
        int innerPairs = 0;
        int innerDiffer = 0;
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE - 1; x++) {
                boolean differ = open[z][x] != open[z][x + 1];
                if (((x + 1) & 15) == 0) {
                    borderPairs++;
                    borderDiffer += differ ? 1 : 0;
                } else {
                    innerPairs++;
                    innerDiffer += differ ? 1 : 0;
                }
            }
        }
        double border = (double) borderDiffer / borderPairs;
        double inner = (double) innerDiffer / innerPairs;
        assertTrue(Math.abs(border - inner) < 0.3 * inner + 0.002, "border " + border + " vs inner " + inner);
    }

    /** Open water of a square of the field; the origin is aligned to chunk borders. */
    private static boolean[][] render(FrozenSea sea, long x0, long z0, double strength) {
        boolean[][] open = new boolean[SIZE][SIZE];
        java.util.stream.IntStream.range(0, SIZE).parallel().forEach(j -> {
            for (int i = 0; i < SIZE; i++) {
                open[j][i] = sea.cover((x0 & ~15) + i, (z0 & ~15) + j, strength) == FrozenSea.Cover.OPEN_WATER;
            }
        });
        return open;
    }

    /** Size of the largest 4-connected component of {@code cells}, and the cells in components under 10. */
    private static int[] components(boolean[][] cells) {
        boolean[][] seen = new boolean[SIZE][SIZE];
        int largest = 0;
        int specks = 0;
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int j = 0; j < SIZE; j++) {
            for (int i = 0; i < SIZE; i++) {
                if (!cells[j][i] || seen[j][i]) {
                    continue;
                }
                int size = 0;
                seen[j][i] = true;
                queue.add(new int[] {i, j});
                while (!queue.isEmpty()) {
                    int[] p = queue.poll();
                    size++;
                    int[][] next = {{p[0] + 1, p[1]}, {p[0] - 1, p[1]}, {p[0], p[1] + 1}, {p[0], p[1] - 1}};
                    for (int[] q : next) {
                        if (q[0] >= 0 && q[0] < SIZE && q[1] >= 0 && q[1] < SIZE && cells[q[1]][q[0]] && !seen[q[1]][q[0]]) {
                            seen[q[1]][q[0]] = true;
                            queue.add(q);
                        }
                    }
                }
                largest = Math.max(largest, size);
                if (size < 10) {
                    specks += size;
                }
            }
        }
        return new int[] {largest, specks};
    }
}
