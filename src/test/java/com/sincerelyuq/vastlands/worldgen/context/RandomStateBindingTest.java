package com.sincerelyuq.vastlands.worldgen.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sincerelyuq.vastlands.worldgen.density.StageDensityFunction;
import com.sincerelyuq.vastlands.worldgen.system.CaveSystem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Exercises {@code RandomStateMixin} through vanilla's real {@link RandomState} construction. */
class RandomStateBindingTest {
    private static final long SEED = 1234567L;

    private static HolderLookup.RegistryLookup<NormalNoise.NoiseParameters> noises;
    private static NoiseGeneratorSettings vanilla;
    private static NoiseGeneratorSettings wrapped;

    @BeforeAll
    static void setUp() {
        HolderLookup.Provider registries = VanillaRegistries.createLookup();
        noises = registries.lookupOrThrow(Registries.NOISE);
        vanilla = registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        wrapped = withFinalDensity(vanilla, new StageDensityFunction(StageDensityFunction.Stage.CAVES, vanilla.noiseRouter().finalDensity(), null));
    }

    @Test
    void vanillaSettingsGetNoContext() {
        assertTrue(GenerationContext.of(RandomState.create(vanilla, noises, SEED)).isEmpty());
    }

    @Test
    void stageInRouterCreatesContextAndBindsIt() {
        RandomState state = RandomState.create(wrapped, noises, SEED);
        GenerationContext context = GenerationContext.of(state).orElseThrow();

        assertEquals(SEED, context.setup().worldSeed());
        assertEquals(vanilla.noiseSettings().minY(), context.setup().minY());
        assertEquals(vanilla.seaLevel(), context.setup().seaLevel());
        StageDensityFunction finalDensity = assertInstanceOf(StageDensityFunction.class, state.router().finalDensity());
        assertSame(context.systems().caves(), finalDensity.implementation());
    }

    /** The wrapped final density is exactly the bound cave system applied to vanilla's density. */
    @Test
    void cavesStageAppliesTheBoundSystemToTheVanillaDensity() {
        RandomState vanillaState = RandomState.create(vanilla, noises, SEED);
        RandomState wrappedState = RandomState.create(wrapped, noises, SEED);
        CaveSystem caves = GenerationContext.of(wrappedState).orElseThrow().systems().caves();
        for (int x = -3000; x <= 3000; x += 777) {
            for (int z = -3000; z <= 3000; z += 913) {
                for (int y = -64; y <= 320; y += 37) {
                    DensityFunction.SinglePointContext pos = new DensityFunction.SinglePointContext(x, y, z);
                    assertEquals(
                            caves.apply(x, y, z, vanillaState.router().finalDensity().compute(pos)),
                            wrappedState.router().finalDensity().compute(pos),
                            "density differs at " + pos);
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static NoiseGeneratorSettings withFinalDensity(NoiseGeneratorSettings s, DensityFunction finalDensity) {
        NoiseRouter r = s.noiseRouter();
        NoiseRouter router = new NoiseRouter(
                r.barrierNoise(), r.fluidLevelFloodednessNoise(), r.fluidLevelSpreadNoise(), r.lavaNoise(),
                r.temperature(), r.vegetation(), r.continents(), r.erosion(), r.depth(), r.ridges(),
                r.initialDensityWithoutJaggedness(), finalDensity, r.veinToggle(), r.veinRidged(), r.veinGap());
        return new NoiseGeneratorSettings(
                s.noiseSettings(), s.defaultBlock(), s.defaultFluid(), router, s.surfaceRule(), s.spawnTarget(),
                s.seaLevel(), s.disableMobGeneration(), s.aquifersEnabled(), s.oreVeinsEnabled(), s.useLegacyRandomSource());
    }
}
