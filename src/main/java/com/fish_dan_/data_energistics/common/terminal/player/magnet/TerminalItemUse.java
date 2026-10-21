package com.fish_dan_.data_energistics.common.terminal.player.magnet;

import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Ephemeral server-thread snapshot for one item-use call, including ammunition in the main inventory. */
public final class TerminalItemUse {

    private final ItemStack[] previous;

    public TerminalItemUse(ServerPlayer player) {
        var inventory = player.getInventory();
        previous = new ItemStack[inventory.getContainerSize()];
        for (int slot = 0; slot < previous.length; slot++) previous[slot] = inventory.getItem(slot).copy();
    }

    /** Settles consumed stacks once; nested use calls already paid by an inner scope have no remaining delta. */
    public void finish(ServerPlayer player, TerminalConnection connection) {
        var inventory = player.getInventory();
        boolean changed = false;
        for (int slot = 0; slot < previous.length; slot++) {
            ItemStack current = inventory.getItem(slot);
            ItemStack result = TerminalRestockSupply.pay(previous[slot], current, connection);
            if (result != current) {
                inventory.setItem(slot, result);
                changed = true;
            }
        }
        if (changed) {
            inventory.setChanged();
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
        }
    }
}
