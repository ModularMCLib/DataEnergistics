package com.fish_dan_.data_energistics.api.crafting.reusable.dispatch;

import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.SlotStack;

import appeng.api.stacks.GenericStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.util.Optional;
import java.util.UUID;

/** Immutable server observation. Held resources belong only to this owner/target, never to global AE availability. */
public record ReusableCraftingSessionView(UUID sessionId, UUID jobId, String cpuOwner, String targetIdentity,
                                          State state, long revision, long accepted, long completed, long cancelled,
                                          ObjectList<SlotStack> heldTools, Optional<String> failure) {

    /** Returns an immutable FastUtil view of held tools. */
    public ObjectList<SlotStack> heldToolsFast() {
        return ObjectLists.unmodifiable(new ObjectArrayList<>(heldTools));
    }

    public ReusableCraftingSessionView {
        heldTools = new ObjectImmutableList<>(heldTools);
        if (revision < 0L || completed < 0L || cancelled < 0L ||
                completed > accepted || cancelled > accepted - completed) {
            throw new IllegalArgumentException("Reusable session counters are inconsistent");
        }
    }

    /** Offline/unreachable is absence of an observation, not a transition to CLOSED. */
    public enum State {
        OPEN,
        CLOSING,
        FAULTED,
        RETURN_PENDING,
        CLOSED
    }

    /** Exact sequence progress used to reconcile a repeated append without repeating work accounting. */
    public record AppendReceipt(long sequence, long accepted, long completed, long cancelled) {

        public AppendReceipt {
            if (sequence < 0L || accepted <= 0L || completed < 0L || cancelled < 0L ||
                    completed > accepted || cancelled > accepted - completed) {
                throw new IllegalArgumentException("Reusable append receipt is inconsistent");
            }
        }
    }

    /**
     * Directed actual-asset return. These values describe the sender's authoritative outbox, not permission to
     * synthesize items. Machine-owned units are released separately; exhausted units are explicitly accounted for.
     */
    public record Settlement(UUID sessionId, UUID jobId, String cpuOwner, String targetIdentity, long sequence,
                             ObjectList<GenericStack> returnedAssets, ObjectList<SlotStack> releasedMachineTools,
                             long exhaustedTools, ObjectList<AppendReceipt> receipts,
                             Optional<String> failure) {

        /** Returns an immutable FastUtil view of returned assets. */
        public ObjectList<GenericStack> returnedAssetsFast() {
            return ObjectLists.unmodifiable(new ObjectArrayList<>(returnedAssets));
        }

        /** Returns an immutable FastUtil view of released machine tools. */
        public ObjectList<SlotStack> releasedMachineToolsFast() {
            return ObjectLists.unmodifiable(new ObjectArrayList<>(releasedMachineTools));
        }

        /** Returns an immutable FastUtil view of append receipts. */
        public ObjectList<AppendReceipt> receiptsFast() {
            return ObjectLists.unmodifiable(new ObjectArrayList<>(receipts));
        }

        public Settlement {
            if (sequence < 0L || exhaustedTools < 0L) {
                throw new IllegalArgumentException("Reusable settlement counters must not be negative");
            }
            returnedAssets = new ObjectImmutableList<>(returnedAssets);
            releasedMachineTools = new ObjectImmutableList<>(releasedMachineTools);
            receipts = new ObjectImmutableList<>(receipts);
            for (GenericStack stack : returnedAssets) {
                if (stack.amount() <= 0L) {
                    throw new IllegalArgumentException("Returned physical assets must have positive quantities");
                }
            }
        }
    }
}
