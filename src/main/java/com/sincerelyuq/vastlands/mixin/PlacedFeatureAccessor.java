package com.sincerelyuq.vastlands.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Places a placed feature with a given context (vanilla's private {@code placeWithContext}). */
@Mixin(PlacedFeature.class)
public interface PlacedFeatureAccessor {
    @Invoker("placeWithContext")
    boolean vastlands$placeWithContext(PlacementContext context, RandomSource random, BlockPos pos);
}
