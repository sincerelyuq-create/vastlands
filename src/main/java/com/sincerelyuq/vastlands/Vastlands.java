package com.sincerelyuq.vastlands;

import com.mojang.logging.LogUtils;
import com.sincerelyuq.vastlands.registry.VastlandsRegistries;
import com.sincerelyuq.vastlands.worldgen.WorldgenDiagnostics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Mod entry point. Everything Vastlands adds to the game is reachable from here:
 * registries in {@link VastlandsRegistries}, worldgen data in {@code data/vastlands}.
 */
@Mod(Vastlands.MOD_ID)
public final class Vastlands {
    public static final String MOD_ID = "vastlands";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Vastlands(IEventBus modEventBus) {
        VastlandsRegistries.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(WorldgenDiagnostics::onLevelLoad);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
