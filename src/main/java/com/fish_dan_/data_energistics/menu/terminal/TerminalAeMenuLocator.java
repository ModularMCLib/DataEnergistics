package com.fish_dan_.data_energistics.menu.terminal;

import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalQuantumAccess;
import com.fish_dan_.data_energistics.common.terminal.player.session.TerminalAeNavigation;

import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.locator.MenuHostLocator;
import appeng.menu.locator.MenuLocators;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * A live carried singularity anchors AE2's native submenu; the player upgrade remains the terminal itself.
 */
public record TerminalAeMenuLocator(UUID sessionId, long frequency) implements ItemMenuHostLocator {

    /** AE2 client menus expose their locator through the item host; existing tree menus store it on the menu. */
    public static boolean matches(AbstractContainerMenu menu, UUID sessionId) {
        if (!(menu instanceof AEBaseMenu aeMenu)) return false;
        MenuHostLocator locator = aeMenu.getLocator();
        if (locator == null && aeMenu.getTarget() instanceof ItemMenuHost<?> host) locator = host.getLocator();
        return locator instanceof TerminalAeMenuLocator terminal && terminal.sessionId().equals(sessionId);
    }

    public static void register() {
        MenuLocators.register(TerminalAeMenuLocator.class,
                (locator, buffer) -> {
                    buffer.writeUUID(locator.sessionId());
                    buffer.writeLong(locator.frequency());
                },
                buffer -> new TerminalAeMenuLocator(buffer.readUUID(), buffer.readLong()));
    }

    @Override
    public <T> @Nullable T locate(Player player, Class<T> hostInterface) {
        if (locateItem(player).isEmpty()) return null;
        var connection = player instanceof ServerPlayer serverPlayer ? TerminalAeNavigation.connection(serverPlayer, sessionId, frequency) : null;
        if (!player.level().isClientSide() && connection == null) return null;
        TerminalAeMenuHost host = new TerminalAeMenuHost(player, this, connection);
        return hostInterface.isInstance(host) ? hostInterface.cast(host) : null;
    }

    @Override
    public ItemStack locateItem(Player player) {
        return TerminalQuantumAccess.carried(player, frequency);
    }

    @Override
    public @Nullable BlockHitResult hitResult() {
        return null;
    }
}
