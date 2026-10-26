package com.sincerelyuq.vastlands.worldgen.forest;

import com.sincerelyuq.vastlands.worldgen.caves.VastlandsCaves;
import com.sincerelyuq.vastlands.worldgen.hydrology.HydrologyModel;
import com.sincerelyuq.vastlands.worldgen.hydrology.VastlandsHydrology;
import com.sincerelyuq.vastlands.worldgen.hydrology.WaterColumn;
import com.sincerelyuq.vastlands.worldgen.system.CaveSystem;
import com.sincerelyuq.vastlands.worldgen.system.ForestSystem;
import com.sincerelyuq.vastlands.worldgen.system.GenerationSetup;
import com.sincerelyuq.vastlands.worldgen.system.HydrologySystem;
import com.sincerelyuq.vastlands.worldgen.system.NatureSystem;
import com.sincerelyuq.vastlands.worldgen.system.TerrainSystem;
import com.sincerelyuq.vastlands.worldgen.terrain.TerrainColumn;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * The Vastlands forest system: a {@link ForestModel} on the world's terrain, rivers and caves.
 *
 * <p>The model needs the world's surface biomes, which only exist once the level's chunk
 * generator and climate sampler do: the forest feature {@link #bind binds} them on first use
 * (tests and previews bind their own). Until then the system reports no trees. Thread-safe.
 */
public final class VastlandsForests implements ForestSystem {
    private final long seed;
    private final int seaLevel;
    private final ForestSettings settings;
    private final ForestModel.Land land;
    @Nullable
    private volatile ForestModel model;
    @Nullable
    private volatile Object boundTo;

    public VastlandsForests(GenerationSetup setup, TerrainSystem terrain, HydrologySystem hydrology, CaveSystem caves, NatureSystem nature) {
        this(setup, terrain, hydrology, caves, nature, ForestSettings.DEFAULT);
    }

    public VastlandsForests(GenerationSetup setup, TerrainSystem terrain, HydrologySystem hydrology, CaveSystem caves, NatureSystem nature,
                           ForestSettings settings) {
        this.seed = setup.random("forests").fromHashOf("forest_model").nextLong();
        this.seaLevel = setup.seaLevel();
        this.settings = settings;
        VastlandsCaves cave = caves instanceof VastlandsCaves vc ? vc : null;
        VastlandsHydrology rivers = hydrology instanceof VastlandsHydrology vh ? vh : null;
        this.land = new ForestModel.Land() {
            @Override
            public double surface(int x, int z) {
                // The caves only raise the ground under their roofs over gorges (natural bridges,
                // tunnel portals), above river water where no tree stands; building a cave column
                // for every query would cost more than the rest of the forest model.
                return hydrology.surfaceHeight(x, z);
            }

            @Override
            public TerrainColumn sample(int x, int z) {
                return terrain.sample(x, z);
            }

            @Override
            public WaterColumn water(int x, int z) {
                return hydrology.water(x, z);
            }

            @Override
            public void channels(int x, int z, double reach, HydrologyModel.ChannelVisitor visitor) {
                if (rivers != null) {
                    rivers.model().channels(x, z, reach, visitor);
                }
            }

            @Override
            public List<double[]> falls(int x, int z) {
                return rivers != null ? rivers.model().falls(x, z) : List.of();
            }

            @Override
            public boolean isCave(int x, int y, int z) {
                return cave != null && cave.isCave(x, y, z);
            }

            @Override
            public boolean clearOfFormations(int x, int z, double trunk, double crown) {
                return nature.clearOfFormations(x, z, trunk, crown);
            }
        };
    }

    /**
     * Binds the world's surface biomes (once; later calls with the same owner are free). The
     * owner identifies the world the biomes belong to (its biome source).
     */
    public void bind(Object owner, ForestModel.Biomes biomes) {
        if (this.boundTo == owner) {
            return;
        }
        synchronized (this) {
            if (this.model == null) {
                this.model = new ForestModel(this.seed, this.land, biomes, this.settings, this.seaLevel);
                this.boundTo = owner;
            }
        }
    }

    /** What the biomes were bound from (the world's biome source), or null. */
    @Nullable
    public Object boundTo() {
        return this.boundTo;
    }

    /** The world-specific seed of the forests (understory patterns). */
    public long seed() {
        return this.seed;
    }

    /** The model, or null before the biomes are bound. */
    @Nullable
    public ForestModel model() {
        return this.model;
    }

    @Override
    public String id() {
        return "vastlands";
    }

    @Override
    public double treeDensity(int blockX, int blockZ) {
        ForestModel model = this.model;
        return model == null ? 0.0 : model.density(blockX, blockZ);
    }

    @Override
    @Nullable
    public ForestSample forestAt(int blockX, int blockZ) {
        ForestModel model = this.model;
        return model == null ? null : model.sample(blockX, blockZ);
    }

    @Override
    public void trees(int minX, int minZ, int maxX, int maxZ, Consumer<TreeSpec> out) {
        ForestModel model = this.model;
        if (model != null) {
            model.trees(minX, minZ, maxX, maxZ, out);
        }
    }

    /**
     * True where Vastlands grows the trees instead of vanilla's tree features, given the forest
     * category of the biome there ({@code ForestBiomes.category}): biomes of a forest community,
     * and river biomes inside forests (riverbank woods).
     */
    public boolean replacesVanillaTrees(int category, int x, int z) {
        ForestModel model = this.model;
        if (model == null) {
            return false;
        }
        return category >= 0 || (category == ForestModel.TRANSPARENT && model.share(x, z) > 0.2);
    }

    /** The top ground block of a column as generated (rivers' channels included; not the caves' roofs over gorges). */
    public int groundTop(int x, int z) {
        return (int) Math.ceil(this.land.surface(x, z)) - 1;
    }

    /** Diagnostics for the F3 screen. */
    public String describe(int x, int z) {
        ForestModel model = this.model;
        if (model == null) {
            return "not bound yet";
        }
        ForestSample s = model.sample(x, z);
        return String.format(Locale.ROOT, "%s (%.0f%%) density %.2f age %.2f clearing %.2f riparian %.2f treeline %.2f exposure %.2f slope %.2f",
                s.type() == null ? "none" : s.type().tagPath(), 100.0 * s.share(), s.density(), s.age(), s.clearing(), s.riparian(), s.treeline(),
                s.exposure(), s.slope());
    }
}
