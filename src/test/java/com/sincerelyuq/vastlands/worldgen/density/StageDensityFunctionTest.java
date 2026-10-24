package com.sincerelyuq.vastlands.worldgen.density;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.sincerelyuq.vastlands.worldgen.context.ContextBindingVisitor;
import com.sincerelyuq.vastlands.worldgen.context.GenerationContext;
import com.sincerelyuq.vastlands.worldgen.system.DensityRange;
import com.sincerelyuq.vastlands.worldgen.system.DensityStage;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSetup;
import com.sincerelyuq.vastlands.worldgen.system.Passthrough;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.junit.jupiter.api.Test;

class StageDensityFunctionTest {
    private static final DensityFunction.FunctionContext POS = new DensityFunction.SinglePointContext(10, 20, 30);

    /** Adds the block's Y to the density, so position handling is observable. */
    private static final DensityStage ADD_Y = new DensityStage() {
        @Override
        public String id() {
            return "add_y";
        }

        @Override
        public double apply(int blockX, int blockY, int blockZ, double density) {
            return density + blockY;
        }

        @Override
        public DensityRange outputRange(DensityRange input) {
            return new DensityRange(input.min() - 256, input.max() + 511);
        }
    };

    private static StageDensityFunction unbound(StageDensityFunction.Stage stage, double value) {
        return new StageDensityFunction(stage, DensityFunctions.constant(value), null);
    }

    @Test
    void unboundNodeReturnsItsInput() {
        StageDensityFunction function = unbound(StageDensityFunction.Stage.TERRAIN, 0.5);
        assertEquals(0.5, function.compute(POS));
        assertEquals(0.5, function.minValue());
        assertEquals(0.5, function.maxValue());
    }

    @Test
    void boundNodeDelegatesToItsStage() {
        StageDensityFunction function = new StageDensityFunction(StageDensityFunction.Stage.TERRAIN, DensityFunctions.constant(0.5), ADD_Y);
        assertEquals(20.5, function.compute(POS));
        assertEquals(0.5 - 256, function.minValue());
        assertEquals(0.5 + 511, function.maxValue());

        DensityFunction.FunctionContext[] points = {
                new DensityFunction.SinglePointContext(0, -256, 0),
                new DensityFunction.SinglePointContext(0, 0, 0),
                new DensityFunction.SinglePointContext(0, 511, 0)
        };
        double[] values = new double[points.length];
        function.fillArray(values, new DensityFunction.ContextProvider() {
            @Override
            public DensityFunction.FunctionContext forIndex(int arrayIndex) {
                return points[arrayIndex];
            }

            @Override
            public void fillAllDirectly(double[] array, DensityFunction densityFunction) {
                for (int i = 0; i < array.length; i++) {
                    array[i] = densityFunction.compute(points[i]);
                }
            }
        });
        assertArrayEquals(new double[] {-255.5, 0.5, 511.5}, values);
    }

    @Test
    void bindingVisitorBindsEveryStageToOneLazilyCreatedContext() {
        AtomicInteger created = new AtomicInteger();
        ContextBindingVisitor binder = new ContextBindingVisitor(f -> f, () -> {
            created.incrementAndGet();
            return new GenerationContext(new GenerationSetup(7L, -256, 768, 63));
        });
        DensityFunction router = DensityFunctions.add(
                unbound(StageDensityFunction.Stage.TERRAIN, 0.0),
                DensityFunctions.min(
                        unbound(StageDensityFunction.Stage.HYDROLOGY, 1.0),
                        unbound(StageDensityFunction.Stage.CAVES, 2.0)));

        DensityFunction wired = router.mapAll(binder);

        assertEquals(1, created.get());
        GenerationContext context = binder.createdContext();
        assertNotNull(context);
        assertEquals(7L, context.setup().worldSeed());
        DensityStage terrain = context.systems().terrain();
        DensityStage hydrology = context.systems().hydrology();
        assertEquals(terrain.apply(POS.blockX(), POS.blockY(), POS.blockZ(), 0.0)
                + Math.min(hydrology.apply(POS.blockX(), POS.blockY(), POS.blockZ(), 1.0), 2.0), wired.compute(POS));
        assertSame(terrain, findStage(wired, StageDensityFunction.Stage.TERRAIN).implementation());
        assertSame(hydrology, findStage(wired, StageDensityFunction.Stage.HYDROLOGY).implementation());
        assertSame(Passthrough.INSTANCE, findStage(wired, StageDensityFunction.Stage.CAVES).implementation());
    }

    @Test
    void routerWithoutStagesCreatesNoContext() {
        ContextBindingVisitor binder = new ContextBindingVisitor(f -> f, () -> {
            throw new AssertionError("context must not be created");
        });
        DensityFunctions.add(DensityFunctions.constant(1.0), DensityFunctions.constant(2.0)).mapAll(binder);
        assertNull(binder.createdContext());
    }

    @Test
    void bindingSurvivesLaterVisitors() {
        StageDensityFunction bound = new StageDensityFunction(StageDensityFunction.Stage.HYDROLOGY, DensityFunctions.constant(0.0), ADD_Y);
        DensityFunction remapped = bound.mapAll(f -> f);
        assertSame(ADD_Y, assertInstanceOf(StageDensityFunction.class, remapped).implementation());
    }

    @Test
    void codecRoundTripsAndDropsTheBinding() {
        for (StageDensityFunction.Stage stage : StageDensityFunction.Stage.values()) {
            StageDensityFunction bound = new StageDensityFunction(stage, DensityFunctions.constant(0.25), ADD_Y);
            JsonElement json = DensityFunction.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, bound).getOrThrow();
            assertEquals(JsonParser.parseString("{\"type\":\"vastlands:" + stage.getSerializedName() + "\",\"input\":0.25}"), json);

            StageDensityFunction decoded = assertInstanceOf(
                    StageDensityFunction.class,
                    DensityFunction.DIRECT_CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
            assertEquals(stage, decoded.stage());
            assertNull(decoded.implementation());
            assertEquals(0.25, decoded.compute(POS));
        }
    }

    private static StageDensityFunction findStage(DensityFunction function, StageDensityFunction.Stage stage) {
        StageDensityFunction[] found = new StageDensityFunction[1];
        function.mapAll(f -> {
            if (f instanceof StageDensityFunction s && s.stage() == stage) {
                found[0] = s;
            }
            return f;
        });
        assertNotNull(found[0], "no " + stage + " node");
        return found[0];
    }
}
