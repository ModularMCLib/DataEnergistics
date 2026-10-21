package com.fish_dan_.data_energistics.common.terminal.player.connection;

import com.fish_dan_.data_energistics.integration.MOD;
import com.fish_dan_.data_energistics.integration.library.curios.equipment.CuriosTerminalSingularities;

import appeng.api.features.Locatables;
import appeng.api.ids.AEComponents;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.core.definitions.AEItems;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.jspecify.annotations.Nullable;

/**
 * Resolves carried entangled singularities through AE2's public quantum bridge registry.
 */
public final class TerminalQuantumAccess {

    private TerminalQuantumAccess() {}

    private static LongList frequencies(ServerPlayer player) {
        LongSet values = accessoryFrequencies(player);
        if (values.isEmpty()) {
            for (int index = 0; index < player.getInventory().getContainerSize(); index++)
                add(player.getInventory().getItem(index), values);
        }
        return new LongArrayList(values);
    }

    private static LongSet accessoryFrequencies(Player player) {
        LongSet values = new LongLinkedOpenHashSet();
        if (MOD.CURIOS.isLoaded()) CuriosTerminalSingularities.collect(player, values);
        return values;
    }

    public static void add(ItemStack stack, LongSet values) {
        if (!stack.is(AEItems.QUANTUM_ENTANGLED_SINGULARITY.asItem())) return;
        Long frequency = stack.get(AEComponents.ENTANGLED_SINGULARITY_ID);
        if (frequency != null) values.add(frequency.longValue());
    }

    /** Finds the physical connection credential without creating an item or changing its inventory slot. */
    public static ItemStack carried(Player player, long frequency) {
        if (!accessoryFrequencies(player).isEmpty()) return CuriosTerminalSingularities.find(player, frequency);
        for (int index = 0; index < player.getInventory().getContainerSize(); index++) {
            ItemStack stack = player.getInventory().getItem(index);
            if (matches(stack, frequency)) return stack;
        }
        return ItemStack.EMPTY;
    }

    public static boolean matches(ItemStack stack, long frequency) {
        if (!stack.is(AEItems.QUANTUM_ENTANGLED_SINGULARITY.asItem())) return false;
        Long paired = stack.get(AEComponents.ENTANGLED_SINGULARITY_ID);
        return paired != null && paired.longValue() == frequency;
    }

    public static @Nullable TerminalConnection resolve(ServerPlayer player) {
        if (player.isSpectator()) return null;
        LongList frequencies = frequencies(player);
        return frequencies.isEmpty() ? null : locate(player, frequencies.getLong(0));
    }

    public static @Nullable TerminalConnection resolve(ServerPlayer player, long frequency) {
        if (!frequencies(player).contains(frequency) || player.isSpectator()) return null;
        return locate(player, frequency);
    }

    private static @Nullable TerminalConnection locate(ServerPlayer player, long frequency) {
        IActionHost host = Locatables.quantumNetworkBridges().get(player.level(), frequency);
        TerminalConnection connection = connection(player, frequency, host);
        if (connection != null) return connection;
        host = Locatables.quantumNetworkBridges().get(player.level(), -frequency);
        return connection(player, frequency, host);
    }

    private static @Nullable TerminalConnection connection(ServerPlayer player, long frequency, @Nullable IActionHost host) {
        if (host == null) return null;
        var node = host.getActionableNode();
        if (node == null || !node.isActive() || !node.getGrid().getEnergyService().isNetworkPowered()) return null;
        return new TerminalConnection(frequency, node.getGrid(), host, IActionSource.ofPlayer(player, host));
    }
}
