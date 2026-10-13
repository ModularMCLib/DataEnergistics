package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.firing;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.applicability.TrinityDeterministicBasis;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;

import java.math.BigInteger;

/**
 * Exact firing vector and net-change proof derived from one applicable primitive basis.
 *
 * @param basis         structural basis used by proof assembly
 * @param firings       selected exact feasible firing vector
 * @param totalNet      exact aggregate net change
 * @param balancePasses bounded residual/repetition refinement passes
 */
public record TrinityDeterministicFiringSolution(
                                                 TrinityDeterministicBasis basis,
                                                 Object2ObjectMap<TrinityPatternVariant, BigInteger> firings,
                                                 Object2ObjectMap<AEKey, BigInteger> totalNet,
                                                 int balancePasses) {}
