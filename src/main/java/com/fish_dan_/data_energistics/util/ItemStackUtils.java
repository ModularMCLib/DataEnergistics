package com.fish_dan_.data_energistics.util;

import net.minecraft.world.item.ItemStack;

/** Shared comparisons for complete item stack identity, including count and components. */
public final class ItemStackUtils {

    private ItemStackUtils() {}

    /** Returns whether two stacks have the same item, components, and count. */
    public static boolean sameItemAndCount(ItemStack first, ItemStack second) {
        return first.getCount() == second.getCount() && ItemStack.isSameItemSameComponents(first, second);
    }
}
