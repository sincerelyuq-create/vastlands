package com.sincerelyuq.vastlands.registry;

import com.mojang.serialization.MapCodec;
import com.sincerelyuq.vastlands.Vastlands;
import com.sincerelyuq.vastlands.worldgen.VastlandsChunkGenerator;
import com.sincerelyuq.vastlands.worldgen.biome.VastlandsBiomeSource;
import com.sincerelyuq.vastlands.worldgen.density.HydrologyParameterFunction;
import com.sincerelyuq.vastlands.worldgen.density.StageDensityFunction;
import com.sincerelyuq.vastlands.worldgen.density.TerrainParameterFunction;
import com.sincerelyuq.vastlands.worldgen.forest.ForestFeature;
import com.sincerelyuq.vastlands.worldgen.forest.VanillaTrees;
import com.sincerelyuq.vastlands.worldgen.nature.NatureFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * All built-in registrations of the mod. Worldgen content that can be expressed as data
 * (presets, dimension types, noise settings, density function graphs, cave biome tags) lives in
 * {@code src/main/resources/data/vastlands} instead.
 */
public final class VastlandsRegistries {
    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, Vastlands.MOD_ID);
    public static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTION_TYPES =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, Vastlands.MOD_ID);
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, Vastlands.MOD_ID);
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, Vastlands.MOD_ID);
    public static final DeferredRegister<PlacementModifierType<?>> PLACEMENT_MODIFIER_TYPES =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, Vastlands.MOD_ID);

    /** {@code "type": "vastlands:noise"} in a dimension's generator. */
    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<VastlandsChunkGenerator>> NOISE_GENERATOR =
            CHUNK_GENERATORS.register("noise", () -> VastlandsChunkGenerator.CODEC);

    /** {@code "type": "vastlands:caves"} in a generator's biome source: surface biomes of a delegate, cave biomes of the caves. */
    public static final DeferredHolder<MapCodec<? extends BiomeSource>, MapCodec<VastlandsBiomeSource>> CAVE_BIOME_SOURCE =
            BIOME_SOURCES.register("caves", () -> VastlandsBiomeSource.CODEC);

    /** {@code "type": "vastlands:forest"}: the Vastlands forests of a chunk (configured feature {@code vastlands:forest}). */
    public static final DeferredHolder<Feature<?>, ForestFeature> FOREST = FEATURES.register("forest", ForestFeature::new);

    /** {@code "type": "vastlands:formations"}: the natural formations of a chunk (configured features {@code vastlands:surface_formations}, {@code vastlands:cave_formations}). */
    public static final DeferredHolder<Feature<?>, NatureFeature> FORMATIONS = FEATURES.register("formations", NatureFeature::new);

    /** Appended to vanilla's tree features in Vastlands worlds: positions where Vastlands grows the trees are dropped. */
    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<VanillaTrees.Filter>> OUTSIDE_FORESTS =
            PLACEMENT_MODIFIER_TYPES.register("outside_forests", () -> () -> VanillaTrees.Filter.CODEC);

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
        BIOME_SOURCES.register(modEventBus);
        FEATURES.register(modEventBus);
        PLACEMENT_MODIFIER_TYPES.register(modEventBus);
    }
}
