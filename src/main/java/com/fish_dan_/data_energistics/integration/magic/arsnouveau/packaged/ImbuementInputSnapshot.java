package com.fish_dan_.data_energistics.integration.magic.arsnouveau.packaged;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import com.hollingsworth.arsnouveau.common.block.tile.ImbuementTile;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.util.List;

/**
 * Ars binds RecipeInput to its tile class. This detached view is never registered or ticked in a world;
 * it supplies prospective inputs to matches/assemble without temporarily changing the actual chamber.
 */
final class ImbuementInputSnapshot extends ImbuementTile {

    private final List<ItemStack> pedestalInputs;
    private final LongList pedestalPositions;
    private final int storedSource;
    private final int sourceCapacity;

    ImbuementInputSnapshot(ImbuementTile original, ItemStack input, List<ItemStack> pedestalInputs) {
        super(original.getBlockPos(), original.getBlockState());
        this.stack = input.copy();
        this.pedestalInputs = pedestalInputs.stream().map(ItemStack::copy).toList();
        LongArrayList pedestalPositions = new LongArrayList(original.getNearbyPedestals().size());
        for (BlockPos position : original.getNearbyPedestals()) pedestalPositions.add(position.asLong());
        this.pedestalPositions = pedestalPositions;
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
    public ObjectList<BlockPos> getNearbyPedestals() {
        ObjectArrayList<BlockPos> positions = new ObjectArrayList<>(this.pedestalPositions.size());
        for (long packedPosition : this.pedestalPositions) positions.add(BlockPos.of(packedPosition));
        return ObjectLists.unmodifiable(positions);
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
