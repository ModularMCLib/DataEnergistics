package com.fish_dan_.data_energistics.common.inventory.output;

import appeng.api.config.Actionable;
import appeng.api.stacks.GenericStack;
import appeng.helpers.externalstorage.GenericStackInv;

import org.jspecify.annotations.NullMarked;

/** Transfers a non-item input balance into the machine's output inventories. */
@NullMarked
public final class NonItemSlotEjection {

    private NonItemSlotEjection() {}

    /** Moves an entire input stack into an available output slot, without invoking external I/O. */
    public static void toOutputs(GenericStackInv source, Iterable<GenericStackInv> outputs) {
        GenericStack stack = source.getStack(0);
        if (stack == null) return;
        for (GenericStackInv output : outputs) {
            if (output.insert(0, stack.what(), stack.amount(), Actionable.SIMULATE) != stack.amount()) continue;
            GenericStack current = output.getStack(0);
            long stored = current == null ? 0L : current.amount();
            output.setStack(0, new GenericStack(stack.what(), stored + stack.amount()));
            source.setStack(0, null);
            return;
        }
    }
}
