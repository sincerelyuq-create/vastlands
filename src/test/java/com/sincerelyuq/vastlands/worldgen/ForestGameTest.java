package com.sincerelyuq.vastlands.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sincerelyuq.vastlands.Vastlands;
import com.sincerelyuq.vastlands.worldgen.context.GenerationContext;
import com.sincerelyuq.vastlands.worldgen.forest.ForestBiomes;
import com.sincerelyuq.vastlands.worldgen.forest.ForestModel;
import com.sincerelyuq.vastlands.worldgen.forest.TreeShape;
import com.sincerelyuq.vastlands.worldgen.forest.TreeSpec;
import com.sincerelyuq.vastlands.worldgen.forest.VastlandsForests;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * The forests on a datapack-loaded server, with the world's real biome source and the mod's
 * mixins: the same trees from any thread and query order.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class ForestGameTest {
    private static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET, Vastlands.id("vastlands"));
    private static final long SEED = 20261018L;

    static VastlandsForests forests(MinecraftServer server) {
        RegistryAccess registries = server.registryAccess();
        VastlandsChunkGenerator generator = (VastlandsChunkGenerator) registries.lookupOrThrow(Registries.WORLD_PRESET)
                .getOrThrow(PRESET).value().overworld().orElseThrow().generator();
        RandomState state = RandomState.create(generator.generatorSettings().value(), registries.lookupOrThrow(Registries.NOISE), SEED);
        VastlandsForests forests = assertInstanceOf(VastlandsForests.class, GenerationContext.of(state).orElseThrow().systems().forests());
        forests.bind(generator.getBiomeSource(), new ForestBiomes(generator.getBiomeSource(), state.sampler()));
        return forests;
    }

    private static String signature(ForestModel model, int chunkX, int chunkZ) {
        StringBuilder out = new StringBuilder();
        model.trees(chunkX << 4, chunkZ << 4, (chunkX << 4) + 15, (chunkZ << 4) + 15, spec -> {
            TreeShape shape = model.shape(spec);
            out.append(spec.key()).append(':').append(spec.x()).append(',').append(spec.y()).append(',').append(spec.z()).append(spec.form())
                    .append(Arrays.hashCode(shape.positions) * 31 + Arrays.hashCode(shape.codes)).append(' ');
        });
        return out.toString();
    }

    @Test
    void sameTreesFromAnyThreadAndOrder(MinecraftServer server) {
        ForestModel a = forests(server).model();
        ForestModel b = forests(server).model();
        List<int[]> chunks = new ArrayList<>();
        // The dark forest and old growth taiga near 288,32 and a lowland square near 0,0.
        for (int[] centre : new int[][] {{18, 2}, {0, 0}}) {
            for (int i = -10; i < 10; i++) {
                for (int j = -10; j < 10; j++) {
                    chunks.add(new int[] {centre[0] + i, centre[1] + j});
                }
            }
        }
        Map<Long, String> first = new ConcurrentHashMap<>();
        int trees = 0;
        for (int[] c : chunks) {
            String s = signature(a, c[0], c[1]);
            first.put(((long) c[0] << 32) ^ (c[1] & 0xFFFFFFFFL), s);
            trees += s.isEmpty() ? 0 : s.split(" ").length;
        }
        Collections.shuffle(chunks, new Random(11));
        AtomicInteger differ = new AtomicInteger();
        chunks.parallelStream().forEach(c -> {
            if (!signature(b, c[0], c[1]).equals(first.get(((long) c[0] << 32) ^ (c[1] & 0xFFFFFFFFL)))) {
                differ.incrementAndGet();
            }
        });
        Vastlands.LOGGER.info("ForestGameTest: {} chunks, {} trees reaching them, {} differ between orders", chunks.size(), trees, differ.get());
        assertTrue(trees > chunks.size(), "too few trees: " + trees);
        assertEquals(0, differ.get(), "chunks whose trees depend on the query order");
    }
}
