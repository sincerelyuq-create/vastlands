package com.sincerelyuq.vastlands.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sincerelyuq.vastlands.worldgen.VastlandsChunkGenerator;
import com.sincerelyuq.vastlands.worldgen.compat.StructureSites;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Structure starts of a Vastlands generator pass the site checks of their structure tags (other
 * mods' structures on level, dry ground, sealed from cave water, apart from the strata sanctum;
 * see {@link StructureSites}); a start that fails is dropped as one in the wrong biome is. Other
 * generators, and structures in none of the tags, are unchanged.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    @WrapOperation(
            method = "tryGenerateStructure",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/structure/Structure;generate(Lnet/minecraft/core/Holder;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/world/level/biome/BiomeSource;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;JLnet/minecraft/world/level/ChunkPos;ILnet/minecraft/world/level/LevelHeightAccessor;Ljava/util/function/Predicate;)Lnet/minecraft/world/level/levelgen/structure/StructureStart;")
    )
    private StructureStart vastlands$siteChecks(Structure structure, Holder<Structure> holder, ResourceKey<Level> level, RegistryAccess registries,
                                                ChunkGenerator generator, BiomeSource biomes, RandomState random, StructureTemplateManager templates, long seed,
                                                ChunkPos pos, int references, LevelHeightAccessor height, Predicate<Holder<Biome>> valid,
                                                Operation<StructureStart> original) {
        StructureStart start = original.call(structure, holder, level, registries, generator, biomes, random, templates, seed, pos, references, height, valid);
        if (start.isValid() && generator instanceof VastlandsChunkGenerator && !StructureSites.accept(holder, start, random, registries, seed)) {
            return StructureStart.INVALID_START;
        }
        return start;
    }
}
