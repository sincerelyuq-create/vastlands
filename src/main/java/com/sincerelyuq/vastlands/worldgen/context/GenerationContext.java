package com.sincerelyuq.vastlands.worldgen.context;

import com.sincerelyuq.vastlands.worldgen.system.GenerationSetup;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSystems;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
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
    private final GenerationSetup setup;
    private final GenerationSystems systems;

    public GenerationContext(GenerationSetup setup) {
        this.setup = setup;
        this.systems = GenerationSystems.create(setup);
    }

    public static Optional<GenerationContext> of(RandomState randomState) {
        return Optional.ofNullable(((RandomStateExtension) (Object) randomState).vastlands$generationContext());
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
}
