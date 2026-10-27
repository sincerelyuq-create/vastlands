package com.sincerelyuq.vastlands.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.sincerelyuq.vastlands.worldgen.VastlandsChunkGenerator;
import com.sincerelyuq.vastlands.worldgen.compat.IntegrationSurface;
import com.sincerelyuq.vastlands.worldgen.context.GenerationContext;
import com.sincerelyuq.vastlands.worldgen.context.RandomStateExtension;
import java.util.function.Supplier;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Vanilla's global fluid picker (private, built from the noise settings) makes every open block
 * below Y -54 lava and every one below sea level water. In Vastlands worlds the noise chunks of
 * chunk filling and of base column queries get the cave system's picker instead (see
 * {@code CaveSystem.fluidPicker}), which knows the caves' flooded, dry and lava domains. The
 * picker is chosen by the {@link RandomState} of the call, so other worlds are unchanged.
 *
 * <p>Surface building of a {@link VastlandsChunkGenerator} runs the surface rules of other mods'
 * biomes before its own (see {@link IntegrationSurface}); other generators are unchanged.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin {
    @WrapOperation(
            method = {"createNoiseChunk", "iterateNoiseColumn"},
            at = @At(value = "INVOKE", target = "Ljava/util/function/Supplier;get()Ljava/lang/Object;")
    )
    private Object vastlands$caveFluids(Supplier<?> picker, Operation<Object> original, @Local(argsOnly = true) RandomState random) {
        Object vanilla = original.call(picker);
        GenerationContext context = ((RandomStateExtension) (Object) random).vastlands$generationContext();
        return context == null ? vanilla : context.systems().caves().fluidPicker((Aquifer.FluidPicker) vanilla);
    }

    @ModifyArg(
            method = "buildSurface(Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/WorldGenerationContext;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/biome/BiomeManager;Lnet/minecraft/core/Registry;Lnet/minecraft/world/level/levelgen/blending/Blender;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/SurfaceSystem;buildSurface(Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/biome/BiomeManager;Lnet/minecraft/core/Registry;ZLnet/minecraft/world/level/levelgen/WorldGenerationContext;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/NoiseChunk;Lnet/minecraft/world/level/levelgen/SurfaceRules$RuleSource;)V"),
            index = 7
    )
    private SurfaceRules.RuleSource vastlands$integrationSurface(SurfaceRules.RuleSource rule) {
        return (Object) this instanceof VastlandsChunkGenerator ? IntegrationSurface.wrap(rule) : rule;
    }
}
