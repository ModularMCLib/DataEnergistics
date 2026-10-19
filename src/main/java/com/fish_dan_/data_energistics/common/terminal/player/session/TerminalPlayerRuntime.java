package com.fish_dan_.data_energistics.common.terminal.player.session;

import com.fish_dan_.data_energistics.api.research.ResearchAccess;
import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;
import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalQuantumAccess;
import com.fish_dan_.data_energistics.common.terminal.player.persistence.TerminalPlayerState;
import com.fish_dan_.data_energistics.common.terminal.player.storage.TerminalNetworkStorage;
import com.fish_dan_.data_energistics.data.research.PlayerTerminalResearch;
import com.fish_dan_.data_energistics.network.terminal.TerminalPickBlockStatePayload;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * One player's tick budget and optional overlay session; local workspaces do not depend on a grid.
 */
public final class TerminalPlayerRuntime {

    private final ServerPlayer player;
    private final TerminalPlayerState state;
    private @Nullable TerminalSession session;
    private @Nullable IGrid paidGrid;
    private int paidTick = -1;
    private @Nullable Boolean sentPickBlock;
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
        if (!terminal) return;
        boolean background = state.magnet().active(magnetUnlocked);
        boolean wantsNetwork = (session != null && session.visible()) || background;
        TerminalConnection connection = wantsNetwork ? connection() : null;
        if (session != null) TerminalAeNavigation.tick(player, session, connection);
        if (session != null) session.tick(player, this, connection);
        if (background) TerminalMagnetTicker.tick(player, state, connection, magnetUnlocked);
        if (session != null && player.tickCount % 5 == 0) session.send(player, this, connection);
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
