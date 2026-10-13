package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.model;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;

import java.math.BigInteger;

/**
 * Carries an exactly decoded radix candidate between objective search, proof verification, and publication.
 *
 * @param firings        positive logical firing counts
 * @param modelSeed      positive initial balances on SCC keys
 * @param externalInputs positive initial balances on boundary keys
 */
public record TrinityRadixSolvedModel(
                                      Object2ObjectMap<TrinityPatternVariant, BigInteger> firings,
                                      Object2ObjectMap<AEKey, BigInteger> modelSeed,
                                      Object2ObjectMap<AEKey, BigInteger> externalInputs) {}
