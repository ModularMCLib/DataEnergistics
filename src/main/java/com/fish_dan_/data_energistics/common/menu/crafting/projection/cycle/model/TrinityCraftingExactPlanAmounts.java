package com.fish_dan_.data_energistics.common.menu.crafting.projection.cycle.model;

import com.fish_dan_.data_energistics.common.crafting.display.CraftingDisplayAmount;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;

/** Exact confirmation-table counters retained alongside AE2's long-only compatibility summary. */
public record TrinityCraftingExactPlanAmounts(
                                              AEKey key,
                                              CraftingDisplayAmount missing,
                                              CraftingDisplayAmount stored,
                                              CraftingDisplayAmount crafting) {

    public TrinityCraftingExactPlanAmounts(AEKey key, BigInteger missing, BigInteger stored, BigInteger crafting) {
        this(key, CraftingDisplayAmount.of(missing), CraftingDisplayAmount.of(stored),
                CraftingDisplayAmount.of(crafting));
    }

    public TrinityCraftingExactPlanAmounts {
        if (missing.signum() < 0 || stored.signum() < 0 || crafting.signum() < 0 ||
                missing.signum() + stored.signum() + crafting.signum() == 0) {
            throw new IllegalArgumentException("A Trinity exact plan row requires non-negative non-empty counters");
        }
    }
}
