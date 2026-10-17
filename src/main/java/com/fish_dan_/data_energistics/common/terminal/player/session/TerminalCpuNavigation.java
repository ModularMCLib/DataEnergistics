package com.fish_dan_.data_energistics.common.terminal.player.session;

import com.fish_dan_.data_energistics.api.research.ResearchAccess;
import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;
import com.fish_dan_.data_energistics.data.research.PlayerTerminalResearch;
import com.fish_dan_.data_energistics.menu.terminal.TerminalCpuMenuLocator;
import com.fish_dan_.data_energistics.network.terminal.TerminalCpuNavigationPayload;

import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.MenuOpener;
import appeng.menu.me.crafting.CraftingStatusMenu;
import appeng.parts.AEBasePart;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Server-thread visits to AE2's original CPU menu suspend the original container and resume that same object.
 */
public final class TerminalCpuNavigation {

    private static final Object2ObjectMap<UUID, Visit> VISITS = new Object2ObjectOpenHashMap<>();

    private TerminalCpuNavigation() {}

    static void open(ServerPlayer player, TerminalSession session, TerminalConnection connection) {
        if (VISITS.containsKey(player.getUUID())) throw new IllegalStateException("A CPU visit is already active");
        Visit visit = new Visit(player, session, connection);
        VISITS.put(player.getUUID(), visit);
        send(visit, "suspend");
        // ServerPlayer.openMenu normally removes the previous menu. The parent is retained for the return trip.
        player.containerMenu = player.inventoryMenu;
        try {
            if (!MenuOpener.open(CraftingStatusMenu.TYPE, player, new TerminalCpuMenuLocator(session.id(), connection.frequency())) ||
                    !(player.containerMenu instanceof CraftingStatusMenu cpuMenu))
                throw new IllegalStateException("AE2 could not open the player terminal CPU list");
            visit.cpu = cpuMenu;
            session.cpuOpened(cpuMenu.containerId);
        } catch (RuntimeException failure) {
            VISITS.remove(player.getUUID());
            if (player.containerMenu != player.inventoryMenu) player.containerMenu.removed(player);
            player.containerMenu = visit.parent;
            session.cpuReturned(player);
            send(visit, "resume");
            visit.parent.sendAllDataToRemote();
            throw failure;
        }
    }

    public static @Nullable TerminalConnection connection(ServerPlayer player, UUID session, long frequency) {
        if (!VISITS.containsKey(player.getUUID())) return null;
        Visit visit = VISITS.get(player.getUUID());
        return visit.session.id().equals(session) && visit.connection.frequency() == frequency ? visit.connection : null;
    }

    static boolean suspended(ServerPlayer player, TerminalSession session) {
        if (!VISITS.containsKey(player.getUUID())) return false;
        Visit visit = VISITS.get(player.getUUID());
        return visit.session == session && player.containerMenu == visit.cpu;
    }

    static void tick(ServerPlayer player, TerminalSession session, @Nullable TerminalConnection connection) {
        if (!VISITS.containsKey(player.getUUID())) return;
        Visit visit = VISITS.get(player.getUUID());
        if (visit.session != session || player.containerMenu != visit.cpu) {
            abandon(player.getUUID(), visit.session.id());
        } else if (!parentValid(visit)) {
            abandon(player.getUUID(), visit.session.id());
        } else if (connection == null || connection.grid() != visit.connection.grid() || connection.frequency() != visit.connection.frequency()) {
            resume(player);
        }
    }

    public static void resume(ServerPlayer player) {
        if (!VISITS.containsKey(player.getUUID())) return;
        Visit visit = VISITS.get(player.getUUID());
        if (player.containerMenu != visit.cpu || !parentValid(visit)) {
            abandon(player.getUUID(), visit.session.id());
            return;
        }
        VISITS.remove(player.getUUID());
        visit.cpu.removed(player);
        NeoForge.EVENT_BUS.post(new PlayerContainerEvent.Close(player, visit.cpu));
        player.containerMenu = visit.parent;
        visit.session.cpuReturned(player);
        send(visit, "resume");
        visit.parent.sendAllDataToRemote();
    }

    static void abandon(UUID playerId, UUID sessionId) {
        if (!VISITS.containsKey(playerId)) return;
        Visit visit = VISITS.get(playerId);
        if (!visit.session.id().equals(sessionId)) return;
        VISITS.remove(playerId);
        if (visit.player.containerMenu == visit.cpu) visit.player.closeContainer();
        visit.parent.removed(visit.player);
        NeoForge.EVENT_BUS.post(new PlayerContainerEvent.Close(visit.player, visit.parent));
        send(visit, "abort");
    }

    private static boolean parentValid(Visit visit) {
        // AEBaseMenu normally checks these identities in broadcastChanges, which the suspended parent no longer runs.
        // Validate the public host without broadcasting packets into the native CPU screen.
        if (visit.parent instanceof AEBaseMenu aeMenu) {
            Object target = aeMenu.getTarget();
            if (target instanceof AEBasePart part) {
                var host = part.getHost();
                if (host == null || !host.isInWorld() || host.getPart(part.getSide()) != part) return false;
            } else if (target instanceof BlockEntity blockEntity) {
                var level = blockEntity.getLevel();
                BlockPos position = blockEntity.getBlockPos();
                if (blockEntity.isRemoved() || level == null || !level.getChunkSource().hasChunk(position.getX() >> 4, position.getZ() >> 4) ||
                        level.getBlockEntity(position) != blockEntity)
                    return false;
            } else if (target instanceof ItemMenuHost<?> itemHost && !itemHost.isValid()) return false;
        }
        return visit.player.isAlive() && !visit.player.isSpectator() && visit.parent.stillValid(visit.player) &&
                ResearchAccess.hasPlayerUpgrade(visit.player, PlayerTerminalResearch.TERMINAL);
    }

    private static void send(Visit visit, String phase) {
        PacketDistributor.sendToPlayer(visit.player, new TerminalCpuNavigationPayload(visit.parent.containerId, visit.session.id(), phase));
    }

    private static final class Visit {

        private final ServerPlayer player;
        private final TerminalSession session;
        private final AbstractContainerMenu parent;
        private final TerminalConnection connection;
        private @Nullable CraftingStatusMenu cpu;

        private Visit(ServerPlayer player, TerminalSession session, TerminalConnection connection) {
            this.player = player;
            this.session = session;
            this.parent = player.containerMenu;
            this.connection = connection;
        }
    }
}
