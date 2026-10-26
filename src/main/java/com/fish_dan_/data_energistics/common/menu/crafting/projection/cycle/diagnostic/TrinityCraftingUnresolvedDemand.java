package com.fish_dan_.data_energistics.common.menu.crafting.projection.cycle.diagnostic;

import com.fish_dan_.data_energistics.common.crafting.display.CraftingDisplayAmount;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;

/**
 * One positive intermediate demand that could not be proved to be an external shortage.
 *
 * @param key    exact AE key
 * @param amount unresolved positive amount
 */
public record TrinityCraftingUnresolvedDemand(AEKey key, CraftingDisplayAmount amount) {

    public TrinityCraftingUnresolvedDemand(AEKey key, BigInteger amount) {
        this(key, CraftingDisplayAmount.of(amount));
    }

    public TrinityCraftingUnresolvedDemand {
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("A Trinity unresolved crafting demand must be positive");
        }
    }
}
