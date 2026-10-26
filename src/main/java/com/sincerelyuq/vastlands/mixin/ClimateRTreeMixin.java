package com.sincerelyuq.vastlands.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sincerelyuq.vastlands.worldgen.forest.ExactClimateSearch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * During the forests' biome lookups ({@link ExactClimateSearch#call}), the climate index searches
 * from scratch instead of from the thread's last result, and does not replace that result: exact
 * ties then always resolve the same way. Every other search is vanilla's.
 */
@Mixin(targets = "net.minecraft.world.level.biome.Climate$RTree")
public abstract class ClimateRTreeMixin {
    @WrapOperation(method = "search", at = @At(value = "INVOKE", target = "Ljava/lang/ThreadLocal;get()Ljava/lang/Object;"))
    private Object vastlands$searchFromScratch(ThreadLocal<?> lastResult, Operation<Object> original) {
        return ExactClimateSearch.active() ? null : original.call(lastResult);
    }

    @WrapOperation(method = "search", at = @At(value = "INVOKE", target = "Ljava/lang/ThreadLocal;set(Ljava/lang/Object;)V"))
    private void vastlands$keepLastResult(ThreadLocal<Object> lastResult, Object leaf, Operation<Void> original) {
        if (!ExactClimateSearch.active()) {
            original.call(lastResult, leaf);
        }
    }
}
