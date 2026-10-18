package com.sincerelyuq.vastlands.worldgen.context;

import javax.annotation.Nullable;

/** Implemented by {@code RandomState} through {@code RandomStateMixin}. Use {@link GenerationContext#of} instead. */
public interface RandomStateExtension {
    @Nullable
    GenerationContext vastlands$generationContext();
}
