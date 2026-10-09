package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle;

import com.fish_dan_.data_energistics.util.FastUtilCollections;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.math.BigInteger;

/**
 * Declares all exact lower bounds that one SCC firing vector must satisfy together.
 *
 * @param settledWithdrawals           positive balances consumed after this SCC has completed
 * @param terminalBalanceLowerBounds   positive balances that must remain after every settled withdrawal
 * @param requiredNetChangeLowerBounds positive net production lower bounds independent of initial inventory
 * @param netNewKeys                   scalar targets whose requested amount must retain NET_NEW semantics
 * @param finalBalanceLowerBounds      precomputed balance required before downstream settlement
 */
public record TrinityCycleDemand(
                                 Object2ObjectMap<AEKey, BigInteger> settledWithdrawals,
                                 Object2ObjectMap<AEKey, BigInteger> terminalBalanceLowerBounds,
                                 Object2ObjectMap<AEKey, BigInteger> requiredNetChangeLowerBounds,
                                 ObjectSet<AEKey> netNewKeys,
                                 Object2ObjectMap<AEKey, BigInteger> finalBalanceLowerBounds) {

    /** Computes the effective final bound while retaining explicit net-new target semantics. */
    public TrinityCycleDemand(
                              Object2ObjectMap<AEKey, BigInteger> settledWithdrawals,
                              Object2ObjectMap<AEKey, BigInteger> terminalBalanceLowerBounds,
                              Object2ObjectMap<AEKey, BigInteger> requiredNetChangeLowerBounds,
                              ObjectSet<AEKey> netNewKeys) {
        this(
                settledWithdrawals,
                terminalBalanceLowerBounds,
                requiredNetChangeLowerBounds,
                netNewKeys,
                combineFinalBalances(settledWithdrawals, terminalBalanceLowerBounds));
    }

    private static Object2ObjectMap<AEKey, BigInteger> combineFinalBalances(
                                                                            Object2ObjectMap<AEKey, BigInteger> settledWithdrawals,
                                                                            Object2ObjectMap<AEKey, BigInteger> terminalBalanceLowerBounds) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> combined = new Object2ObjectLinkedOpenHashMap<>(settledWithdrawals);
        terminalBalanceLowerBounds.forEach((key, amount) -> combined.merge(key, amount, BigInteger::add));
        return FastUtilCollections.immutableMap(combined);
    }

    /**
     * Adds internal restart reserves without changing delivery or net-new semantics.
     */
    public TrinityCycleDemand withRetainedSeed(Object2ObjectMap<AEKey, BigInteger> retainedSeed) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> terminal = new Object2ObjectLinkedOpenHashMap<>(terminalBalanceLowerBounds);
        retainedSeed.forEach((key, amount) -> terminal.merge(key, amount, BigInteger::max));
        return new TrinityCycleDemand(
                settledWithdrawals,
                terminal,
                requiredNetChangeLowerBounds,
                netNewKeys);
    }
}
