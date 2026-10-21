package com.fish_dan_.data_energistics.common.menu.terminal;

import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;
import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalQuantumAccess;
import com.fish_dan_.data_energistics.common.terminal.player.session.TerminalSessions;

import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.locator.MenuLocators;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** A world pick-block request opens AE2's normal crafting chain and returns to the game on close. */
@NullMarked
public record TerminalPickBlockMenuLocator(long frequency) implements ItemMenuHostLocator {

    static void register() {
        MenuLocators.register(TerminalPickBlockMenuLocator.class,
                (locator, buffer) -> buffer.writeLong(locator.frequency()), buffer -> new TerminalPickBlockMenuLocator(buffer.readLong()));
    }

    @Override
    public <T> @Nullable T locate(Player player, Class<T> hostInterface) {
        if (locateItem(player).isEmpty()) return null;
        TerminalConnection connection = player instanceof ServerPlayer serverPlayer ? TerminalSessions.pickBlockConnection(serverPlayer, frequency) : null;
        if (!player.level().isClientSide() && connection == null) return null;
        TerminalAeMenuHost host = new TerminalAeMenuHost(player, this, connection,
                () -> player instanceof ServerPlayer serverPlayer && connectionMatches(serverPlayer, connection), ServerPlayer::closeContainer);
        return hostInterface.isInstance(host) ? hostInterface.cast(host) : null;
    }

    private boolean connectionMatches(ServerPlayer player, @Nullable TerminalConnection original) {
        TerminalConnection current = TerminalSessions.pickBlockConnection(player, frequency);
        return current != null && original != null && current.grid() == original.grid();
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
