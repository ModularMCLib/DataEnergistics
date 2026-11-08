package com.fish_dan_.data_energistics.core.mixins.normal.appeng.crafting;

import com.fish_dan_.data_energistics.integration.ae.ae2.accessor.crafting.CraftingPlanTiming;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.crafting.CraftingCalculation;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Measures the complete native AE2 calculation, including its intentional cross-tick pauses.
 */
@Mixin(CraftingCalculation.class)
@NullMarked
public abstract class CraftingCalculationMixin {

    @WrapMethod(method = "run")
    private ICraftingPlan dataEnergistics$measureCalculation(Operation<ICraftingPlan> original) {
        long startedNanos = System.nanoTime();
        ICraftingPlan plan = original.call();
        if (plan instanceof CraftingPlanTiming.Mutable mutablePlan) {
            mutablePlan.dataEnergistics$setCalculationNanos(System.nanoTime() - startedNanos);
        }
        return plan;
    }
}
