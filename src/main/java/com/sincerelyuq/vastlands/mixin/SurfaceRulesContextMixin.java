package com.sincerelyuq.vastlands.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.sincerelyuq.vastlands.worldgen.context.GenerationContext;
import com.sincerelyuq.vastlands.worldgen.context.RandomStateExtension;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Surface rules (grass, sand, snow...) only apply above a minimum surface level that vanilla
 * interpolates from preliminary surface heights at 16-block cell corners. Vastlands mountains are
 * cut by gullies narrower than that, so their floors would fall below the interpolated level and
 * stay bare stone. In Vastlands worlds, the interpolated preliminary surface is replaced by the
 * exact surface height of the column; other worlds are unchanged.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$Context")
public abstract class SurfaceRulesContextMixin {
    /**
     * Vanilla's preliminary surface lies 3 to 11 blocks below the real surface (density threshold,
     * 8-block scan steps); this keeps the depth of surface layers (dirt, sandstone) similar.
     */
    @Unique
    private static final int vastlands$PRELIMINARY_OFFSET = 7;

    @Shadow
    @Final
    RandomState randomState;
    @Shadow
    int blockX;
    @Shadow
    int blockZ;

    @ModifyExpressionValue(
            method = "getMinSurfaceLevel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;floor(D)I")
    )
    private int vastlands$exactSurface(int interpolatedPreliminarySurface) {
        GenerationContext context = ((RandomStateExtension) (Object) this.randomState).vastlands$generationContext();
        if (context == null) {
            return interpolatedPreliminarySurface;
        }
        return Mth.floor(context.systems().surfaceHeight(this.blockX, this.blockZ)) - vastlands$PRELIMINARY_OFFSET;
    }
}
