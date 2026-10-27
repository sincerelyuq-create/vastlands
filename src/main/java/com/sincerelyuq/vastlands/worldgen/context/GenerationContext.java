package com.sincerelyuq.vastlands.worldgen.context;

import com.sincerelyuq.vastlands.worldgen.biome.RegionalBiomes;
import com.sincerelyuq.vastlands.worldgen.caves.VastlandsCaves;
import com.sincerelyuq.vastlands.worldgen.hydrology.VastlandsHydrology;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSetup;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSystems;
import com.sincerelyuq.vastlands.worldgen.terrain.TerrainColumn;
import com.google.common.collect.MapMaker;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Per-world state shared by all Vastlands generation systems.
 *
 * <p>A context belongs to exactly one {@link RandomState}, which vanilla creates once per dimension
 * from the world seed and the dimension's noise settings. It is created while that RandomState wires
 * its noise router, and only if the router contains a Vastlands stage (see
 * {@code StageDensityFunction}). Dimensions without Vastlands stages have no context.
 */
public final class GenerationContext {
    /**
     * The context of each climate sampler a Vastlands {@code RandomState} built, so biome sources
     * (which only get the sampler) find their world's systems. Weak, identity-keyed keys.
     */
    private static final ConcurrentMap<Climate.Sampler, GenerationContext> SAMPLERS = new MapMaker().weakKeys().makeMap();
    /** The same for the wired noise router of each Vastlands {@code RandomState}: per-chunk samplers are built from it. */
    private static final ConcurrentMap<NoiseRouter, GenerationContext> ROUTERS = new MapMaker().weakKeys().makeMap();

    private final GenerationSetup setup;
    private final GenerationSystems systems;
    /** Built on first use (the biome source asks for it once tags name a regional biome). */
    @Nullable
    private volatile RegionalBiomes regions;

    public GenerationContext(GenerationSetup setup) {
        this.setup = setup;
        this.systems = GenerationSystems.create(setup);
    }

    public static Optional<GenerationContext> of(RandomState randomState) {
        return Optional.ofNullable(((RandomStateExtension) (Object) randomState).vastlands$generationContext());
    }

    /** Called by {@code RandomStateMixin} once the sampler of a Vastlands {@code RandomState} exists. */
    public static void bind(Climate.Sampler sampler, GenerationContext context) {
        SAMPLERS.put(sampler, context);
    }

    /** Called by {@code RandomStateMixin} with the wired router of a Vastlands {@code RandomState}. */
    public static void bind(NoiseRouter router, GenerationContext context) {
        ROUTERS.put(router, context);
    }

    /** The context whose {@code RandomState} wired a noise router, or null. */
    @Nullable
    public static GenerationContext of(NoiseRouter router) {
        return ROUTERS.get(router);
    }

    /**
     * The context of the {@code RandomState} that built a climate sampler (directly, or a chunk's
     * cached sampler from its router, see {@code NoiseChunkMixin}), or null (vanilla worlds, other
     * dimensions).
     */
    @Nullable
    public static GenerationContext of(Climate.Sampler sampler) {
        return SAMPLERS.get(sampler);
    }

    /** For code running inside a level, e.g. a feature: {@code GenerationContext.of(featureContext.level().getLevel())}. */
    public static Optional<GenerationContext> of(ServerLevel level) {
        return of(level.getChunkSource().randomState());
    }

    public GenerationSetup setup() {
        return this.setup;
    }

    public GenerationSystems systems() {
        return this.systems;
    }

    /** The regional surface biomes of this world (see {@link RegionalBiomes}). */
    public RegionalBiomes regions() {
        RegionalBiomes r = this.regions;
        if (r == null) {
            synchronized (this) {
                r = this.regions;
                if (r == null) {
                    GenerationSystems s = this.systems;
                    RegionalBiomes.Land land = new RegionalBiomes.Land() {
                        @Override
                        public TerrainColumn column(int x, int z) {
                            return s.terrain().column(x, z);
                        }

                        @Override
                        public double volcanic(int x, int z) {
                            return s.caves() instanceof VastlandsCaves caves ? caves.geology(x, z)[2] : 0.0;
                        }

                        @Override
                        public double valleyDepth(int x, int z) {
                            return s.hydrology() instanceof VastlandsHydrology rivers
                                    ? Math.max(0.0, s.terrain().column(x, z).height() - rivers.carvedHeight(x, z)) : 0.0;
                        }
                    };
                    r = new RegionalBiomes(this.setup.random("regions").fromHashOf("regional_biomes").nextLong(), land, this.setup.seaLevel());
                    this.regions = r;
                }
            }
        }
        return r;
    }
}
