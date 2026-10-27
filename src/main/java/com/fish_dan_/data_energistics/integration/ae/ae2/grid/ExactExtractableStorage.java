package com.fish_dan_.data_energistics.integration.ae.ae2.grid;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;

import java.math.BigInteger;

/**
 * Exact finite mount access used on the server thread for planning and transactional ingredient ownership.
 * Implementations enforce their live access and capacity policy without retaining the action source.
 */
public interface ExactExtractableStorage {

    /** Returns the finite amount currently extractable for the exact key without retaining the action source. */
    BigInteger exactAvailable(AEKey key, IActionSource source);

    /** Extracts up to the positive request, or simulates it, without narrowing the quantity to long. */
    BigInteger extractExact(AEKey key, BigInteger amount, Actionable mode, IActionSource source);

    /** Returns ownership to the same mount; returns the accepted amount under its current capacity policy. */
    BigInteger insertExact(AEKey key, BigInteger amount, Actionable mode, IActionSource source);
}
