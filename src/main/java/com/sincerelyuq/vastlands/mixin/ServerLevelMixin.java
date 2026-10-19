package com.sincerelyuq.vastlands.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sincerelyuq.vastlands.worldgen.ocean.OceanSurface;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Weather freezes water at the edge of ice in cold biomes, which would close the cracks and leads
 * of Vastlands ice fields within minutes of a chunk being loaded. In Vastlands worlds, water in an
 * open crack of an ice field (see {@code FrozenSea}) does not freeze; everything else, and every
 * other world, is unchanged.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @WrapOperation(
            method = "tickPrecipitation",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z")
    )
    private boolean vastlands$keepLeadsOpen(Biome biome, LevelReader level, BlockPos water, Operation<Boolean> original) {
        return original.call(biome, level, water) && !OceanSurface.isOpenLead((ServerLevel) (Object) this, water);
    }
}
