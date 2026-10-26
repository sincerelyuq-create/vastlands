package com.sincerelyuq.vastlands.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sincerelyuq.vastlands.Vastlands;
import com.sincerelyuq.vastlands.registry.VastlandsRegistries;
import com.sincerelyuq.vastlands.tools.PreviewBiomeTraits;
import com.sincerelyuq.vastlands.tools.PreviewNatureBiomes;
import com.sincerelyuq.vastlands.worldgen.biome.BiomeColors;
import com.sincerelyuq.vastlands.worldgen.biome.GrassColors;
import com.sincerelyuq.vastlands.worldgen.caves.CaveBiome;
import com.sincerelyuq.vastlands.worldgen.caves.VastlandsCaves;
import com.sincerelyuq.vastlands.worldgen.context.GenerationContext;
import com.sincerelyuq.vastlands.worldgen.nature.CaveProfile;
import com.sincerelyuq.vastlands.worldgen.nature.FormationShape;
import com.sincerelyuq.vastlands.worldgen.nature.NatureBiomes;
import com.sincerelyuq.vastlands.worldgen.nature.NatureModel;
import com.sincerelyuq.vastlands.worldgen.nature.SurfaceStyle;
import com.sincerelyuq.vastlands.worldgen.nature.VastlandsNature;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSetup;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSystems;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * The natural formations on a datapack-loaded server: their features, placements and biome modifier
 * load and reach every overworld and cave biome at the right step; biome tags map vanilla's biomes
 * to their styles; grass colours computed on the server match vanilla's colour map and biome data
 * (and the preview table the model tests use); with the world's real biome source the formations
 * are the same from any thread and order, and the previews' styles agree with the game's.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class NatureDataTest {
    private static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET, Vastlands.id("vastlands"));
    private static final long SEED = 20261018L;

    @Test
    void featuresAndBiomeModifierReachEveryOverworldBiome(MinecraftServer server) {
        RegistryAccess registries = server.registryAccess();
        HolderLookup.RegistryLookup<PlacedFeature> placed = registries.lookupOrThrow(Registries.PLACED_FEATURE);
        for (String name : new String[] {"surface_formations", "cave_formations"}) {
            ConfiguredFeature<?, ?> configured = registries.lookupOrThrow(Registries.CONFIGURED_FEATURE)
                    .getOrThrow(ResourceKey.create(Registries.CONFIGURED_FEATURE, Vastlands.id(name))).value();
            assertEquals(VastlandsRegistries.FORMATIONS.get(), configured.feature(), name + " feature");
            placed.getOrThrow(ResourceKey.create(Registries.PLACED_FEATURE, Vastlands.id(name)));
        }
        registries.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS).getOrThrow(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, Vastlands.id("nature")));
        int step = GenerationStep.Decoration.LOCAL_MODIFICATIONS.ordinal();
        int biomes = 0;
        for (Holder.Reference<Biome> biome : registries.lookupOrThrow(Registries.BIOME).listElements().toList()) {
            if (!biome.is(BiomeTags.IS_OVERWORLD)) {
                continue;
            }
            biomes++;
            var features = biome.value().getGenerationSettings().features();
            assertTrue(features.size() > step, biome.key().location() + " has no local modifications step");
            for (String name : new String[] {"surface_formations", "cave_formations"}) {
                assertTrue(features.get(step).stream().anyMatch(h -> h.is(Vastlands.id(name))), biome.key().location() + " lacks " + name);
            }
        }
        assertTrue(biomes > 50, "overworld biomes: " + biomes);
    }

    @Test
    void biomeTagsGiveVanillaBiomesTheirStyles(MinecraftServer server) {
        HolderLookup.RegistryLookup<Biome> lookup = server.registryAccess().lookupOrThrow(Registries.BIOME);
        Map<ResourceKey<Biome>, SurfaceStyle> styles = Map.of(Biomes.DARK_FOREST, SurfaceStyle.DARK, Biomes.BADLANDS, SurfaceStyle.BADLANDS,
                Biomes.STONY_SHORE, SurfaceStyle.STONY_SHORE, Biomes.FROZEN_PEAKS, SurfaceStyle.MOUNTAIN, Biomes.DESERT, SurfaceStyle.DESERT,
                Biomes.SWAMP, SurfaceStyle.WETLAND, Biomes.OLD_GROWTH_SPRUCE_TAIGA, SurfaceStyle.OLD_GROWTH, Biomes.RIVER, SurfaceStyle.RIVER,
                Biomes.JUNGLE, SurfaceStyle.JUNGLE, Biomes.PLAINS, SurfaceStyle.TEMPERATE);
        styles.forEach((key, style) -> assertEquals(style, NatureBiomes.style(lookup.getOrThrow(key)), key.location() + " style"));
        Map<SurfaceStyle, Integer> counts = new TreeMap<>();
        for (Holder.Reference<Biome> biome : lookup.listElements().toList()) {
            if (biome.is(BiomeTags.IS_OVERWORLD)) {
                counts.merge(NatureBiomes.style(biome), 1, Integer::sum);
            }
        }
        Vastlands.LOGGER.info("NatureDataTest: overworld biomes by surface style {}", counts);
        assertEquals(CaveProfile.DRIPSTONE, NatureBiomes.profile(lookup.getOrThrow(Biomes.DRIPSTONE_CAVES), CaveBiome.DRIPSTONE, true));
        assertEquals(CaveProfile.LUSH, NatureBiomes.profile(lookup.getOrThrow(Biomes.LUSH_CAVES), CaveBiome.LUSH, true));
        assertEquals(CaveProfile.DEEP_DARK, NatureBiomes.profile(lookup.getOrThrow(Biomes.DEEP_DARK), CaveBiome.DEEP_DARK, true));
        // Plain rock under a surface biome, whatever the slot.
        assertEquals(CaveProfile.PLAIN, NatureBiomes.profile(lookup.getOrThrow(Biomes.PLAINS), CaveBiome.VOLCANIC, false));
    }

    /**
     * {@link GrassColors}' table against vanilla's colour map (the client resource, on the test
     * classpath): within 2 of 255 at its samples, 3 between them.
     */
    @Test
    void grassColoursMatchTheColourMap() throws IOException {
        BufferedImage map = colourMap("assets/minecraft/textures/colormap/grass.png");
        Random random = new Random(1);
        int worst = 0;
        for (int n = 0; n < 4000; n++) {
            boolean sample = n < 81;
            double t = sample ? (n % 9) / 8.0 : random.nextDouble();
            double d = sample ? (n / 9) / 8.0 : random.nextDouble();
            // ColorMapColorUtil.get
            int i = (int) ((1.0 - t) * 255.0);
            int j = (int) ((1.0 - d * t) * 255.0);
            int expected = map.getRGB(i, j) & 0xFFFFFF;
            int actual = GrassColors.tint(t, d);
            int diff = 0;
            for (int shift = 0; shift <= 16; shift += 8) {
                diff = Math.max(diff, Math.abs((expected >> shift & 255) - (actual >> shift & 255)));
            }
            worst = Math.max(worst, diff);
            assertTrue(diff <= (sample ? 2 : 3), String.format(Locale.ROOT, "tint at t %.3f d %.3f: %06X, map %06X", t, d, actual, expected));
        }
        // The textures the moss rule compares: moss's mean colour and the grass top's mean grey.
        int moss = mean(colourMap("assets/minecraft/textures/block/moss_block.png"));
        int grass = mean(colourMap("assets/minecraft/textures/block/grass_block_top.png"));
        Vastlands.LOGGER.info("NatureDataTest: grass tint table within {} of the colour map; moss texture mean {}, grass top mean {}", worst,
                String.format("%06X", moss), String.format("%06X", grass));
        for (int shift = 0; shift <= 16; shift += 8) {
            assertTrue(Math.abs((moss >> shift & 255) - (GrassColors.MOSS >> shift & 255)) <= 2, "moss texture mean " + String.format("%06X", moss));
            assertTrue(Math.abs((grass >> shift & 255) - 147) <= 2, "grass top texture mean " + String.format("%06X", grass));
        }
    }

    /**
     * The mean colour of an image's opaque pixels. Grey images are read from their raster: getRGB
     * would convert their linear grey to sRGB and brighten them.
     */
    private static int mean(BufferedImage image) {
        long[] sum = new long[3];
        int n = 0;
        boolean grey = image.getColorModel().getNumColorComponents() == 1 && !(image.getColorModel() instanceof java.awt.image.IndexColorModel);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb;
                if (grey) {
                    int v = image.getRaster().getSample(x, y, 0);
                    int a = image.getColorModel().hasAlpha() ? image.getRaster().getSample(x, y, 1) : 255;
                    argb = a << 24 | v << 16 | v << 8 | v;
                } else {
                    argb = image.getRGB(x, y);
                }
                if ((argb >>> 24) < 128) {
                    continue;
                }
                sum[0] += argb >> 16 & 255;
                sum[1] += argb >> 8 & 255;
                sum[2] += argb & 255;
                n++;
            }
        }
        return (int) Math.round(sum[0] / (double) n) << 16 | (int) Math.round(sum[1] / (double) n) << 8 | (int) Math.round(sum[2] / (double) n);
    }

    /**
     * A client resource: through the game's class loaders, else from the jars of the legacy class
     * path (where the test run lists the client's resources jar).
     */
    private static BufferedImage colourMap(String path) throws IOException {
        for (ClassLoader loader : new ClassLoader[] {Thread.currentThread().getContextClassLoader(), Biome.class.getClassLoader(), NatureDataTest.class.getClassLoader()}) {
            if (loader == null) {
                continue;
            }
            try (InputStream in = loader.getResourceAsStream(path)) {
                if (in != null) {
                    return ImageIO.read(in);
                }
            }
        }
        String list = System.getProperty("legacyClassPath.file");
        assertTrue(list != null, path + " is on no class path");
        for (String jar : java.nio.file.Files.readString(java.nio.file.Path.of(list)).split(java.io.File.pathSeparator)) {
            if (!jar.strip().endsWith(".jar")) {
                continue;
            }
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jar.strip())) {
                java.util.zip.ZipEntry entry = zip.getEntry(path);
                if (entry != null) {
                    try (InputStream in = zip.getInputStream(entry)) {
                        return ImageIO.read(in);
                    }
                }
            }
        }
        throw new AssertionError(path + " is on no class path");
    }

    /**
     * Grass colours from the server's biomes equal the preview table's (so the model tests see the
     * game's moss rules), every vanilla overworld biome is in the table, and moss blends into the
     * yellow-green grass of temperate woods and plains but not into dark forest, swamp, snowy or
     * badlands grass. (Taiga grass is bluish: there moss keeps to podzol, see ForestBlocks.)
     */
    @Test
    void biomeGrassColoursAndMossBlend(MinecraftServer server) {
        Map<String, Double> blends = new TreeMap<>();
        for (Holder.Reference<Biome> biome : server.registryAccess().lookupOrThrow(Registries.BIOME).listElements().toList()) {
            if (!biome.is(BiomeTags.IS_OVERWORLD) || !biome.key().location().getNamespace().equals("minecraft")) {
                continue;
            }
            String path = biome.key().location().getPath();
            int tint = BiomeColors.grassTint(biome.value());
            assertEquals(String.format("%06X", PreviewBiomeTraits.grassTint(path)), String.format("%06X", tint), path + ": grass tint in the preview table");
            blends.put(path, BiomeColors.mossBlend(biome.value()));
        }
        StringBuilder text = new StringBuilder();
        blends.forEach((k, v) -> text.append(String.format(Locale.ROOT, " %s %.2f", k, v)));
        Vastlands.LOGGER.info("NatureDataTest: moss blend by biome:{}", text);
        for (String b : new String[] {"dark_forest", "swamp", "mangrove_swamp", "snowy_taiga", "badlands", "pale_garden"}) {
            assertTrue(blends.get(b) < 0.3, "moss blends into " + b + " grass: " + blends.get(b));
        }
        for (String b : new String[] {"forest", "flower_forest", "birch_forest", "plains", "meadow"}) {
            assertTrue(blends.get(b) > 0.7, "moss does not blend into " + b + ": " + blends.get(b));
        }
    }

    private static String signature(NatureModel model, int cx, int cz) {
        StringBuilder out = new StringBuilder();
        for (boolean surface : new boolean[] {true, false}) {
            model.formations(cx << 4, cz << 4, (cx << 4) + 15, (cz << 4) + 15, surface, s -> {
                FormationShape shape = model.shape(s, model.world());
                out.append(s).append(Arrays.hashCode(shape.positions) * 31 + Arrays.hashCode(shape.codes)).append(';');
            });
        }
        return out.toString();
    }

    private static NatureModel bound(VastlandsChunkGenerator generator, RegistryAccess registries, Object owner) {
        RandomState state = RandomState.create(generator.generatorSettings().value(), registries.lookupOrThrow(Registries.NOISE), SEED);
        GenerationSystems systems = GenerationContext.of(state).orElseThrow().systems();
        VastlandsNature nature = (VastlandsNature) systems.nature();
        nature.bind(owner, new NatureBiomes(generator.getBiomeSource(), state.sampler(), (VastlandsCaves) systems.caves()));
        return nature.model();
    }

    /**
     * With the world's own biome source: the same formations from any thread and order, and the
     * previews' biome styles (vanilla's table on the router's climate) as the game's.
     */
    @Test
    void realBiomesGiveTheSameFormationsAndAgreeWithPreviews(MinecraftServer server) {
        RegistryAccess registries = server.registryAccess();
        VastlandsChunkGenerator generator = (VastlandsChunkGenerator) registries.lookupOrThrow(Registries.WORLD_PRESET)
                .getOrThrow(PRESET).value().overworld().orElseThrow().generator();
        NatureModel a = bound(generator, registries, new Object());
        NatureModel b = bound(generator, registries, new Object());
        List<int[]> chunks = new ArrayList<>();
        for (int[] p : new int[][] {{288, 32}, {-12704, -11808}, {-2976, -1348}}) {
            for (int i = -3; i < 3; i++) {
                for (int j = -3; j < 3; j++) {
                    chunks.add(new int[] {(p[0] >> 4) + i, (p[1] >> 4) + j});
                }
            }
        }
        Map<String, String> first = new HashMap<>();
        int formations = 0;
        for (int[] c : chunks) {
            String s = signature(a, c[0], c[1]);
            formations += s.split(";").length;
            first.put(c[0] + "," + c[1], s);
        }
        Collections.shuffle(chunks, new Random(3));
        AtomicInteger differ = new AtomicInteger();
        chunks.parallelStream().forEach(c -> differ.addAndGet(signature(b, c[0], c[1]).equals(first.get(c[0] + "," + c[1])) ? 0 : 1));
        assertEquals(0, differ.get(), "chunks whose formations depend on the query order or thread");
        assertTrue(formations > 200, "too few formations: " + formations);

        RandomState state = RandomState.create(generator.generatorSettings().value(), registries.lookupOrThrow(Registries.NOISE), SEED);
        GenerationSystems systems = GenerationContext.of(state).orElseThrow().systems();
        NatureBiomes game = new NatureBiomes(generator.getBiomeSource(), state.sampler(), (VastlandsCaves) systems.caves());
        PreviewNatureBiomes preview = new PreviewNatureBiomes(GenerationSystems.create(new GenerationSetup(SEED, WorldHeight.MIN_Y, WorldHeight.HEIGHT, 63)));
        int same = 0;
        int total = 0;
        for (int x = -8192; x <= 8192; x += 512) {
            for (int z = -8192; z <= 8192; z += 512) {
                int y = (int) Math.ceil(systems.surfaceHeight(x, z));
                total++;
                same += game.surfaceStyle(x, y, z) == preview.surfaceStyle(x, y, z) ? 1 : 0;
            }
        }
        Vastlands.LOGGER.info("NatureDataTest: {} formations the same from any order; preview styles agree with the game's at {} of {} points", formations, same,
                total);
        assertTrue(same >= total * 0.9, "previews disagree with the game's biomes: " + same + " of " + total);
    }
}
