package com.fish_dan_.data_energistics.common.inventory.output;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.helpers.externalstorage.GenericStackInv;

import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

import org.jspecify.annotations.NullMarked;

/** Transfers a single key balance, debiting each confirmed insertion before offering the next destination. */
@NullMarked
public final class NonItemSlotEjection {

    private NonItemSlotEjection() {}

    public static void toNetwork(GenericStackInv source, int slot, MEStorage target, IActionSource actionSource) {
        GenericStack stack = source.getStack(slot);
        if (stack != null) {
            debit(source, slot, stack, target.insert(stack.what(), stack.amount(), Actionable.MODULATE, actionSource));
        }
    }

    public static void toAdjacent(GenericStackInv source, int slot, Iterable<GenericInternalInventory> targets) {
        for (GenericInternalInventory target : targets) {
            GenericStack stack = source.getStack(slot);
            if (stack == null) {
                return;
            }
            if (!target.canInsert() || !target.isSupportedType(stack.what())) {
                continue;
            }
            target.beginBatch();
            try {
                for (int targetSlot = 0; targetSlot < target.size(); targetSlot++) {
                    stack = source.getStack(slot);
                    if (stack == null) {
                        return;
                    }
                    if (target.isAllowedIn(targetSlot, stack.what())) {
                        debit(source, slot, stack,
                                target.insert(targetSlot, stack.what(), stack.amount(), Actionable.MODULATE));
                    }
                }
            } finally {
                target.endBatch();
            }
        }
    }

    public static void toFluidHandlers(GenericStackInv source, int slot, Iterable<IFluidHandler> targets) {
        for (IFluidHandler target : targets) {
            GenericStack stack = source.getStack(slot);
            if (stack == null) {
                return;
            }
            if (!(stack.what() instanceof AEFluidKey fluid)) {
                throw new IllegalArgumentException("Fluid output contains a non-fluid key");
            }
            int offered = (int) Math.min(Integer.MAX_VALUE, stack.amount());
            int accepted = target.fill(fluid.toStack(offered), FluidAction.EXECUTE);
            if (accepted < 0 || accepted > offered) {
                throw new IllegalStateException("Fluid destination accepted " + accepted + " from " + offered);
            }
            debit(source, slot, stack, accepted);
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
