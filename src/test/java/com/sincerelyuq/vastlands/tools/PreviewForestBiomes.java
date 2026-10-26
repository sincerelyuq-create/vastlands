package com.sincerelyuq.vastlands.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.sincerelyuq.vastlands.worldgen.forest.ForestModel;
import com.sincerelyuq.vastlands.worldgen.forest.ForestType;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSystems;
import com.sincerelyuq.vastlands.worldgen.system.TerrainParameter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

/**
 * Surface biomes without the game, for previews and model tests: vanilla's overworld table on the
 * router's climate parameters (as the hydrology reshapes them), mapped to forest categories by the
 * mod's own tag files ({@code data/vastlands/tags/worldgen/biome/forest/*.json}), read from the
 * classpath. Only vanilla biomes; in the game the biome source and the resolved tags decide.
 */
public final class PreviewForestBiomes implements ForestModel.Biomes {
    private final GenerationSystems systems;
    private final Map<String, Integer> categories = new HashMap<>();


    public PreviewForestBiomes(GenerationSystems systems) {
        this.systems = systems;
        for (ForestType type : ForestType.VALUES) {
            for (String id : read(type.tagPath())) {
                this.categories.put(id, type.ordinal());
            }
        }
        for (String id : read("transparent")) {
            this.categories.put(id, ForestModel.TRANSPARENT);
        }
    }

    private static Iterable<String> read(String name) {
        String path = "/data/vastlands/tags/worldgen/biome/forest/" + name + ".json";
        try (InputStream in = PreviewForestBiomes.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing " + path);
            }
            java.util.List<String> ids = new java.util.ArrayList<>();
            for (JsonElement e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("values")) {
                ids.add(e.isJsonPrimitive() ? e.getAsString() : e.getAsJsonObject().get("id").getAsString());
            }
            return ids;
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The vanilla biome at the surface of a column (the router's parameters at the 4-block grid). */
    public ResourceKey<Biome> biome(int x, int y, int z) {
        int gx = x & ~3;
        int gz = z & ~3;
        var h = this.systems.hydrology();
        Climate.TargetPoint target = Climate.target(
                (float) h.parameter(TerrainParameter.TEMPERATURE, gx, y, gz),
                (float) h.parameter(TerrainParameter.VEGETATION, gx, y, gz),
                (float) h.parameter(TerrainParameter.CONTINENTALNESS, gx, y, gz),
                (float) h.parameter(TerrainParameter.EROSION, gx, y, gz),
                (float) h.parameter(TerrainParameter.DEPTH, gx, y, gz),
                (float) h.parameter(TerrainParameter.WEIRDNESS, gx, y, gz));
        // Brute force: exact, with ties resolved by table order (the index search keeps the thread's last result on ties).
        return TerrainPreview.BIOME_TABLE.findValueBruteForce(target);
    }

    @Override
    public int category(int x, int y, int z) {
        String id = this.biome(x, y, z).location().toString();
        return this.categories.getOrDefault(id, ForestModel.NONE);
    }

    /** How well moss blends into the vanilla biome's grass (its colour from the biome's climate, {@link PreviewBiomeTraits}). */
    @Override
    public double mossBlend(int x, int y, int z) {
        return PreviewBiomeTraits.mossBlend(this.biome(x, y, z).location().getPath());
    }
}
