package com.fish_dan_.data_energistics.api.registry.adaptive.client;

import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * Client-phase toolbar factory registration. Only write during the owning client entrypoint callback.
 * Common provider declarations select these factories by action ID, keeping client classes out of server setup.
 */
public interface AdaptivePatternProviderToolbarRegistry {

    /**
     * Registers a non-null ID and factory. Lower order values appear first; ties use ID order.
     * Duplicate IDs and writes after registration closes throw IllegalStateException.
     * A factory must return a fresh non-null binding per screen and may not share mutable widgets between screens.
     */
    void register(ResourceLocation actionId, int order,
                  Function<AdaptivePatternProviderToolbarContext, AdaptivePatternProviderToolbarButton> factory);
}
