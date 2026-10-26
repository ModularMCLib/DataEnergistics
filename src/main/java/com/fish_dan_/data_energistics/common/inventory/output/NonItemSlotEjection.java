package com.fish_dan_.data_energistics.common.inventory.output;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.util.inv.AppEngInternalInventory;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

import org.jspecify.annotations.NullMarked;

import java.util.function.ToLongFunction;

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

    /** Uses wrapped resource stacks only as the existing item-output inventory's internal representation. */
    public static void toItemOutputs(GenericStackInv source, AppEngInternalInventory outputs, int firstSlot, int endSlot) {
        for (int pass = 0; pass < 2; pass++) {
            for (int slot = firstSlot; slot < endSlot; slot++) {
                GenericStack stack = source.getStack(0);
                if (stack == null) return;
                ItemStack current = outputs.getStackInSlot(slot);
                if (pass == 0 && current.isEmpty() || pass == 1 && !current.isEmpty()) continue;
                if (!current.isEmpty()) {
                    if (!GenericStack.isWrapped(current)) continue;
                    GenericStack stored = GenericStack.fromItemStack(current);
                    if (stored == null || !stored.what().equals(stack.what())) continue;
                    long available = Math.max(0L, source.getMaxAmount(stack.what()) - stored.amount());
                    long accepted = Math.min(stack.amount(), available);
                    if (accepted <= 0L) continue;
                    outputs.setItemDirect(slot, GenericStack.wrapInItemStack(stack.what(), stored.amount() + accepted));
                    debit(source, 0, stack, accepted);
                } else {
                    outputs.setItemDirect(slot, GenericStack.wrapInItemStack(stack.what(), stack.amount()));
                    debit(source, 0, stack, stack.amount());
                }
            }
        }
    }

    /** Debits an output slot only after its automatic destination confirms acceptance. */
    public static boolean exportWrapped(AppEngInternalInventory outputs, int slot, ToLongFunction<GenericStack> sink) {
        ItemStack current = outputs.getStackInSlot(slot);
        if (!GenericStack.isWrapped(current)) return false;
        GenericStack stack = GenericStack.fromItemStack(current);
        if (stack == null) return false;
        long accepted = sink.applyAsLong(stack);
        if (accepted < 0L || accepted > stack.amount()) throw new IllegalStateException("Invalid automatic output amount: " + accepted);
        if (accepted == 0L) return false;
        long remaining = stack.amount() - accepted;
        outputs.setItemDirect(slot, remaining == 0L ? ItemStack.EMPTY : GenericStack.wrapInItemStack(stack.what(), remaining));
        return true;
    }

    public static long insertIntoAdjacent(GenericStack stack, Iterable<GenericInternalInventory> targets) {
        long remaining = stack.amount();
        for (GenericInternalInventory target : targets) {
            if (!target.canInsert() || !target.isSupportedType(stack.what())) continue;
            target.beginBatch();
            try {
                for (int slot = 0; slot < target.size() && remaining > 0L; slot++) {
                    if (!target.isAllowedIn(slot, stack.what())) continue;
                    long accepted = target.insert(slot, stack.what(), remaining, Actionable.MODULATE);
                    if (accepted < 0L || accepted > remaining) throw new IllegalStateException("Invalid generic output amount: " + accepted);
                    remaining -= accepted;
                }
            } finally {
                target.endBatch();
            }
            if (remaining == 0L) break;
        }
        return stack.amount() - remaining;
    }

    public static long insertIntoFluidHandlers(GenericStack stack, Iterable<IFluidHandler> targets) {
        if (!(stack.what() instanceof AEFluidKey fluid)) return 0L;
        long remaining = stack.amount();
        for (IFluidHandler target : targets) {
            int offered = (int) Math.min(Integer.MAX_VALUE, remaining);
            int accepted = target.fill(fluid.toStack(offered), FluidAction.EXECUTE);
            if (accepted < 0 || accepted > offered) throw new IllegalStateException("Invalid fluid output amount: " + accepted);
            remaining -= accepted;
            if (remaining == 0L) break;
        }
        return stack.amount() - remaining;
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
