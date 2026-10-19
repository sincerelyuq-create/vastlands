package com.sincerelyuq.vastlands.worldgen.system;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Noise router outputs owned by {@link TerrainSystem}. They are bound into the router by
 * {@code vastlands:terrain_parameter} nodes (see {@code TerrainParameterFunction}), so biome
 * selection, aquifers and the preliminary surface follow the Vastlands terrain instead of vanilla's
 * climate noises.
 *
 * <p>Each parameter has a conservative value range; implementations must stay inside it.
 */
public enum TerrainParameter implements StringRepresentable {
    /** Router {@code continents}: vanilla climate continentalness (ocean / coast / inland). */
    CONTINENTALNESS("continentalness", -1.2, 1.0),
    /** Router {@code erosion}: vanilla climate erosion (low = rugged mountains, high = flat). */
    EROSION("erosion", -1.0, 1.0),
    /** Router {@code ridges}: vanilla climate weirdness; |w| folds into peaks and valleys. */
    WEIRDNESS("weirdness", -1.0, 1.0),
    /** Router {@code depth}: (surface - y) / 128, 0 at the surface, positive underground. */
    DEPTH("depth", -8.0, 8.0),
    /** Router {@code initial_density_without_jaggedness}: positive below a conservative surface estimate. */
    INITIAL_DENSITY("initial_density", -128.0, 128.0),
    /** Router {@code temperature}: vanilla climate temperature, from the geography's climate. */
    TEMPERATURE("temperature", -1.0, 1.0),
    /** Router {@code vegetation}: vanilla climate humidity, from the geography's climate. */
    VEGETATION("vegetation", -1.0, 1.0),
    /**
     * Bottom slide factor inside the final density (vanilla: {@code y_clamped_gradient} -64..-40):
     * 0 forces the rock below the landscape solid, 1 leaves the landscape. Vanilla's band except
     * under abyssal trenches, where it moves down with the trench floor.
     */
    BOTTOM_SLIDE("bottom_slide", 0.0, 1.0);

    public static final Codec<TerrainParameter> CODEC = StringRepresentable.fromEnum(TerrainParameter::values);

    private final String name;
    private final double min;
    private final double max;

    TerrainParameter(String name, double min, double max) {
        this.name = name;
        this.min = min;
        this.max = max;
    }

    public double min() {
        return this.min;
    }

    public double max() {
        return this.max;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
