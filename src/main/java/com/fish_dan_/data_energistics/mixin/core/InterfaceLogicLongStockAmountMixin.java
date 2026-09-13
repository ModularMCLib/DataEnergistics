package com.fish_dan_.data_energistics.mixin.core;

import com.fish_dan_.data_energistics.ae2.sanctum.DataSanctumLargeInterfaceHost;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Keeps the extreme interface's restock plan in long-width quantities instead of AE2's int bridge. */
@Mixin(InterfaceLogic.class)
public abstract class InterfaceLogicLongStockAmountMixin {

    @Shadow
    @Final
    private InterfaceLogicHost host;
    @Shadow
    @Final
    private GenericStack[] plannedWork;

    @Shadow
    abstract void updatePlan(int slot);

    @Invoker("tryUsePlan")
    abstract boolean dataEnergistics$tryUsePlan(int slot, AEKey what, int amount);

    /** Replays AE2's update loop while preserving long quantities for the full-block interface host. */
    @Overwrite
    protected boolean updateStorage() {
        boolean didSomething = false;
        for (int slot = 0; slot < plannedWork.length; slot++) {
            GenericStack work = plannedWork[slot];
            if (work == null) {
                continue;
            }
            long amount = work.amount();
            int bridgeAmount = host instanceof DataSanctumLargeInterfaceHost ? amount >= Integer.MAX_VALUE ? Integer.MAX_VALUE : amount <= Integer.MIN_VALUE ? Integer.MIN_VALUE : (int) amount : (int) amount;
            boolean changed = dataEnergistics$tryUsePlan(slot, work.what(), bridgeAmount);
            if (changed) {
                updatePlan(slot);
            }
            didSomething |= changed;
        }
        return didSomething;
    }
}
