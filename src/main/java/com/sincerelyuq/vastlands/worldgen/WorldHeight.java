package com.sincerelyuq.vastlands.worldgen;

/**
 * Vertical bounds of the Vastlands overworld.
 *
 * <p>The authoritative values live in data: {@code dimension_type/overworld.json} (build limits)
 * and {@code worldgen/noise_settings/overworld.json} (noise range). Both must agree with these
 * constants; {@code WorldHeightDataTest} fails the build if they drift apart, and
 * {@link WorldgenDiagnostics} logs an error if a loaded level disagrees at runtime.
 *
 * <p>Vanilla limits: min_y and height must be multiples of 16 and min_y + height must not
 * exceed 2032 ({@code DimensionType.MAX_Y + 1}).
 */
public final class WorldHeight {
    public static final int MIN_Y = -256;
    public static final int HEIGHT = 768;
    /** Highest buildable block, inclusive. */
    public static final int MAX_Y = MIN_Y + HEIGHT - 1;

    private WorldHeight() {
    }
}
