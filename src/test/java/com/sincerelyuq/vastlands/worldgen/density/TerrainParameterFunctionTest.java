package com.sincerelyuq.vastlands.worldgen.density;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.sincerelyuq.vastlands.worldgen.context.ContextBindingVisitor;
import com.sincerelyuq.vastlands.worldgen.context.GenerationContext;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSetup;
import com.sincerelyuq.vastlands.worldgen.system.TerrainParameter;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.junit.jupiter.api.Test;

class TerrainParameterFunctionTest {
    private static final DensityFunction.FunctionContext POS = new DensityFunction.SinglePointContext(1001, 64, -2003);

    @Test
    void unboundReturnsZeroWithinTheParameterRange() {
        for (TerrainParameter parameter : TerrainParameter.values()) {
            TerrainParameterFunction function = new TerrainParameterFunction(parameter, null);
            assertEquals(0.0, function.compute(POS));
            assertEquals(parameter.min(), function.minValue());
            assertEquals(parameter.max(), function.maxValue());
        }
    }

    @Test
    void bindsToTheTerrainOfTheContext() {
        ContextBindingVisitor binder = new ContextBindingVisitor(f -> f, () -> new GenerationContext(new GenerationSetup(5L, -256, 768, 63)));
        DensityFunction wired = new TerrainParameterFunction(TerrainParameter.EROSION, null).mapAll(binder);

        TerrainParameterFunction bound = assertInstanceOf(TerrainParameterFunction.class, wired);
        GenerationContext context = binder.createdContext();
        assertSame(context.systems().terrain(), bound.terrain());
        assertEquals(context.systems().terrain().parameter(TerrainParameter.EROSION, 1001, 64, -2003), bound.compute(POS));
        assertSame(bound.terrain(), assertInstanceOf(TerrainParameterFunction.class, bound.mapAll(f -> f)).terrain());
    }

    @Test
    void codecRoundTripsAndDropsTheBinding() {
        for (TerrainParameter parameter : TerrainParameter.values()) {
            JsonElement json = DensityFunction.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, new TerrainParameterFunction(parameter, null)).getOrThrow();
            assertEquals(JsonParser.parseString("{\"type\":\"vastlands:terrain_parameter\",\"parameter\":\"" + parameter.getSerializedName() + "\"}"), json);
            TerrainParameterFunction decoded = assertInstanceOf(TerrainParameterFunction.class, DensityFunction.DIRECT_CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
            assertEquals(parameter, decoded.parameter());
            assertNull(decoded.terrain());
        }
    }
}
