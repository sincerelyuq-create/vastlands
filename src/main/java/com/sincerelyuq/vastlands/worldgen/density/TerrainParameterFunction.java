package com.sincerelyuq.vastlands.worldgen.density;

import com.sincerelyuq.vastlands.worldgen.context.ContextBindingVisitor;
import com.sincerelyuq.vastlands.worldgen.system.TerrainParameter;
import com.sincerelyuq.vastlands.worldgen.system.TerrainSystem;
import javax.annotation.Nullable;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Noise router node that reads a {@link TerrainParameter} from the world's terrain system.
 *
 * <p>JSON: {@code {"type": "vastlands:terrain_parameter", "parameter": "continentalness"}}; also
 * {@code erosion}, {@code weirdness}, {@code depth}, {@code initial_density}. Binding works like
 * {@link StageDensityFunction}: an unbound node returns 0 (as unwired vanilla noises do), and the
 * {@link ContextBindingVisitor} of {@code RandomStateMixin} binds it to the terrain system.
 */
public record TerrainParameterFunction(TerrainParameter parameter, @Nullable TerrainSystem terrain) implements DensityFunction {
    public static final KeyDispatchDataCodec<TerrainParameterFunction> CODEC = KeyDispatchDataCodec.of(
            TerrainParameter.CODEC.fieldOf("parameter")
                    .xmap(parameter -> new TerrainParameterFunction(parameter, null), TerrainParameterFunction::parameter)
    );

    @Override
    public double compute(FunctionContext context) {
        return this.terrain == null ? 0.0 : this.terrain.parameter(this.parameter, context.blockX(), context.blockY(), context.blockZ());
    }

    @Override
    public void fillArray(double[] array, ContextProvider contextProvider) {
        contextProvider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        TerrainSystem bound = this.terrain;
        if (bound == null && visitor instanceof ContextBindingVisitor binder) {
            bound = binder.context().systems().terrain();
        }
        return visitor.apply(new TerrainParameterFunction(this.parameter, bound));
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
