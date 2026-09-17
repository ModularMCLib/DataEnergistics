package com.fish_dan_.data_energistics.api.crafting.dispatch;

import java.math.BigInteger;

/**
 * One-shot server-thread admission retaining an exact logical count beyond the legacy long window.
 * The inherited ownership and commit contract applies to every logical copy of the supplied one-craft prototype.
 * A failed commit after ownership transfer must retain all received assets and must never request a resend.
 */
public interface BigIntegerCraftingAdmission extends CountedCraftingAdmission {

    /** Returns the fixed positive accepted count; it must not change between preparation and commit. */
    BigInteger exactCount();

    /** Legacy callers may use only representable batches; oversized admissions fail instead of truncating. */
    @Override
    default long count() {
        return exactCount().longValueExact();
    }
}
