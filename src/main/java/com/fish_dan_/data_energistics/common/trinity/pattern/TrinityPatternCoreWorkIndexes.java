package com.fish_dan_.data_energistics.common.trinity.pattern;

import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.TrinityReusableSlot;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Maintains sparse queue and pending-output indexes for one Trinity pattern core.
 *
 * <p>
 * The core owns pattern publication and persistence. This value owns only derived work membership, so every
 * mutation path uses the same index update operation and cannot accidentally update one host index without the other.
 * </p>
 */
final class TrinityPatternCoreWorkIndexes {

    private IntAVLTreeSet queuedSlots = new IntAVLTreeSet();
    private IntAVLTreeSet pendingOutputSlots = new IntAVLTreeSet();
    private Object2ObjectMap<UUID, IntAVLTreeSet> workingSlotsByHost = new Object2ObjectOpenHashMap<>();
    private Object2ObjectMap<UUID, IntAVLTreeSet> pendingOutputSlotsByHost = new Object2ObjectOpenHashMap<>();

    IntAVLTreeSet queuedSlots() {
        return this.queuedSlots;
    }

    IntAVLTreeSet pendingOutputSlots() {
        return this.pendingOutputSlots;
    }

    IntList pendingOutputSlots(UUID hostId) {
        IntAVLTreeSet slots = this.pendingOutputSlotsByHost.get(hostId);
        return slots == null ? IntList.of() : IntList.of(slots.toIntArray());
    }

    IntList workingSlots(UUID hostId) {
        IntAVLTreeSet slots = this.workingSlotsByHost.get(hostId);
        return slots == null ? IntList.of() : IntList.of(slots.toIntArray());
    }

    boolean isSlotWorking(UUID hostId, int slot) {
        IntAVLTreeSet slots = this.workingSlotsByHost.get(hostId);
        return slots != null && slots.contains(slot);
    }

    boolean hasWork() {
        return !this.workingSlotsByHost.isEmpty();
    }

    boolean hasWork(UUID hostId) {
        return this.workingSlotsByHost.containsKey(hostId);
    }

    IntAVLTreeSet persistentSlots(IntAVLTreeSet occupiedPatternSlots) {
        IntAVLTreeSet persistent = new IntAVLTreeSet(occupiedPatternSlots);
        persistent.addAll(this.queuedSlots);
        persistent.addAll(this.pendingOutputSlots);
        return persistent;
    }

    void updateSlot(int slot,
                    ObjectList<TrinityPatternSlot> slots,
                    Int2ObjectMap<TrinityReusableSlot> reusableSlots,
                    Consumer<TrinityPatternSlot.Change> changeListener) {
        ObjectSet<UUID> previousWorkHosts = removeIndexedSlot(this.workingSlotsByHost, slot);
        TrinityPatternSlot patternSlot = slots.get(slot);
        if (patternSlot.hasQueuedWork()) {
            this.queuedSlots.add(slot);
        } else {
            this.queuedSlots.remove(slot);
        }
        removeIndexedSlot(this.pendingOutputSlotsByHost, slot);
        if (patternSlot.hasPendingOutputs()) {
            this.pendingOutputSlots.add(slot);
            for (UUID hostId : patternSlot.pendingOutputHostIds()) {
                this.pendingOutputSlotsByHost.computeIfAbsent(hostId, ignored -> new IntAVLTreeSet()).add(slot);
            }
        } else {
            this.pendingOutputSlots.remove(slot);
        }
        ObjectSet<UUID> currentWorkHosts = new ObjectOpenHashSet<>(patternSlot.workHostIds());
        TrinityReusableSlot reusable = reusableSlots.get(slot);
        if (reusable != null && reusable.hasWork()) {
            currentWorkHosts.add(reusable.route().hostId());
        }
        for (UUID hostId : currentWorkHosts) {
            this.workingSlotsByHost.computeIfAbsent(hostId, ignored -> new IntAVLTreeSet()).add(slot);
        }
        if (!previousWorkHosts.equals(currentWorkHosts)) {
            changeListener.accept(new TrinityPatternSlot.Change(slot, TrinityPatternSlot.ChangeKind.WORK));
        }
    }

    Snapshot snapshot(Iterable<TrinityPatternSlot> slots,
                      Int2ObjectMap<TrinityReusableSlot> reusableSlots) {
        IntAVLTreeSet queued = new IntAVLTreeSet();
        IntAVLTreeSet pending = new IntAVLTreeSet();
        Object2ObjectOpenHashMap<UUID, IntAVLTreeSet> workingByHost = new Object2ObjectOpenHashMap<>();
        Object2ObjectOpenHashMap<UUID, IntAVLTreeSet> pendingByHost = new Object2ObjectOpenHashMap<>();
        for (TrinityPatternSlot patternSlot : slots) {
            int slot = patternSlot.index();
            if (patternSlot.hasQueuedWork()) {
                queued.add(slot);
            }
            if (patternSlot.hasPendingOutputs()) {
                pending.add(slot);
                for (UUID hostId : patternSlot.pendingOutputHostIds()) {
                    pendingByHost.computeIfAbsent(hostId, ignored -> new IntAVLTreeSet()).add(slot);
                }
            }
            for (UUID hostId : patternSlot.workHostIds()) {
                workingByHost.computeIfAbsent(hostId, ignored -> new IntAVLTreeSet()).add(slot);
            }
        }
        for (TrinityReusableSlot reusable : reusableSlots.values()) {
            if (reusable.hasWork()) {
                workingByHost.computeIfAbsent(reusable.route().hostId(), ignored -> new IntAVLTreeSet())
                        .add(reusable.route().slot());
            }
        }
        return new Snapshot(queued, pending, workingByHost, pendingByHost);
    }

    void apply(Snapshot snapshot) {
        this.queuedSlots = snapshot.queuedSlots();
        this.pendingOutputSlots = snapshot.pendingOutputSlots();
        this.workingSlotsByHost = snapshot.workingSlotsByHost();
        this.pendingOutputSlotsByHost = snapshot.pendingOutputSlotsByHost();
    }

    private static ObjectSet<UUID> removeIndexedSlot(Object2ObjectMap<UUID, IntAVLTreeSet> index, int slot) {
        ObjectOpenHashSet<UUID> removedHosts = new ObjectOpenHashSet<>();
        index.entrySet().removeIf(entry -> {
            if (!entry.getValue().remove(slot)) {
                return false;
            }
            removedHosts.add(entry.getKey());
            return entry.getValue().isEmpty();
        });
        return ObjectSets.unmodifiable(new ObjectOpenHashSet<>(removedHosts));
    }

    record Snapshot(IntAVLTreeSet queuedSlots,
                    IntAVLTreeSet pendingOutputSlots,
                    Object2ObjectMap<UUID, IntAVLTreeSet> workingSlotsByHost,
                    Object2ObjectMap<UUID, IntAVLTreeSet> pendingOutputSlotsByHost) {}
}
