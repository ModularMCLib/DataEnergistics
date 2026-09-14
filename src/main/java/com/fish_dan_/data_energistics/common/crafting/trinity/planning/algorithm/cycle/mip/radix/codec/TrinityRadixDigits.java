package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec;

import java.math.BigInteger;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;

/**
 * Fixed-width unsigned base-2^8 digits stored least-significant first.
 *
 * @param values digit values in {@code [0, 255]}
 */
public record TrinityRadixDigits(IntList values) {

    /** Exact base selected so every digit and digit-product coefficient remains safely integral in ojAlgo. */
    public static final int BASE = 1 << 8;

    /**
     * Freezes the trusted codec output once.
     */
    public TrinityRadixDigits {
        values = IntLists.unmodifiable(new IntArrayList(values));
    }

    /**
     * @return exact non-negative reconstructed value
     */
    public BigInteger value() {
        BigInteger result = BigInteger.ZERO;
        BigInteger base = BigInteger.valueOf(BASE);
        for (int index = values.size() - 1; index >= 0; index--) {
            result = result.multiply(base).add(BigInteger.valueOf(values.getInt(index)));
        }
        return result;
    }

    /**
     * @param index least-significant-first index
     * @return encoded digit or zero beyond this fixed width
     */
    public int digit(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("A Trinity radix digit index cannot be negative");
        }
        return index < values.size() ? values.getInt(index) : 0;
    }
}
