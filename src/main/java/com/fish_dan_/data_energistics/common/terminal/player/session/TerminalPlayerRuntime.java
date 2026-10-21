package com.fish_dan_.data_energistics.common.terminal.player.session;

import com.fish_dan_.data_energistics.api.research.ResearchAccess;
import com.fish_dan_.data_energistics.common.network.packets.terminal.TerminalPickBlockStatePayload;
import com.fish_dan_.data_energistics.common.network.packets.terminal.TerminalRestockStatePayload;
import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;
import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalQuantumAccess;
import com.fish_dan_.data_energistics.common.terminal.player.magnet.TerminalRestockSupply;
import com.fish_dan_.data_energistics.common.terminal.player.maintenance.TerminalMaintenanceTicker;
import com.fish_dan_.data_energistics.common.terminal.player.persistence.TerminalPlayerState;
import com.fish_dan_.data_energistics.common.terminal.player.storage.TerminalNetworkStorage;
import com.fish_dan_.data_energistics.data.research.PlayerTerminalResearch;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One player's tick budget and optional overlay session; local workspaces do not depend on a grid.
 */
public final class TerminalPlayerRuntime {

    private final ServerPlayer player;
    private final TerminalPlayerState state;
    private final TerminalMaintenanceTicker maintenance = new TerminalMaintenanceTicker();
    private @Nullable TerminalSession session;
    private @Nullable IGrid paidGrid;
    private int paidTick = -1;
    private @Nullable Boolean sentPickBlock;
    private List<GenericStack> sentRestock = List.of();
    private final ObjectSet<UUID> openedSessions = new ObjectOpenHashSet<>();

    public TerminalPlayerRuntime(ServerPlayer player, TerminalPlayerState state) {
        this.player = player;
        this.state = state;
    }

    public TerminalPlayerState state() {
        return state;
    }

    public @Nullable TerminalSession session() {
        return session;
    }

    public void open(UUID id) {
        if (!openedSessions.add(id)) return;
        close();
        session = new TerminalSession(player, id);
    }

    public void close() {
        if (session != null) session.close();
        session = null;
    }

    public @Nullable TerminalConnection connection() {
        TerminalConnection connection = TerminalQuantumAccess.resolve(player);
        if (connection == null) return null;
        int tick = player.server.getTickCount();
        if (paidTick == tick) return paidGrid == connection.grid() ? connection : null;
        paidTick = tick;
        paidGrid = null;
        TerminalNetworkStorage.refund(connection, state);
        var energy = connection.grid().getEnergyService();
        if (energy.extractAEPower(22.5, Actionable.SIMULATE, PowerMultiplier.ONE) + 1.0e-7 < 22.5) return null;
        double paid = energy.extractAEPower(22.5, Actionable.MODULATE, PowerMultiplier.ONE);
        if (!Double.isFinite(paid) || paid < 0 || paid > 22.5 + 1.0e-7)
            throw new IllegalStateException("Invalid terminal connection energy charge");
        if (paid + 1.0e-7 < 22.5) {
            if (paid > 0) state.returns().energy(connection.frequency(), paid);
            return null;
        }
        paidGrid = connection.grid();
        return connection;
    }

    public void tick() {
        if (player.tickCount % 20 == 0) settleReturns();
        if (session != null) {
            TerminalAeNavigation.restoreClosed(player, session);
            if (!session.valid(player)) close();
        }
        boolean terminal = ResearchAccess.hasPlayerUpgrade(player, PlayerTerminalResearch.TERMINAL);
        boolean magnetUnlocked = terminal && ResearchAccess.hasPlayerUpgrade(player, PlayerTerminalResearch.MAGNET);
        boolean pickBlock = terminal && state.magnet().pickBlock();
        if (sentPickBlock == null || sentPickBlock != pickBlock) {
            PacketDistributor.sendToPlayer(player, new TerminalPickBlockStatePayload(pickBlock));
            sentPickBlock = pickBlock;
        }
        if (!terminal || !player.isAlive()) {
            syncRestock(null);
            maintenance.tick(player, state.maintenance(), null);
            return;
        }
        boolean background = state.magnet().active(magnetUnlocked);
        boolean maintain = state.maintenance().active();
        boolean wantsNetwork = (session != null && session.visible()) || background || maintain;
        TerminalConnection connection = wantsNetwork ? connection() : null;
        maintenance.tick(player, state.maintenance(), connection);
        if (session != null) TerminalAeNavigation.tick(player, session, connection);
        if (session != null) session.tick(player, this, connection);
        if (background) TerminalMagnetTicker.tick(player, state, connection, magnetUnlocked);
        syncRestock(connection);
        if (session != null && player.tickCount % 5 == 0) session.send(player, this, connection);
    }

    private void syncRestock(@Nullable TerminalConnection connection) {
        List<GenericStack> next = new ArrayList<>();
        if (state.magnet().restock() && connection != null && !player.isCreative() && !player.isSpectator()) {
            if (player.tickCount % 5 != 0) return;
            var available = connection.grid().getStorageService().getCachedInventory();
            ObjectSet<AEItemKey> keys = new ObjectLinkedOpenHashSet<>();
            for (int slot = 0; slot < 9; slot++) addRestockKey(keys, player.getInventory().getItem(slot));
            addRestockKey(keys, player.getOffhandItem());
            for (AEItemKey key : keys) {
                long stored = available.get(key);
                if (stored > 0) next.add(new GenericStack(key, stored));
            }
        }
        if (!next.equals(sentRestock)) {
            PacketDistributor.sendToPlayer(player, new TerminalRestockStatePayload(next));
            sentRestock = List.copyOf(next);
        }
    }

    private static void addRestockKey(ObjectSet<AEItemKey> keys, ItemStack stack) {
        if (TerminalRestockSupply.eligible(stack)) keys.add(Objects.requireNonNull(AEItemKey.of(stack)));
    }

    public TerminalMaintenanceTicker maintenance() {
        return maintenance;
    }

    public void shutdown() {
        close();
        maintenance.close();
    }

    private void settleReturns() {
        for (long frequency : state.returns().frequencies()) {
            var original = TerminalQuantumAccess.resolve(player, frequency);
            if (original != null) TerminalNetworkStorage.refund(original, state);
        }
        if (state.anvilPayment().pending()) {
            var original = TerminalQuantumAccess.resolve(player, state.paymentFrequency());
            if (original != null) TerminalNetworkStorage.refund(original, state);
        }
    }
}
