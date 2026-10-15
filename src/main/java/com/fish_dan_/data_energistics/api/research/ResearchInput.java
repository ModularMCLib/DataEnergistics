package com.fish_dan_.data_energistics.api.research;

import appeng.api.stacks.AEKey;
import appeng.core.definitions.AEItems;

import java.util.Objects;

/** Exact AE resource and amount, in the key type's native units (fluids use millibuckets). */
public record ResearchInput(AEKey key, long amount) {

    public ResearchInput {
        Objects.requireNonNull(key, "key");
        // AEKey's codec turns decoding failures into a successful missing-content key.
        if (AEItems.MISSING_CONTENT.is(key)) {
            throw new IllegalArgumentException("Research input contains an unknown or malformed AE key");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Research input amount must be positive");
        }
    }
}
