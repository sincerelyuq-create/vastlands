package com.sincerelyuq.vastlands.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.NoiseSettings;
import org.junit.jupiter.api.Test;

/** The data files that define the Vastlands height must agree with {@link WorldHeight} and pass vanilla's validation. */
class WorldHeightDataTest {
    @Test
    void constantsDescribeTheTargetHeight() {
        assertEquals(-256, WorldHeight.MIN_Y);
        assertEquals(511, WorldHeight.MAX_Y);
        assertEquals(768, WorldHeight.HEIGHT);
    }

    @Test
    void dimensionTypeMatchesWorldHeight() {
        DimensionType type = DimensionType.DIRECT_CODEC
                .parse(JsonOps.INSTANCE, read("/data/vastlands/dimension_type/overworld.json"))
                .getOrThrow();
        assertEquals(WorldHeight.MIN_Y, type.minY());
        assertEquals(WorldHeight.HEIGHT, type.height());
        assertEquals(WorldHeight.HEIGHT, type.logicalHeight());
    }

    @Test
    void noiseSettingsMatchWorldHeight() {
        JsonElement noise = read("/data/vastlands/worldgen/noise_settings/overworld.json").getAsJsonObject().get("noise");
        NoiseSettings settings = NoiseSettings.CODEC.parse(JsonOps.INSTANCE, noise).getOrThrow();
        assertEquals(WorldHeight.MIN_Y, settings.minY());
        assertEquals(WorldHeight.HEIGHT, settings.height());
    }

    private static JsonElement read(String path) {
        try (InputStream stream = WorldHeightDataTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "missing resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
