package com.sincerelyuq.vastlands.worldgen.density;

import com.sincerelyuq.vastlands.worldgen.context.ContextBindingVisitor;
import com.sincerelyuq.vastlands.worldgen.system.HydrologySystem;
import com.sincerelyuq.vastlands.worldgen.system.TerrainParameter;
import javax.annotation.Nullable;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Noise router node that reads a {@link TerrainParameter} as reshaped by the world's hydrology
 * system (river biomes, channel floors in the initial density).
 *
 * <p>JSON: {@code {"type": "vastlands:hydrology_parameter", "parameter": "weirdness"}}. Binding
 * works like {@link TerrainParameterFunction}: an unbound node returns 0, the
 * {@link ContextBindingVisitor} of {@code RandomStateMixin} binds it to the hydrology system.
 */
public record HydrologyParameterFunction(TerrainParameter parameter, @Nullable HydrologySystem hydrology) implements DensityFunction {
    public static final KeyDispatchDataCodec<HydrologyParameterFunction> CODEC = KeyDispatchDataCodec.of(
            TerrainParameter.CODEC.fieldOf("parameter")
                    .xmap(parameter -> new HydrologyParameterFunction(parameter, null), HydrologyParameterFunction::parameter)
    );

    @Override
    public double compute(FunctionContext context) {
        return this.hydrology == null ? 0.0 : this.hydrology.parameter(this.parameter, context.blockX(), context.blockY(), context.blockZ());
    }

    @Override
    public void fillArray(double[] array, ContextProvider contextProvider) {
        contextProvider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        HydrologySystem bound = this.hydrology;
        if (bound == null && visitor instanceof ContextBindingVisitor binder) {
            bound = binder.context().systems().hydrology();
        }
        return visitor.apply(new HydrologyParameterFunction(this.parameter, bound));
    }

    @Override
    public double minValue() {
        return this.parameter.min();
    }

    @Override
    public double maxValue() {
        return this.parameter.max();
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
