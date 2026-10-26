package com.sincerelyuq.vastlands.worldgen.compat;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sincerelyuq.vastlands.Vastlands;
import com.sincerelyuq.vastlands.registry.VastlandsRegistries;
import com.sincerelyuq.vastlands.worldgen.VastlandsChunkGenerator;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.common.world.ModifiableStructureInfo;
import net.neoforged.neoforge.common.world.StructureModifier;
import net.neoforged.neoforge.common.world.StructureSettingsBuilder;
import net.neoforged.neoforge.registries.holdersets.AnyHolderSet;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * {@code vastlands:add_biomes}: lets structures also start in more biomes, in Vastlands worlds
 * only. Sincerelyuq's Overhaul lists vanilla biomes for its structures; the files
 * {@code data/vastlands/neoforge/structure_modifier/overhaul_*.json} add Sincerelyuq's Biomes'
 * biomes to a chosen few that suit them (camps and ruins in the ashen wastes, buried chests in the
 * bone dunes, a fairy ring in the twilight thicket, crystal finds in the amethyst caves ... see
 * ARCHITECTURE.md, Integration). Both lists are tags with optional entries, so with either mod
 * missing the modifier changes nothing. Other worlds (vanilla presets with the same mods) keep
 * the structures' own biome lists.
 *
 * <pre>
 * {"type": "vastlands:add_biomes", "structures": "#vastlands:overhaul/ashen_wastes", "biomes": "#vastlands:overhaul/ashen_wastes"}
 * </pre>
 */
public record AddBiomesModifier(HolderSet<Structure> structures, HolderSet<Biome> biomes) implements StructureModifier {
    public static final MapCodec<AddBiomesModifier> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            net.minecraft.core.RegistryCodecs.homogeneousList(Registries.STRUCTURE).fieldOf("structures").forGetter(AddBiomesModifier::structures),
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(AddBiomesModifier::biomes)
    ).apply(instance, AddBiomesModifier::new));

    @Override
    public void modify(Holder<Structure> structure, Phase phase, ModifiableStructureInfo.StructureInfo.Builder builder) {
        if (phase != Phase.ADD || this.biomes.size() == 0 || !this.structures.contains(structure) || !vastlandsWorld()) {
            return;
        }
        StructureSettingsBuilder settings = builder.getStructureSettings();
        HolderSet<Biome> old = settings.getBiomes();
        if (old instanceof AnyHolderSet<Biome>) {
            return;
        }
        Set<Holder<Biome>> union = new LinkedHashSet<>();
        old.forEach(union::add);
        int before = union.size();
        this.biomes.forEach(union::add);
        if (union.size() > before) {
            List<Holder<Biome>> list = new ArrayList<>(union);
            settings.setBiomes(HolderSet.direct(list));
            Vastlands.LOGGER.debug("Vastlands: {} may also start in {}", structure.unwrapKey().map(k -> k.location().toString()).orElse("?"),
                    this.biomes.stream().map(h -> h.unwrapKey().map(k -> k.location().toString()).orElse("?")).toList());
        }
    }

    /** True if the server's overworld is a Vastlands world (structure modifiers run at server start, after the world is known). */
    static boolean vastlandsWorld() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return false;
        }
        return server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM).get(LevelStem.OVERWORLD)
                .map(stem -> stem.value().generator() instanceof VastlandsChunkGenerator).orElse(false);
    }

    @Override
    public MapCodec<? extends StructureModifier> codec() {
        return VastlandsRegistries.ADD_BIOMES.get();
    }
}
