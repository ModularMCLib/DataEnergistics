package com.fish_dan_.data_energistics.ae2.sanctum.connector;

import com.fish_dan_.data_energistics.ae2.sanctum.DataSanctumInterfaceInventory;
import com.fish_dan_.data_energistics.ae2.sanctum.DataSanctumLargeInterfaceHost;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorLink;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorPolicy;

import appeng.api.AECapabilities;
import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.me.storage.CompositeStorage;
import appeng.parts.automation.StackWorldBehaviors;

import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.jspecify.annotations.Nullable;

/** Bounded remote inventory transfers, independent of interface config markers and legacy adjacent pull settings. */
final class InterfaceRemoteTransfer {

    /** Maximum number of registered targets inspected by one interface tick. */
    private static final int LINKS_PER_TICK = 32;

    private InterfaceRemoteTransfer() {}

    static void tick(DataSanctumLargeInterfaceHost host, InterfaceRemoteLinks state) {
        if (!(host.getInterfaceLevel() instanceof ServerLevel level) || !state.isActive() || !state.flushReturn()) {
            return;
        }
        var links = state.bindings();
        if (links.isEmpty()) {
            return;
        }
        IActionSource actionSource = state.actionSource();
        var bySlot = new Int2ObjectLinkedOpenHashMap<IntArrayList>();
        for (int index = 0; index < links.size(); index++) {
            bySlot.computeIfAbsent(links.get(index).slot(), ignored -> new IntArrayList()).add(index);
        }
        var groups = new ObjectArrayList<>(bySlot.int2ObjectEntrySet());
        int groupStart = Math.floorMod(state.linkCursor(), groups.size());
        int budget = LINKS_PER_TICK;
        var config = (DataSanctumInterfaceInventory) host.getInterfaceLogic().getConfig();
        for (int groupOffset = 0; groupOffset < groups.size() && budget > 0; groupOffset++) {
            int groupIndex = (groupStart + groupOffset) % groups.size();
            var group = groups.get(groupIndex);
            var indices = group.getValue();
            boolean priority = config.getSlotPolicy(group.getIntKey()) == ConnectorPolicy.PRIORITY;
            int start = priority ? 0 : Math.floorMod(state.routeCursor(group.getIntKey()), indices.size());
            state.advanceLink((groupIndex + 1) % groups.size());
            for (int offset = 0; offset < indices.size() && budget > 0; offset++) {
                budget--;
                int localIndex = (start + offset) % indices.size();
                int linkIndex = indices.getInt(localIndex);
                var link = links.get(linkIndex);
                if (link.slot() >= state.slotCount() || link.position().equals(host.getInterfaceBlockPos()) ||
                        !level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(link.position().getX()), SectionPos.blockToSectionCoord(link.position().getZ()))) {
                    continue;
                }
                var generic = level.getCapability(AECapabilities.GENERIC_INTERNAL_INV, link.position(), link.side());
                var storage = generic == null ? externalStorage(level, host, link) : null;
                boolean moved = false;
                // Pull before pushing so BOTH never immediately removes the inputs placed by this tick.
                if (link.mode().supportsPull()) {
                    if (generic != null) {
                        moved = pullGeneric(host, state, linkIndex, generic, actionSource);
                    } else if (storage != null) {
                        moved = pullStorage(host, state, linkIndex, storage, actionSource);
                    }
                    if (!state.flushReturn()) {
                        return;
                    }
                }
                if (link.mode().supportsInput()) {
                    moved |= push(host, state, link, generic, storage, actionSource);
                    if (!state.flushReturn()) {
                        return;
                    }
                }
                state.advanceRoute(group.getIntKey(), (localIndex + 1) % indices.size());
                if (moved) break;
            }
        }
    }

    private static @Nullable MEStorage externalStorage(ServerLevel level, DataSanctumLargeInterfaceHost host,
                                                       ConnectorLink link) {
        var storage = level.getCapability(AECapabilities.ME_STORAGE, link.position(), link.side());
        if (storage != null) {
            return storage;
        }
        var wrappers = new Reference2ReferenceOpenHashMap<AEKeyType, MEStorage>();
        for (var entry : StackWorldBehaviors.createExternalStorageStrategies(level, link.position(), link.side()).entrySet()) {
            var wrapper = entry.getValue().createWrapper(false, host::saveChanges);
            if (wrapper != null) {
                wrappers.put(entry.getKey(), wrapper);
            }
        }
        return wrappers.isEmpty() ? null : new CompositeStorage(wrappers);
    }

    private static boolean push(DataSanctumLargeInterfaceHost host, InterfaceRemoteLinks state, ConnectorLink link,
                                @Nullable GenericInternalInventory generic, @Nullable MEStorage storage, IActionSource actionSource) {
        var source = host.getInterfaceLogic().getStorage();
        var stack = source.getStack(link.slot());
        if (stack == null || generic == null && storage == null) {
            return false;
        }
        // Do not impose an artificial batch size. The destination's simulated insert decides how much
        // the container can accept, while the source slot remains the upper bound.
        long offered = stack.amount();
        long accepted = generic != null ? insertGeneric(generic, stack.what(), offered, Actionable.SIMULATE) : storage.insert(stack.what(), offered, Actionable.SIMULATE, actionSource);
        if (accepted <= 0) {
            return false;
        }
        long extracted = source.extract(link.slot(), stack.what(), accepted, Actionable.MODULATE);
        if (extracted <= 0) {
            return false;
        }
        long inserted = generic != null ? insertGeneric(generic, stack.what(), extracted, Actionable.MODULATE) : storage.insert(stack.what(), extracted, Actionable.MODULATE, actionSource);
        long remainder = extracted - inserted;
        if (remainder > 0) {
            // A refused remainder is still owned; retain it even if the source slot's capacity changed during transfer.
            state.receive(new GenericStack(stack.what(), remainder));
        }
        return inserted > 0;
    }

    private static long insertGeneric(GenericInternalInventory inventory, AEKey key, long amount, Actionable mode) {
        if (!inventory.canInsert()) {
            return 0;
        }
        long inserted = 0;
        for (int slot = 0; slot < inventory.size() && inserted < amount; slot++) {
            inserted += inventory.insert(slot, key, amount - inserted, mode);
        }
        return inserted;
    }

    private static boolean pullGeneric(DataSanctumLargeInterfaceHost host, InterfaceRemoteLinks state, int linkIndex,
                                       GenericInternalInventory inventory, IActionSource actionSource) {
        if (!inventory.canExtract() || inventory.size() == 0) {
            return false;
        }
        boolean moved = false;
        int start = Math.floorMod(state.sourceCursor(linkIndex), inventory.size());
        int visited = inventory.size();
        for (int offset = 0; offset < visited; offset++) {
            int slot = (start + offset) % inventory.size();
            var key = inventory.getKey(slot);
            if (key == null) {
                continue;
            }
            long amount = host.getReturnInventory().insert(key, inventory.getAmount(slot), Actionable.SIMULATE, actionSource);
            if (amount > 0) {
                long extracted = inventory.extract(slot, key, amount, Actionable.MODULATE);
                if (extracted > 0) {
                    state.receive(new GenericStack(key, extracted));
                    moved = true;
                    if (!state.flushReturn()) {
                        visited = offset + 1;
                        break;
                    }
                }
            }
        }
        state.advanceSource(linkIndex, (start + visited) % inventory.size());
        return moved;
    }

    private static boolean pullStorage(DataSanctumLargeInterfaceHost host, InterfaceRemoteLinks state, int linkIndex,
                                       MEStorage storage, IActionSource actionSource) {
        var keys = new ObjectArrayList<AEKey>();
        var amounts = new Object2ObjectOpenHashMap<AEKey, Long>();
        for (var entry : storage.getAvailableStacks()) {
            keys.add(entry.getKey());
            amounts.put(entry.getKey(), entry.getLongValue());
        }
        if (keys.isEmpty()) {
            return false;
        }
        boolean moved = false;
        int start = Math.floorMod(state.sourceCursor(linkIndex), keys.size());
        int visited = keys.size();
        for (int offset = 0; offset < visited; offset++) {
            AEKey key = keys.get((start + offset) % keys.size());
            long available = amounts.getOrDefault(key, 0L);
            long amount = host.getReturnInventory().insert(key, available, Actionable.SIMULATE, actionSource);
            if (amount > 0) {
                long extracted = storage.extract(key, amount, Actionable.MODULATE, actionSource);
                if (extracted > 0) {
                    state.receive(new GenericStack(key, extracted));
                    moved = true;
                    if (!state.flushReturn()) {
                        visited = offset + 1;
                        break;
                    }
                }
            }
        }
        state.advanceSource(linkIndex, (start + visited) % keys.size());
        return moved;
    }
}
