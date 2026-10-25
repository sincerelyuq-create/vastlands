package com.sincerelyuq.vastlands.mixin;

import com.sincerelyuq.vastlands.worldgen.context.GenerationContext;
import java.util.List;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseRouter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Chunk biome filling ({@code NoiseBasedChunkGenerator.doCreateBiomes}) does not use the
 * {@code RandomState}'s climate sampler but a per-chunk sampler with cached density functions
 * from {@code NoiseChunk.cachedClimateSampler}. The Vastlands biome source finds its world's caves
 * through the sampler it is given, so this registers each such sampler with the context of the
 * router it was built from (see {@link GenerationContext#bind(NoiseRouter, GenerationContext)}).
 * Routers of other worlds have no context: nothing changes there.
 */
@Mixin(NoiseChunk.class)
public abstract class NoiseChunkMixin {
    @Inject(method = "cachedClimateSampler", at = @At("RETURN"))
    private void vastlands$bindCachedSampler(NoiseRouter router, List<Climate.ParameterPoint> points, CallbackInfoReturnable<Climate.Sampler> info) {
        GenerationContext context = GenerationContext.of(router);
        if (context != null) {
            GenerationContext.bind(info.getReturnValue(), context);
        }
    }
}
