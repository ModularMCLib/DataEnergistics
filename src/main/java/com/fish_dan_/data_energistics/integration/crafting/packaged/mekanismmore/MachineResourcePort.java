package com.fish_dan_.data_energistics.integration.crafting.packaged.mekanismmore;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.inventory.IInventorySlot;
import org.jspecify.annotations.Nullable;

/** A live machine slot, used only inside a server-thread callback; no resource is kept in this view. */
sealed interface MachineResourcePort {

    /** Returns a snapshot, or null for an empty slot; does not extract anything. */
    @Nullable
    GenericStack contents();

    /** Simulates or performs insertion and returns the accepted amount. Never creates the offered resource. */
    long insert(GenericStack stack, Action action);

    /** Removes the requested amount from this slot and returns what was actually removed. */
    long extract(long amount);

    /** Checks output storage capacity without using the automation insertion policy of an output-only slot. */
    boolean canHold(GenericStack stack);

    record Chemical(IChemicalTank tank) implements MachineResourcePort {

        public boolean canHold(GenericStack stack) {
            return stack.what() instanceof MekanismKey && stack.amount() <= tank.getCapacity();
        }

        public @Nullable GenericStack contents() {
            return tank.isEmpty() ? null : new GenericStack(MekanismKey.of(tank.getStack()), tank.getStored());
        }

        public long insert(GenericStack stack, Action action) {
            if (!(stack.what() instanceof MekanismKey key)) return 0;
            return stack.amount() - tank.insert(key.withAmount(stack.amount()), action, AutomationType.INTERNAL).getAmount();
        }

        public long extract(long amount) {
            return tank.extract(amount, Action.EXECUTE, AutomationType.INTERNAL).getAmount();
        }
    }

    record Fluid(IExtendedFluidTank tank) implements MachineResourcePort {

        public boolean canHold(GenericStack stack) {
            return stack.what() instanceof AEFluidKey && stack.amount() <= tank.getCapacity();
        }

        public @Nullable GenericStack contents() {
            return tank.isEmpty() ? null : new GenericStack(AEFluidKey.of(tank.getFluid()), tank.getFluidAmount());
        }

        public long insert(GenericStack stack, Action action) {
            if (!(stack.what() instanceof AEFluidKey key) || stack.amount() > Integer.MAX_VALUE) return 0;
            return stack.amount() - tank.insert(key.toStack((int) stack.amount()), action, AutomationType.INTERNAL).getAmount();
        }

        public long extract(long amount) {
            return tank.extract(Math.toIntExact(amount), Action.EXECUTE, AutomationType.INTERNAL).getAmount();
        }
    }

    record Item(IInventorySlot slot) implements MachineResourcePort {

        public boolean canHold(GenericStack stack) {
            return stack.what() instanceof AEItemKey key && stack.amount() <= slot.getLimit(key.toStack());
        }

        public @Nullable GenericStack contents() {
            return slot.isEmpty() ? null : new GenericStack(AEItemKey.of(slot.getStack()), slot.getCount());
        }

        public long insert(GenericStack stack, Action action) {
            if (!(stack.what() instanceof AEItemKey key) || stack.amount() > Integer.MAX_VALUE) return 0;
            return stack.amount() - slot.insertItem(key.toStack((int) stack.amount()), action, AutomationType.INTERNAL).getCount();
        }

        public long extract(long amount) {
            return slot.extractItem(Math.toIntExact(amount), Action.EXECUTE, AutomationType.INTERNAL).getCount();
        }
    }
}
