package com.fish_dan_.data_energistics.common.terminal.player.session;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.research.ResearchAccess;
import com.fish_dan_.data_energistics.common.terminal.player.persistence.TerminalSavedData;
import com.fish_dan_.data_energistics.data.research.PlayerTerminalResearch;
import com.fish_dan_.data_energistics.menu.terminal.TerminalCpuMenuLocator;
import com.fish_dan_.data_energistics.network.terminal.TerminalRequestPayload;
import com.fish_dan_.data_energistics.network.terminal.TerminalSnapshotPayload;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.UUID;

/**
 * Server-thread lifecycle and top-level failure boundary for player terminal sessions and background magnets.
 */
public final class TerminalSessions {

    private static final Object2ObjectMap<UUID, TerminalPlayerRuntime> PLAYERS = new Object2ObjectOpenHashMap<>();
    private static final ObjectSet<UUID> FAILED = new ObjectOpenHashSet<>();

    public TerminalSessions() {
        TerminalCpuMenuLocator.register();
    }

    private static TerminalPlayerRuntime runtime(ServerPlayer player) {
        return PLAYERS.computeIfAbsent(player.getUUID(), unused -> new TerminalPlayerRuntime(player, TerminalSavedData.get(player.server).player(player.getUUID())));
    }

    public static void handle(ServerPlayer player, TerminalRequestPayload request) {
        if (request.action().equals("cpu_return")) {
            if (player.isSpectator() || !PLAYERS.containsKey(player.getUUID())) return;
            try {
                TerminalPlayerRuntime runtime = PLAYERS.get(player.getUUID());
                TerminalSession session = runtime.session();
                if (session == null || !session.acceptsCpuReturn(player, request)) return;
                TerminalCpuNavigation.resume(player);
                session.send(player, runtime, session.visible() ? runtime.connection() : null);
            } catch (RuntimeException failure) {
                fail(player, "CPU return", failure);
            }
            return;
        }
        if (player.isSpectator()) return;
        if (PLAYERS.containsKey(player.getUUID())) {
            try {
                TerminalSession session = PLAYERS.get(player.getUUID()).session();
                if (session != null && session.discardDuringCpuVisit(player, request)) return;
            } catch (RuntimeException failure) {
                fail(player, "suspended host input", failure);
                return;
            }
        }
        if (player.containerMenu.containerId != request.containerId()) return;
        if (!ResearchAccess.hasPlayerUpgrade(player, PlayerTerminalResearch.TERMINAL)) {
            CompoundTag locked = new CompoundTag();
            locked.putBoolean("enabled", false);
            locked.putString("reason", "locked");
            PacketDistributor.sendToPlayer(player, new TerminalSnapshotPayload(request.containerId(), request.sessionId(), request.sequence(), locked));
            return;
        }
        if (FAILED.contains(player.getUUID())) return;
        try {
            TerminalPlayerRuntime runtime = runtime(player);
            if (runtime.faulted()) return;
            if (request.action().equals("open") && request.sequence() == 0) runtime.open(request.sessionId());
            else {
                TerminalSession session = runtime.session();
                if (session == null || !session.accepts(player, request)) return;
                session.action(player, runtime, request.action(), request.data());
            }
            TerminalSession session = runtime.session();
            if (session != null) session.send(player, runtime, session.visible() ? runtime.connection() : null);
        } catch (RuntimeException failure) {
            fail(player, "action " + request.action(), failure);
            CompoundTag fault = new CompoundTag();
            fault.putBoolean("enabled", true);
            fault.putString("reason", "fault");
            PacketDistributor.sendToPlayer(player, new TerminalSnapshotPayload(request.containerId(), request.sessionId(), request.sequence(), fault));
        }
    }

    @SubscribeEvent
    public void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) ||
                !ResearchAccess.hasPlayerUpgrade(player, PlayerTerminalResearch.TERMINAL) && !PLAYERS.containsKey(player.getUUID()) ||
                FAILED.contains(player.getUUID()) && !PLAYERS.containsKey(player.getUUID()))
            return;
        try {
            runtime(player).tick();
        } catch (RuntimeException failure) {
            fail(player, "background tick", failure);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void pickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !ResearchAccess.hasPlayerUpgrade(player, PlayerTerminalResearch.TERMINAL) ||
                !ResearchAccess.hasPlayerUpgrade(player, PlayerTerminalResearch.MAGNET))
            return;
        if (FAILED.contains(player.getUUID())) return;
        try {
            TerminalPlayerRuntime runtime = runtime(player);
            if (runtime.faulted()) return;
            TerminalMagnetTicker.pickup(event, player, runtime);
        } catch (RuntimeException failure) {
            fail(player, "item pickup", failure);
        }
    }

    @SubscribeEvent
    public void death(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY) ||
                !ResearchAccess.hasPlayerUpgrade(player, PlayerTerminalResearch.TERMINAL))
            return;
        try {
            TerminalSavedData.get(player.server).player(player.getUUID()).dropPhysicalItems(stack -> player.drop(stack, true, false));
        } catch (RuntimeException failure) {
            fail(player, "death inventory", failure);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void dying(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && PLAYERS.containsKey(player.getUUID())) {
            try {
                PLAYERS.get(player.getUUID()).close();
            } catch (RuntimeException failure) {
                fail(player, "death menu cleanup", failure);
            }
        }
    }

    @SubscribeEvent
    public void clonePlayer(PlayerEvent.Clone event) {
        remove(event.getOriginal().getUUID());
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void stopped(ServerStoppedEvent event) {
        PLAYERS.values().forEach(TerminalPlayerRuntime::close);
        PLAYERS.clear();
        FAILED.clear();
    }

    private static void remove(UUID player) {
        FAILED.remove(player);
        if (PLAYERS.containsKey(player)) PLAYERS.remove(player).close();
    }

    private static void fail(ServerPlayer player, String operation, RuntimeException failure) {
        if (PLAYERS.containsKey(player.getUUID())) {
            TerminalPlayerRuntime runtime = PLAYERS.get(player.getUUID());
            if (runtime.session() != null) runtime.session().failure(player);
            runtime.fault();
        }
        if (FAILED.add(player.getUUID()))
            Data_Energistics.LOGGER.error("Stopped player terminal for {} during {}", player.getUUID(), operation, failure);
    }
}
