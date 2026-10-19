package com.sincerelyuq.vastlands.worldgen.geography;

/**
 * Tunable targets of the geography. {@link #DEFAULT} is what Vastlands worlds use; the preview tool
 * ({@code ./gradlew terrainPreview --args="--region-scale 1.5 ..."}) can render other values.
 *
 * <p>Changing any value changes generation: worlds created with other settings get seams at the
 * border between old and new chunks.
 *
 * @param regionScale multiplies every macro wavelength: continents and ocean regions, plains and
 *                    hill provinces, dry provinces, mountain systems, plateaus and climate zones
 *                    (1.0: continents ~11 000 blocks, ocean regions ~42 000, provinces ~16 000,
 *                    climate zones ~26 000 for temperature and ~17 000 for moisture)
 * @param oceanBias   lowers the continent field everywhere; larger values give more and wider oceans
 *                    (0.1: about 36% of the surface is sea)
 * @param plainsBias  threshold of the plains provinces in their noise; larger values give more and
 *                    larger plains (-0.08: about a third of the land)
 * @param lapseRate   temperature drop per block of regional elevation above sea level + 12
 *                    (vanilla temperature scale; 0.0022: one climate band per ~150 blocks)
 * @param rainShadow  moisture removed in the full lee of a high range (vanilla humidity scale)
 * @param trenchThreshold gate of abyssal trenches in their 30 000-block rarity noise; higher is rarer
 */
public record GeographySettings(
        double regionScale,
        double oceanBias,
        double plainsBias,
        double lapseRate,
        double rainShadow,
        double trenchThreshold
) {
    public static final GeographySettings DEFAULT = new GeographySettings(1.0, 0.1, -0.08, 0.0022, 0.5, 0.25);

    public GeographySettings {
        if (!(regionScale > 0.0)) {
            throw new IllegalArgumentException("regionScale must be positive: " + regionScale);
        }
    }

    public GeographySettings withRegionScale(double scale) {
        return new GeographySettings(scale, this.oceanBias, this.plainsBias, this.lapseRate, this.rainShadow, this.trenchThreshold);
    }
}
