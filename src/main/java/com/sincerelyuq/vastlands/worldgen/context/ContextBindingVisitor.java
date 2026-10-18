package com.sincerelyuq.vastlands.worldgen.context;

import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Wraps the visitor {@code RandomState} uses to wire its noise router, so that Vastlands density
 * functions can bind themselves to the world's {@link GenerationContext} during that same pass.
 * Everything else is delegated unchanged. The context is created on first request, so routers
 * without Vastlands functions never create one.
 *
 * <p>Used on a single thread, inside the {@code RandomState} constructor.
 */
public final class ContextBindingVisitor implements DensityFunction.Visitor {
    private final DensityFunction.Visitor delegate;
    private final Supplier<GenerationContext> contextFactory;
    @Nullable
    private GenerationContext context;

    public ContextBindingVisitor(DensityFunction.Visitor delegate, Supplier<GenerationContext> contextFactory) {
        this.delegate = delegate;
        this.contextFactory = contextFactory;
    }

    public GenerationContext context() {
        if (this.context == null) {
            this.context = this.contextFactory.get();
        }
        return this.context;
    }

    /** The context if any function requested it, otherwise {@code null}. */
    @Nullable
    public GenerationContext createdContext() {
        return this.context;
    }

    @Override
    public DensityFunction apply(DensityFunction densityFunction) {
        return this.delegate.apply(densityFunction);
    }

    @Override
    public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noiseHolder) {
        return this.delegate.visitNoise(noiseHolder);
    }
}
