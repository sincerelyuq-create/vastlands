package com.sincerelyuq.vastlands.mixin;

import com.sincerelyuq.vastlands.worldgen.forest.VanillaTrees;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In Vastlands worlds, vanilla's tree features and surface lava lakes (the tag
 * {@code #vastlands:forest/displaced}) skip the positions where the Vastlands forests grow the
 * trees, and underground lava lakes ({@code #vastlands:forest/buried}) skip those just under them;
 * see {@link VanillaTrees}. Every other feature, and every other world, places as before.
 */
@Mixin(PlacedFeature.class)
public abstract class PlacedFeatureMixin {
    @Inject(method = "placeWithBiomeCheck", at = @At("HEAD"), cancellable = true)
    private void vastlands$giveWayToForests(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        PlacedFeature self = (PlacedFeature) (Object) this;
        PlacedFeature filtered = VanillaTrees.filtered(self, level, generator);
        if (filtered != null) {
            cir.setReturnValue(((PlacedFeatureAccessor) (Object) filtered).vastlands$placeWithContext(VanillaTrees.context(level, generator, self), random, pos));
        }
    }
}
