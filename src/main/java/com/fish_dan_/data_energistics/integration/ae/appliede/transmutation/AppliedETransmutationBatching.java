package com.fish_dan_.data_energistics.integration.ae.appliede.transmutation;

import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingCapacity;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingRoutingMode;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingTarget;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import gripe._90.appliede.me.service.TransmutationPattern;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.OptionalLong;

/** Server-thread counted submissions into AppliedE's native pending-output queue. */
@NullMarked
public final class AppliedETransmutationBatching {

    private AppliedETransmutationBatching() {}

    /** Observes queue space without reserving outputs or extracting EMC. */
    public static ObjectList<CountedCraftingCapacity> captureCapacity(
                                                                      IManagedGridNode node, Object2LongMap<AEKey> outputs,
                                                                      IPatternDetails pattern, long requestedCount) {
        GenericStack output = transmutationOutput(pattern);
        if (output == null) return ObjectList.of();
        long capacity = Math.min(requestedCount, capacity(node, outputs, output));
        return ObjectList.of(new CountedCraftingCapacity(CountedCraftingTarget.provider(), CountedCraftingRoutingMode.AGGREGATE,
                OptionalLong.of(capacity), OptionalLong.of(capacity)));
    }

    /** Fixes one batch size; the CPU owns EMC extraction and rollback until this admission commits. */
    public static @Nullable CountedCraftingAdmission prepare(
                                                             IManagedGridNode node, Object2LongMap<AEKey> outputs,
                                                             IPatternDetails pattern, long requestedCount) {
        if (requestedCount <= 0L) throw new IllegalArgumentException("An EMC transmutation batch must be positive");
        GenericStack output = transmutationOutput(pattern);
        if (output == null) return null;
        long count = Math.min(requestedCount, capacity(node, outputs, output));
        if (count == 0L) return null;
        GenericStack total = new GenericStack(output.what(), Math.multiplyExact(output.amount(), count));
        return new Admission(count, total, new CommitTarget(node, outputs));
    }

    private static @Nullable GenericStack transmutationOutput(IPatternDetails pattern) {
        if (!(pattern instanceof TransmutationPattern)) return null;
        var outputs = pattern.getOutputs();
        if (outputs.size() != 1) return null;
        GenericStack output = outputs.getFirst();
        return output.amount() > 0L ? output : null;
    }

    private static long capacity(IManagedGridNode node, Object2LongMap<AEKey> outputs, GenericStack output) {
        if (!node.isActive()) return 0L;
        long pending = outputs.getLong(output.what());
        if (pending < 0L) throw new IllegalStateException("AppliedE pending output amount must not be negative");
        return (Long.MAX_VALUE - pending) / output.amount();
    }

    private record CommitTarget(IManagedGridNode node, Object2LongMap<AEKey> outputs) {}

    private static final class Admission implements CountedCraftingAdmission {

        private final long count;
        private final GenericStack output;
        private @Nullable CommitTarget target;
        private boolean transferred;

        private Admission(long count, GenericStack output, CommitTarget target) {
            this.count = count;
            this.output = output;
            this.target = target;
        }

        @Override
        public long count() {
            return this.count;
        }

        @Override
        public boolean hasTransferredInputOwnership() {
            return this.transferred;
        }

        @Override
        public boolean commit(KeyCounter[] prototype) {
            CommitTarget current = this.target;
            this.target = null;
            if (current == null || capacity(current.node(), current.outputs(), this.output) == 0L) return false;
            long next = Math.addExact(current.outputs().getLong(this.output.what()), this.output.amount());
            // AppliedE ignores inputHolder: the CPU has already extracted every admitted EMC input.
            // Mark ownership before queue mutation so a later tick-alert failure cannot refund those inputs.
            this.transferred = true;
            current.outputs().put(this.output.what(), next);
            current.node().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
            return true;
        }
    }
}
