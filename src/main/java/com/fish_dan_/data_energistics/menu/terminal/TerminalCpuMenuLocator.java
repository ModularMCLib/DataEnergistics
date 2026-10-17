package com.fish_dan_.data_energistics.menu.terminal;

import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalQuantumAccess;
import com.fish_dan_.data_energistics.common.terminal.player.session.TerminalCpuNavigation;

import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.locator.MenuLocators;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * A live carried singularity anchors AE2's native submenu; the player upgrade remains the terminal itself.
 */
public record TerminalCpuMenuLocator(UUID sessionId, long frequency) implements ItemMenuHostLocator {

    public static void register() {
        MenuLocators.register(TerminalCpuMenuLocator.class,
                (locator, buffer) -> {
                    buffer.writeUUID(locator.sessionId());
                    buffer.writeLong(locator.frequency());
                },
                buffer -> new TerminalCpuMenuLocator(buffer.readUUID(), buffer.readLong()));
    }

    @Override
    public <T> @Nullable T locate(Player player, Class<T> hostInterface) {
        if (locateItem(player).isEmpty()) return null;
        var connection = player instanceof ServerPlayer serverPlayer ? TerminalCpuNavigation.connection(serverPlayer, sessionId, frequency) : null;
        if (!player.level().isClientSide() && connection == null) return null;
        TerminalCpuMenuHost host = new TerminalCpuMenuHost(player, this, connection);
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
