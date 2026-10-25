package com.sincerelyuq.vastlands.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.sincerelyuq.vastlands.worldgen.context.ContextBindingVisitor;
import com.sincerelyuq.vastlands.worldgen.context.GenerationContext;
import com.sincerelyuq.vastlands.worldgen.context.RandomStateExtension;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSetup;
import javax.annotation.Nullable;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives density functions access to the world seed. Vanilla passes the seed to its own functions
 * through hardcoded {@code instanceof} checks in {@code RandomState}'s wiring visitor; this wraps
 * that visitor so Vastlands functions can bind to a per-world {@link GenerationContext} instead,
 * and registers the finished climate sampler with the context, so the Vastlands biome source finds
 * the caves from the sampler it is given.
 */
@Mixin(RandomState.class)
public abstract class RandomStateMixin implements RandomStateExtension {
    @Shadow
    @Final
    private Climate.Sampler sampler;

    @Unique
    @Nullable
    private volatile GenerationContext vastlands$generationContext;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void vastlands$bindSampler(CallbackInfo info) {
        GenerationContext context = this.vastlands$generationContext;
        if (context != null) {
            GenerationContext.bind(this.sampler, context);
        }
    }

    @WrapOperation(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/NoiseRouter;"
            )
    )
    private NoiseRouter vastlands$bindGenerationContext(
            NoiseRouter router,
            DensityFunction.Visitor visitor,
            Operation<NoiseRouter> original,
            @Local(argsOnly = true) NoiseGeneratorSettings settings,
            @Local(argsOnly = true) long levelSeed
    ) {
        ContextBindingVisitor binder = new ContextBindingVisitor(visitor, () -> new GenerationContext(GenerationSetup.of(levelSeed, settings)));
        NoiseRouter wired = original.call(router, binder);
        this.vastlands$generationContext = binder.createdContext();
        return wired;
    }

    @Override
    @Nullable
    public GenerationContext vastlands$generationContext() {
        return this.vastlands$generationContext;
    }
}
