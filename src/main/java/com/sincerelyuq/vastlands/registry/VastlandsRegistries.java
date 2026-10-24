package com.sincerelyuq.vastlands.registry;

import com.mojang.serialization.MapCodec;
import com.sincerelyuq.vastlands.Vastlands;
import com.sincerelyuq.vastlands.worldgen.VastlandsChunkGenerator;
import com.sincerelyuq.vastlands.worldgen.density.HydrologyParameterFunction;
import com.sincerelyuq.vastlands.worldgen.density.StageDensityFunction;
import com.sincerelyuq.vastlands.worldgen.density.TerrainParameterFunction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * All built-in registrations of the mod. Worldgen content that can be expressed as data
 * (presets, dimension types, noise settings, density function graphs) lives in
 * {@code src/main/resources/data/vastlands} instead.
 */
public final class VastlandsRegistries {
    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, Vastlands.MOD_ID);
    public static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTION_TYPES =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, Vastlands.MOD_ID);

    /** {@code "type": "vastlands:noise"} in a dimension's generator. */
    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<VastlandsChunkGenerator>> NOISE_GENERATOR =
            CHUNK_GENERATORS.register("noise", () -> VastlandsChunkGenerator.CODEC);

    static {
        for (StageDensityFunction.Stage stage : StageDensityFunction.Stage.values()) {
            DENSITY_FUNCTION_TYPES.register(stage.getSerializedName(), () -> stage.codec().codec());
        }
        DENSITY_FUNCTION_TYPES.register("terrain_parameter", () -> TerrainParameterFunction.CODEC.codec());
        DENSITY_FUNCTION_TYPES.register("hydrology_parameter", () -> HydrologyParameterFunction.CODEC.codec());
    }

    private VastlandsRegistries() {
    }

    public static void register(IEventBus modEventBus) {
        CHUNK_GENERATORS.register(modEventBus);
        DENSITY_FUNCTION_TYPES.register(modEventBus);
    }
}
