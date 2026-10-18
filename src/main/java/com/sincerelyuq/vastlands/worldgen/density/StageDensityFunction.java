package com.sincerelyuq.vastlands.worldgen.density;

import com.sincerelyuq.vastlands.worldgen.context.ContextBindingVisitor;
import com.sincerelyuq.vastlands.worldgen.system.DensityRange;
import com.sincerelyuq.vastlands.worldgen.system.DensityStage;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSystems;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Noise router node that hands density to a Vastlands generation system.
 *
 * <p>JSON: {@code {"type": "vastlands:terrain", "input": <density function>}}, likewise
 * {@code vastlands:hydrology} and {@code vastlands:caves}.
 *
 * <p>An unbound node returns its input unchanged, the same way vanilla noises read 0 before
 * {@code RandomState} wires them. Binding happens in {@link #mapAll} when the visitor is the
 * {@link ContextBindingVisitor} installed by {@code RandomStateMixin}; the bound implementation is
 * kept through every later {@code mapAll}, such as {@code NoiseChunk}'s marker wrapping.
 */
public record StageDensityFunction(Stage stage, DensityFunction input, @Nullable DensityStage implementation) implements DensityFunction {
    @Override
    public double compute(FunctionContext context) {
        double density = this.input.compute(context);
        return this.implementation == null
                ? density
                : this.implementation.apply(context.blockX(), context.blockY(), context.blockZ(), density);
    }

    @Override
    public void fillArray(double[] array, ContextProvider contextProvider) {
        this.input.fillArray(array, contextProvider);
        if (this.implementation != null) {
            for (int i = 0; i < array.length; i++) {
                FunctionContext context = contextProvider.forIndex(i);
                array[i] = this.implementation.apply(context.blockX(), context.blockY(), context.blockZ(), array[i]);
            }
        }
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        DensityStage bound = this.implementation;
        if (bound == null && visitor instanceof ContextBindingVisitor binder) {
            bound = this.stage.select(binder.context().systems());
        }
        return visitor.apply(new StageDensityFunction(this.stage, this.input.mapAll(visitor), bound));
    }

    @Override
    public double minValue() {
        return this.implementation == null ? this.input.minValue() : this.outputRange().min();
    }

    @Override
    public double maxValue() {
        return this.implementation == null ? this.input.maxValue() : this.outputRange().max();
    }

    private DensityRange outputRange() {
        return this.implementation.outputRange(new DensityRange(this.input.minValue(), this.input.maxValue()));
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return this.stage.codec;
    }

    public enum Stage implements StringRepresentable {
        TERRAIN("terrain", GenerationSystems::terrain),
        HYDROLOGY("hydrology", GenerationSystems::hydrology),
        CAVES("caves", GenerationSystems::caves);

        private final String name;
        private final Function<GenerationSystems, DensityStage> selector;
        private final KeyDispatchDataCodec<StageDensityFunction> codec;

        Stage(String name, Function<GenerationSystems, DensityStage> selector) {
            this.name = name;
            this.selector = selector;
            this.codec = KeyDispatchDataCodec.of(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("input")
                            .xmap(input -> new StageDensityFunction(this, input, null), StageDensityFunction::input)
            );
        }

        public DensityStage select(GenerationSystems systems) {
            return this.selector.apply(systems);
        }

        public KeyDispatchDataCodec<StageDensityFunction> codec() {
            return this.codec;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
