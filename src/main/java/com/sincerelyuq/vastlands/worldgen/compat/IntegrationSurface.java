package com.sincerelyuq.vastlands.worldgen.compat;

import com.google.common.collect.MapMaker;
import com.sincerelyuq.vastlands.Vastlands;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.placement.CaveSurface;

/**
 * Surface rules of other mods' biomes in Vastlands worlds. Sincerelyuq's Biomes adds its surface
 * rules only to dimensions of vanilla's overworld type, which the Vastlands overworld is not
 * ({@code vastlands:overworld}), so without this its biomes would get vanilla's stone. The rules
 * here run before the Vastlands rules ({@code NoiseBasedChunkGeneratorMixin}, Vastlands worlds
 * only) and are built from vanilla's rule builders with the mod's blocks looked up by id, so
 * nothing is built and nothing changes when the mod is absent:
 * <ul>
 *     <li>painted strata: the mod's own colour stack of terracotta bands (its 60-block pattern,
 *     drifting by the clay band noise like the mod's) on steep ground and above its valley line
 *     (Y 86), over the whole Vastlands height (the mod's stack stops at Y 319); grass, dirt and
 *     gravel in the valleys below;</li>
 *     <li>ashen wastes: ash over tuff, gravel under water, exposed basalt, smooth basalt and
 *     blackstone on steep slopes and in streaks of bare rock;</li>
 *     <li>bone dunes: sand over sandstone, like vanilla's deserts;</li>
 *     <li>lotus pools and the twilight thicket: grass over dirt (clay, gravel under water);</li>
 *     <li>the amethyst caves: floors of amethyst with calcite streaks and patches of smooth basalt
 *     (basalt under water), calcite under them; ceilings of amethyst with some glowing amethyst
 *     and calcite streaks, calcite over them;</li>
 *     <li>the smoldering cave: netherrack floors with patches of magma (not a carpet: the mod's
 *     own rule makes every floor magma) and blackstone rims, basalt on some ceilings; the
 *     eruptive smoldering magma comes only with the cave formations, which keep clear of
 *     structures;</li>
 *     <li>the bioluminescent depths: lumen moss in patches over tuff, clay under water.</li>
 * </ul>
 */
public final class IntegrationSurface {
    private static final String NS = ModBlock.NAMESPACE;
    static final ResourceKey<Biome> PAINTED_STRATA = biome("painted_strata");
    static final ResourceKey<Biome> ASHEN_WASTES = biome("ashen_wastes");
    static final ResourceKey<Biome> BONE_DUNES = biome("bone_dunes");
    static final ResourceKey<Biome> LOTUS_POOLS = biome("lotus_pools");
    static final ResourceKey<Biome> TWILIGHT_THICKET = biome("twilight_thicket");
    static final ResourceKey<Biome> AMETHYST_CAVES = biome("amethystcaves");
    static final ResourceKey<Biome> SMOLDERING_CAVE = biome("smoldering_cave");
    static final ResourceKey<Biome> BIOLUMINESCENT_DEPTHS = biome("bioluminescent_depths");

    /** The mod's strata colour stack (its {@code StrataSurface.STACK}), one letter per block of height. */
    static final String STRATA_STACK = "GGGLLLWMMMMUMMUUWLLLLGGGYBBMMMWWLLLGGGGNUMMMMMUBBWLLLGGNMMMAW";
    private static final int VALLEY_LINE = 86;
    private static final int BANDS_FROM = 40;
    private static final int BANDS_TO = 511;
    private static final int[] DRIFT = {-2, -1, 0, 1, 2};

    private static volatile Optional<SurfaceRules.RuleSource> rules;
    /** The Vastlands rule with these prepended, per original rule (identity). */
    private static final ConcurrentMap<SurfaceRules.RuleSource, SurfaceRules.RuleSource> WRAPPED = new MapMaker().weakKeys().makeMap();

    private IntegrationSurface() {
    }

    private static ResourceKey<Biome> biome(String path) {
        return ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(NS, path));
    }

    /** The surface rule of a Vastlands world: the integration rules first, then the original. */
    public static SurfaceRules.RuleSource wrap(SurfaceRules.RuleSource original) {
        Optional<SurfaceRules.RuleSource> extra = rules();
        if (extra.isEmpty()) {
            return original;
        }
        return WRAPPED.computeIfAbsent(original, o -> SurfaceRules.sequence(extra.get(), o));
    }

    /** The integration rules, or empty when no supported mod is installed. Built once. */
    public static Optional<SurfaceRules.RuleSource> rules() {
        Optional<SurfaceRules.RuleSource> r = rules;
        if (r == null) {
            synchronized (IntegrationSurface.class) {
                r = rules;
                if (r == null) {
                    r = Optional.ofNullable(build());
                    rules = r;
                    Vastlands.LOGGER.info("Vastlands integration surface rules: {}", r.isPresent() ? "Sincerelyuq's Biomes" : "none (no supported mod)");
                }
            }
        }
        return r;
    }

    private static SurfaceRules.RuleSource build() {
        if (!CompatBlocks.biomesInstalled()) {
            return null;
        }
        BlockState ash = CompatBlocks.state(ModBlock.ASH_BLOCK);
        BlockState lumenMoss = CompatBlocks.state(ModBlock.LUMEN_MOSS);
        BlockState glowingAmethyst = CompatBlocks.state(ModBlock.GLOWING_AMETHYST);
        SurfaceRules.ConditionSource onFloor = SurfaceRules.stoneDepthCheck(0, false, CaveSurface.FLOOR);
        SurfaceRules.ConditionSource underFloor = SurfaceRules.stoneDepthCheck(0, true, CaveSurface.FLOOR);
        SurfaceRules.ConditionSource deepUnderFloor = SurfaceRules.stoneDepthCheck(0, true, 6, CaveSurface.FLOOR);
        SurfaceRules.ConditionSource onCeiling = SurfaceRules.stoneDepthCheck(0, false, CaveSurface.CEILING);
        SurfaceRules.ConditionSource overCeiling = SurfaceRules.stoneDepthCheck(0, true, CaveSurface.CEILING);
        SurfaceRules.ConditionSource dry = SurfaceRules.waterBlockCheck(-1, 0);
        // Cheap conditions first, the biome last: a biome condition looks the biome up again at
        // every height of every column it is tested on (vanilla's rules test theirs only near the
        // surface), so the surface biomes are tested only above the preliminary surface and the
        // cave biomes only on cave floors and ceilings.
        List<SurfaceRules.RuleSource> surface = new ArrayList<>();

        // Painted strata: bands on steep ground and above the valley line, the mod's own palette.
        SurfaceRules.RuleSource bands = driftingBands();
        surface.add(SurfaceRules.ifTrue(SurfaceRules.isBiome(PAINTED_STRATA), SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.yBlockCheck(VerticalAnchor.absolute(VALLEY_LINE), 2), bands),
                SurfaceRules.ifTrue(SurfaceRules.steep(), bands),
                SurfaceRules.ifTrue(onFloor, SurfaceRules.sequence(SurfaceRules.ifTrue(dry, state(Blocks.GRASS_BLOCK)), state(Blocks.GRAVEL))),
                SurfaceRules.ifTrue(underFloor, state(Blocks.DIRT)))));

        // Ashen wastes: ash over tuff; bare volcanic rock on steep slopes and in streaks.
        SurfaceRules.RuleSource bareRock = SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.GRAVEL, -0.12, 0.18), state(Blocks.SMOOTH_BASALT)),
                SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.PATCH, 0.15), state(Blocks.BLACKSTONE)),
                state(Blocks.BASALT));
        surface.add(SurfaceRules.ifTrue(SurfaceRules.isBiome(ASHEN_WASTES), SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.steep(), bareRock),
                SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.CALCITE, -0.045, 0.045), bareRock),
                SurfaceRules.ifTrue(onFloor, SurfaceRules.sequence(SurfaceRules.ifTrue(dry, SurfaceRules.state(ash)), state(Blocks.GRAVEL))),
                SurfaceRules.ifTrue(underFloor, state(Blocks.TUFF)))));

        // Bone dunes: sand over sandstone.
        surface.add(SurfaceRules.ifTrue(SurfaceRules.isBiome(BONE_DUNES), SurfaceRules.sequence(
                SurfaceRules.ifTrue(onFloor, state(Blocks.SAND)),
                SurfaceRules.ifTrue(underFloor, state(Blocks.SAND)),
                SurfaceRules.ifTrue(deepUnderFloor, state(Blocks.SANDSTONE)))));

        // Lotus pools and the twilight thicket: grass over dirt.
        surface.add(SurfaceRules.ifTrue(SurfaceRules.isBiome(LOTUS_POOLS), SurfaceRules.sequence(
                SurfaceRules.ifTrue(onFloor, SurfaceRules.sequence(SurfaceRules.ifTrue(dry, state(Blocks.GRASS_BLOCK)), state(Blocks.CLAY))),
                SurfaceRules.ifTrue(underFloor, state(Blocks.DIRT)))));
        surface.add(SurfaceRules.ifTrue(SurfaceRules.isBiome(TWILIGHT_THICKET), SurfaceRules.sequence(
                SurfaceRules.ifTrue(onFloor, SurfaceRules.sequence(SurfaceRules.ifTrue(dry, state(Blocks.GRASS_BLOCK)), state(Blocks.GRAVEL))),
                SurfaceRules.ifTrue(underFloor, state(Blocks.DIRT)))));

        // Cave biomes, at any depth above the bedrock floor: floors, the ground under them, ceilings.
        SurfaceRules.RuleSource caveFloors = SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.isBiome(AMETHYST_CAVES), SurfaceRules.sequence(
                        SurfaceRules.ifTrue(SurfaceRules.not(dry), state(Blocks.SMOOTH_BASALT)),
                        SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.CALCITE, -0.05, 0.05), state(Blocks.CALCITE)),
                        SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.PATCH, 0.3), state(Blocks.SMOOTH_BASALT)),
                        state(Blocks.AMETHYST_BLOCK))),
                SurfaceRules.ifTrue(SurfaceRules.isBiome(SMOLDERING_CAVE), SurfaceRules.sequence(
                        SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.PATCH, 0.22), state(Blocks.MAGMA_BLOCK)),
                        SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.PATCH, 0.1), state(Blocks.BLACKSTONE)),
                        SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.GRAVEL, -0.15, 0.15), state(Blocks.BASALT)),
                        state(Blocks.NETHERRACK))),
                SurfaceRules.ifTrue(SurfaceRules.isBiome(BIOLUMINESCENT_DEPTHS), SurfaceRules.sequence(
                        SurfaceRules.ifTrue(SurfaceRules.not(dry), state(Blocks.CLAY)),
                        SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.PATCH, -0.08), SurfaceRules.state(lumenMoss)),
                        SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.GRAVEL, -0.12, 0.12), state(Blocks.CLAY)),
                        state(Blocks.TUFF))));
        SurfaceRules.RuleSource caveUnder = SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.isBiome(AMETHYST_CAVES), state(Blocks.CALCITE)),
                SurfaceRules.ifTrue(SurfaceRules.isBiome(SMOLDERING_CAVE), state(Blocks.NETHERRACK)),
                SurfaceRules.ifTrue(SurfaceRules.isBiome(BIOLUMINESCENT_DEPTHS), state(Blocks.TUFF)));
        // Amethyst ceilings in place of the mod's ceiling patches (replaced: they reached only
        // ceilings within 12 blocks of a random height in Y -250..48), glowing amethyst one block in
        // about twenty as in the mod's patches, calcite streaks and calcite behind.
        List<SurfaceRules.RuleSource> amethystCeiling = new ArrayList<>();
        amethystCeiling.add(SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.CALCITE, -0.05, 0.05), state(Blocks.CALCITE)));
        if (glowingAmethyst != null) {
            amethystCeiling.add(SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.POWDER_SNOW, 0.45), SurfaceRules.state(glowingAmethyst)));
        }
        amethystCeiling.add(state(Blocks.AMETHYST_BLOCK));
        SurfaceRules.RuleSource caveCeilings = SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.isBiome(AMETHYST_CAVES), SurfaceRules.sequence(amethystCeiling.toArray(SurfaceRules.RuleSource[]::new))),
                SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.GRAVEL, -0.2, 0.2),
                        SurfaceRules.ifTrue(SurfaceRules.isBiome(SMOLDERING_CAVE), state(Blocks.BASALT))));
        SurfaceRules.RuleSource caveOverCeiling = SurfaceRules.ifTrue(SurfaceRules.isBiome(AMETHYST_CAVES), state(Blocks.CALCITE));

        List<SurfaceRules.RuleSource> rules = new ArrayList<>();
        rules.add(SurfaceRules.ifTrue(SurfaceRules.abovePreliminarySurface(), SurfaceRules.sequence(surface.toArray(SurfaceRules.RuleSource[]::new))));
        rules.add(SurfaceRules.ifTrue(SurfaceRules.yBlockCheck(VerticalAnchor.aboveBottom(6), 0), SurfaceRules.sequence(
                SurfaceRules.ifTrue(onFloor, caveFloors),
                SurfaceRules.ifTrue(underFloor, caveUnder),
                SurfaceRules.ifTrue(onCeiling, caveCeilings),
                SurfaceRules.ifTrue(overCeiling, caveOverCeiling))));
        return SurfaceRules.sequence(rules.toArray(SurfaceRules.RuleSource[]::new));
    }

    private static SurfaceRules.RuleSource state(Block block) {
        return SurfaceRules.state(block.defaultBlockState());
    }

    /** The strata bands drifting by ±2 blocks with the clay band noise, as the mod does. */
    private static SurfaceRules.RuleSource driftingBands() {
        List<SurfaceRules.RuleSource> slices = new ArrayList<>();
        double step = 2.0 / DRIFT.length;
        for (int i = 0; i < DRIFT.length - 1; i++) {
            double from = -1.0 + step * i;
            double to = from + step;
            SurfaceRules.ConditionSource inSlice = i == 0 ? SurfaceRules.not(SurfaceRules.noiseCondition(Noises.CLAY_BANDS_OFFSET, to))
                    : SurfaceRules.noiseCondition(Noises.CLAY_BANDS_OFFSET, from, to);
            slices.add(SurfaceRules.ifTrue(inSlice, stack(DRIFT[i])));
        }
        slices.add(stack(DRIFT[DRIFT.length - 1]));
        return SurfaceRules.ifTrue(SurfaceRules.yBlockCheck(VerticalAnchor.absolute(BANDS_FROM), 0),
                SurfaceRules.sequence(slices.toArray(SurfaceRules.RuleSource[]::new)));
    }

    /** One drifted stack as a binary search over runs of one colour (few conditions per block). */
    private static SurfaceRules.RuleSource stack(int shift) {
        List<int[]> runs = new ArrayList<>();
        int runStart = BANDS_FROM;
        int runColour = colour(BANDS_FROM + shift);
        for (int y = BANDS_FROM + 1; y <= BANDS_TO; y++) {
            int c = colour(y + shift);
            if (c != runColour) {
                runs.add(new int[] {runStart, runColour});
                runStart = y;
                runColour = c;
            }
        }
        runs.add(new int[] {runStart, runColour});
        return search(runs, 0, runs.size() - 1);
    }

    private static SurfaceRules.RuleSource search(List<int[]> runs, int lo, int hi) {
        if (lo == hi) {
            return SurfaceRules.state(terracotta(runs.get(lo)[1]).defaultBlockState());
        }
        int mid = (lo + hi + 1) / 2;
        return SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.yBlockCheck(VerticalAnchor.absolute(runs.get(mid)[0]), 0), search(runs, mid, hi)),
                search(runs, lo, mid - 1));
    }

    /** The terracotta of the strata stack at a height (formations of the painted strata use the same bands). */
    public static Block strataBand(int y) {
        return terracotta(colour(y));
    }

    static int colour(int y) {
        return STRATA_STACK.charAt(Math.floorMod(y, STRATA_STACK.length()));
    }

    /** The terracotta of a letter of the strata stack. */
    static Block terracotta(int letter) {
        return switch (letter) {
            case 'A' -> Blocks.BLUE_TERRACOTTA;
            case 'B' -> Blocks.LIGHT_BLUE_TERRACOTTA;
            case 'G' -> Blocks.GREEN_TERRACOTTA;
            case 'L' -> Blocks.LIME_TERRACOTTA;
            case 'M' -> Blocks.MAGENTA_TERRACOTTA;
            case 'N' -> Blocks.BROWN_TERRACOTTA;
            case 'P' -> Blocks.PINK_TERRACOTTA;
            case 'U' -> Blocks.PURPLE_TERRACOTTA;
            case 'W' -> Blocks.WHITE_TERRACOTTA;
            case 'Y' -> Blocks.YELLOW_TERRACOTTA;
            default -> Blocks.TERRACOTTA;
        };
    }
}
