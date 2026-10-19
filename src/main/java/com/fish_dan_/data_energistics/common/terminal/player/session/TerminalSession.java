package com.fish_dan_.data_energistics.common.terminal.player.session;

import com.fish_dan_.data_energistics.api.crafting.matching.ProcessingMatchMode;
import com.fish_dan_.data_energistics.api.research.ResearchAccess;
import com.fish_dan_.data_energistics.common.anvil.DigitalAnvilUpgrade;
import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;
import com.fish_dan_.data_energistics.common.terminal.player.encoding.TerminalPatternUpload;
import com.fish_dan_.data_energistics.common.terminal.player.encoding.TerminalRecipeTransfer;
import com.fish_dan_.data_energistics.common.terminal.player.magnet.TerminalMagnetSettings;
import com.fish_dan_.data_energistics.common.terminal.player.persistence.TerminalPlayerState;
import com.fish_dan_.data_energistics.common.terminal.player.storage.TerminalNetworkStorage;
import com.fish_dan_.data_energistics.common.terminal.player.storage.TerminalStorageView;
import com.fish_dan_.data_energistics.common.terminal.player.transfer.TerminalContainerTransfer;
import com.fish_dan_.data_energistics.common.terminal.player.transfer.TerminalInputTargets;
import com.fish_dan_.data_energistics.common.terminal.player.workstation.TerminalPatternEncoding;
import com.fish_dan_.data_energistics.common.terminal.player.workstation.TerminalSlotActions;
import com.fish_dan_.data_energistics.common.terminal.player.workstation.TerminalWorkspace;
import com.fish_dan_.data_energistics.common.terminal.player.workstation.TerminalWorkstation;
import com.fish_dan_.data_energistics.data.research.PlayerTerminalResearch;
import com.fish_dan_.data_energistics.network.terminal.TerminalRequestPayload;
import com.fish_dan_.data_energistics.network.terminal.TerminalSnapshotPayload;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.PacketDistributor;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
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
    private final TerminalPatternUpload upload = new TerminalPatternUpload();
    private long sequence;
    private int lastRequest;
    private final IntSet nativeMenuIds = new IntOpenHashSet();
    private boolean visible = true;
    private boolean closed;
    private String message = "";
    private String lastAction = "";
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

    void nativeOpened(int containerId) {
        nativeMenuIds.add(containerId);
    }

    void nativeVisitStarted() {
        nativeMenuIds.clear();
    }

    void nativeReturned(ServerPlayer player) {
        lastRequest = player.server.getTickCount();
    }

    public boolean valid(ServerPlayer player) {
        boolean suspended = TerminalAeNavigation.suspended(player, this);
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

    boolean acceptsNativeReturn(ServerPlayer player, TerminalRequestPayload request) {
        // A native Back packet may already have resumed the host before a queued Escape arrives.
        // Consume that one valid sequence as well, keeping later overlay mutations ordered.
        if (!valid(player) || !id.equals(request.sessionId()) || !nativeMenuIds.contains(request.containerId()) || request.sequence() != sequence + 1)
            return false;
        sequence = request.sequence();
        lastRequest = player.server.getTickCount();
        return true;
    }

    boolean discardDuringNativeVisit(ServerPlayer player, TerminalRequestPayload request) {
        // Host input can already be in flight when the server opens a native submenu. Acknowledge its
        // sequence without applying it to a suspended menu, so the following return stays ordered.
        if (!TerminalAeNavigation.suspended(player, this) || !valid(player) || !id.equals(request.sessionId()) ||
                request.containerId() != menu.containerId || request.sequence() != sequence + 1)
            return false;
        sequence = request.sequence();
        lastRequest = player.server.getTickCount();
        return true;
    }

    public void action(ServerPlayer player, TerminalPlayerRuntime runtime, String action, CompoundTag data) {
        TerminalPlayerState state = runtime.state();
        message = "";
        lastAction = action;
        switch (action) {
            case "refresh" -> {}
            case "close" -> close();
            case "visible" -> visible = data.getBoolean("value");
            case "workspace" -> state.workspace(TerminalWorkspace.byId(data.getString("id")));
            case "pin_rows" -> state.pins().rows(data.getInt("rows"));
            case "pin" -> {
                int button = data.getInt("button");
                if (button < 0 || button > 1) throw new IllegalArgumentException("Invalid terminal pin button");
                GenericStack stack = data.contains("stack", Tag.TAG_COMPOUND) ? GenericStack.readTag(player.registryAccess(), data.getCompound("stack")) :
                        GenericStack.fromItemStack(menu.getCarried());
                if (stack != null) state.pins().set(data.getInt("index"), stack.what());
                else if (button == 1 && menu.getCarried().isEmpty()) state.pins().set(data.getInt("index"), null);
            }
            case "encode" -> {
                if (state.workspace().supportsEncoding()) message = TerminalPatternEncoding.commit(player, state, runtime.connection());
            }
            case "substitutes" -> {
                if (state.workspace() == TerminalWorkspace.CRAFTING || state.workspace() == TerminalWorkspace.SMITHING)
                    state.work().substitutes(!state.work().substitutes());
            }
            case "fluid_substitutes" -> {
                if (state.workspace() == TerminalWorkspace.CRAFTING)
                    state.work().fluidSubstitutes(!state.work().fluidSubstitutes());
            }
            case "record_type" -> {
                if (state.workspace() == TerminalWorkspace.PROCESSING)
                    state.work().recordRecipeType(!state.work().recordRecipeType());
            }
            case "recipe_transfer" -> message = TerminalRecipeTransfer.apply(player, state, data);
            case "processing_match" -> {
                if (state.workspace() != TerminalWorkspace.PROCESSING)
                    throw new IllegalArgumentException("Matching is only configurable for processing patterns");
                String group = data.getString("inventory");
                if (!group.equals("inputs") && !group.equals("outputs")) throw new IllegalArgumentException("Invalid processing match inventory");
                boolean output = group.equals("outputs");
                var inventory = output ? state.work().patternOutputs : state.work().patternInputs;
                int index = data.getInt("index");
                int mode = data.getInt("mode");
                if (index < 0 || index >= inventory.size() || mode < 0 || mode >= ProcessingMatchMode.values().length)
                    throw new IllegalArgumentException("Invalid processing match request");
                var stack = inventory.get(index);
                if (stack != null && stack.what() instanceof AEItemKey && stack.what().equals(key(player, data)))
                    state.work().matching.set(output, index, ProcessingMatchMode.values()[mode]);
            }
            case "slot" -> TerminalSlotActions.physical(player, state, data);
            case "ghost" -> {
                if (data.getString("inventory").equals("pickup") || data.getString("inventory").equals("insert"))
                    requireMagnet(player);
                message = TerminalSlotActions.ghost(player, state, data);
            }
            case "recipe", "cut" -> {
                int index = data.getInt("index");
                if (state.workspace() != TerminalWorkspace.STONECUTTING || index < 0 ||
                        index >= TerminalWorkstation.stonecuttingRecipes(player, state.work(), true).size())
                    return;
                state.work().recipeIndex(index);
                if (action.equals("cut")) message = TerminalWorkstation.take(player, state, null, false);
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
            case "magnet_mode" -> {
                requireMagnet(player);
                state.magnet().mode(TerminalMagnetSettings.Mode.valueOf(data.getString("value")));
            }
            case "magnet_restock" -> {
                requireMagnet(player);
                state.magnet().restock(!state.magnet().restock());
            }
            case "magnet_pick_block" -> {
                requireMagnet(player);
                state.magnet().pickBlock(!state.magnet().pickBlock());
            }
            case "magnet_craft_if_missing" -> {
                requireMagnet(player);
                state.magnet().craftIfMissing(!state.magnet().craftIfMissing());
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
            case "storage" -> TerminalNetworkStorage.click(player, connection, runtime.state(), key(player, data), data.getInt("button"), data.getBoolean("shift"));
            case "inventory_insert" -> {
                if (visible) TerminalNetworkStorage.insertInventorySlot(player, connection, data.getInt("slot"), key(player, data));
            }
            case "transfer_processing" -> {
                if (runtime.state().workspace() == TerminalWorkspace.PROCESSING)
                    message = TerminalContainerTransfer.transfer(player, connection, runtime.state());
            }
            case "craft_request" -> TerminalAeNavigation.openCrafting(player, this, connection, key(player, data));
            case "cpu_list" -> TerminalAeNavigation.open(player, this, connection);
            case "upload_refresh", "upload_pattern", "upload_rename", "upload_history" -> message = upload.action(player, runtime.state(), connection, action, data);
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
        if (closed || TerminalAeNavigation.suspended(player, this)) return;
        TerminalPlayerState state = runtime.state();
        CompoundTag snapshot = new CompoundTag();
        snapshot.putBoolean("enabled", true);
        snapshot.putBoolean("connected", connection != null);
        snapshot.putString("reason", connection == null ? "offline" : "online");
        snapshot.putString("message", message);
        snapshot.putString("action", lastAction);
        snapshot.putString("workspace", state.workspace().id());
        snapshot.putBoolean("substitutes", state.work().substitutes());
        snapshot.putBoolean("fluid_substitutes", state.work().fluidSubstitutes());
        snapshot.putBoolean("record_recipe_type", state.work().recordRecipeType());
        snapshot.putBoolean("magnet_unlocked", ResearchAccess.hasPlayerUpgrade(player, PlayerTerminalResearch.MAGNET));
        snapshot.put("magnet", state.magnet().save(player.registryAccess()));
        snapshot.put("pins", state.pins().save(player.registryAccess()));
        snapshot.put("work", state.work().save(player.registryAccess()));
        if (state.workspace().supportsEncoding()) {
            snapshot.put("encoding_preview", TerminalPatternEncoding.encode(player, state).saveOptional(player.registryAccess()));
            long blanks = connection == null ? 0 : connection.storage().getAvailableStacks().get(AEItemKey.of(AEItems.BLANK_PATTERN));
            snapshot.putLong("blank_patterns", blanks);
        }
        upload.refresh(player, state, connection, false);
        upload.snapshot(snapshot);
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
        upload.close();
        TerminalAeNavigation.abandon(owner, id);
    }

    void failure(ServerPlayer player) {
        previousSnapshot = null;
        CompoundTag fault = new CompoundTag();
        fault.putBoolean("enabled", true);
        fault.putString("reason", "fault");
        PacketDistributor.sendToPlayer(player, new TerminalSnapshotPayload(menu.containerId, id, sequence, fault));
    }
}
