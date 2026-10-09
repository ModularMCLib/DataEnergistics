package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import org.ojalgo.optimisation.Variable;

import java.math.BigInteger;

/**
 * Exact normalized carry-column equation replayed after every ojAlgo result.
 *
 * @param terms         signed integer solver-variable coefficients
 * @param rightHandSide exact column constant
 */
public record TrinityRadixColumnEquation(
                                         Object2ObjectMap<Variable, BigInteger> terms,
                                         BigInteger rightHandSide) {}
