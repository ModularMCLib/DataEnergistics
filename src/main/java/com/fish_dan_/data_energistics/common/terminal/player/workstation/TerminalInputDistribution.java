package com.fish_dan_.data_energistics.common.terminal.player.workstation;

import com.fish_dan_.data_energistics.common.terminal.player.persistence.TerminalPlayerState;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

/** Applies one ordered physical drag directly to the player's persistent inputs, without creating a menu. */
public final class TerminalInputDistribution {

    private TerminalInputDistribution() {}

    public static void apply(ServerPlayer player, TerminalPlayerState state, CompoundTag data) {
        if (!state.workspace().id().equals(data.getString("workspace"))) return;
        if (state.workspace() == TerminalWorkspace.PROCESSING)
            throw new IllegalArgumentException("Processing inputs only accept configuration resources");
        int button = data.getInt("button");
        int[] indices = data.getIntArray("slots");
        if (button < 0 || button > 1 || indices.length < 2 || indices.length > state.workspace().inputSlots())
            throw new IllegalArgumentException("Invalid terminal input distribution");
        IntSet visited = new IntOpenHashSet();
        for (int index : indices)
            if (index < 0 || index >= state.workspace().inputSlots() || !visited.add(index))
                throw new IllegalArgumentException("Invalid terminal input distribution slot");
        ItemStack carried = player.containerMenu.getCarried();
        ItemStack expected = ItemStack.parseOptional(player.registryAccess(), data.getCompound("carried"));
        if (carried.isEmpty() || !ItemStack.matches(carried, expected)) return;
        int amount = button == 0 ? carried.getCount() / indices.length : 1;
        for (int index : indices) {
            ItemStack before = state.work().inputs.getItem(index);
            if (!before.isEmpty() && !ItemStack.isSameItemSameComponents(before, carried)) continue;
            int maximum = maximum(state.workspace(), index, carried);
            int moved = Math.min(Math.min(amount, maximum - before.getCount()), carried.getCount());
            if (moved <= 0) continue;
            state.work().inputs.setItem(index, carried.copyWithCount(before.getCount() + moved));
            carried.shrink(moved);
            TerminalSlotActions.inputChanged(state, index, before);
        }
        if (carried.isEmpty()) player.containerMenu.setCarried(ItemStack.EMPTY);
    }

    public static int maximum(TerminalWorkspace workspace, int index, ItemStack stack) {
        return workspace == TerminalWorkspace.ANVIL && index == 0 ? 1 : stack.getMaxStackSize();
    }
}
