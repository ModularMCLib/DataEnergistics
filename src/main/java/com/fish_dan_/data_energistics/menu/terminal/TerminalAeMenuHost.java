package com.fish_dan_.data_energistics.menu.terminal;

import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;

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
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.util.ConfigManager;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Adapts the actual connection credential to AE2's native submenus and their parent-menu return contract.
 */
final class TerminalAeMenuHost extends ItemMenuHost<Item> implements ITerminalHost, IActionHost {

    private final @Nullable TerminalConnection connection;
    private final BooleanSupplier validConnection;
    private final Consumer<ServerPlayer> returnToParent;
    private final IConfigManager settings = new ConfigManager((IConfigManagerListener) null);

    TerminalAeMenuHost(Player player, ItemMenuHostLocator locator, @Nullable TerminalConnection connection,
                       BooleanSupplier validConnection, Consumer<ServerPlayer> returnToParent) {
        super(AEItems.QUANTUM_ENTANGLED_SINGULARITY.asItem(), player, locator);
        this.connection = connection;
        this.validConnection = validConnection;
        this.returnToParent = returnToParent;
    }

    @Override
    public boolean isValid() {
        // Let the overlay session resume its local parent when the credential disappears.
        // Vanilla item-host invalidation would close the native menu before that coordinated return.
        if (getPlayer() instanceof ServerPlayer) return validConnection.getAsBoolean();
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
        if (player instanceof ServerPlayer serverPlayer) returnToParent.accept(serverPlayer);
    }
}
