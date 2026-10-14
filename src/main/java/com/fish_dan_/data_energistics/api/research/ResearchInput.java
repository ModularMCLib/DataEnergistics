package com.fish_dan_.data_energistics.api.research;

import appeng.api.stacks.AEKey;

import java.util.Objects;

/** Exact AE resource and amount, in the key type's native units (fluids use millibuckets). */
public record ResearchInput(AEKey key, long amount) {

    public ResearchInput {
        Objects.requireNonNull(key, "key");
        if (amount <= 0) {
            throw new IllegalArgumentException("Research input amount must be positive");
        }
    }
}
