package com.fish_dan_.data_energistics.util;

import java.math.BigInteger;

/** Exact and saturating arithmetic shared by logical amounts and counters. */
public final class AmountMath {

    private AmountMath() {}

    /** Divides a non-negative numerator by a positive denominator and rounds the result upward. */
    public static BigInteger ceilDivideNonNegative(BigInteger numerator, BigInteger denominator) {
        if (numerator.signum() < 0 || denominator.signum() <= 0) {
            throw new IllegalArgumentException("Ceiling division requires a non-negative numerator and positive denominator");
        }
        BigInteger[] division = numerator.divideAndRemainder(denominator);
        return division[1].signum() == 0 ? division[0] : division[0].add(BigInteger.ONE);
    }

    /** Adds two non-negative long values, capping positive overflow at {@link Long#MAX_VALUE}. */
    public static long addNonNegative(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    /** Multiplies two non-negative long values, capping positive overflow at {@link Long#MAX_VALUE}. */
    public static long multiplyNonNegative(long left, long right) {
        return left != 0L && right > Long.MAX_VALUE / left ? Long.MAX_VALUE : left * right;
    }
}
