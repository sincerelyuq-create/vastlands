package com.sincerelyuq.vastlands.worldgen.hydrology;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The river segments and lakes that can affect one {@value #SIZE}-block square region, bucketed
 * into {@value #BUCKET}-block squares for fast column queries.
 *
 * <p>Built from every tile that may own a basin reaching the region; each basin is taken only from
 * its owning tile, so the index is the same whichever thread or chunk builds it.
 */
final class WaterIndex {
    static final int SIZE = 512;
    static final int BUCKET = 32;
    private static final int BUCKETS = SIZE / BUCKET;
    /** Farthest a channel can reshape terrain from its centre line (see {@code BasinRivers.cutoff}). */
    static final double MAX_INFLUENCE = 330.0;

    final BasinRivers[] owners;
    final int[] segments;
    private final int[][] buckets;
    /** Boulders in the region: owning basin and index, bucketed like the segments. */
    final BasinRivers[] rockOwners;
    final int[] rockIds;
    private final int[][] rockBuckets;
    /** Coarse cell key → water tops of the lakes whose region contains the cell. */
    private final Map<Long, int[]> lakeTops;

    WaterIndex(HydrologyModel model, int regionX, int regionZ) {
        double minX = (double) regionX * SIZE;
        double minZ = (double) regionZ * SIZE;
        double maxX = minX + SIZE;
        double maxZ = minZ + SIZE;
        int cell = DrainageDomain.CELL;
        // A basin owned by a tile lies within its core grown by the margin, less the clearance;
        // its curves may overshoot their cells by up to a cell.
        double ownerReach = (DrainageDomain.MARGIN - DrainageDomain.CLEARANCE) * (double) cell + MAX_INFLUENCE + cell;
        double tileSize = DrainageDomain.CORE * (double) cell;
        int tx0 = (int) Math.floor((minX - ownerReach) / tileSize);
        int tx1 = (int) Math.floor((maxX + ownerReach) / tileSize);
        int tz0 = (int) Math.floor((minZ - ownerReach) / tileSize);
        int tz1 = (int) Math.floor((maxZ + ownerReach) / tileSize);
        int reachCells = (int) Math.ceil(MAX_INFLUENCE / cell) + 1;
        long ci0 = (long) Math.floor(minX / cell) - reachCells;
        long ci1 = (long) Math.floor((maxX - 1) / cell) + reachCells;
        long cj0 = (long) Math.floor(minZ / cell) - reachCells;
        long cj1 = (long) Math.floor((maxZ - 1) / cell) + reachCells;

        Set<BasinRivers> basins = new LinkedHashSet<>();
        Map<Long, List<Integer>> tops = new LinkedHashMap<>();
        model.prefetchDomains(tx0, tx1, tz0, tz1);
        for (int tx = tx0; tx <= tx1; tx++) {
            for (int tz = tz0; tz <= tz1; tz++) {
                DrainageDomain domain = model.domain(tx, tz);
                for (long ci = ci0; ci <= ci1; ci++) {
                    for (long cj = cj0; cj <= cj1; cj++) {
                        long key = DrainageDomain.key(ci, cj);
                        Integer river = domain.cellIndex.get(key);
                        if (river != null) {
                            basins.add(domain.rivers(domain.basins[river]));
                        }
                        int[] lakes = domain.lakeRegions.get(key);
                        if (lakes == null) {
                            continue;
                        }
                        for (int lake : lakes) {
                            BasinRivers rivers = domain.rivers(domain.lakes.get(lake).basin());
                            basins.add(rivers);
                            boolean inRegion = ci * cell < maxX && (ci + 1) * cell > minX && cj * cell < maxZ && (cj + 1) * cell > minZ;
                            if (inRegion) {
                                tops.computeIfAbsent(key, k -> new ArrayList<>()).add(rivers.lakeTops.get(lake));
                            }
                        }
                    }
                }
            }
        }
        this.lakeTops = new HashMap<>();
        for (Map.Entry<Long, List<Integer>> entry : tops.entrySet()) {
            this.lakeTops.put(entry.getKey(), entry.getValue().stream().mapToInt(Integer::intValue).toArray());
        }

        List<BasinRivers> owners = new ArrayList<>();
        List<Integer> segments = new ArrayList<>();
        for (BasinRivers rivers : basins) {
            for (int k = 0; k < rivers.segments; k++) {
                if (rivers.maxX(k) >= minX && rivers.minX(k) < maxX && rivers.maxZ(k) >= minZ && rivers.minZ(k) < maxZ) {
                    owners.add(rivers);
                    segments.add(k);
                }
            }
        }
        this.owners = owners.toArray(new BasinRivers[0]);
        this.segments = segments.stream().mapToInt(Integer::intValue).toArray();
        List<List<Integer>> lists = new ArrayList<>();
        for (int b = 0; b < BUCKETS * BUCKETS; b++) {
            lists.add(new ArrayList<>());
        }
        for (int ref = 0; ref < this.segments.length; ref++) {
            BasinRivers rivers = this.owners[ref];
            int k = this.segments[ref];
            int bx0 = clampBucket((int) Math.floor((rivers.minX(k) - minX) / BUCKET));
            int bx1 = clampBucket((int) Math.floor((rivers.maxX(k) - minX) / BUCKET));
            int bz0 = clampBucket((int) Math.floor((rivers.minZ(k) - minZ) / BUCKET));
            int bz1 = clampBucket((int) Math.floor((rivers.maxZ(k) - minZ) / BUCKET));
            for (int bz = bz0; bz <= bz1; bz++) {
                for (int bx = bx0; bx <= bx1; bx++) {
                    lists.get(bz * BUCKETS + bx).add(ref);
                }
            }
        }
        this.buckets = new int[BUCKETS * BUCKETS][];
        for (int b = 0; b < this.buckets.length; b++) {
            this.buckets[b] = lists.get(b).stream().mapToInt(Integer::intValue).toArray();
        }

        List<BasinRivers> rockOwners = new ArrayList<>();
        List<Integer> rockIds = new ArrayList<>();
        List<List<Integer>> rockLists = new ArrayList<>();
        for (int b = 0; b < BUCKETS * BUCKETS; b++) {
            rockLists.add(new ArrayList<>());
        }
        for (BasinRivers rivers : basins) {
            for (int q = 0; q < rivers.rocks; q++) {
                double reach = 1.4 * rivers.rockR[q] + 1.0;
                double x = rivers.rockX[q];
                double z = rivers.rockZ[q];
                if (x + reach < minX || x - reach >= maxX || z + reach < minZ || z - reach >= maxZ) {
                    continue;
                }
                int ref = rockIds.size();
                rockOwners.add(rivers);
                rockIds.add(q);
                for (int bz = clampBucket((int) Math.floor((z - reach - minZ) / BUCKET)); bz <= clampBucket((int) Math.floor((z + reach - minZ) / BUCKET)); bz++) {
                    for (int bx = clampBucket((int) Math.floor((x - reach - minX) / BUCKET)); bx <= clampBucket((int) Math.floor((x + reach - minX) / BUCKET)); bx++) {
                        rockLists.get(bz * BUCKETS + bx).add(ref);
                    }
                }
            }
        }
        this.rockOwners = rockOwners.toArray(new BasinRivers[0]);
        this.rockIds = rockIds.stream().mapToInt(Integer::intValue).toArray();
        this.rockBuckets = new int[BUCKETS * BUCKETS][];
        for (int b = 0; b < this.rockBuckets.length; b++) {
            this.rockBuckets[b] = rockLists.get(b).stream().mapToInt(Integer::intValue).toArray();
        }
    }

    private static int clampBucket(int b) {
        return Math.max(0, Math.min(BUCKETS - 1, b));
    }

    /** Segment references whose influence may reach the block column (local coordinates in the region). */
    int[] bucket(int localX, int localZ) {
        return this.buckets[(localZ / BUCKET) * BUCKETS + localX / BUCKET];
    }

    /** Boulder references that may cover the block column (local coordinates in the region). */
    int[] rockBucket(int localX, int localZ) {
        return this.rockBuckets[(localZ / BUCKET) * BUCKETS + localX / BUCKET];
    }

    /** Water tops of the lakes whose region contains the coarse cell, or null. */
    int[] lakeTops(long cellKey) {
        return this.lakeTops.get(cellKey);
    }
}
