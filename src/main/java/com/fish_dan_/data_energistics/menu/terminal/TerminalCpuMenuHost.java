package com.fish_dan_.data_energistics.menu.terminal;

import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;
import com.fish_dan_.data_energistics.common.terminal.player.session.TerminalCpuNavigation;

import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.ILinkStatus;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.MEStorage;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigManagerListener;
import appeng.core.definitions.AEItems;
import appeng.me.storage.NullInventory;
import appeng.menu.ISubMenu;
import appeng.util.ConfigManager;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

/**
 * Adapts the actual connection credential to AE2's native CPU screen and its parent-menu return contract.
 */
final class TerminalCpuMenuHost extends ItemMenuHost<Item> implements ITerminalHost, IActionHost {

    private final @Nullable TerminalConnection connection;
    private final TerminalCpuMenuLocator locator;
    private final IConfigManager settings = new ConfigManager((IConfigManagerListener) null);

    TerminalCpuMenuHost(Player player, TerminalCpuMenuLocator locator, @Nullable TerminalConnection connection) {
        super(AEItems.QUANTUM_ENTANGLED_SINGULARITY.asItem(), player, locator);
        this.connection = connection;
        this.locator = locator;
    }

    @Override
    public boolean isValid() {
        // Let the overlay session resume its local parent when the credential disappears.
        // Vanilla item-host invalidation would close the native menu before that coordinated return.
        if (getPlayer() instanceof ServerPlayer player)
            return TerminalCpuNavigation.connection(player, locator.sessionId(), locator.frequency()) != null;
        return super.isValid();
    }

    @Override
    public @Nullable IGridNode getActionableNode() {
        return connection == null ? null : connection.host().getActionableNode();
    }

    @Override
    public MEStorage getInventory() {
        return connection == null ? NullInventory.of() : connection.storage();
    }

    @Override
    public ILinkStatus getLinkStatus() {
        return isValid() ? ILinkStatus.ofConnected() : ILinkStatus.ofDisconnected();
    }

    @Override
    public IConfigManager getConfigManager() {
        return settings;
    }

    @Override
    public ItemStack getMainMenuIcon() {
        ItemStack icon = getItemStack().copyWithCount(1);
        icon.set(DataComponents.CUSTOM_NAME, Component.translatable("gui.data_energistics.player_terminal.title"));
        return icon;
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        if (player instanceof ServerPlayer serverPlayer) TerminalCpuNavigation.resume(serverPlayer);
    }
}
