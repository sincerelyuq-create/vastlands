package com.sincerelyuq.vastlands.worldgen.nature;

import com.sincerelyuq.vastlands.worldgen.caves.VastlandsCaves;
import com.sincerelyuq.vastlands.worldgen.hydrology.VastlandsHydrology;
import com.sincerelyuq.vastlands.worldgen.hydrology.WaterColumn;
import com.sincerelyuq.vastlands.worldgen.system.CaveSystem;
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
 * The Vastlands natural formations: a {@link NatureModel} on the world's terrain, rivers and caves.
 * Like the forests, the model needs the world's biomes, which the features {@link #bind} on first
 * use (tests and previews bind their own); until then the system reports no formations.
 * Thread-safe.
 */
public final class VastlandsNature implements NatureSystem {
    private final long seed;
    private final int seaLevel;
    private final int worldTop;
    private final NatureSettings settings;
    private final NatureModel.Land land;
    @Nullable
    private volatile NatureModel model;
    @Nullable
    private volatile Object boundTo;

    public VastlandsNature(GenerationSetup setup, TerrainSystem terrain, HydrologySystem hydrology, CaveSystem caves) {
        this(setup, terrain, hydrology, caves, NatureSettings.DEFAULT);
    }

    public VastlandsNature(GenerationSetup setup, TerrainSystem terrain, HydrologySystem hydrology, CaveSystem caves, NatureSettings settings) {
        this.seed = setup.random("nature").fromHashOf("nature_model").nextLong();
        this.seaLevel = setup.seaLevel();
        this.worldTop = setup.minY() + setup.height() - 1;
        this.settings = settings;
        VastlandsCaves cave = caves instanceof VastlandsCaves vc ? vc : null;
        VastlandsHydrology rivers = hydrology instanceof VastlandsHydrology vh ? vh : null;
        this.land = new NatureModel.Land() {
            @Override
            public double surface(int x, int z) {
                return hydrology.surfaceHeight(x, z);
            }

            @Override
            public WaterColumn water(int x, int z) {
                return hydrology.water(x, z);
            }

            @Override
            public TerrainColumn sample(int x, int z) {
                return terrain.sample(x, z);
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
            public int runs(int x, int z, int[] out) {
                return cave != null ? cave.runs(x, z, out) : 0;
            }

            @Override
            public double[] geology(int x, int z) {
                return cave != null ? cave.geology(x, z) : new double[3];
            }
        };
    }

    /** Binds the world's biomes (once; later calls with the same owner are free). */
    public void bind(Object owner, NatureModel.Biomes biomes) {
        if (this.boundTo == owner) {
            return;
        }
        synchronized (this) {
            if (this.model == null) {
                this.model = new NatureModel(this.seed, this.land, biomes, this.settings, this.seaLevel, this.worldTop);
                this.boundTo = owner;
            }
        }
    }

    @Nullable
    public Object boundTo() {
        return this.boundTo;
    }

    /** The model, or null before the biomes are bound. */
    @Nullable
    public NatureModel model() {
        return this.model;
    }

    @Override
    public String id() {
        return "vastlands";
    }

    @Override
    public void formations(int minX, int minZ, int maxX, int maxZ, boolean surface, Consumer<FormationSpec> out) {
        NatureModel model = this.model;
        if (model != null) {
            model.formations(minX, minZ, maxX, maxZ, surface, out);
        }
    }

    @Override
    public boolean clearOfFormations(int x, int z, double trunk, double crown) {
        NatureModel model = this.model;
        return model == null || model.clearOfFormations(x, z, trunk, crown);
    }

    /** Diagnostics for the F3 screen: the formations whose roots lie within 8 blocks. */
    public String describe(int x, int y, int z) {
        NatureModel model = this.model;
        if (model == null) {
            return "not bound yet";
        }
        StringBuilder out = new StringBuilder();
        for (boolean surface : new boolean[] {true, false}) {
            model.formations(x - 8, z - 8, x + 8, z + 8, surface, spec -> {
                if (Math.abs(spec.x() - x) <= 8 && Math.abs(spec.z() - z) <= 8 && out.length() < 160) {
                    out.append(out.length() == 0 ? "" : ", ").append(spec.kind().name().toLowerCase(Locale.ROOT)).append(" at ")
                            .append(spec.x()).append(' ').append(spec.y()).append(' ').append(spec.z());
                }
            });
        }
        return out.length() == 0 ? "none near" : out.toString();
    }
}
