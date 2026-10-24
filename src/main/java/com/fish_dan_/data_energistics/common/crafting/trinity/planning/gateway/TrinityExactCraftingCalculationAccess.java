package com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;

import net.minecraft.world.level.Level;

import java.math.BigInteger;
import java.util.concurrent.Future;

/**
 * Server-thread bridge for exact crafting requests routed from input above AE2's long-sized amount range.
 * The implementation is the AE2 crafting service mixin and routes these requests only through Trinity planning.
 */
public interface TrinityExactCraftingCalculationAccess {

    /**
     * Begins a positive exact request without narrowing its amount. Quantity-mode adjustment may reduce the amount
     * below AE2's long range, but the original input still requires Trinity routing.
     *
     * @param level        server level that owns the crafting request
     * @param actionSource permission and request context captured from the requesting menu
     * @param target       requested output key
     * @param amount       exact positive amount after quantity-mode adjustment
     * @param strategy     existing confirmation or replan strategy
     * @return asynchronous Trinity plan or diagnostic result
     */
    Future<ICraftingPlan> data_energistics$beginExactTrinityCalculation(Level level,
                                                                        IActionSource actionSource,
                                                                        AEKey target,
                                                                        BigInteger amount,
                                                                        CalculationStrategy strategy);
}
