package com.fish_dan_.data_energistics.menu.terminal;

import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;

import appeng.api.stacks.AEKey;
import appeng.api.storage.ILinkStatus;
import appeng.helpers.InventoryAction;
import appeng.menu.me.common.MEStorageMenu;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Executes AE's storage actions on the server thread after the overlay session validates its connection.
 * This menu is never opened or broadcast: AE owns the transfer rules and the host menu retains its cursor.
 */
@NullMarked
public final class TerminalStorageInteractionMenu extends MEStorageMenu {

    private TerminalStorageInteractionMenu(ServerPlayer player, TerminalConnection connection) {
        super(MEStorageMenu.TYPE, player.containerMenu.containerId, player.getInventory(), host(player, connection));
    }

    /** Validated overlay actions execute synchronously without replacing the player's current container. */
    public static void interact(ServerPlayer player, TerminalConnection connection, @Nullable AEKey key, InventoryAction action) {
        switch (action) {
            case PICKUP_OR_SET_DOWN, SPLIT_OR_PLACE_SINGLE, SHIFT_CLICK, PICKUP_SINGLE, MOVE_REGION, ROLL_UP, ROLL_DOWN, CREATIVE_DUPLICATE, FILL_ITEM, FILL_ENTIRE_ITEM, FILL_ENTIRE_ITEM_MOVE_TO_PLAYER, EMPTY_ITEM, EMPTY_ENTIRE_ITEM -> {
                TerminalStorageInteractionMenu menu = new TerminalStorageInteractionMenu(player, connection);
                menu.handleNetworkInteraction(player, key, action);
            }
            default -> throw new IllegalArgumentException("Unsupported terminal storage action: " + action);
        }
    }

    private static TerminalAeMenuHost host(ServerPlayer player, TerminalConnection connection) {
        AbstractContainerMenu parent = player.containerMenu;
        return new TerminalAeMenuHost(player, new TerminalPickBlockMenuLocator(connection.frequency()), connection,
                () -> player.containerMenu == parent && parent.stillValid(player), ServerPlayer::closeContainer);
    }

    @Override
    public ILinkStatus getLinkStatus() {
        return getHost().getLinkStatus();
    }

    @Override
    public ItemStack getCarried() {
        return getPlayer().containerMenu.getCarried();
    }

    @Override
    public void setCarried(ItemStack stack) {
        getPlayer().containerMenu.setCarried(stack);
    }
}
