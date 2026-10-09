package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;

import java.math.BigInteger;

/**
 * Exact logical equation retained for the finite overflow-proof domain derivation.
 *
 * @param terms         signed logical-variable coefficients
 * @param rightHandSide exact logical constant
 */
public record TrinityRadixLogicalEquation(
                                          Object2ObjectMap<TrinityRadixVariable, BigInteger> terms,
                                          BigInteger rightHandSide) {}
