package com.fish_dan_.data_energistics.common.terminal.player.storage;

import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.stacks.GenericStack;
import appeng.helpers.InventoryAction;

import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** AE configuration-stack click rules, independent of a host menu and of physical item transfers. */
@NullMarked
public final class TerminalGhostClicks {

    private TerminalGhostClicks() {}

    public static @Nullable GenericStack apply(@Nullable GenericStack previous, ItemStack carried, InventoryAction action) {
        if (action != InventoryAction.PICKUP_OR_SET_DOWN && action != InventoryAction.PLACE_SINGLE && action != InventoryAction.SPLIT_OR_PLACE_SINGLE)
            throw new IllegalArgumentException("Unsupported terminal configuration interaction: " + action);
        boolean decrease = action == InventoryAction.SPLIT_OR_PLACE_SINGLE;
        if (decrease) {
            var emptying = ContainerItemStrategies.getEmptyingAction(carried);
            if (emptying != null) return new GenericStack(emptying.what(), Math.max(1, emptying.maxAmount()));
        }
        GenericStack held = GenericStack.fromItemStack(action == InventoryAction.PLACE_SINGLE ? carried.copyWithCount(1) : carried);
        if (held == null) return action == InventoryAction.PICKUP_OR_SET_DOWN ? null : previous;
        if (previous == null) return decrease ? new GenericStack(held.what(), 1) : held;
        boolean sameResource = previous.what().equals(held.what());
        if (decrease) {
            if (!sameResource) return previous;
            long amount = previous.amount() - held.amount();
            return amount > 0 ? new GenericStack(previous.what(), amount) : null;
        }
        if (!sameResource) return held;
        return new GenericStack(held.what(), previous.amount() + Math.min(held.amount(), Long.MAX_VALUE - previous.amount()));
    }
}
