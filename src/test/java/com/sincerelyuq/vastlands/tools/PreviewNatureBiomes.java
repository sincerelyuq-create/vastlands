package com.sincerelyuq.vastlands.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.sincerelyuq.vastlands.worldgen.caves.CaveBiome;
import com.sincerelyuq.vastlands.worldgen.caves.VastlandsCaves;
import com.sincerelyuq.vastlands.worldgen.nature.CaveProfile;
import com.sincerelyuq.vastlands.worldgen.nature.NatureModel;
import com.sincerelyuq.vastlands.worldgen.nature.SurfaceStyle;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSystems;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Biomes as decoration styles without the game, for previews and model tests: the surface biome
 * of vanilla's table on the router's climate ({@link PreviewForestBiomes}) mapped through the
 * mod's own tag files ({@code data/vastlands/tags/worldgen/biome/nature/surface/*.json}); the cave
 * profile from the caves' slot as the game resolves it without other mods (lush caves, dripstone
 * and crystal as dripstone, the deep dark; other slots are plain rock). Grass colours from
 * {@link PreviewBiomeTraits}.
 */
public final class PreviewNatureBiomes implements NatureModel.Biomes {
    private final PreviewForestBiomes surface;
    private final VastlandsCaves caves;
    private final Map<String, Integer> styles = new HashMap<>();

    public PreviewNatureBiomes(GenerationSystems systems) {
        this.surface = new PreviewForestBiomes(systems);
        this.caves = (VastlandsCaves) systems.caves();
        for (SurfaceStyle style : SurfaceStyle.VALUES) {
            for (String id : read(style.tagPath())) {
                this.styles.put(id, style.ordinal());
            }
        }
    }

    private static List<String> read(String name) {
        String path = "/data/vastlands/tags/worldgen/biome/nature/surface/" + name + ".json";
        try (InputStream in = PreviewNatureBiomes.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing " + path);
            }
            List<String> ids = new ArrayList<>();
            for (JsonElement e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("values")) {
                ids.add(e.isJsonPrimitive() ? e.getAsString() : e.getAsJsonObject().get("id").getAsString());
            }
            return ids;
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public String biomeName(int x, int y, int z) {
        return this.surface.biome(x, y, z).location().getPath();
    }

    @Override
    public int surfaceStyle(int x, int y, int z) {
        String id = this.surface.biome(x, y, z).location().toString();
        Integer style = this.styles.get(id);
        if (style != null) {
            return style;
        }
        return (id.contains("ocean") ? SurfaceStyle.OCEAN : SurfaceStyle.TEMPERATE).ordinal();
    }

    @Override
    public int caveProfile(int x, int y, int z) {
        CaveBiome slot = this.caves.caveBiome(x, y, z);
        return (switch (slot) {
            case LUSH -> CaveProfile.LUSH;
            case DRIPSTONE, CRYSTAL -> CaveProfile.DRIPSTONE;
            case DEEP_DARK -> CaveProfile.DEEP_DARK;
            default -> CaveProfile.PLAIN;
        }).ordinal();
    }

    @Override
    public double mossBlend(int x, int y, int z) {
        return this.surface.mossBlend(x, y, z);
    }
}
