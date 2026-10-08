package com.fish_dan_.data_energistics.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

/** Encodes and decodes the current registry-aware NBT representation of item stack lists. */
public final class ItemStackListCodec {

    private ItemStackListCodec() {}

    /** Writes each stack as one registry-aware compound in list order. */
    public static ListTag encode(Iterable<? extends ItemStack> stacks, HolderLookup.Provider registries) {
        var encoded = new ListTag();
        for (ItemStack stack : stacks) {
            encoded.add(stack.saveOptional(registries));
        }
        return encoded;
    }

    /** Reads each current stack compound, preserving empty entries produced by optional decoding. */
    public static ObjectList<ItemStack> decode(ListTag encoded, HolderLookup.Provider registries) {
        var stacks = new ObjectArrayList<ItemStack>(encoded.size());
        for (int index = 0; index < encoded.size(); index++) {
            stacks.add(ItemStack.parseOptional(registries, encoded.getCompound(index)));
        }
        return stacks;
    }
}
