package com.sincerelyuq.vastlands.worldgen.compat;

import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * The blocks of other mods, looked up by id in the block registry when first needed (after the
 * registries are frozen), so Vastlands neither depends on those mods nor references a class of
 * theirs: an absent mod's blocks are simply null, and nothing that uses them is generated (their
 * biomes are absent too). Properties are set by name for the same reason. Thread-safe.
 */
public final class CompatBlocks {
    private static final Block[] BLOCKS = new Block[ModBlock.VALUES.length];
    private static final boolean[] LOOKED_UP = new boolean[ModBlock.VALUES.length];

    private CompatBlocks() {
    }

    /** The block, or null if its mod is not installed. */
    @Nullable
    public static Block block(ModBlock block) {
        int i = block.ordinal();
        synchronized (BLOCKS) {
            if (!LOOKED_UP[i]) {
                BLOCKS[i] = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(ModBlock.NAMESPACE, block.path))
                        .map(Holder::value).orElse(null);
                LOOKED_UP[i] = true;
            }
            return BLOCKS[i];
        }
    }

    public static boolean present(ModBlock block) {
        return block(block) != null;
    }

    /** True if Sincerelyuq's Biomes' blocks are in the game (its lumen stem stands for all of them). */
    public static boolean biomesInstalled() {
        return present(ModBlock.LUMEN_STEM) && present(ModBlock.AMETHYST_SPIKE);
    }

    /** The block's default state, or null. */
    @Nullable
    public static BlockState state(ModBlock block) {
        Block b = block(block);
        return b == null ? null : b.defaultBlockState();
    }

    /** A state with an integer property set by name (unchanged if the block has no such property or the value is out of range). */
    public static BlockState with(BlockState state, String name, int value) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
        if (property instanceof IntegerProperty ints && ints.getPossibleValues().contains(value)) {
            return state.setValue(ints, value);
        }
        return state;
    }

    /** A state with a boolean property set by name. */
    public static BlockState with(BlockState state, String name, boolean value) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
        if (property instanceof BooleanProperty bools) {
            return state.setValue(bools, value);
        }
        return state;
    }

    /** A state with an enum property set by its serialized name (axis "x", "y", "z" and the like). */
    public static <T extends Comparable<T>> BlockState with(BlockState state, String name, String value) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
        if (property instanceof EnumProperty<?> e) {
            return withValue(state, e, value);
        }
        return state;
    }

    private static <T extends Enum<T> & net.minecraft.util.StringRepresentable> BlockState withValue(BlockState state, EnumProperty<T> property, String value) {
        return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
    }
}
