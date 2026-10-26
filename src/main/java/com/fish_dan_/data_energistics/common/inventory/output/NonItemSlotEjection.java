package com.fish_dan_.data_energistics.common.inventory.output;

import appeng.api.config.Actionable;
import appeng.api.stacks.GenericStack;
import appeng.helpers.externalstorage.GenericStackInv;

import org.jspecify.annotations.NullMarked;

/** Transfers a single key balance, debiting each confirmed insertion before offering the next destination. */
@NullMarked
public final class NonItemSlotEjection {

    private NonItemSlotEjection() {}

    /** Moves only accepted amounts into the machine's own output slots, without invoking external I/O. */
    public static void toOutputs(GenericStackInv source, Iterable<GenericStackInv> outputs) {
        for (GenericStackInv output : outputs) {
            GenericStack stack = source.getStack(0);
            if (stack == null) return;
            debit(source, 0, stack, output.insert(0, stack.what(), stack.amount(), Actionable.MODULATE));
        }
    }

    private static void debit(GenericStackInv source, int slot, GenericStack stack, long accepted) {
        if (accepted < 0L || accepted > stack.amount()) {
            throw new IllegalStateException("Key destination accepted " + accepted + " from " + stack.amount());
        }
        if (accepted > 0L && source.extract(slot, stack.what(), accepted, Actionable.MODULATE) != accepted) {
            throw new IllegalStateException("Machine key balance changed during ejection");
        }
    }
}
