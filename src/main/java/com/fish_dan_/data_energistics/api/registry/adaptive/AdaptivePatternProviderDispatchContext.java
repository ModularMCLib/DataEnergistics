package com.fish_dan_.data_energistics.api.registry.adaptive;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;

import net.minecraft.world.item.ItemStack;

/**
 * Context passed to an adaptive provider dispatch registration.
 *
 * <p>The target exposes the supported provider operations as a narrow API.
 * Implementations can therefore register a route without reaching into the
 * adaptive logic or injecting a Mixin.</p>
 */
public record AdaptivePatternProviderDispatchContext(
                                                     ItemStack providerStack,
                                                     AdaptivePatternProviderProfile profile,
                                                     IPatternDetails patternDetails,
                                                     KeyCounter[] inputHolder,
                                                     AdaptivePatternProviderDispatchTarget target) {

    /**
     * Defensive copy of the mutable provider stack at the registration boundary.
     */
    public AdaptivePatternProviderDispatchContext {
        providerStack = providerStack.copy();
    }
}
