package com.sincerelyuq.vastlands.worldgen.biome;

/**
 * Grass colours as the client draws them, computed on the server: vanilla's grass colour map
 * ({@code assets/minecraft/textures/colormap/grass.png}) is a client resource, so a dedicated
 * server's {@code GrassColor} table is empty and {@code Biome.getGrassColor} returns nothing
 * useful there. Pure Java (previews and tests use it too).
 *
 * <p>{@link #MAP} is vanilla's colour map sampled at temperature and downfall 0, 1/8 .. 1 (the map is
 * indexed by temperature and downfall × temperature, as {@code ColorMapColorUtil} does); bilinear
 * interpolation between the samples stays within 2 of 255 of the full map everywhere. Colour
 * comparisons use CIELAB (D65), with lightness weighted up: a floor material that is much brighter
 * than the grass around it (bright moss on dark-forest grass) is what reads as a foreign patch.
 */
public final class GrassColors {
    /** Rows: downfall 0 .. 1 in eighths; columns: temperature 0 .. 1 in eighths. */
    private static final int[] MAP = {
            0x80B497, 0x88B58E, 0x90B586, 0x97B67E, 0xA0B676, 0xA7B66D, 0xB0B665, 0xB7B75D, 0xBFB755,
            0x80B497, 0x88B58E, 0x8EB685, 0x95B67D, 0x9BB774, 0xA1B76C, 0xA6B962, 0xABB959, 0xB0BA51,
            0x80B497, 0x87B58E, 0x8CB685, 0x92B67C, 0x97B873, 0x9AB96A, 0x9DBA5F, 0xA0BB57, 0xA0BD4D,
            0x80B497, 0x86B58E, 0x8BB685, 0x8FB77C, 0x92B971, 0x94BA68, 0x94BC5D, 0x94BD52, 0x91BF49,
            0x80B497, 0x85B68E, 0x8AB685, 0x8CB87A, 0x8EB971, 0x8DBB66, 0x8BBD5B, 0x88C050, 0x82C245,
            0x80B497, 0x85B68E, 0x88B684, 0x8AB87A, 0x89BA6F, 0x86BC64, 0x82BF58, 0x7CC14B, 0x73C540,
            0x80B497, 0x85B68E, 0x87B783, 0x87B979, 0x85BB6E, 0x80BD62, 0x79C155, 0x70C449, 0x65C83C,
            0x80B497, 0x84B68D, 0x85B783, 0x84B978, 0x80BC6D, 0x7ABF60, 0x70C253, 0x64C645, 0x55CA37,
            0x80B497, 0x83B68D, 0x84B883, 0x81BA78, 0x7CBD6B, 0x73C05E, 0x67C450, 0x58C842, 0x47CD33,
    };
    /** Mean colour of the grass block's top texture (grey, multiplied by the tint). */
    private static final double GRASS_TEXTURE = 147.0 / 255.0;
    /** Mean colour of vanilla's moss block texture ({@code textures/block/moss_block.png}). */
    public static final int MOSS = 0x596E2D;
    /** The two colours of vanilla's swamp grass modifier (it picks one by noise). */
    private static final int SWAMP_A = 0x4C763C;
    private static final int SWAMP_B = 0x6A7039;

    private GrassColors() {
    }

    /** Vanilla's grass tint for a biome's climate (temperature and downfall are clamped to 0..1). */
    public static int tint(double temperature, double downfall) {
        double t = Math.max(0.0, Math.min(1.0, temperature)) * 8.0;
        double d = Math.max(0.0, Math.min(1.0, downfall)) * 8.0;
        int i = Math.min(7, (int) d);
        int j = Math.min(7, (int) t);
        double u = d - i;
        double v = t - j;
        int a = MAP[i * 9 + j];
        int b = MAP[i * 9 + j + 1];
        int c = MAP[(i + 1) * 9 + j];
        int e = MAP[(i + 1) * 9 + j + 1];
        int rgb = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            double ch = (1 - u) * ((1 - v) * (a >> shift & 255) + v * (b >> shift & 255)) + u * ((1 - v) * (c >> shift & 255) + v * (e >> shift & 255));
            rgb |= Math.max(0, Math.min(255, (int) Math.round(ch))) << shift;
        }
        return rgb;
    }

    /** Vanilla's dark forest grass modifier. */
    public static int darkForest(int tint) {
        return (tint & 0xFEFEFE) + 0x28340A >> 1;
    }

    /** Vanilla's swamp grass modifier, averaged over its two colours. */
    public static int swamp() {
        return mix(SWAMP_A, SWAMP_B, 0.5);
    }

    /** The colour of a grass block's top as drawn with a tint (texture mean × tint). */
    public static int grassTop(int tint) {
        int r = (int) Math.round((tint >> 16 & 255) * GRASS_TEXTURE);
        int g = (int) Math.round((tint >> 8 & 255) * GRASS_TEXTURE);
        int b = (int) Math.round((tint & 255) * GRASS_TEXTURE);
        return r << 16 | g << 8 | b;
    }

    public static int mix(int a, int b, double t) {
        int out = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            out |= (int) Math.round((a >> shift & 255) * (1 - t) + (b >> shift & 255) * t) << shift;
        }
        return out;
    }

    /** CIELAB (D65) of an sRGB colour: L, a, b. */
    public static double[] lab(int rgb) {
        double[] c = new double[3];
        for (int k = 0; k < 3; k++) {
            double v = (rgb >> (16 - 8 * k) & 255) / 255.0;
            c[k] = v > 0.04045 ? Math.pow((v + 0.055) / 1.055, 2.4) : v / 12.92;
        }
        double x = (0.4124 * c[0] + 0.3576 * c[1] + 0.1805 * c[2]) / 0.95047;
        double y = 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
        double z = (0.0193 * c[0] + 0.1192 * c[1] + 0.9505 * c[2]) / 1.08883;
        double fx = f(x);
        double fy = f(y);
        double fz = f(z);
        return new double[] {116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz)};
    }

    private static double f(double t) {
        return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16.0 / 116.0;
    }

    /**
     * Difference between two colours as seen side by side on the ground: CIE76 in Lab with the
     * lightness difference counted 1.5 times (a brighter patch stands out more than a shift of hue).
     */
    public static double contrast(int a, int b) {
        double[] p = lab(a);
        double[] q = lab(b);
        double dl = 1.5 * (p[0] - q[0]);
        double da = p[1] - q[1];
        double db = p[2] - q[2];
        return Math.sqrt(dl * dl + da * da + db * db);
    }

    /**
     * 0..1: how well moss blends into grass drawn in {@code grassTop}: 1 where the two read as the
     * same green (plains, forest, birch forest), about 0 where moss is a bright foreign patch (dark
     * forest and swamp grass, the blue-green of taiga and snowy biomes, pale gardens, badlands).
     */
    public static double mossBlend(int grassTop) {
        double d = contrast(MOSS, grassTop);
        double t = Math.max(0.0, Math.min(1.0, (d - 7.0) / (24.0 - 7.0)));
        return 1.0 - t * t * (3.0 - 2.0 * t);
    }
}
