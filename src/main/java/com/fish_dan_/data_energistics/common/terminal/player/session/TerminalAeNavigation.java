package com.fish_dan_.data_energistics.common.terminal.player.session;

import com.fish_dan_.data_energistics.api.research.ResearchAccess;
import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;
import com.fish_dan_.data_energistics.data.research.PlayerTerminalResearch;
import com.fish_dan_.data_energistics.menu.terminal.TerminalAeMenuLocator;
import com.fish_dan_.data_energistics.network.terminal.TerminalNavigationPayload;

import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEKey;
import appeng.helpers.ICraftingGridMenu;
import appeng.menu.AEBaseMenu;
import appeng.menu.MenuOpener;
import appeng.menu.me.crafting.CraftAmountMenu;
import appeng.menu.me.crafting.CraftConfirmMenu;
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
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Keeps the original container alive while AE2 owns its CPU list or existing crafting-request menu chain.
 */
public final class TerminalAeNavigation {

    private static final Object2ObjectMap<UUID, Visit> VISITS = new Object2ObjectOpenHashMap<>();

    private TerminalAeNavigation() {}

    static void open(ServerPlayer player, TerminalSession session, TerminalConnection connection) {
        begin(player, session, connection, locator -> MenuOpener.open(CraftingStatusMenu.TYPE, player, locator));
    }

    static void openCrafting(ServerPlayer player, TerminalSession session, TerminalConnection connection, AEKey key) {
        begin(player, session, connection, locator -> CraftAmountMenu.open(player, locator, key, key.getAmountPerUnit()));
    }

    static void openCraftingList(ServerPlayer player, TerminalSession session, TerminalConnection connection,
                                 ObjectList<ICraftingGridMenu.AutoCraftEntry> missing) {
        begin(player, session, connection, locator -> CraftConfirmMenu.openWithCraftingList(
                locator.locate(player, IActionHost.class), player, locator, missing));
    }

    private static void begin(ServerPlayer player, TerminalSession session, TerminalConnection connection, Consumer<TerminalAeMenuLocator> open) {
        if (VISITS.containsKey(player.getUUID())) throw new IllegalStateException("An AE2 menu visit is already active");
        Visit visit = new Visit(player, session, connection);
        VISITS.put(player.getUUID(), visit);
        session.nativeVisitStarted();
        send(visit, "suspend");
        // ServerPlayer.openMenu normally removes the previous menu. The parent is retained for the return trip.
        player.containerMenu = player.inventoryMenu;
        try {
            var locator = new TerminalAeMenuLocator(session.id(), connection.frequency());
            open.accept(locator);
            if (!suspended(player, session)) throw new IllegalStateException("AE2 could not open the terminal submenu");
        } catch (RuntimeException failure) {
            VISITS.remove(player.getUUID());
            if (player.containerMenu != player.inventoryMenu) player.containerMenu.removed(player);
            player.containerMenu = visit.parent;
            session.nativeReturned(player);
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
        if (visit.session != session || !TerminalAeMenuLocator.matches(player.containerMenu, session.id())) return false;
        if (visit.nativeMenu != player.containerMenu) {
            visit.nativeMenu = player.containerMenu;
            session.nativeOpened(player.containerMenu.containerId);
        }
        return true;
    }

    static void tick(ServerPlayer player, TerminalSession session, @Nullable TerminalConnection connection) {
        if (!VISITS.containsKey(player.getUUID())) return;
        Visit visit = VISITS.get(player.getUUID());
        if (!suspended(player, session)) {
            abandon(player.getUUID(), visit.session.id());
        } else if (!parentValid(visit)) {
            abandon(player.getUUID(), visit.session.id());
        } else if (connection == null || connection.grid() != visit.connection.grid() || connection.frequency() != visit.connection.frequency()) {
            resume(player);
        }
    }

    static void restoreClosed(ServerPlayer player, TerminalSession session) {
        if (!VISITS.containsKey(player.getUUID())) return;
        Visit visit = VISITS.get(player.getUUID());
        if (visit.session != session || visit.nativeMenu == null || player.containerMenu != player.inventoryMenu) return;
        if (!parentValid(visit)) {
            abandon(player.getUUID(), session.id());
            return;
        }
        // AE2 may close an invalid plan before our player tick. Its removed/Close lifecycle has already run.
        VISITS.remove(player.getUUID());
        restoreParent(visit);
    }

    public static void resume(ServerPlayer player) {
        if (!VISITS.containsKey(player.getUUID())) return;
        Visit visit = VISITS.get(player.getUUID());
        if (!suspended(player, visit.session) || !parentValid(visit)) {
            abandon(player.getUUID(), visit.session.id());
            return;
        }
        VISITS.remove(player.getUUID());
        AbstractContainerMenu nativeMenu = player.containerMenu;
        nativeMenu.removed(player);
        NeoForge.EVENT_BUS.post(new PlayerContainerEvent.Close(player, nativeMenu));
        restoreParent(visit);
    }

    private static void restoreParent(Visit visit) {
        visit.player.containerMenu = visit.parent;
        visit.session.nativeReturned(visit.player);
        send(visit, "resume");
        visit.parent.sendAllDataToRemote();
    }

    static void abandon(UUID playerId, UUID sessionId) {
        if (!VISITS.containsKey(playerId)) return;
        Visit visit = VISITS.get(playerId);
        if (!visit.session.id().equals(sessionId)) return;
        VISITS.remove(playerId);
        if (TerminalAeMenuLocator.matches(visit.player.containerMenu, sessionId)) visit.player.closeContainer();
        visit.parent.removed(visit.player);
        NeoForge.EVENT_BUS.post(new PlayerContainerEvent.Close(visit.player, visit.parent));
        send(visit, "abort");
    }

    private static boolean parentValid(Visit visit) {
        // AEBaseMenu normally checks these identities in broadcastChanges, which the suspended parent no longer runs.
        // Validate the public host without broadcasting packets into the native submenu.
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
        PacketDistributor.sendToPlayer(visit.player, new TerminalNavigationPayload(visit.parent.containerId, visit.session.id(), phase));
    }

    private static final class Visit {

        private final ServerPlayer player;
        private final TerminalSession session;
        private final AbstractContainerMenu parent;
        private final TerminalConnection connection;
        private @Nullable AbstractContainerMenu nativeMenu;

        private Visit(ServerPlayer player, TerminalSession session, TerminalConnection connection) {
            this.player = player;
            this.session = session;
            this.parent = player.containerMenu;
            this.connection = connection;
        }
    }
}
