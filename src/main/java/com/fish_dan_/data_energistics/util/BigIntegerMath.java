package com.fish_dan_.data_energistics.util;

import java.math.BigInteger;

/** Exact arithmetic operations shared by amount planning and transport code. */
public final class BigIntegerMath {

    private BigIntegerMath() {}

    /**
     * Divides a non-negative numerator by a positive denominator and rounds the result upward.
     *
     * @param numerator non-negative dividend
     * @param denominator positive divisor
     * @return the smallest integer greater than or equal to {@code numerator / denominator}
     * @throws IllegalArgumentException when either operand violates its amount contract
     */
    public static BigInteger ceilDivideNonNegative(BigInteger numerator, BigInteger denominator) {
        if (numerator.signum() < 0 || denominator.signum() <= 0) {
            throw new IllegalArgumentException("Ceiling division requires a non-negative numerator and positive denominator");
        }
        BigInteger[] division = numerator.divideAndRemainder(denominator);
        return division[1].signum() == 0 ? division[0] : division[0].add(BigInteger.ONE);
    }
}
