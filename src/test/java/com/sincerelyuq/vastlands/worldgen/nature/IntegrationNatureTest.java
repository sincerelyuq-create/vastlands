package com.sincerelyuq.vastlands.worldgen.nature;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sincerelyuq.vastlands.Vastlands;
import com.sincerelyuq.vastlands.tools.PreviewNatureBiomes;
import com.sincerelyuq.vastlands.worldgen.WorldHeight;
import com.sincerelyuq.vastlands.worldgen.caves.CaveBiome;
import com.sincerelyuq.vastlands.worldgen.caves.VastlandsCaves;
import com.sincerelyuq.vastlands.worldgen.compat.ModBlock;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSetup;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSystems;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The cave formations of Sincerelyuq's Biomes' cave biomes (Agent 7) with the real caves of seed
 * 20261018, the mod's biomes standing in for their slots as the game puts them: the crystal slot
 * amethyst caves, the volcanic slot the smoldering cave, the luminous provinces the
 * bioluminescent depths. Every kind of their formations appears, the giant lumen mushrooms stay
 * rare, and the vents' smoldering magma is never open above (it would erupt by itself).
 */
class IntegrationNatureTest {
    private static final long SEED = 20261018L;

    /** Preview biomes with the mod's cave biomes in their slots. */
    private record ModCaves(PreviewNatureBiomes preview, VastlandsCaves caves) implements NatureModel.Biomes {
        @Override
        public int surfaceStyle(int x, int y, int z) {
            return this.preview.surfaceStyle(x, y, z);
        }

        @Override
        public int caveProfile(int x, int y, int z) {
            CaveBiome slot = this.caves.caveBiome(x, y, z);
            if ((slot == CaveBiome.LUSH || slot == CaveBiome.DRIPSTONE) && this.caves.luminous(x, y, z)) {
                return CaveProfile.LUMINOUS.ordinal();
            }
            return switch (slot) {
                case CRYSTAL -> CaveProfile.AMETHYST.ordinal();
                case VOLCANIC -> CaveProfile.SMOLDERING.ordinal();
                default -> this.preview.caveProfile(x, y, z);
            };
        }
    }

    @Test
    void modCaveFormationsAppearInTheirCaves() {
        GenerationSystems systems = GenerationSystems.create(new GenerationSetup(SEED, WorldHeight.MIN_Y, WorldHeight.HEIGHT, 63));
        VastlandsCaves caves = (VastlandsCaves) systems.caves();
        VastlandsNature nature = (VastlandsNature) systems.nature();
        nature.bind(this, new ModCaves(new PreviewNatureBiomes(systems), caves));
        NatureModel model = nature.model();
        FormationShapes.World world = model.world();
        Map<FormationKind, int[]> kinds = new EnumMap<>(FormationKind.class);
        int exposedSmoldering = 0;
        int smoldering = 0;
        // Squares around the amethyst, smoldering and luminous caves the game located at this seed.
        int[][] squares = {{192, 96}, {-256, 384}, {1984, 1920}, {-512, 640}, {2300, 2300}};
        for (int[] sq : squares) {
            int x0 = sq[0] - 256;
            int z0 = sq[1] - 256;
            model.formations(x0, z0, x0 + 511, z0 + 511, false, spec -> {
                if (spec.x() < x0 || spec.x() >= x0 + 512 || spec.z() < z0 || spec.z() >= z0 + 512) {
                    return;
                }
                kinds.computeIfAbsent(spec.kind(), k -> new int[1])[0]++;
            });
            int[] exposed = {0, 0};
            model.formations(x0, z0, x0 + 511, z0 + 511, false, spec -> {
                if (spec.kind() != FormationKind.MAGMA_VENT && spec.kind() != FormationKind.GEOTHERMAL_FIELD
                        || spec.x() < x0 || spec.x() >= x0 + 512 || spec.z() < z0 || spec.z() >= z0 + 512) {
                    return;
                }
                FormationShape shape = model.shape(spec, world);
                java.util.Set<Integer> cells = new java.util.HashSet<>();
                for (int i = 0; i < shape.size(); i++) {
                    if (FormationBlocks.isSolid(shape.codes[i])) {
                        cells.add(shape.positions[i]);
                    }
                }
                for (int i = 0; i < shape.size(); i++) {
                    int code = shape.codes[i];
                    if (FormationBlocks.kind(code) == FormationBlocks.MOD && FormationBlocks.modBlock(code) == ModBlock.SMOLDERING_MAGMA) {
                        exposed[1]++;
                        int p = shape.positions[i];
                        int above = FormationShape.pack(FormationShape.dx(p), FormationShape.dy(p) + 1, FormationShape.dz(p));
                        if (!cells.contains(above) && world.isCave(spec.x() + FormationShape.dx(p), spec.y() + FormationShape.dy(p) + 1,
                                spec.z() + FormationShape.dz(p))) {
                            exposed[0]++;
                        }
                    }
                }
            });
            exposedSmoldering += exposed[0];
            smoldering += exposed[1];
        }
        StringBuilder report = new StringBuilder();
        kinds.forEach((k, n) -> report.append(k).append(' ').append(n[0]).append(", "));
        Vastlands.LOGGER.info("IntegrationNatureTest: cave formations {}; smoldering magma {} ({} open above)", report, smoldering, exposedSmoldering);
        for (FormationKind k : new FormationKind[] {FormationKind.LUMEN_MUSHROOM, FormationKind.LUMEN_COLONY, FormationKind.LUMEN_GROWTH,
                FormationKind.POOL, FormationKind.CRYSTAL_GARDEN, FormationKind.CRYSTAL_SPIRES, FormationKind.MAGMA_VENT}) {
            assertTrue(kinds.containsKey(k), k + " never chosen");
        }
        int giants = kinds.getOrDefault(FormationKind.LUMEN_GIANT, new int[1])[0];
        int mushrooms = kinds.getOrDefault(FormationKind.LUMEN_MUSHROOM, new int[1])[0];
        assertTrue(giants * 5 <= mushrooms, "giant lumen mushrooms not rare: " + giants + " of " + mushrooms);
        assertTrue(smoldering > 0, "no smoldering magma in any vent");
        assertTrue(exposedSmoldering == 0, "smoldering magma open above: " + exposedSmoldering);
    }
}
