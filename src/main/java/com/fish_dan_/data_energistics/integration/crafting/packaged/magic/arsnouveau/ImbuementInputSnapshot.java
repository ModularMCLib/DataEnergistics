package com.fish_dan_.data_energistics.integration.crafting.packaged.magic.arsnouveau;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import com.hollingsworth.arsnouveau.common.block.tile.ImbuementTile;

import java.util.List;

/**
 * Ars binds RecipeInput to its tile class. This detached view is never registered or ticked in a world;
 * it supplies prospective inputs to matches/assemble without temporarily changing the actual chamber.
 */
final class ImbuementInputSnapshot extends ImbuementTile {

    private final List<ItemStack> pedestalInputs;
    private final List<BlockPos> pedestalPositions;
    private final int storedSource;
    private final int sourceCapacity;

    ImbuementInputSnapshot(ImbuementTile original, ItemStack input, List<ItemStack> pedestalInputs) {
        super(original.getBlockPos(), original.getBlockState());
        this.stack = input.copy();
        this.pedestalInputs = pedestalInputs.stream().map(ItemStack::copy).toList();
        this.pedestalPositions = List.copyOf(original.getNearbyPedestals());
        this.storedSource = original.getSource();
        this.sourceCapacity = original.getMaxSource();
        setLevel(original.getLevel());
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.stack.copy();
    }

    @Override
    public ItemStack getStack() {
        return this.stack.copy();
    }

    @Override
    public List<ItemStack> getPedestalItems() {
        return this.pedestalInputs.stream().map(ItemStack::copy).toList();
    }

    @Override
    public List<BlockPos> getNearbyPedestals() {
        return this.pedestalPositions;
    }

    @Override
    public int getSource() {
        return this.storedSource;
    }

    @Override
    public int getMaxSource() {
        return this.sourceCapacity;
    }
}
