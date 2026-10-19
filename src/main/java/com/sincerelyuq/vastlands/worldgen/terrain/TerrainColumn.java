package com.sincerelyuq.vastlands.worldgen.terrain;

import com.sincerelyuq.vastlands.worldgen.geography.GeographySample;

/**
 * Everything the terrain model knows about one block column. Immutable.
 *
 * @param height          surface height in blocks (fractional; the solid surface is at
 *                        {@code floor(height)}); below sea level this is the sea or lake floor
 * @param baseHeight      regional ground level without hills, mountains and plateaus: the level
 *                        valleys cut down to. Useful as a drainage reference
 * @param continentalness raw continent field: below 0 is sea, 0 is the coastline, ~1 deep inland
 * @param mountain        mountain system strength, 0 (none) .. 1 (core of a range)
 * @param plateau         plateau strength, 0 .. 1 (1 on top of a plateau)
 * @param gullyDepth      blocks removed by gully erosion (0 on flat ground); the deepest values
 *                        mark the drainage lines of slopes
 * @param ruggedness      0 (flat plains) .. 1 (high rugged mountains); drives the erosion parameter
 * @param slopeX          d(height)/dx of the terrain before gully erosion, blocks per block
 * @param slopeZ          d(height)/dz of the terrain before gully erosion, blocks per block
 * @param climateContinentalness vanilla climate parameter for biome selection
 * @param climateErosion  vanilla climate parameter for biome selection
 * @param climateWeirdness vanilla climate parameter ("ridges") for biome selection
 * @param climateTemperature vanilla climate parameter for biome selection (see {@code BiomeClimate})
 * @param climateHumidity vanilla climate parameter ("vegetation") for biome selection
 * @param geography       the macro geography and climate of the column
 */
public record TerrainColumn(
        double height,
        double baseHeight,
        double continentalness,
        double mountain,
        double plateau,
        double gullyDepth,
        double ruggedness,
        double slopeX,
        double slopeZ,
        double climateContinentalness,
        double climateErosion,
        double climateWeirdness,
        double climateTemperature,
        double climateHumidity,
        GeographySample geography
) {
    /** Steepness of the large-scale terrain, blocks per block. */
    public double slope() {
        return Math.sqrt(this.slopeX * this.slopeX + this.slopeZ * this.slopeZ);
    }
}
