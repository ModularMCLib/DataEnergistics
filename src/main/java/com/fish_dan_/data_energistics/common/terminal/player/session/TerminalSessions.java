package com.fish_dan_.data_energistics.common.terminal.player.session;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.research.ResearchAccess;
import com.fish_dan_.data_energistics.common.menu.terminal.TerminalAeMenuLocator;
import com.fish_dan_.data_energistics.common.network.packets.terminal.TerminalRequestPayload;
import com.fish_dan_.data_energistics.common.network.packets.terminal.TerminalSnapshotPayload;
import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;
import com.fish_dan_.data_energistics.common.terminal.player.magnet.TerminalItemUse;
import com.fish_dan_.data_energistics.common.terminal.player.magnet.TerminalPickBlock;
import com.fish_dan_.data_energistics.common.terminal.player.magnet.TerminalRestockSupply;
import com.fish_dan_.data_energistics.common.terminal.player.persistence.TerminalSavedData;
import com.fish_dan_.data_energistics.common.terminal.player.storage.TerminalNetworkStorage;
import com.fish_dan_.data_energistics.data.research.PlayerTerminalResearch;

import appeng.api.stacks.AEItemKey;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Server-thread lifecycle and top-level failure boundary for player terminal sessions and background magnets.
 */
public final class TerminalSessions {

    private static final Object2ObjectMap<UUID, TerminalPlayerRuntime> PLAYERS = new Object2ObjectOpenHashMap<>();

    public TerminalSessions() {
        TerminalAeMenuLocator.register();
    }

    private static TerminalPlayerRuntime runtime(ServerPlayer player) {
        return PLAYERS.computeIfAbsent(player.getUUID(), unused -> new TerminalPlayerRuntime(player, TerminalSavedData.get(player.server).player(player.getUUID())));
    }

    /** Captures real use inputs only for enabled survival terminals; inventory/menu moves never enter this scope. */
    public static @Nullable TerminalItemUse beginItemUse(ServerPlayer player) {
        if (!player.isAlive() || player.isCreative() || player.isSpectator() ||
                !ResearchAccess.hasPlayerUpgrade(player, PlayerTerminalResearch.TERMINAL) || !runtime(player).state().magnet().restock())
            return null;
        return new TerminalItemUse(player);
    }

    /** Completes the use on the server thread, charging ME for the observed consumption while access is still valid. */
    public static void finishItemUse(ServerPlayer player, @Nullable TerminalItemUse use) {
        if (use == null) return;
        try {
            TerminalPlayerRuntime runtime = runtime(player);
            if (!player.isAlive() || !runtime.state().magnet().restock() ||
                    !ResearchAccess.hasPlayerUpgrade(player, PlayerTerminalResearch.TERMINAL)) return;
            TerminalConnection connection = runtime.connection();
            if (connection != null) use.finish(player, connection);
        } catch (RuntimeException failure) {
            fail(player, "network item consumption", failure);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void finishedUsing(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player)
            event.setResultStack(payConsumedItems(player, event.getItem(), event.getResultStack()));
    }

    /** Server-thread payment shared by finished item use and real player-inventory capability extraction. */
    public static ItemStack payConsumedItems(ServerPlayer player, ItemStack previous, ItemStack current) {
        if (TerminalRestockSupply.consumed(previous, current) == 0 || player.isCreative() || player.isSpectator() || !player.isAlive() ||
                !ResearchAccess.hasPlayerUpgrade(player, PlayerTerminalResearch.TERMINAL)) return current;
        try {
            TerminalPlayerRuntime runtime = runtime(player);
            if (!runtime.state().magnet().restock()) return current;
            TerminalConnection connection = runtime.connection();
            return connection == null ? current : TerminalRestockSupply.pay(previous, current, connection);
        } catch (RuntimeException failure) {
            fail(player, "network inventory consumption", failure);
            return current;
        }
    }

    /** Server-thread AE hotkey action; unavailable terminal access leaves the original wireless action available. */
    public static boolean stowMainHand(Player actor) {
        if (!(actor instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator() ||
                player.containerMenu != player.inventoryMenu || !ResearchAccess.hasPlayerUpgrade(player, PlayerTerminalResearch.TERMINAL))
            return false;
        ItemStack stack = player.getMainHandItem();
        AEItemKey key = AEItemKey.of(stack);
        if (key == null || stack.isNotReplaceableByPickAction(player, player.getInventory().selected)) return false;
        try {
            TerminalPlayerRuntime runtime = runtime(player);
            TerminalConnection connection = runtime.connection();
            if (connection == null) return false;
            int slot = player.inventoryMenu.findSlot(player.getInventory(), player.getInventory().selected).orElseThrow();
            TerminalNetworkStorage.stowSlot(player, runtime.state(), connection, slot, key);
            player.inventoryMenu.broadcastChanges();
            // A connected terminal owns the gesture even when ME accepts only part of the stack.
            return true;
        } catch (RuntimeException failure) {
            fail(player, "stow main hand", failure);
            return true;
        }
    }

    /**
     * Server-thread validity check for AE2's pick-block crafting menu; unavailable access returns null.
     */
    public static @Nullable TerminalConnection pickBlockConnection(ServerPlayer player, long frequency) {
        if (!player.isAlive() || player.isSpectator() || !ResearchAccess.hasPlayerUpgrade(player, PlayerTerminalResearch.TERMINAL))
            return null;
        TerminalPlayerRuntime runtime = runtime(player);
        if (!runtime.state().magnet().pickBlock()) return null;
        TerminalConnection connection = runtime.connection();
        return connection != null && connection.frequency() == frequency ? connection : null;
    }

    /**
     * Validates the network request on the server thread and resolves the block's item in the player's current level.
     */
    public static void pickBlock(ServerPlayer player, BlockHitResult hit) {
        if (!player.isAlive() || player.isCreative() || player.isSpectator() || player.containerMenu != player.inventoryMenu ||
                !ResearchAccess.hasPlayerUpgrade(player, PlayerTerminalResearch.TERMINAL) ||
                !player.canInteractWithBlock(hit.getBlockPos(), 1) ||
                !player.level().getChunkSource().hasChunk(hit.getBlockPos().getX() >> 4, hit.getBlockPos().getZ() >> 4))
            return;
        try {
            TerminalPlayerRuntime runtime = runtime(player);
            if (!runtime.state().magnet().pickBlock()) return;
            ItemStack stack = player.level().getBlockState(hit.getBlockPos()).getCloneItemStack(hit, player.level(), hit.getBlockPos(), player);
            if (stack.isEmpty()) return;
            TerminalConnection connection = runtime.connection();
            if (connection != null)
                TerminalPickBlock.pick(player, connection, stack, runtime.state().magnet().craftIfMissing());
        } catch (RuntimeException failure) {
            fail(player, "pick block", failure);
        }
    }

    public static void handle(ServerPlayer player, TerminalRequestPayload request) {
        if (request.action().equals("native_return")) {
            if (player.isSpectator() || !PLAYERS.containsKey(player.getUUID())) return;
            try {
                TerminalPlayerRuntime runtime = PLAYERS.get(player.getUUID());
                TerminalSession session = runtime.session();
                if (session == null || !session.acceptsNativeReturn(player, request)) return;
                TerminalAeNavigation.resume(player);
                session.send(player, runtime, session.visible() ? runtime.connection() : null);
            } catch (RuntimeException failure) {
                fail(player, "native-menu return", failure);
            }
            return;
        }
        if (player.isSpectator()) return;
        if (PLAYERS.containsKey(player.getUUID())) {
            try {
                TerminalSession session = PLAYERS.get(player.getUUID()).session();
                if (session != null && session.discardDuringNativeVisit(player, request)) return;
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
        try {
            TerminalPlayerRuntime runtime = runtime(player);
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
                !ResearchAccess.hasPlayerUpgrade(player, PlayerTerminalResearch.TERMINAL) && !PLAYERS.containsKey(player.getUUID()))
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
        try {
            TerminalPlayerRuntime runtime = runtime(player);
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
        PLAYERS.values().forEach(TerminalPlayerRuntime::shutdown);
        PLAYERS.clear();
    }

    private static void remove(UUID player) {
        if (PLAYERS.containsKey(player)) PLAYERS.remove(player).shutdown();
    }

    private static void fail(ServerPlayer player, String operation, RuntimeException failure) {
        if (PLAYERS.containsKey(player.getUUID())) {
            TerminalPlayerRuntime runtime = PLAYERS.get(player.getUUID());
            if (runtime.session() != null) runtime.session().failure(player);
        }
        Data_Energistics.LOGGER.error("Player terminal operation failed for {} during {}", player.getUUID(), operation, failure);
    }
}
