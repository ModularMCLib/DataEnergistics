package com.fish_dan_.data_energistics.common.terminal.player.connection;

import com.fish_dan_.data_energistics.common.terminal.player.persistence.TerminalPlayerState;
import com.fish_dan_.data_energistics.integration.MOD;
import com.fish_dan_.data_energistics.integration.library.curios.equipment.CuriosTerminalSingularities;

import appeng.api.features.Locatables;
import appeng.api.ids.AEComponents;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.core.definitions.AEItems;

import net.minecraft.server.level.ServerPlayer;
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

    private TerminalQuantumAccess() {
    }

    public static LongList frequencies(ServerPlayer player) {
        LongSet values = new LongLinkedOpenHashSet();
        for (int index = 0; index < player.getInventory().getContainerSize(); index++)
            add(player.getInventory().getItem(index), values);
        if (MOD.isCuriosLoaded()) CuriosTerminalSingularities.collect(player, values);
        return new LongArrayList(values);
    }

    public static void add(ItemStack stack, LongSet values) {
        if (!stack.is(AEItems.QUANTUM_ENTANGLED_SINGULARITY.asItem())) return;
        Long frequency = stack.get(AEComponents.ENTANGLED_SINGULARITY_ID);
        if (frequency != null) values.add(frequency.longValue());
    }

    public static @Nullable TerminalConnection resolve(ServerPlayer player, TerminalPlayerState state) {
        LongList frequencies = frequencies(player);
        if (state.frequency() == null && !frequencies.isEmpty()) {
            for (long frequency : frequencies) {
                TerminalConnection available = resolve(player, frequency);
                if (available != null) {
                    state.frequency(frequency);
                    return available;
                }
            }
            state.frequency(frequencies.getLong(0));
        }
        Long selected = state.frequency();
        return selected == null || !frequencies.contains(selected.longValue()) ? null : resolve(player, selected.longValue());
    }

    public static @Nullable TerminalConnection resolve(ServerPlayer player, long frequency) {
        if (!frequencies(player).contains(frequency) || player.isSpectator()) return null;
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
