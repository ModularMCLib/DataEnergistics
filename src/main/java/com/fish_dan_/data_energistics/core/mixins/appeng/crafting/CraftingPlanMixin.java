package com.fish_dan_.data_energistics.core.mixins.appeng.crafting;

import com.fish_dan_.data_energistics.integration.ae.ae2.accessor.crafting.CraftingPlanTiming;

import appeng.crafting.CraftingPlan;

import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Keeps AE2 calculation timing on the exact plan instance returned by its calculation future.
 */
@Mixin(CraftingPlan.class)
@NullMarked
public abstract class CraftingPlanMixin implements CraftingPlanTiming.Mutable {

    @Unique
    private long dataEnergistics$calculationNanos;

    @Override
    public long dataEnergistics$calculationNanos() {
        return this.dataEnergistics$calculationNanos;
    }

    @Override
    public void dataEnergistics$setCalculationNanos(long calculationNanos) {
        this.dataEnergistics$calculationNanos = calculationNanos;
    }
}
