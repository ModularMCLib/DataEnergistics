package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.applicability;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.math.BigInteger;

/**
 * Exact acyclic residual needed alongside repeated firings of one primitive productive basis.
 *
 * @param firings        residual logical firing vector
 * @param netChange      residual net change
 * @param executionOrder topological residual order
 */
public record TrinityDeterministicResidualResult(
                                                 Object2ObjectMap<TrinityPatternVariant, BigInteger> firings,
                                                 Object2ObjectMap<AEKey, BigInteger> netChange,
                                                 ObjectList<TrinityVariantFiring> executionOrder) {}
