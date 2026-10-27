package com.sincerelyuq.vastlands.worldgen.nature;

import static com.sincerelyuq.vastlands.worldgen.noise.NoiseMath.smoothstep;

import com.sincerelyuq.vastlands.worldgen.caves.VastlandsCaves;
import com.sincerelyuq.vastlands.worldgen.geography.GeographySample;
import com.sincerelyuq.vastlands.worldgen.hydrology.WaterColumn;
import com.sincerelyuq.vastlands.worldgen.noise.NoiseMath;
import com.sincerelyuq.vastlands.worldgen.noise.SimplexNoise;
import com.sincerelyuq.vastlands.worldgen.terrain.TerrainColumn;
import java.util.List;
import java.util.function.Consumer;

/**
 * The natural formations of the world as a pure function of the seed and the position: where
 * boulders, tors, spires, arches, driftwood and shore stones stand at the surface, and what
 * decorates every cave (dripstone, roots and moss, sculk, crystals, basalt, rubble, pillars,
 * monumental columns). No game classes; the world is read through {@link Land} and {@link Biomes}.
 *
 * <p>Formations sit on jittered lattices of four scales per layer (surface: 12, 40, 160 and 768
 * blocks; caves: 6, 16, 40 and 96), so small details are frequent, medium formations occasional,
 * large ones uncommon and monumental wonders very rare. Each cell holds at most one candidate
 * column; a surface candidate is one formation, a cave candidate one formation per open run of
 * its column (every cave level the column crosses), chosen from the biome's style or the cave
 * biome's profile, the climate, the ground (slope, crests, water, the sea) or the cave run (its
 * height, the room around it, water or lava on its floor, megacaves), and a regional richness
 * field (~400 blocks at the surface, ~260 underground) so some places stay bare. A candidate
 * yields to a larger formation of the same layer whose footprint covers it (not recursively: the
 * larger ones never yield), so nothing depends on the order chunks ask.
 *
 * <p>Thread-safe: candidates and shapes are cached per thread and keyed by their cells.
 */
public final class NatureModel {
    /** What the formations read from the world: the other systems' models. Thread-safe. */
    public interface Land {
        /** The ground surface as generated (top solid block {@code ceil(surface) - 1}). */
        double surface(int x, int z);

        WaterColumn water(int x, int z);

        /** The terrain model's column (geography and climate), uncached. */
        TerrainColumn sample(int x, int z);

        /** Falls of 6+ blocks near a point: x, z, upper and lower water top. */
        List<double[]> falls(int x, int z);

        boolean isCave(int x, int y, int z);

        /** The open runs of a column ({@code VastlandsCaves.runs}); 0 without caves. */
        int runs(int x, int z, int[] out);

        /** Karst, fracture and volcanic strength of the rock of a column, 0..1 each. */
        double[] geology(int x, int z);
    }

    /** The world's biomes as decoration styles. Thread-safe. */
    public interface Biomes {
        /** {@link SurfaceStyle} ordinal of the surface biome at a block. */
        int surfaceStyle(int x, int y, int z);

        /** {@link CaveProfile} ordinal of the biome at a block underground. */
        int caveProfile(int x, int y, int z);

        /** 0..1: how well moss blends into the biome's grass at a block (see {@code GrassColors.mossBlend}). */
        default double mossBlend(int x, int y, int z) {
            return 1.0;
        }
    }

    static final int S_SMALL = 0;
    static final int S_MEDIUM = 1;
    static final int S_LARGE = 2;
    static final int S_WONDER = 3;
    static final int C_SMALL = 4;
    static final int C_MEDIUM = 5;
    static final int C_LARGE = 6;
    static final int C_GIANT = 7;
    static final int LAYERS = 8;
    private static final int[] CELL = {12, 40, 160, 768, 6, 16, 40, 96};
    /** Horizontal reach of each layer's formations (the largest of its kinds'). */
    private static final int[] REACH = {8, 14, 16, 32, 6, 10, 16, 16};
    private static final int MAX_RUNS = 16;

    /** The horizontal reach of a layer's formations (tests). */
    static int reach(int layer) {
        return REACH[layer];
    }
    /** The most a footprint may extend past its root (for queries of footprints near a point). */
    private static final double MAX_FOOTPRINT = 34.0;

    private final long seed;
    private final Land land;
    private final Biomes biomes;
    private final NatureSettings settings;
    private final int seaLevel;
    private final SimplexNoise rockiness;
    private final SimplexNoise richness;
    /** Groves of lumen mushrooms against open stretches of the bioluminescent depths (~110 blocks). */
    private final SimplexNoise groves;
    private final ThreadLocal<Caches> caches = ThreadLocal.withInitial(Caches::new);

    private static final class Caches {
        final NatureCache<Cand> cands = new NatureCache<>(8192);
        final NatureCache<FormationShape> shapes = new NatureCache<>(1024);
        final NatureCache<List<double[]>> falls = new NatureCache<>(64);
        final int[] runs = new int[MAX_RUNS * VastlandsCaves.RUN_FIELDS];
    }

    public NatureModel(long seed, Land land, Biomes biomes, NatureSettings settings, int seaLevel) {
        this.seed = seed;
        this.land = land;
        this.biomes = biomes;
        this.settings = settings;
        this.seaLevel = seaLevel;
        this.rockiness = new SimplexNoise(seed + 1);
        this.richness = new SimplexNoise(seed + 2);
        this.groves = new SimplexNoise(seed + 3);
    }

    public NatureSettings settings() {
        return this.settings;
    }

    // ------------------------------------------------------------------ candidates

    /** One lattice cell's candidate: its column and the formations it holds (one per cave run). */
    private static final class Cand {
        int layer;
        long i;
        long j;
        int x;
        int z;
        FormationSpec[] specs = NONE;
        double[] footprints = NO_FOOTPRINTS;
        /** -1 undecided, 0 yields to a larger formation, 1 stands. */
        byte[] accepted = NO_ACCEPTED;
    }

    private static final FormationSpec[] NONE = new FormationSpec[0];
    private static final double[] NO_FOOTPRINTS = new double[0];
    private static final byte[] NO_ACCEPTED = new byte[0];

    private Cand cand(int layer, long i, long j) {
        return this.caches.get().cands.get(NatureCache.key(layer, i, j), k -> layer < C_SMALL ? this.surfaceCand(layer, i, j) : this.caveCand(layer, i, j));
    }

    private long cellSeed(int layer, long i, long j) {
        return SimplexNoise.mix(this.seed ^ (layer + 1) * 0x632BE59BD9B4E019L ^ i * 0x9E3779B97F4A7C15L ^ j * 0xC2B2AE3D27D4EB4FL);
    }

    private static int groundTop(double surface) {
        return (int) Math.ceil(surface) - 1;
    }

    /** The top solid block under a column: the bed under water, the ground elsewhere. */
    int groundTop(int x, int z) {
        WaterColumn water = this.land.water(x, z);
        return water.wet() ? water.groundTop() : groundTop(this.land.surface(x, z));
    }

    int waterTop(int x, int z) {
        WaterColumn water = this.land.water(x, z);
        return water.wet() ? water.waterTop() : Integer.MIN_VALUE;
    }

    private void set(Cand c, FormationSpec spec, double footprint) {
        c.specs = new FormationSpec[] {spec};
        c.footprints = new double[] {footprint};
        c.accepted = new byte[] {-1};
    }

    // ------------------------------------------------------------------ the surface

    /** Chance of a surface formation per cell, by layer and style, before the regional rockiness and the settings. */
    private static double surfaceChance(int layer, SurfaceStyle style) {
        return switch (layer) {
            case S_SMALL -> switch (style) {
                case TEMPERATE -> 0.08;
                case DARK -> 0.09;
                case BOREAL -> 0.13;
                case OLD_GROWTH -> 0.2;
                case SNOWY -> 0.1;
                case MOUNTAIN -> 0.22;
                case WETLAND -> 0.03;
                case JUNGLE -> 0.05;
                case SAVANNA -> 0.1;
                case DESERT -> 0.08;
                case BADLANDS -> 0.12;
                case BEACH, RIVER -> 0.1;
                case STONY_SHORE -> 0.35;
                case OCEAN -> 0.0;
                case MUSHROOM -> 0.01;
                case NONE -> 0.0;
                case ASHEN -> 0.12;
                case STRATA -> 0.18;
                case DUNES -> 0.015;
            };
            case S_MEDIUM -> switch (style) {
                case MOUNTAIN -> 0.5;
                case STRATA -> 0.45;
                case ASHEN -> 0.16;
                case BADLANDS -> 0.36;
                case SAVANNA -> 0.14;
                case DESERT -> 0.12;
                case STONY_SHORE -> 0.2;
                case BOREAL, SNOWY -> 0.1;
                case TEMPERATE, OLD_GROWTH, JUNGLE -> 0.06;
                case OCEAN, RIVER, BEACH -> 0.08;
                default -> 0.0;
            };
            case S_LARGE -> switch (style) {
                case MOUNTAIN, DESERT -> 0.16;
                case STRATA -> 0.22;
                case ASHEN -> 0.1;
                case BADLANDS -> 0.25;
                case STONY_SHORE, BEACH, OCEAN -> 0.2;
                case SAVANNA -> 0.18;
                case TEMPERATE, BOREAL, SNOWY, JUNGLE -> 0.06;
                default -> 0.0;
            };
            case S_WONDER -> switch (style) {
                case MOUNTAIN, DESERT, BADLANDS, STONY_SHORE, STRATA -> 0.14;
                case ASHEN -> 0.06;
                case JUNGLE, SAVANNA, TEMPERATE -> 0.1;
                default -> 0.0;
            };
            default -> 0.0;
        };
    }

    private Cand surfaceCand(int layer, long i, long j) {
        Cand c = new Cand();
        c.layer = layer;
        c.i = i;
        c.j = j;
        NatureRandom r = new NatureRandom(this.cellSeed(layer, i, j));
        int size = CELL[layer];
        c.x = (int) (i * size + Math.floor(r.next() * size));
        c.z = (int) (j * size + Math.floor(r.next() * size));
        double u = r.next();
        double scale = this.settings.surface() * (layer == S_LARGE ? this.settings.large() : 1.0) * (layer == S_WONDER ? this.settings.wonders() : 1.0);
        // The regional rockiness: rocky country and places with hardly a stone.
        double rocky = 0.3 + 1.4 * smoothstep(-0.55, 0.55, NoiseMath.fbm(this.rockiness, c.x / 420.0, c.z / 420.0, 2, 0.5));
        double max = (layer == S_WONDER ? 0.35 : (layer == S_SMALL ? 0.7 : 0.5)) * scale * (layer >= S_LARGE ? 1.0 : rocky) * 2.5;
        if (u >= max) {
            return c;
        }
        WaterColumn water = this.land.water(c.x, c.z);
        int ground = water.wet() ? water.groundTop() : groundTop(this.land.surface(c.x, c.z));
        SurfaceStyle style = SurfaceStyle.VALUES[this.biomes.surfaceStyle(c.x, Math.max(ground, this.seaLevel - 1), c.z)];
        if (style == SurfaceStyle.NONE) {
            return c;
        }
        // The ground around: slope, crest, the nearest water.
        double slope = this.slope(c.x, c.z, 4);
        Shore shore = this.shore(c.x, c.z, water);
        double chance = surfaceChance(layer, style) * scale * (layer >= S_LARGE ? 1.0 : rocky);
        if (layer == S_SMALL) {
            chance *= 1.0 + 1.5 * smoothstep(0.3, 1.0, slope);
            if (shore != null && !water.wet()) {
                chance = Math.max(chance, 0.3 * scale);
            }
        }
        if (u >= chance) {
            return c;
        }
        double[] geology = this.land.geology(c.x, c.z);
        GeographySample geo = this.land.sample(c.x, c.z).geography();
        Site site = new Site(c, r, style, ground, water, slope, shore, geology, geo);
        switch (layer) {
            case S_SMALL -> this.small(site);
            case S_MEDIUM -> this.medium(site);
            case S_LARGE -> this.large(site);
            default -> this.wonder(site);
        }
        return c;
    }

    /** Where a surface candidate stands. */
    private record Site(Cand c, NatureRandom r, SurfaceStyle style, int ground, WaterColumn water, double slope, Shore shore, double[] geology,
                        GeographySample geo) {
    }

    /** The water's edge near a column: the direction to the water and its top. */
    private record Shore(double dirX, double dirZ, int waterTop, int distance, WaterColumn.Kind kind) {
    }

    private double slope(int x, int z, int r) {
        double ex = this.land.surface(x + r, z) - this.land.surface(x - r, z);
        double ez = this.land.surface(x, z + r) - this.land.surface(x, z - r);
        return Math.sqrt(ex * ex + ez * ez) / (2.0 * r);
    }

    /** The nearest water within 5 blocks (8 directions), or null. For a wet column, the nearest dry ground instead. */
    private Shore shore(int x, int z, WaterColumn here) {
        for (int d = 2; d <= 5; d += 3) {
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4;
                int sx = x + (int) Math.round(Math.cos(a) * d);
                int sz = z + (int) Math.round(Math.sin(a) * d);
                WaterColumn w = this.land.water(sx, sz);
                if (w.wet() != here.wet()) {
                    WaterColumn wet = here.wet() ? here : w;
                    return new Shore(here.wet() ? -Math.cos(a) : Math.cos(a), here.wet() ? -Math.sin(a) : Math.sin(a), wet.waterTop(), d, wet.kind());
                }
            }
        }
        return null;
    }

    private Rock surfaceRock(SurfaceStyle style, double[] geology, GeographySample geo) {
        double karst = geology[0];
        double fracture = geology[1];
        double volcanic = geology[2];
        if (style == SurfaceStyle.DESERT || style == SurfaceStyle.DUNES) {
            return Rock.SANDSTONE;
        }
        if (style == SurfaceStyle.BADLANDS) {
            return Rock.TERRACOTTA;
        }
        if (style == SurfaceStyle.STRATA) {
            return Rock.STRATA;
        }
        if (style == SurfaceStyle.ASHEN) {
            return Rock.BASALT;
        }
        if (volcanic > 0.45) {
            return Rock.BASALT;
        }
        if (karst > 0.55) {
            return Rock.LIMESTONE;
        }
        if (style == SurfaceStyle.SNOWY || geo.temperature() < -0.5) {
            return Rock.PALE;
        }
        // Granite in warm, fractured hills; cold peaks keep vanilla's grey stone.
        if (style == SurfaceStyle.SAVANNA || geo.temperature() > -0.25 && (fracture > 0.45 || geo.alpine() > 0.6 && geo.mountain() > 0.4)) {
            return Rock.GRANITE;
        }
        if (style == SurfaceStyle.STONY_SHORE || style == SurfaceStyle.BEACH || style == SurfaceStyle.RIVER) {
            return Rock.SHORE;
        }
        return Rock.STONE;
    }

    /** Flags of a surface formation from its climate: moss in damp woods (where it suits the grass), snow, dryness. */
    private int surfaceFlags(Site s, NatureRandom r) {
        int flags = 0;
        if (s.geo.temperature() < -0.45) {
            flags |= FormationSpec.SNOWY;
        }
        if (s.geo.moisture() < -0.3 || s.style == SurfaceStyle.DESERT || s.style == SurfaceStyle.BADLANDS || s.style == SurfaceStyle.SAVANNA
                || s.style == SurfaceStyle.ASHEN || s.style == SurfaceStyle.DUNES) {
            flags |= FormationSpec.DRY;
        }
        if (s.style.wooded() && s.geo.moisture() > -0.1 && (flags & FormationSpec.SNOWY) == 0) {
            double weight = switch (s.style) {
                case OLD_GROWTH -> 0.85;
                case JUNGLE, WETLAND -> 0.6;
                case DARK -> 0.55;
                default -> 0.35;
            };
            if (r.chance(weight * smoothstep(-0.1, 0.5, s.geo.moisture()) * this.settings.mossy())) {
                flags |= FormationSpec.MOSSY;
            }
        }
        return flags;
    }

    private long specKey(Cand c, int run) {
        return NatureCache.key(c.layer, c.i, c.j) ^ (long) run << 52;
    }

    /** Small surface formations: boulders and rock clusters, driftwood and shore stones at the water. */
    private void small(Site s) {
        Cand c = s.c;
        NatureRandom r = s.r;
        Rock rock = this.surfaceRock(s.style, s.geology, s.geo);
        int flags = this.surfaceFlags(s, r);
        if (s.shore != null && s.shore.distance <= 5) {
            // At the water's edge.
            boolean wetRoot = s.water.wet();
            if (wetRoot && s.water.waterTop() - s.water.groundTop() > 3) {
                return;
            }
            double along = Math.atan2(s.shore.dirZ, s.shore.dirX) + Math.PI / 2;
            boolean wood = !wetRoot && s.style != SurfaceStyle.DESERT && s.style != SurfaceStyle.BADLANDS && s.style != SurfaceStyle.MUSHROOM
                    && r.chance(s.style.wooded() || s.style == SurfaceStyle.BEACH || s.style == SurfaceStyle.RIVER ? 0.45 : 0.2);
            if (wood) {
                int y = s.ground + 1;
                if (!this.clearSurface(c.x, c.z, y, 4.0, true)) {
                    return;
                }
                int woodType = this.driftwoodType(s.style, s.geo, r);
                double length = r.between(3, 7);
                this.set(c, new FormationSpec(this.specKey(c, 0), FormationKind.DRIFTWOOD, c.x, c.z, y, length, 1.0, woodType, along, rock, flags,
                        r.nextLong()), length);
            } else {
                if (!this.clearSurface(c.x, c.z, s.ground + 1, 4.0, true)) {
                    return;
                }
                this.set(c, new FormationSpec(this.specKey(c, 0), FormationKind.SHORE_STONES, c.x, c.z, s.ground + 1, 2.0, 2.0, 0, along,
                        s.style == SurfaceStyle.DESERT || s.style == SurfaceStyle.BADLANDS ? rock : Rock.SHORE, flags & ~FormationSpec.MOSSY, r.nextLong()), 5.0);
            }
            return;
        }
        if (s.water.wet() || s.ground < this.seaLevel - 2) {
            return;
        }
        if (s.style == SurfaceStyle.ASHEN && s.slope < 0.45 && r.chance(0.55)) {
            // Fumaroles: ash vents on level, dry ground.
            int y = s.ground + 1;
            if (!this.clearSurface(c.x, c.z, y, 5.0, false) || this.relief(c.x, c.z, 4.0, r.next() * Math.PI) > 2.5) {
                return;
            }
            this.set(c, new FormationSpec(this.specKey(c, 0), FormationKind.ASH_VENTS, c.x, c.z, y, 1.0, r.range(1.5, 3.5), r.between(1, 3), 0,
                    Rock.BASALT, flags, r.nextLong()), 5.0);
            return;
        }
        boolean cluster = r.chance(s.style == SurfaceStyle.MOUNTAIN || s.style == SurfaceStyle.STONY_SHORE || s.style == SurfaceStyle.DESERT ? 0.45 : 0.3);
        int y = s.ground + 1;
        if (cluster) {
            if (!this.clearSurface(c.x, c.z, y, 6.0, false)) {
                return;
            }
            this.set(c, new FormationSpec(this.specKey(c, 0), FormationKind.ROCK_CLUSTER, c.x, c.z, y, 1.0, r.range(1.0, 1.9), 0, r.next() * Math.PI, rock,
                    flags, r.nextLong()), 6.5);
        } else {
            // Mostly small stones and boulders; now and then a large erratic.
            double radius = r.chance(0.07) ? r.range(2.6, 3.8) : r.skewed(0.7, 2.4, 1.7);
            if (!this.clearSurface(c.x, c.z, y, radius + 1.0, false)) {
                return;
            }
            this.set(c, new FormationSpec(this.specKey(c, 0), FormationKind.BOULDER, c.x, c.z, y, radius * r.range(0.6, 0.95), radius, 0,
                    r.next() * Math.PI, rock, flags, r.nextLong()), radius + 1.0);
        }
    }

    private int driftwoodType(SurfaceStyle style, GeographySample geo, NatureRandom r) {
        // Ordinals of the forests' Wood: OAK 0, BIRCH 1, SPRUCE 2, DARK_OAK 3, JUNGLE 4, ACACIA 5.
        return switch (style) {
            case DARK -> r.chance(0.6) ? 3 : 0;
            case BOREAL, OLD_GROWTH, SNOWY, MOUNTAIN -> r.chance(0.8) ? 2 : 1;
            case JUNGLE -> 4;
            case SAVANNA -> 5;
            case WETLAND -> 0;
            default -> geo.temperature() < -0.2 ? 2 : (r.chance(0.6) ? 0 : (r.chance(0.5) ? 1 : 2));
        };
    }

    /** Medium surface formations: scree, tors, hoodoos and spires, islets, balanced rocks. */
    private void medium(Site s) {
        Cand c = s.c;
        NatureRandom r = s.r;
        Rock rock = this.surfaceRock(s.style, s.geology, s.geo);
        int flags = this.surfaceFlags(s, r);
        if (s.water.wet()) {
            int depth = s.water.waterTop() - s.water.groundTop();
            boolean sea = s.water.kind() == WaterColumn.Kind.SEA;
            // Rivers keep their channels clear; islets and stacks stand in lakes and near the coast.
            if (depth < 1 || depth > (sea ? 8 : 5) || s.water.kind() == WaterColumn.Kind.RIVER || sea && s.shore == null) {
                return;
            }
            // Islets in lakes and the shallow sea, sea stacks off rocky coasts.
            if (sea && depth >= 2 && (s.style == SurfaceStyle.STONY_SHORE || s.style == SurfaceStyle.OCEAN) && r.chance(0.45)) {
                double height = depth + r.range(4, 12);
                double radius = r.range(1.8, 3.2);
                if (!this.clearSurface(c.x, c.z, s.ground + 1, radius + 2, true)) {
                    return;
                }
                this.set(c, new FormationSpec(this.specKey(c, 0), FormationKind.SPIRE, c.x, c.z, s.ground + 1, height, radius, 0, 0, Rock.SHORE,
                        flags | FormationSpec.FLOODED, r.nextLong()), radius + 2);
                return;
            }
            if (depth <= 5) {
                double radius = r.range(2.5, 5.0);
                if (!this.clearSurface(c.x, c.z, s.ground + 1, radius + 1, true)) {
                    return;
                }
                this.set(c, new FormationSpec(this.specKey(c, 0), FormationKind.ISLET, c.x, c.z, s.ground + 1, r.between(1, 3), radius, 0,
                        r.next() * Math.PI, rock == Rock.STONE ? Rock.SHORE : rock, flags | FormationSpec.FLOODED, r.nextLong()), radius + 1);
            }
            return;
        }
        if (s.ground < this.seaLevel - 1) {
            return;
        }
        int y = s.ground + 1;
        double tpi = this.tpi(c.x, c.z, s.ground, 12);
        boolean crest = tpi > 2.5;
        boolean steep = s.slope > 0.65;
        boolean mountain = s.style == SurfaceStyle.MOUNTAIN || s.style == SurfaceStyle.STRATA;
        double u = r.next();
        if (s.style == SurfaceStyle.ASHEN) {
            this.ashen(s, y, crest, u);
            return;
        }
        if (steep && (mountain || s.style == SurfaceStyle.BOREAL || s.style == SurfaceStyle.SNOWY || s.style == SurfaceStyle.STONY_SHORE) && u < 0.55) {
            double radius = r.range(9.0, 13.0);
            if (!this.clearSurface(c.x, c.z, y, radius, false)) {
                return;
            }
            this.set(c, new FormationSpec(this.specKey(c, 0), FormationKind.TALUS, c.x, c.z, y, 2.0, radius, 0, 0,
                    rock == Rock.STONE ? Rock.RUBBLE : rock, flags, r.nextLong()), radius + 1.5);
            return;
        }
        boolean hoodoos = s.style == SurfaceStyle.BADLANDS || s.style == SurfaceStyle.DESERT || s.style == SurfaceStyle.STRATA && r.chance(0.5);
        if (hoodoos && u < (s.style == SurfaceStyle.BADLANDS ? 0.7 : 0.4) || mountain && steep && u < 0.75) {
            double radius = r.range(1.4, 2.8);
            double height = r.range(7.0, 16.0);
            if (s.slope > 1.2 || !this.clearSurface(c.x, c.z, y, radius + 1.5, false)) {
                return;
            }
            this.set(c, new FormationSpec(this.specKey(c, 0), FormationKind.SPIRE, c.x, c.z, y, height, radius, 0, 0, rock, flags, r.nextLong()),
                    radius + 2.0);
            return;
        }
        if (hoodoos && r.chance(0.15) || s.style == SurfaceStyle.SAVANNA && r.chance(0.12)) {
            double radius = r.range(2.2, 3.8);
            if (s.slope > 0.4 || !this.clearSurface(c.x, c.z, y, radius + 1, false)) {
                return;
            }
            this.set(c, new FormationSpec(this.specKey(c, 0), FormationKind.BALANCED_ROCK, c.x, c.z, y, r.between(3, 5), radius, 0, 0, rock, flags,
                    r.nextLong()), radius + 1.0);
            return;
        }
        // Tors on crests and hilltops (kopjes on the savanna stand anywhere).
        if (crest || s.style == SurfaceStyle.SAVANNA || mountain && r.chance(0.4)) {
            double radius = r.range(2.5, 5.5);
            double height = r.range(3.0, 7.0) + (mountain ? 2.0 : 0.0);
            if (s.slope > 1.1 || !this.clearSurface(c.x, c.z, y, radius + 2, false)) {
                return;
            }
            this.set(c, new FormationSpec(this.specKey(c, 0), FormationKind.OUTCROP, c.x, c.z, y, height, radius, 0, r.next() * Math.PI,
                    rock == Rock.STONE && mountain ? Rock.GRANITE : rock, flags, r.nextLong()), radius + 2.0);
        }
    }

    /**
     * The ashen wastes: volcanic fissures with ash vents along them on level ground (the scars of
     * the old eruptions), hornitos (small spatter cones of basalt) and lava-rock outcrops on crests.
     */
    private void ashen(Site s, int y, boolean crest, double u) {
        Cand c = s.c;
        NatureRandom r = s.r;
        int flags = this.surfaceFlags(s, r);
        if (u < 0.5 && s.slope < 0.35) {
            double length = r.range(8.0, 13.0);
            double angle = r.next() * Math.PI;
            // The whole fissure on dry, level ground away from caves and falls.
            if (!this.clearSurface(c.x, c.z, y, length, false) || this.relief(c.x, c.z, length, angle) > 3.5) {
                return;
            }
            this.set(c, new FormationSpec(this.specKey(c, 0), FormationKind.FISSURE, c.x, c.z, y, r.range(2.0, 4.5), length, r.between(1, 3), angle,
                    Rock.BASALT, flags, r.nextLong()), 2.5);
            return;
        }
        if (u < 0.78) {
            double radius = r.range(1.4, 2.6);
            if (s.slope > 0.8 || !this.clearSurface(c.x, c.z, y, radius + 1.5, false)) {
                return;
            }
            this.set(c, new FormationSpec(this.specKey(c, 0), FormationKind.SPIRE, c.x, c.z, y, r.range(3.0, 7.0), radius, 0, 0, Rock.BASALT, flags,
                    r.nextLong()), radius + 2.0);
            return;
        }
        double radius = r.range(2.5, 5.0);
        if (s.slope > 1.1 || !this.clearSurface(c.x, c.z, y, radius + 2, false)) {
            return;
        }
        this.set(c, new FormationSpec(this.specKey(c, 0), FormationKind.OUTCROP, c.x, c.z, y, r.range(3.0, crest ? 8.0 : 5.0), radius, 0,
                r.next() * Math.PI, Rock.BASALT, flags, r.nextLong()), radius + 2.0);
    }

    /** The greatest height difference of the ground along a line through a column (5 points each side). */
    private double relief(int x, int z, double half, double angle) {
        double lo = Double.POSITIVE_INFINITY;
        double hi = Double.NEGATIVE_INFINITY;
        for (int k = -5; k <= 5; k++) {
            double t = half * k / 5.0;
            double h = this.land.surface(x + (int) Math.round(Math.cos(angle) * t), z + (int) Math.round(Math.sin(angle) * t));
            lo = Math.min(lo, h);
            hi = Math.max(hi, h);
        }
        return hi - lo;
    }

    /** The ground at a column minus the mean ground of 8 points at a distance: positive on crests. */
    private double tpi(int x, int z, int ground, int distance) {
        double sum = 0.0;
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            sum += groundTop(this.land.surface(x + (int) Math.round(Math.cos(a) * distance), z + (int) Math.round(Math.sin(a) * distance)));
        }
        return ground - sum / 8.0;
    }

    /** Large surface formations: arches, tall spires and sea stacks, big tors, balanced rocks. */
    private void large(Site s) {
        Cand c = s.c;
        NatureRandom r = s.r;
        Rock rock = this.surfaceRock(s.style, s.geology, s.geo);
        int flags = this.surfaceFlags(s, r) & ~FormationSpec.MOSSY;
        boolean coast = s.style == SurfaceStyle.STONY_SHORE || s.style == SurfaceStyle.BEACH || s.style == SurfaceStyle.OCEAN;
        if (s.water.wet()) {
            int depth = s.water.waterTop() - s.water.groundTop();
            if (!coast || s.water.kind() != WaterColumn.Kind.SEA || depth < 2 || depth > 10) {
                return;
            }
            double height = depth + r.range(10, 24);
            double radius = r.range(2.6, 4.5);
            if (!this.clearSurface(c.x, c.z, s.ground + 1, radius + 2, true)) {
                return;
            }
            this.set(c, new FormationSpec(this.specKey(c, 0), FormationKind.SPIRE, c.x, c.z, s.ground + 1, height, radius, 0, 0, Rock.SHORE,
                    flags | FormationSpec.FLOODED, r.nextLong()), radius + 2);
            return;
        }
        if (s.ground < this.seaLevel) {
            return;
        }
        int y = s.ground + 1;
        double u = r.next();
        boolean dry = s.style == SurfaceStyle.DESERT || s.style == SurfaceStyle.BADLANDS || s.style == SurfaceStyle.STRATA || s.style == SurfaceStyle.ASHEN;
        if ((dry || s.style == SurfaceStyle.MOUNTAIN || coast) && u < (s.style == SurfaceStyle.ASHEN ? 0.15 : 0.45)) {
            double half = r.range(6.0, 11.0);
            double rise = r.range(6.0, 12.0);
            double angle = r.next() * Math.PI;
            if (!this.archFits(c.x, c.z, s.ground, half, angle, rise, coast)) {
                return;
            }
            this.set(c, new FormationSpec(this.specKey(c, 0), FormationKind.ARCH, c.x, c.z, y, rise, half, 0, angle, rock, flags, r.nextLong()), half + 4.0);
            return;
        }
        if (dry || s.style == SurfaceStyle.MOUNTAIN || coast) {
            double height = r.range(16.0, 30.0);
            double radius = r.range(2.4, 4.4);
            if (s.slope > 0.9 || !this.clearSurface(c.x, c.z, y, radius + 2, false)) {
                return;
            }
            this.set(c, new FormationSpec(this.specKey(c, 0), FormationKind.SPIRE, c.x, c.z, y, height, radius, 0, 0, rock, flags, r.nextLong()), radius + 2.5);
            return;
        }
        if (r.chance(0.5)) {
            double radius = r.range(2.6, 4.0);
            if (s.slope > 0.4 || !this.clearSurface(c.x, c.z, y, radius + 1, false)) {
                return;
            }
            this.set(c, new FormationSpec(this.specKey(c, 0), FormationKind.BALANCED_ROCK, c.x, c.z, y, r.between(3, 6), radius, 0, 0, rock, flags,
                    r.nextLong()), radius + 1.5);
        } else {
            double radius = r.range(4.5, 7.5);
            double height = r.range(5.0, 10.0);
            if (s.slope > 0.9 || !this.clearSurface(c.x, c.z, y, radius + 2, false)) {
                return;
            }
            this.set(c, new FormationSpec(this.specKey(c, 0), FormationKind.OUTCROP, c.x, c.z, y, height, radius, 0, r.next() * Math.PI,
                    rock, this.surfaceFlags(s, r), r.nextLong()), radius + 2.0);
        }
    }

    /** Monumental wonders: great arches and spires, stone forests. */
    private void wonder(Site s) {
        Cand c = s.c;
        NatureRandom r = s.r;
        if (s.water.wet() || s.ground < this.seaLevel + 1) {
            return;
        }
        Rock rock = this.surfaceRock(s.style, s.geology, s.geo);
        int flags = this.surfaceFlags(s, r) & ~FormationSpec.MOSSY;
        int y = s.ground + 1;
        boolean forestLand = s.style == SurfaceStyle.JUNGLE || s.style == SurfaceStyle.SAVANNA || s.style == SurfaceStyle.TEMPERATE;
        if (forestLand) {
            // Stone forests need limestone.
            if (s.geology[0] < 0.4 || s.slope > 0.5) {
                return;
            }
            double radius = r.range(20.0, 26.0);
            if (!this.clearSurface(c.x, c.z, y, radius, false)) {
                return;
            }
            this.set(c, new FormationSpec(this.specKey(c, 0), FormationKind.STONE_FOREST, c.x, c.z, y, r.range(18.0, 30.0), radius, 0, 0, Rock.LIMESTONE,
                    this.surfaceFlags(s, r), r.nextLong()), radius + 3.0);
            return;
        }
        if (s.style != SurfaceStyle.ASHEN && r.chance(0.55)) {
            double half = r.range(14.0, 22.0);
            double rise = r.range(14.0, 28.0);
            double angle = r.next() * Math.PI;
            if (!this.archFits(c.x, c.z, s.ground, half, angle, rise, false)) {
                return;
            }
            this.set(c, new FormationSpec(this.specKey(c, 0), FormationKind.GREAT_ARCH, c.x, c.z, y, rise, half, 0, angle, rock, flags, r.nextLong()),
                    half + 8.0);
        } else {
            double radius = r.range(6.0, 9.0);
            double height = r.range(35.0, 70.0);
            if (s.slope > 0.8 || !this.clearSurface(c.x, c.z, y, radius + 3, false)) {
                return;
            }
            this.set(c, new FormationSpec(this.specKey(c, 0), FormationKind.GREAT_SPIRE, c.x, c.z, y, height, radius, 0, 0, rock, flags, r.nextLong()),
                    radius + 4.0);
        }
    }

    /** An arch needs dry ground under both legs, not far below the root's, and open ground under its span. */
    private boolean archFits(int x, int z, int ground, double half, double angle, double rise, boolean coast) {
        for (int side = -1; side <= 1; side += 2) {
            int lx = x + (int) Math.round(Math.cos(angle) * half * side);
            int lz = z + (int) Math.round(Math.sin(angle) * half * side);
            WaterColumn w = this.land.water(lx, lz);
            if (w.wet() && !coast) {
                return false;
            }
            int g = w.wet() ? w.groundTop() : groundTop(this.land.surface(lx, lz));
            if (Math.abs(g - ground) > rise * 0.45 || (w.wet() && w.waterTop() - w.groundTop() > 4)) {
                return false;
            }
        }
        // Nothing under the crown may stand higher than half the rise above the root.
        for (int k = -2; k <= 2; k++) {
            int mx = x + (int) Math.round(Math.cos(angle) * half * k * 0.35);
            int mz = z + (int) Math.round(Math.sin(angle) * half * k * 0.35);
            if (groundTop(this.land.surface(mx, mz)) > ground + rise * 0.5) {
                return false;
            }
        }
        return this.clearSurface(x, z, ground + 1, half + 2.0, coast);
    }

    /**
     * The ground around a surface formation: no cave opening under it (entrances, sinkholes,
     * cenotes and ravines stay open), no waterfall near it, dry ground under it unless it belongs
     * at the water.
     */
    private boolean clearSurface(int x, int z, int y, double radius, boolean water) {
        for (int k = 0; k <= 8; k++) {
            int px = x;
            int pz = z;
            if (k > 0) {
                double a = k * Math.PI / 4;
                px += (int) Math.round(Math.cos(a) * radius);
                pz += (int) Math.round(Math.sin(a) * radius);
            }
            WaterColumn w = this.land.water(px, pz);
            if (w.wet() && !water) {
                return false;
            }
            int g = w.wet() ? w.groundTop() : groundTop(this.land.surface(px, pz));
            if (this.land.isCave(px, g, pz) || this.land.isCave(px, g - 3, pz) || this.land.isCave(px, g + 1, pz) && !w.wet()) {
                return false;
            }
        }
        double keep = radius + 10.0;
        for (int rx = Math.floorDiv(x - (int) keep, 512); rx <= Math.floorDiv(x + (int) keep, 512); rx++) {
            for (int rz = Math.floorDiv(z - (int) keep, 512); rz <= Math.floorDiv(z + (int) keep, 512); rz++) {
                for (double[] f : this.falls(rx * 512, rz * 512)) {
                    if (Math.hypot(f[0] - x, f[1] - z) < keep) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private List<double[]> falls(int x, int z) {
        int rx = Math.floorDiv(x, 512);
        int rz = Math.floorDiv(z, 512);
        return this.caches.get().falls.get(NatureCache.key(15, rx, rz), k -> this.land.falls(rx * 512 + 256, rz * 512 + 256));
    }

    // ------------------------------------------------------------------ caves

    /** The most likely a cave cell holds anything (its runs then draw their own chance below this). */
    private static final double[] CAVE_MAX = {0, 0, 0, 0, 0.75, 0.75, 0.7, 0.7};

    private Cand caveCand(int layer, long i, long j) {
        Cand c = new Cand();
        c.layer = layer;
        c.i = i;
        c.j = j;
        NatureRandom r = new NatureRandom(this.cellSeed(layer, i, j));
        int size = CELL[layer];
        c.x = (int) (i * size + Math.floor(r.next() * size));
        c.z = (int) (j * size + Math.floor(r.next() * size));
        double rich = this.caveRichness(c.x, c.z);
        double scale = this.settings.caves() * (layer == C_LARGE ? this.settings.large() : 1.0) * (layer == C_GIANT ? this.settings.wonders() : 1.0);
        double gate = CAVE_MAX[layer] * Math.min(1.0, rich * scale);
        if (r.next() >= gate) {
            return c;
        }
        int[] runs = this.caches.get().runs;
        int n = this.land.runs(c.x, c.z, runs);
        if (n == 0) {
            return c;
        }
        double surface = this.land.surface(c.x, c.z);
        double[] geology = null;
        FormationSpec[] specs = new FormationSpec[n];
        double[] footprints = new double[n];
        int count = 0;
        for (int k = 0; k < n; k++) {
            int o = k * VastlandsCaves.RUN_FIELDS;
            Run run = new Run(runs[o + VastlandsCaves.RUN_FLOOR], runs[o + VastlandsCaves.RUN_CEILING], runs[o + VastlandsCaves.RUN_FLUID],
                    runs[o + VastlandsCaves.RUN_LEVEL], runs[o + VastlandsCaves.RUN_ROOM] / 10.0, runs[o + VastlandsCaves.RUN_MEGACAVE],
                    runs[o + VastlandsCaves.RUN_OPEN] == 1);
            double u = r.next();
            long shapeSeed = r.nextLong();
            if (run.open || run.height() < 3 || run.ceiling > surface - 8.0) {
                continue;
            }
            CaveProfile profile = CaveProfile.VALUES[this.biomes.caveProfile(c.x, run.floor + Math.min(2, run.height() - 1), c.z)];
            if (profile == CaveProfile.NONE) {
                continue;
            }
            if (geology == null) {
                geology = this.land.geology(c.x, c.z);
            }
            CaveSite site = new CaveSite(c, k, run, profile, geology, u, new NatureRandom(shapeSeed));
            FormationSpec spec = switch (layer) {
                case C_SMALL -> this.caveSmall(site);
                case C_MEDIUM -> this.caveMedium(site);
                case C_LARGE -> this.caveLarge(site);
                default -> this.caveGiant(site);
            };
            if (spec != null) {
                specs[count] = spec;
                footprints[count] = this.footprint(spec);
                count++;
            }
        }
        if (count > 0) {
            c.specs = java.util.Arrays.copyOf(specs, count);
            c.footprints = java.util.Arrays.copyOf(footprints, count);
            c.accepted = new byte[count];
            java.util.Arrays.fill(c.accepted, (byte) -1);
        }
        return c;
    }

    /** 0.15..1.6: regions of richly decorated caves and nearly bare ones (~260 blocks). */
    private double caveRichness(int x, int z) {
        return 0.15 + 1.45 * smoothstep(-0.5, 0.6, NoiseMath.fbm(this.richness, x / 260.0, z / 260.0, 2, 0.5));
    }

    /** 0.4..1.6: groves of lumen mushrooms against open stretches (~110 blocks; bioluminescent depths). */
    private double grove(CaveSite s) {
        return 0.4 + 1.2 * smoothstep(-0.35, 0.45, NoiseMath.fbm(this.groves, s.c.x / 110.0, s.c.z / 110.0, 2, 0.5));
    }

    /** An open run of a cave column. */
    private record Run(int floor, int ceiling, int fluid, int level, double room, int megacave, boolean open) {
        int height() {
            return this.ceiling - this.floor + 1;
        }

        /** Water depth on the floor (0 dry). */
        int depth() {
            return this.fluid == 1 ? Math.max(0, Math.min(this.level, this.ceiling) - this.floor + 1) : 0;
        }

        boolean lava() {
            return this.fluid == 2;
        }
    }

    /** A cave candidate's run: {@code u} is its own roll in [0, 1) (the cell passed its gate). */
    private record CaveSite(Cand c, int run, Run r, CaveProfile profile, double[] geology, double u, NatureRandom random) {
    }

    private Rock caveRock(CaveProfile profile, int y, double[] geology, boolean rubble) {
        if (profile == CaveProfile.DRIPSTONE) {
            return Rock.DRIPSTONE;
        }
        if (profile == CaveProfile.AMETHYST) {
            return Rock.AMETHYST;
        }
        if (profile == CaveProfile.SMOLDERING) {
            return Rock.MAGMATIC;
        }
        if (profile == CaveProfile.CRYSTAL) {
            return Rock.CALCITE;
        }
        if (profile == CaveProfile.VOLCANIC || geology[2] > 0.5) {
            return Rock.BASALT;
        }
        if (profile == CaveProfile.DEEP_DARK || y < 0) {
            return rubble ? Rock.DEEP_RUBBLE : Rock.DEEPSLATE;
        }
        if (rubble) {
            return Rock.RUBBLE;
        }
        if (geology[0] > 0.55) {
            return Rock.LIMESTONE;
        }
        return geology[1] > 0.55 ? Rock.GRANITE : Rock.STONE;
    }

    private int caveFlags(CaveSite s) {
        int flags = 0;
        if (s.profile == CaveProfile.DRIPSTONE) {
            flags |= FormationSpec.DRIPSTONE;
        }
        if (s.profile == CaveProfile.LUSH) {
            flags |= FormationSpec.LUSH;
        }
        if (s.profile == CaveProfile.DEEP_DARK) {
            flags |= FormationSpec.SCULK;
        }
        if (s.r.depth() > 0) {
            flags |= FormationSpec.FLOODED;
        }
        if (s.r.lava()) {
            flags |= FormationSpec.LAVA;
        }
        return flags;
    }

    private FormationSpec cave(CaveSite s, FormationKind kind, double height, double radius, int span, Rock rock, int flags) {
        return new FormationSpec(this.specKey(s.c, s.run), kind, s.c.x, s.c.z, s.r.floor, height, radius, span, s.random.next() * Math.PI, rock, flags,
                s.random.nextLong());
    }

    /** Small cave details: pointed dripstone, rubble, roots and moss, sculk, crystals, basalt. */
    private FormationSpec caveSmall(CaveSite s) {
        Run run = s.r;
        int h = run.height();
        NatureRandom r = s.random;
        int flags = this.caveFlags(s);
        if (run.lava()) {
            return s.geology[2] > 0.3 && s.u < 0.25 ? this.cave(s, FormationKind.BASALT_COLUMNS, 0, r.range(1.5, 2.5), h, Rock.BASALT, flags) : null;
        }
        boolean full = run.depth() >= h;
        switch (s.profile) {
            case LUMINOUS -> {
                if (!full && run.depth() == 0 && s.u < 0.34 * this.grove(s)) {
                    return this.cave(s, FormationKind.LUMEN_GROWTH, 0, r.range(1.5, 3.2), h, Rock.STONE, flags);
                }
            }
            case AMETHYST -> {
                if (!full && run.depth() == 0 && s.u < 0.4) {
                    boolean ceiling = h >= 4 && r.chance(0.3);
                    return this.cave(s, FormationKind.CRYSTAL_GARDEN, Math.min(4.0, h * 0.4), r.range(1.5, 2.8), h, Rock.AMETHYST,
                            flags | (ceiling ? FormationSpec.CEILING : 0));
                }
                if (!full && s.u < 0.5) {
                    return this.cave(s, FormationKind.CRYSTALS, r.range(1.0, 2.0), r.range(1.3, 2.2), h, Rock.CALCITE, flags);
                }
            }
            case SMOLDERING -> {
                if (s.u < 0.22) {
                    return this.cave(s, FormationKind.BASALT_COLUMNS, 0, r.range(1.5, 2.5), h, Rock.BASALT, flags);
                }
                if (s.u < 0.36) {
                    return this.cave(s, FormationKind.RUBBLE, 0, 2.0, h, Rock.MAGMATIC, flags);
                }
            }
            case DRIPSTONE -> {
                if (s.u < 0.55) {
                    return this.cave(s, FormationKind.POINTED_GROUP, 0, 2.0, h, Rock.DRIPSTONE, flags);
                }
                if (s.u < 0.75 && h >= 5 && !full) {
                    double len = r.range(2.0, Math.min(6.0, h * 0.4));
                    boolean up = r.chance(0.5);
                    return this.cave(s, up ? FormationKind.STALAGMITE : FormationKind.STALACTITE, len, Math.max(0.6, len * r.range(0.2, 0.3)), h, Rock.DRIPSTONE,
                            flags | (up ? 0 : FormationSpec.CEILING));
                }
            }
            case LUSH -> {
                if (full) {
                    return null;
                }
                if (s.u < 0.3 && h >= 4) {
                    return this.cave(s, FormationKind.ROOT_CURTAIN, 0, r.range(1.0, 2.0), h, Rock.STONE, flags);
                }
                if (s.u < 0.5 && run.depth() == 0) {
                    return this.cave(s, FormationKind.MOSS_GARDEN, 0, r.range(2.0, 3.2), h, Rock.STONE, flags);
                }
            }
            case DEEP_DARK -> {
                if (s.u < 0.2 && !full) {
                    return this.cave(s, FormationKind.SCULK_GROWTH, 0, r.range(1.5, 3.2), h, Rock.DEEPSLATE, flags);
                }
            }
            case CRYSTAL -> {
                if (s.u < 0.3 && !full) {
                    return this.cave(s, FormationKind.CRYSTALS, r.range(1.2, 2.2), r.range(1.4, 2.4), h, Rock.CALCITE, flags);
                }
            }
            case VOLCANIC -> {
                if (s.u < 0.2) {
                    return this.cave(s, FormationKind.BASALT_COLUMNS, 0, r.range(1.5, 2.5), h, Rock.BASALT, flags);
                }
            }
            default -> {
            }
        }
        if (s.profile == CaveProfile.NONE) {
            return null;
        }
        // Rubble anywhere (less in the special caves, which have their own clutter).
        double rubble = s.profile == CaveProfile.PLAIN ? 0.14 : (s.profile == CaveProfile.OTHER ? 0.08 : 0.05);
        if (s.u > 1.0 - rubble) {
            return this.cave(s, FormationKind.RUBBLE, 0, 2.0, h, this.caveRock(s.profile, run.floor, s.geology, true), flags);
        }
        return null;
    }

    /** Medium cave formations: stalagmites, stalactites, columns, pillars, pendants, moss gardens, sculk mounds, islets. */
    private FormationSpec caveMedium(CaveSite s) {
        Run run = s.r;
        int h = run.height();
        double room = run.room;
        NatureRandom r = s.random;
        int flags = this.caveFlags(s);
        if (h < 5 || room < 2.5) {
            return null;
        }
        if (run.lava()) {
            return s.geology[2] > 0.2 && s.u < 0.4 ? this.cave(s, FormationKind.BASALT_COLUMNS, 0, r.range(2.0, 3.5), h, Rock.BASALT, flags) : null;
        }
        Rock rock = this.caveRock(s.profile, run.floor, s.geology, false);
        int depth = run.depth();
        if (depth >= 2) {
            // Flooded: islets in shallow lakes with room, rocks under water.
            if (depth <= 5 && room >= 6 && h > depth + 3 && s.u < 0.2) {
                return this.cave(s, FormationKind.CAVE_ISLET, r.between(1, 2), r.range(2.5, Math.min(6.0, room * 0.6)), depth - 1, rock,
                        flags | (s.profile == CaveProfile.LUSH ? FormationSpec.MOSSY : 0));
            }
            if (s.u < 0.3) {
                // A boulder on a deep bed, a pillar breaking the surface of shallower water.
                double height = depth > 14 ? r.range(2.0, 6.0) : Math.min(h - 1, depth + r.range(-2.0, 3.0));
                return this.cave(s, FormationKind.SUBMERGED_ROCK, Math.max(1.0, height), r.range(1.2, Math.min(3.0, room * 0.6)), h, rock, flags);
            }
            if (s.profile != CaveProfile.DRIPSTONE) {
                return null;
            }
        }
        switch (s.profile) {
            case LUMINOUS -> {
                double g = this.grove(s);
                if (depth == 0 && h >= 8 && room >= 3 && s.u < 0.3 * g) {
                    double height = r.skewed(4.0, Math.min(12.0, h - 4.0), 1.3);
                    double radius = Math.min(Math.min(6.5, room * 0.8), Math.max(1.8, height * r.range(0.32, 0.8)));
                    return this.cave(s, FormationKind.LUMEN_MUSHROOM, height, radius, h, Rock.STONE, flags);
                }
                if (depth == 0 && h >= 4 && s.u < 0.42 * g) {
                    return this.cave(s, FormationKind.LUMEN_COLONY, Math.min(6.0, h - 2.0), r.range(2.5, 4.5), h, Rock.STONE, flags);
                }
                if (depth == 0 && room >= 5 && s.u < 0.52) {
                    return this.cave(s, FormationKind.POOL, r.between(1, 2), r.range(2.5, Math.min(5.5, room * 0.7)), h, Rock.STONE, flags);
                }
            }
            case AMETHYST -> {
                if (s.u < 0.24) {
                    return this.cave(s, FormationKind.CRYSTALS, r.range(1.5, 3.0), r.range(2.0, Math.min(3.5, room * 0.7)), h, Rock.CALCITE, flags);
                }
                if (s.u < 0.38 && h <= 18 && room >= 3) {
                    double radius = Math.min(room * 0.5, Math.max(1.0, h * r.range(0.08, 0.14) + 0.7));
                    return this.cave(s, FormationKind.COLUMN, h, radius, h, Rock.AMETHYST, flags);
                }
                if (depth == 0 && room >= 5 && h >= 4 && s.u < 0.46) {
                    return this.cave(s, FormationKind.POOL, 1, r.range(2.5, Math.min(5.0, room * 0.7)), h, Rock.AMETHYST, flags | FormationSpec.SPINEL);
                }
                if (depth == 0 && s.u < 0.62) {
                    boolean ceiling = r.chance(0.35);
                    return this.cave(s, FormationKind.CRYSTAL_GARDEN, Math.min(5.0, h * 0.4), r.range(2.5, 4.0), h, Rock.AMETHYST,
                            flags | (ceiling ? FormationSpec.CEILING : 0));
                }
            }
            case SMOLDERING -> {
                if (depth == 0 && room >= 4 && s.u < 0.14) {
                    return this.cave(s, FormationKind.MAGMA_VENT, r.range(2.0, Math.min(5.0, h * 0.4)), r.range(2.0, 3.5), h, Rock.MAGMATIC, flags);
                }
                if (s.u < 0.34 && h <= 18 && room >= 3) {
                    return this.cave(s, FormationKind.ROCK_PILLAR, h, Math.min(room * 0.45, r.range(1.2, 2.6)), h, Rock.MAGMATIC, flags);
                }
                if (s.u < 0.5) {
                    double radius = r.range(1.4, Math.min(3.2, room * 0.7));
                    return this.cave(s, FormationKind.CAVE_BOULDER, radius * r.range(0.6, 0.9), radius, h, Rock.MAGMATIC, flags);
                }
            }
            case DRIPSTONE -> {
                if (s.u < 0.6) {
                    if (h <= 18 && r.chance(0.35)) {
                        double radius = Math.min(room * 0.55, Math.max(1.0, h * r.range(0.08, 0.15) + 0.6));
                        return this.cave(s, FormationKind.COLUMN, h, radius, h, rock, flags);
                    }
                    double len = r.skewed(3.0, Math.min(16.0, h * 0.5), 1.4);
                    double radius = Math.min(room * 0.6, Math.max(0.9, len * r.range(0.16, 0.28)));
                    boolean up = r.chance(0.5);
                    return this.cave(s, up ? FormationKind.STALAGMITE : FormationKind.STALACTITE, len, radius, h, rock, flags | (up ? 0 : FormationSpec.CEILING));
                }
            }
            case LUSH -> {
                if (s.u < 0.3 && depth == 0) {
                    return this.cave(s, FormationKind.MOSS_GARDEN, 0, r.range(3.0, Math.min(6.0, room)), h, rock, flags);
                }
                if (s.u < 0.55) {
                    return this.cave(s, FormationKind.ROOT_CURTAIN, 0, r.range(1.8, 3.0), h, rock, flags);
                }
                if (s.u < 0.65 && h <= 16 && room >= 4) {
                    return this.cave(s, FormationKind.MOSSY_PILLAR, h, Math.min(room * 0.5, r.range(1.2, 2.4)), h, rock, flags);
                }
            }
            case DEEP_DARK -> {
                if (s.u < 0.18) {
                    return this.cave(s, FormationKind.SCULK_MOUND, r.range(1.2, 2.6), r.range(1.8, Math.min(4.0, room * 0.7)), h, Rock.DEEPSLATE, flags);
                }
                if (s.u < 0.28 && h <= 16 && room >= 4) {
                    return this.cave(s, FormationKind.SCULK_PILLAR, h, Math.min(room * 0.45, r.range(1.2, 2.4)), h, Rock.DEEPSLATE, flags);
                }
            }
            case CRYSTAL -> {
                if (s.u < 0.4) {
                    return this.cave(s, FormationKind.CRYSTALS, r.range(1.5, 3.0), r.range(2.0, Math.min(3.5, room * 0.7)), h, Rock.CALCITE, flags);
                }
            }
            case VOLCANIC -> {
                if (s.u < 0.4) {
                    return this.cave(s, FormationKind.BASALT_COLUMNS, 0, r.range(2.0, 3.5), h, Rock.BASALT, flags);
                }
            }
            case PLAIN -> {
                if (s.u < 0.13) {
                    double radius = r.range(1.2, Math.min(3.0, room * 0.7));
                    return this.cave(s, FormationKind.CAVE_BOULDER, radius * r.range(0.6, 0.85), radius, h, rock, flags);
                }
                if (s.u < 0.21 && h >= 7) {
                    return this.cave(s, FormationKind.PENDANT, r.range(2.0, Math.min(6.0, h / 3.0)), r.range(1.4, Math.min(3.5, room * 0.6)), h, rock, flags);
                }
                if (s.u < 0.27 && h <= 14 && room >= 4) {
                    return this.cave(s, FormationKind.ROCK_PILLAR, h, Math.min(room * 0.4, r.range(1.0, 2.4)), h, rock, flags);
                }
                if (s.u < 0.31) {
                    double len = r.skewed(2.0, Math.min(8.0, h * 0.4), 1.5);
                    boolean up = r.chance(0.5);
                    return this.cave(s, up ? FormationKind.STALAGMITE : FormationKind.STALACTITE, len, Math.max(0.8, len * 0.3), h, rock,
                            flags | (up ? 0 : FormationSpec.CEILING));
                }
            }
            default -> {
            }
        }
        return null;
    }

    /** Large cave formations: giant dripstone, spike fields, big mossy or sculk pillars, collapse piles, islets. */
    private FormationSpec caveLarge(CaveSite s) {
        Run run = s.r;
        int h = run.height();
        double room = run.room;
        NatureRandom r = s.random;
        int flags = this.caveFlags(s);
        if (h < 14 || room < 6 || run.lava()) {
            return null;
        }
        Rock rock = this.caveRock(s.profile, run.floor, s.geology, false);
        int depth = run.depth();
        if (depth >= 2) {
            if (depth <= 6 && room >= 9 && s.u < 0.35) {
                return this.cave(s, FormationKind.CAVE_ISLET, r.between(1, 3), r.range(4.0, Math.min(8.0, room * 0.6)), depth - 1, rock,
                        flags | (s.profile == CaveProfile.LUSH ? FormationSpec.MOSSY : 0));
            }
            if (s.profile != CaveProfile.DRIPSTONE) {
                return null;
            }
        }
        switch (s.profile) {
            case LUMINOUS -> {
                if (depth == 0 && s.u < 0.35 * this.grove(s)) {
                    double height = r.range(10.0, Math.min(22.0, h - 6.0));
                    double radius = Math.min(room * 0.7, r.range(5.0, 10.0));
                    return this.cave(s, FormationKind.LUMEN_GIANT, height, radius, h, Rock.STONE, flags);
                }
                if (depth == 0 && room >= 8 && s.u < 0.45) {
                    return this.cave(s, FormationKind.POOL, 2, r.range(4.0, Math.min(7.0, room * 0.6)), h, Rock.STONE, flags);
                }
            }
            case AMETHYST -> {
                if (s.u < 0.3) {
                    boolean ceiling = r.chance(0.35);
                    return this.cave(s, FormationKind.CRYSTAL_SPIRES, Math.min(h * 0.5, r.range(6.0, 14.0)), Math.min(room * 0.7, r.range(5.0, 9.0)), h,
                            Rock.AMETHYST, flags | (ceiling ? FormationSpec.CEILING : 0));
                }
                if (depth == 0 && room >= 8 && s.u < 0.4) {
                    return this.cave(s, FormationKind.POOL, 2, r.range(4.0, Math.min(7.0, room * 0.6)), h, Rock.AMETHYST, flags | FormationSpec.SPINEL);
                }
                if (s.u < 0.5 && h <= 40) {
                    return this.cave(s, FormationKind.COLUMN, h, Math.min(room * 0.4, r.range(2.0, 4.0)), h, Rock.AMETHYST, flags);
                }
            }
            case SMOLDERING -> {
                if (s.u < 0.25 && h <= 36) {
                    return this.cave(s, FormationKind.ROCK_PILLAR, h, Math.min(room * 0.4, r.range(2.2, 4.0)), h, Rock.MAGMATIC, flags);
                }
                if (depth == 0 && room >= 6 && s.u < 0.33) {
                    return this.cave(s, FormationKind.MAGMA_VENT, r.range(3.0, 6.0), r.range(3.0, 5.0), h, Rock.MAGMATIC, flags);
                }
                if (s.u < 0.42) {
                    double radius = Math.min(room * 0.8, r.range(4.0, 8.0));
                    return this.cave(s, FormationKind.COLLAPSE, Math.min(h / 3.0, r.range(2.0, 5.0)), radius, h, Rock.MAGMATIC, flags);
                }
            }
            case DRIPSTONE -> {
                if (s.u < 0.12 && h >= 20 && room >= 8) {
                    double radius = Math.min(room * 0.5, Math.max(3.0, h * r.range(0.1, 0.16)));
                    return this.cave(s, FormationKind.GIANT_DRIPSTONE, r.range(20.0, 40.0), Math.min(8.0, radius), h, rock, flags);
                }
                if (s.u < 0.24) {
                    return this.cave(s, FormationKind.SPIKE_FIELD, Math.min(h * 0.5, 30.0), Math.min(room, r.range(8.0, 12.0)), h, rock, flags);
                }
            }
            case LUSH -> {
                if (s.u < 0.15 && h <= 40) {
                    return this.cave(s, FormationKind.MOSSY_PILLAR, h, Math.min(room * 0.4, r.range(2.0, 4.5)), h, rock, flags);
                }
                if (s.u < 0.4 && depth == 0) {
                    return this.cave(s, FormationKind.MOSS_GARDEN, 0, r.range(5.0, Math.min(8.0, room)), h, rock, flags);
                }
            }
            case DEEP_DARK -> {
                if (s.u < 0.12 && h <= 40) {
                    return this.cave(s, FormationKind.SCULK_PILLAR, h, Math.min(room * 0.4, r.range(2.0, 4.0)), h, Rock.DEEPSLATE, flags);
                }
                if (s.u < 0.25) {
                    return this.cave(s, FormationKind.SCULK_MOUND, r.range(2.5, 4.5), r.range(3.5, Math.min(6.0, room * 0.6)), h, Rock.DEEPSLATE, flags);
                }
            }
            case PLAIN -> {
                if (s.u < 0.12) {
                    double radius = Math.min(room * 0.8, r.range(4.0, 10.0));
                    return this.cave(s, FormationKind.COLLAPSE, Math.min(h / 3.0, r.range(2.0, 7.0)), radius,
                            h, this.caveRock(s.profile, run.floor, s.geology, true), flags);
                }
                if (s.u < 0.18 && h <= 30) {
                    return this.cave(s, FormationKind.ROCK_PILLAR, h, Math.min(room * 0.4, r.range(2.0, 4.0)), h, rock, flags);
                }
                if (s.u < 0.22) {
                    return this.cave(s, FormationKind.SPIKE_FIELD, Math.min(h * 0.4, 16.0), Math.min(room, r.range(7.0, 11.0)), h, rock, flags);
                }
            }
            default -> {
            }
        }
        return null;
    }

    /** Monumental formations in megacaves and the tallest caves: giant columns, giant dripstone, spike fields. */
    private FormationSpec caveGiant(CaveSite s) {
        Run run = s.r;
        int h = run.height();
        double room = run.room;
        NatureRandom r = s.random;
        int flags = this.caveFlags(s);
        if (h < 30 || room < 12 || run.lava() || run.depth() > 0 && run.depth() >= h / 2) {
            return null;
        }
        if (run.megacave == 0 && room < 20) {
            return null;
        }
        Rock rock = this.caveRock(s.profile, run.floor, s.geology, false);
        double radius = Math.min(room * 0.45, r.range(5.0, 11.0));
        switch (s.profile) {
            case LUMINOUS -> {
                if (run.depth() == 0 && s.u < 0.45) {
                    double height = r.range(18.0, Math.min(34.0, h - 8.0));
                    return this.cave(s, FormationKind.LUMEN_GIANT, height, Math.min(room * 0.6, r.range(8.0, 12.5)), h, Rock.STONE, flags);
                }
            }
            case AMETHYST -> {
                if (s.u < 0.3) {
                    return this.cave(s, FormationKind.GIANT_CRYSTAL, Math.min(h * 0.7, r.range(20.0, 48.0)), r.range(3.5, 6.5), h, Rock.AMETHYST, flags);
                }
                if (s.u < 0.45 && h <= 140) {
                    return this.cave(s, FormationKind.GIANT_COLUMN, h, Math.min(10.0, radius), h, Rock.AMETHYST, flags);
                }
            }
            case SMOLDERING -> {
                if (run.depth() == 0 && s.u < 0.3) {
                    return this.cave(s, FormationKind.GEOTHERMAL_FIELD, r.range(4.0, 8.0), Math.min(room * 0.6, r.range(10.0, 14.0)), h, Rock.MAGMATIC, flags);
                }
                if (s.u < 0.5 && h <= 140) {
                    return this.cave(s, FormationKind.GIANT_COLUMN, h, Math.min(10.0, radius), h, Rock.MAGMATIC, flags);
                }
            }
            case DRIPSTONE -> {
                if (s.u < 0.3) {
                    return this.cave(s, FormationKind.GIANT_DRIPSTONE, r.range(40.0, 150.0), Math.min(12.0, radius), h, rock, flags);
                }
                if (s.u < 0.42) {
                    return this.cave(s, FormationKind.SPIKE_FIELD, Math.min(h * 0.6, 60.0), Math.min(room, 13.0), h, rock, flags);
                }
            }
            case PLAIN, DEEP_DARK, LUSH, CRYSTAL, VOLCANIC -> {
                if (s.u < 0.25 && h <= 140) {
                    Rock giantRock = s.profile == CaveProfile.CRYSTAL ? Rock.CALCITE : (s.profile == CaveProfile.VOLCANIC ? Rock.BASALT : rock);
                    return this.cave(s, FormationKind.GIANT_COLUMN, h, Math.min(10.0, radius), h, giantRock, flags);
                }
            }
            default -> {
            }
        }
        return null;
    }

    /** How far a formation reaches from its root on the ground (for yields and the forests). */
    private double footprint(FormationSpec spec) {
        return switch (spec.kind()) {
            case RUBBLE, POINTED_GROUP -> 3.5;
            case SPIKE_FIELD, COLLAPSE, MOSS_GARDEN -> spec.radius() + 2.0;
            case GIANT_DRIPSTONE -> spec.radius() * 1.8 + 4.0;
            case LUMEN_MUSHROOM, LUMEN_GIANT -> spec.radius() + 2.5;
            case CRYSTAL_SPIRES, GEOTHERMAL_FIELD, LUMEN_COLONY, POOL -> spec.radius() + 2.0;
            case GIANT_CRYSTAL -> spec.radius() + 8.0;
            default -> spec.radius() + 1.5;
        };
    }

    // ------------------------------------------------------------------ acceptance and queries

    /** True if a candidate's formation stands: it yields to a larger formation of its layer whose footprint covers its root. */
    private boolean accepted(Cand c, int k) {
        if (c.accepted[k] >= 0) {
            return c.accepted[k] == 1;
        }
        boolean stands = true;
        FormationSpec spec = c.specs[k];
        int top = c.layer < C_SMALL ? S_WONDER : C_GIANT;
        for (int layer = c.layer + 1; layer <= top && stands; layer++) {
            int size = CELL[layer];
            double reach = REACH[layer] + c.footprints[k];
            for (long i = Math.floorDiv(spec.x() - (int) reach, size); i <= Math.floorDiv(spec.x() + (int) reach, size) && stands; i++) {
                for (long j = Math.floorDiv(spec.z() - (int) reach, size); j <= Math.floorDiv(spec.z() + (int) reach, size) && stands; j++) {
                    Cand n = this.cand(layer, i, j);
                    for (int m = 0; m < n.specs.length; m++) {
                        FormationSpec big = n.specs[m];
                        double keep = n.footprints[m] + c.footprints[k] * 0.5;
                        if (Math.abs(big.x() - spec.x()) > keep || Math.abs(big.z() - spec.z()) > keep
                                || Math.hypot(big.x() - spec.x(), big.z() - spec.z()) > keep) {
                            continue;
                        }
                        // Caves: only where their heights meet.
                        if (c.layer >= C_SMALL && (big.y() > spec.y() + spec.span() || spec.y() > big.y() + big.span())) {
                            continue;
                        }
                        stands = false;
                        break;
                    }
                }
            }
        }
        c.accepted[k] = (byte) (stands ? 1 : 0);
        return stands;
    }

    /**
     * Every formation of a layer family (surface or caves) whose shape can reach the box
     * (inclusive), in a fixed order (layer, then cells by x, then z, then runs bottom-up).
     */
    public void formations(int minX, int minZ, int maxX, int maxZ, boolean surface, Consumer<FormationSpec> out) {
        int from = surface ? S_SMALL : C_SMALL;
        int to = surface ? S_WONDER : C_GIANT;
        for (int layer = from; layer <= to; layer++) {
            int size = CELL[layer];
            int reach = REACH[layer];
            long i0 = Math.floorDiv(minX - reach, size);
            long i1 = Math.floorDiv(maxX + reach, size);
            long j0 = Math.floorDiv(minZ - reach, size);
            long j1 = Math.floorDiv(maxZ + reach, size);
            for (long i = i0; i <= i1; i++) {
                for (long j = j0; j <= j1; j++) {
                    Cand c = this.cand(layer, i, j);
                    for (int k = 0; k < c.specs.length; k++) {
                        FormationSpec spec = c.specs[k];
                        int r = spec.kind().reach;
                        if (spec.x() < minX - r || spec.x() > maxX + r || spec.z() < minZ - r || spec.z() > maxZ + r) {
                            continue;
                        }
                        if (this.accepted(c, k)) {
                            out.accept(spec);
                        }
                    }
                }
            }
        }
    }

    /**
     * False where a surface formation keeps a tree away: its trunk within {@code trunk} blocks of the
     * formation's footprint, or for a tall formation its crown of radius {@code crown} over it.
     */
    public boolean clearOfFormations(int x, int z, double trunk, double crown) {
        for (int layer = S_SMALL; layer <= S_WONDER; layer++) {
            int size = CELL[layer];
            int reach = (int) Math.ceil(Math.min(MAX_FOOTPRINT, REACH[layer] + 2.0) + Math.max(trunk, crown));
            for (long i = Math.floorDiv(x - reach, size); i <= Math.floorDiv(x + reach, size); i++) {
                for (long j = Math.floorDiv(z - reach, size); j <= Math.floorDiv(z + reach, size); j++) {
                    Cand c = this.cand(layer, i, j);
                    for (int k = 0; k < c.specs.length; k++) {
                        FormationSpec spec = c.specs[k];
                        double keep = c.footprints[k] + (spec.kind().tall ? Math.max(trunk, crown) : trunk);
                        double dx = spec.x() - x;
                        double dz = spec.z() - z;
                        if (Math.abs(dx) <= keep && Math.abs(dz) <= keep && dx * dx + dz * dz <= keep * keep && this.accepted(c, k)) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    /** The shape of a formation (cached per thread by its key and kind). */
    public FormationShape shape(FormationSpec spec, FormationShapes.World world) {
        return this.caches.get().shapes.get(spec.key() ^ SimplexNoise.mix(spec.kind().ordinal() + 0x53686170L), k -> FormationShapes.shape(spec, world));
    }

    /** The world the shapes read: the ground (the bed under water), the water, the caves. */
    public FormationShapes.World world() {
        return new FormationShapes.World() {
            @Override
            public int groundTop(int x, int z) {
                return NatureModel.this.groundTop(x, z);
            }

            @Override
            public int waterTop(int x, int z) {
                return NatureModel.this.waterTop(x, z);
            }

            @Override
            public boolean isCave(int x, int y, int z) {
                return NatureModel.this.land.isCave(x, y, z);
            }
        };
    }

    /** Diagnostics: the candidate of a cell and its formations. */
    public String explain(int layer, long i, long j) {
        Cand c = this.cand(layer, i, j);
        StringBuilder out = new StringBuilder(String.format(java.util.Locale.ROOT, "layer %d cell %d %d at %d %d:", layer, i, j, c.x, c.z));
        for (int k = 0; k < c.specs.length; k++) {
            out.append(' ').append(c.specs[k].kind()).append(" y ").append(c.specs[k].y()).append(" accepted ").append(this.accepted(c, k));
        }
        return out.toString();
    }
}
