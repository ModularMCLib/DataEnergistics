package com.fish_dan_.data_energistics.common.terminal.player.session;

import com.fish_dan_.data_energistics.api.research.ResearchAccess;
import com.fish_dan_.data_energistics.common.anvil.DigitalAnvilUpgrade;
import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;
import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalQuantumAccess;
import com.fish_dan_.data_energistics.common.terminal.player.crafting.TerminalCraftingRequest;
import com.fish_dan_.data_energistics.common.terminal.player.magnet.TerminalMagnetSettings;
import com.fish_dan_.data_energistics.common.terminal.player.persistence.TerminalPlayerState;
import com.fish_dan_.data_energistics.common.terminal.player.storage.TerminalNetworkStorage;
import com.fish_dan_.data_energistics.common.terminal.player.storage.TerminalStorageView;
import com.fish_dan_.data_energistics.common.terminal.player.transfer.TerminalContainerTransfer;
import com.fish_dan_.data_energistics.common.terminal.player.transfer.TerminalInputTargets;
import com.fish_dan_.data_energistics.common.terminal.player.workstation.TerminalSlotActions;
import com.fish_dan_.data_energistics.common.terminal.player.workstation.TerminalWorkspace;
import com.fish_dan_.data_energistics.common.terminal.player.workstation.TerminalWorkstation;
import com.fish_dan_.data_energistics.data.research.PlayerTerminalResearch;
import com.fish_dan_.data_energistics.network.terminal.TerminalRequestPayload;
import com.fish_dan_.data_energistics.network.terminal.TerminalSnapshotPayload;

import appeng.api.stacks.AEKey;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.PacketDistributor;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Server overlay endpoint: host-menu identity, per-open token and an ordered sequence reject stale mutations.
 */
public final class TerminalSession {

    private final AbstractContainerMenu menu;
    private final UUID id;
    private final UUID owner;
    private final TerminalStorageView storage = new TerminalStorageView();
    private final TerminalCraftingRequest crafting = new TerminalCraftingRequest();
    private long sequence;
    private int lastRequest;
    private int cpuMenuId = -1;
    private boolean visible = true;
    private boolean closed;
    private String message = "";
    private @Nullable CompoundTag previousSnapshot;

    public TerminalSession(ServerPlayer player, UUID id) {
        this.menu = player.containerMenu;
        this.id = id;
        this.owner = player.getUUID();
        this.lastRequest = player.server.getTickCount();
    }

    public boolean visible() {
        return visible;
    }

    UUID id() {
        return id;
    }

    void cpuOpened(int containerId) {
        cpuMenuId = containerId;
    }

    void cpuReturned(ServerPlayer player) {
        lastRequest = player.server.getTickCount();
    }

    public boolean valid(ServerPlayer player) {
        boolean suspended = TerminalCpuNavigation.suspended(player, this);
        return !closed && (player.containerMenu == menu || suspended) && menu.stillValid(player) && player.isAlive() &&
                (suspended || player.server.getTickCount() - lastRequest < 100) && ResearchAccess.hasPlayerUpgrade(player, PlayerTerminalResearch.TERMINAL);
    }

    public boolean accepts(ServerPlayer player, TerminalRequestPayload request) {
        if (player.containerMenu != menu || !valid(player) || !id.equals(request.sessionId()) || request.containerId() != menu.containerId || request.sequence() != sequence + 1)
            return false;
        sequence = request.sequence();
        lastRequest = player.server.getTickCount();
        return true;
    }

    boolean acceptsCpuReturn(ServerPlayer player, TerminalRequestPayload request) {
        // A native Back packet may already have resumed the host before a queued Escape arrives.
        // Consume that one valid sequence as well, keeping later overlay mutations ordered.
        if (!valid(player) || !id.equals(request.sessionId()) || request.containerId() != cpuMenuId || request.sequence() != sequence + 1)
            return false;
        sequence = request.sequence();
        lastRequest = player.server.getTickCount();
        return true;
    }

    boolean discardDuringCpuVisit(ServerPlayer player, TerminalRequestPayload request) {
        // Host input can already be in flight when the server opens the CPU screen. Acknowledge its
        // sequence without applying it to a suspended menu, so the following return stays ordered.
        if (!TerminalCpuNavigation.suspended(player, this) || !valid(player) || !id.equals(request.sessionId()) ||
                request.containerId() != menu.containerId || request.sequence() != sequence + 1)
            return false;
        sequence = request.sequence();
        lastRequest = player.server.getTickCount();
        return true;
    }

    public void action(ServerPlayer player, TerminalPlayerRuntime runtime, String action, CompoundTag data) {
        TerminalPlayerState state = runtime.state();
        message = "";
        switch (action) {
            case "refresh" -> {}
            case "close" -> close();
            case "visible" -> visible = data.getBoolean("value");
            case "workspace" -> state.workspace(TerminalWorkspace.byId(data.getString("id")));
            case "toggle_encoding" -> {
                if (state.workspace().supportsEncoding()) state.work().encoding(!state.work().encoding());
            }
            case "substitutes" -> state.work().substitutes(!state.work().substitutes());
            case "fluid_substitutes" -> state.work().fluidSubstitutes(!state.work().fluidSubstitutes());
            case "slot" -> TerminalSlotActions.physical(player, state, data);
            case "ghost" -> {
                if (data.getString("inventory").equals("pickup") || data.getString("inventory").equals("insert"))
                    requireMagnet(player);
                TerminalSlotActions.ghost(player, state, data);
            }
            case "recipe" -> {
                int index = data.getInt("index");
                if (state.workspace() != TerminalWorkspace.STONECUTTING || index < 0 ||
                        index >= TerminalWorkstation.stonecuttingRecipes(player, state.work(), state.work().encoding()).size())
                    return;
                state.work().recipeIndex(index);
            }
            case "name" -> {
                String name = StringUtil.filterText(data.getString("value"));
                if (name.length() <= 50) state.work(TerminalWorkspace.ANVIL).name(name);
            }
            case "boost" -> {
                if (ResearchAccess.hasPlayerUpgrade(player, PlayerTerminalResearch.anvilUpgradeId(DigitalAnvilUpgrade.UNRESTRICTED_LEVELS)))
                    state.work(TerminalWorkspace.ANVIL).boost(data.getInt("value"));
            }
            case "result" -> {
                TerminalConnection connection = state.workspace() == TerminalWorkspace.ANVIL ? runtime.connection() : null;
                message = TerminalWorkstation.take(player, state, connection, data.getBoolean("shift"));
            }
            case "frequency" -> {
                long value = data.getLong("value");
                if (TerminalQuantumAccess.frequencies(player).contains(value)) {
                    crafting.cancel();
                    state.frequency(value);
                }
            }
            case "magnet_mode" -> {
                requireMagnet(player);
                state.magnet().mode(TerminalMagnetSettings.Mode.valueOf(data.getString("value")));
            }
            case "magnet_restock" -> {
                requireMagnet(player);
                state.magnet().restock(!state.magnet().restock());
            }
            case "pickup_whitelist" -> {
                requireMagnet(player);
                state.magnet().pickupWhitelist(!state.magnet().pickupWhitelist());
            }
            case "insert_whitelist" -> {
                requireMagnet(player);
                state.magnet().insertWhitelist(!state.magnet().insertWhitelist());
            }
            case "filter_copy_up" -> {
                requireMagnet(player);
                state.magnet().pickup.copyFrom(state.magnet().insert);
            }
            case "filter_copy_down" -> {
                requireMagnet(player);
                state.magnet().insert.copyFrom(state.magnet().pickup);
            }
            case "filter_swap" -> {
                requireMagnet(player);
                state.magnet().pickup.swap(state.magnet().insert);
            }
            case "craft_cancel" -> crafting.cancel();
            default -> networkAction(player, runtime, action, data);
        }
        if (player.containerMenu == menu) menu.broadcastChanges();
    }

    private void networkAction(ServerPlayer player, TerminalPlayerRuntime runtime, String action, CompoundTag data) {
        TerminalConnection connection = runtime.connection();
        if (connection == null) {
            message = "offline";
            return;
        }
        switch (action) {
            case "storage" -> {
                if (!data.getBoolean("craft"))
                    TerminalNetworkStorage.click(player, connection, runtime.state(), key(player, data), data.getInt("button"), data.getBoolean("shift"));
            }
            case "insert" -> TerminalNetworkStorage.insertCursor(player, connection, Integer.MAX_VALUE);
            case "transfer_processing" -> {
                if (runtime.state().workspace() == TerminalWorkspace.PROCESSING && !runtime.state().work().encoding())
                    message = TerminalContainerTransfer.transfer(player, connection, runtime.state());
            }
            case "craft_plan" -> crafting.begin(player, connection, key(player, data), data.getLong("amount"));
            case "craft_start" -> message = crafting.start(connection, data.getInt("cpu"));
            case "cpu_list" -> TerminalCpuNavigation.open(player, this, connection);
            default -> throw new IllegalArgumentException("Unknown player terminal action " + action);
        }
    }

    private static AEKey key(ServerPlayer player, CompoundTag data) {
        AEKey key = AEKey.fromTagGeneric(player.registryAccess(), data.getCompound("key"));
        if (key == null) throw new IllegalArgumentException("Unknown terminal resource");
        return key;
    }

    private static void requireMagnet(ServerPlayer player) {
        if (!ResearchAccess.hasPlayerUpgrade(player, PlayerTerminalResearch.MAGNET))
            throw new IllegalArgumentException("Player magnet research is locked");
    }

    public void send(ServerPlayer player, TerminalPlayerRuntime runtime, @Nullable TerminalConnection connection) {
        if (closed || TerminalCpuNavigation.suspended(player, this)) return;
        TerminalPlayerState state = runtime.state();
        CompoundTag snapshot = new CompoundTag();
        snapshot.putBoolean("enabled", true);
        snapshot.putBoolean("connected", connection != null);
        snapshot.putString("reason", connection == null ? "offline" : "online");
        snapshot.putString("message", message);
        snapshot.putString("workspace", state.workspace().id());
        snapshot.putBoolean("encoding", state.work().encoding());
        snapshot.putBoolean("substitutes", state.work().substitutes());
        snapshot.putBoolean("fluid_substitutes", state.work().fluidSubstitutes());
        if (state.frequency() != null) snapshot.putLong("frequency", state.frequency());
        snapshot.putLongArray("frequencies", TerminalQuantumAccess.frequencies(player).toLongArray());
        snapshot.putBoolean("magnet_unlocked", ResearchAccess.hasPlayerUpgrade(player, PlayerTerminalResearch.MAGNET));
        snapshot.put("magnet", state.magnet().save(player.registryAccess()));
        snapshot.put("work", state.work().save(player.registryAccess()));
        snapshot.put("craft", crafting.snapshot(player, connection));
        snapshot.putBoolean("transfer_supported", !TerminalInputTargets.resolve(player, menu).isEmpty());
        storage.snapshot(snapshot, connection, player.registryAccess());
        TerminalWorkstation.snapshot(player, state, snapshot);
        snapshot.putLong("sequence", sequence);
        if (!snapshot.equals(previousSnapshot)) {
            PacketDistributor.sendToPlayer(player, new TerminalSnapshotPayload(menu.containerId, id, sequence, snapshot));
            previousSnapshot = snapshot;
        }
    }

    public void close() {
        if (closed) return;
        closed = true;
        crafting.cancel();
        TerminalCpuNavigation.abandon(owner, id);
    }

    void failure(ServerPlayer player) {
        CompoundTag fault = new CompoundTag();
        fault.putBoolean("enabled", true);
        fault.putString("reason", "fault");
        PacketDistributor.sendToPlayer(player, new TerminalSnapshotPayload(menu.containerId, id, sequence, fault));
    }
}
