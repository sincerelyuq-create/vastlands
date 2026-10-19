package com.sincerelyuq.vastlands.worldgen.geography;

import com.sincerelyuq.vastlands.worldgen.noise.NoiseMath;

/**
 * The macro geography and climate of one block column. Immutable; produced by
 * {@link GeographyModel#sample}. All fields are continuous in (x, z) except {@link #landform}.
 *
 * <p>Heights are block Y values. Climate values use vanilla's biome parameter scale (-1..1), so
 * the thresholds of vanilla's biome table apply directly (temperature bands at -0.45, -0.15, 0.2,
 * 0.55; humidity bands at -0.35, -0.1, 0.1, 0.3).
 *
 * @param elevation           regional surface: continents, sea floor, hills, mountain systems and
 *                            plateaus, without gully erosion and small detail. The terrain surface
 *                            ({@code TerrainColumn.height}) is this minus gullies plus detail
 * @param baseHeight          regional ground level without hills, mountains and plateaus: what
 *                            valleys cut down to; on the sea floor it includes ridges, seamounts and
 *                            trenches
 * @param continentalness     continent field: below 0 is sea (shelf down to -0.12, slope to -0.35,
 *                            abyss beyond), 0 the coastline, about 1 far inland
 * @param plains              0..1, strength of a plains province (vast lowland with few hills)
 * @param hilliness           0..1, rolling hills
 * @param mountain            0..1, mountain system strength (1 on a main crest, falls off over the
 *                            flanks; includes branch ranges)
 * @param mountainHeight      relief of the mountain system above the base, in blocks
 * @param alpine              0..1, young sharp ranges (1) vs old rounded ones (0)
 * @param plateau             0..1, plateau strength (1 on top of a tableland)
 * @param basin               0..1, interior basin strength
 * @param dryness             0..1, arid province (more tablelands, rockier and less gullied
 *                            mountains, drier climate)
 * @param trench              0..1, abyssal trench strength (1 on a trench axis)
 * @param temperature         climate temperature at the regional elevation (lapse rate applied)
 * @param seaLevelTemperature climate temperature without the elevation term; frozen seas use it
 * @param moisture            climate moisture: coasts wetter, deep interiors and rain shadows drier
 * @param rainShadow          0..1, how much of the prevailing wind's moisture upwind ranges remove
 * @param windX               prevailing wind direction (unit vector, x component; air moves this way)
 * @param windZ               prevailing wind direction (unit vector, z component)
 * @param landform            regional landform classification
 */
public record GeographySample(
        double elevation,
        double baseHeight,
        double continentalness,
        double plains,
        double hilliness,
        double mountain,
        double mountainHeight,
        double alpine,
        double plateau,
        double basin,
        double dryness,
        double trench,
        double temperature,
        double seaLevelTemperature,
        double moisture,
        double rainShadow,
        double windX,
        double windZ,
        Landform landform
) {
    /** 0..1, soft land mask of the continent field (0 at sea, 1 inland of the coastline). */
    public double land() {
        return NoiseMath.smoothstep(-0.02, 0.07, this.continentalness);
    }

    /** 0 at and beyond the coastline .. 1 deep in a continental interior (a distance-to-sea proxy). */
    public double continentality() {
        return NoiseMath.clamp01(this.continentalness / 1.2);
    }

    /** 0..1, how firmly the sea surface here is frozen into an ice field (see {@link FrozenSea}). */
    public double iceCover() {
        return FrozenSea.strength(this.seaLevelTemperature);
    }
}
