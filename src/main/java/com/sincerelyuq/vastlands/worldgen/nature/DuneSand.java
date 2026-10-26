package com.sincerelyuq.vastlands.worldgen.nature;

import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.smoothstep;

import com.sincerelyuq.vastlands.worldgen.biome.DuneField;
import com.sincerelyuq.vastlands.worldgen.biome.VastlandsBiomeSource;
import com.sincerelyuq.vastlands.worldgen.compat.IntegrationFeatures;
import com.sincerelyuq.vastlands.worldgen.context.GenerationContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The block step of the dunes ({@link DuneField}): sand heaped on the sand of the bone dunes, after
 * the surface formations (a rock among the dunes stays half buried) and before structures and
 * vegetation (dead bushes and cacti grow on the dunes; Sincerelyuq's Biomes' ribcages and skulls,
 * placed at the ground under the dunes, come out of them half buried).
 *
 * <p>Each column only writes itself, from pure queries and what a decorating region may read:
 * <ul>
 *     <li>the field's amplitude on a 4-block lattice (bilinear between its points);</li>
 *     <li>water (rivers, lakes) on a 4-block lattice: the dunes fall away within 12 blocks of it;</li>
 *     <li>structure pieces reaching the ground within 12 blocks (references of this chunk and its
 *     neighbours): the dunes fall away to nothing at them, so a ruin, a well or a pyramid stands
 *     in a hollow of the dunes, never buried;</li>
 *     <li>the column's own biome (bone dunes only) and its top block (sand, open above).</li>
 * </ul>
 * Nothing outside Vastlands worlds or without a dune sea biome.
 */
public final class DuneSand {
    private static final int LATTICE = 4;
    private static final int WATER_REACH = 12;
    private static final int STRUCTURE_REACH = 12;

    private DuneSand() {
    }

    /** Heaps the dunes of one chunk; returns the number of sand blocks placed. */
    public static int build(WorldGenLevel level, ChunkAccess chunk, ChunkGenerator generator, NatureModel model) {
        if (!(generator.getBiomeSource() instanceof VastlandsBiomeSource source)) {
            return 0;
        }
        int enabled = source.regionMask();
        Optional<GenerationContext> context = GenerationContext.of(level.getLevel());
        if (context.isEmpty() || enabled == 0) {
            return 0;
        }
        DuneField dunes = context.get().regions().dunes();
        ChunkPos pos = chunk.getPos();
        int minX = pos.getMinBlockX();
        int minZ = pos.getMinBlockZ();
        // The amplitude at the lattice points of the chunk (corners included).
        int n = 16 / LATTICE + 1;
        double[] amp = new double[n * n];
        boolean any = false;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                double a = dunes.amplitude(minX + i * LATTICE, minZ + j * LATTICE, enabled);
                amp[j * n + i] = a;
                any |= a > 0.0;
            }
        }
        if (!any) {
            return 0;
        }
        FormationShapes.World world = model.world();
        // Water on the lattice around the chunk.
        List<int[]> wet = new ArrayList<>();
        for (int z = minZ - WATER_REACH; z <= minZ + 15 + WATER_REACH; z += LATTICE) {
            for (int x = minX - WATER_REACH; x <= minX + 15 + WATER_REACH; x += LATTICE) {
                if (world.waterTop(x, z) != Integer.MIN_VALUE) {
                    wet.add(new int[] {x, z});
                }
            }
        }
        List<BoundingBox> pieces = IntegrationFeatures.piecesAround(level, pos.x, pos.z);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockState sand = Blocks.SAND.defaultBlockState();
        int placed = 0;
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int x = minX + lx;
                int z = minZ + lz;
                double a = bilinear(amp, n, lx / (double) LATTICE, lz / (double) LATTICE);
                if (a <= 0.0) {
                    continue;
                }
                double h = a * dunes.height(x, z);
                if (h < 0.5) {
                    continue;
                }
                h *= smoothstep(3.0, WATER_REACH, nearest(wet, x, z));
                if (h < 0.5) {
                    continue;
                }
                int ground = top(chunk, m, x, world.groundTop(x, z), z);
                if (ground == Integer.MIN_VALUE || !chunk.getBlockState(m.set(x, ground, z)).is(Blocks.SAND)
                        || NatureBiomes.style(chunk.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(ground), QuartPos.fromBlock(z)))
                        != SurfaceStyle.DUNES) {
                    continue;
                }
                h *= smoothstep(2.0, STRUCTURE_REACH, distance(pieces, x, ground, z));
                int blocks = (int) Math.floor(h + 0.5);
                for (int k = 1; k <= blocks; k++) {
                    int y = ground + k;
                    if (y > chunk.getMaxY() || !chunk.getBlockState(m.set(x, y, z)).isAir()) {
                        break;
                    }
                    chunk.setBlockState(m, sand, Block.UPDATE_CLIENTS);
                    placed++;
                }
            }
        }
        return placed;
    }

    /** The top block of a column near the model's ground (formations may stand on it), or {@link Integer#MIN_VALUE}. */
    private static int top(ChunkAccess chunk, BlockPos.MutableBlockPos m, int x, int ground, int z) {
        for (int y = Math.min(chunk.getMaxY() - 1, ground + 10); y >= Math.max(chunk.getMinY(), ground - 4); y--) {
            if (!chunk.getBlockState(m.set(x, y, z)).isAir()) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    private static double bilinear(double[] v, int n, double fx, double fz) {
        int i = Math.min(n - 2, (int) fx);
        int j = Math.min(n - 2, (int) fz);
        double tx = fx - i;
        double tz = fz - j;
        double a = v[j * n + i] + (v[j * n + i + 1] - v[j * n + i]) * tx;
        double b = v[(j + 1) * n + i] + (v[(j + 1) * n + i + 1] - v[(j + 1) * n + i]) * tx;
        return a + (b - a) * tz;
    }

    private static double nearest(List<int[]> points, int x, int z) {
        double best = Double.MAX_VALUE;
        for (int[] p : points) {
            best = Math.min(best, Math.hypot(p[0] - x, p[1] - z));
        }
        return best;
    }

    /** Horizontal distance to the nearest structure piece reaching the ground (8 below to 32 above it). */
    private static double distance(List<BoundingBox> pieces, int x, int ground, int z) {
        double best = Double.MAX_VALUE;
        for (BoundingBox b : pieces) {
            if (b.maxY() < ground - 8 || b.minY() > ground + 32) {
                continue;
            }
            int dx = Math.max(0, Math.max(b.minX() - x, x - b.maxX()));
            int dz = Math.max(0, Math.max(b.minZ() - z, z - b.maxZ()));
            best = Math.min(best, Math.hypot(dx, dz));
        }
        return best;
    }
}
