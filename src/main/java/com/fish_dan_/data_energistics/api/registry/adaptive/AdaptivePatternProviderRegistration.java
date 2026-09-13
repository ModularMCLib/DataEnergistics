package com.fish_dan_.data_energistics.api.registry.adaptive;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

/**
 * One atomic adaptive pattern-provider declaration.
 *
 * @param registrationId stable identity used for duplicate and ambiguity diagnostics
 * @param definition     provider-specific profile resolver
 * @param dispatch       provider-specific pattern dispatch callbacks
 * @param toolbarActions left-toolbar actions exposed by the provider
 */
public record AdaptivePatternProviderRegistration(
                                                  ResourceLocation registrationId,
                                                  AdaptivePatternProviderDefinition definition,
                                                  AdaptivePatternProviderDispatch dispatch,
                                                  ObjectList<AdaptivePatternProviderToolbarAction> toolbarActions) {

    public AdaptivePatternProviderRegistration {
        toolbarActions = ObjectLists.unmodifiable(new ObjectArrayList<>(toolbarActions));
    }

    /**
     * Creates a profile-only registration using the normal AE2 dispatch route.
     */
    public AdaptivePatternProviderRegistration(
                                               ResourceLocation registrationId,
                                               AdaptivePatternProviderDefinition definition) {
        this(registrationId, definition, context -> false, ObjectList.of());
    }

    /**
     * Creates a dispatch registration without client toolbar actions.
     */
    public AdaptivePatternProviderRegistration(
                                               ResourceLocation registrationId,
                                               AdaptivePatternProviderDefinition definition,
                                               AdaptivePatternProviderDispatch dispatch) {
        this(registrationId, definition, dispatch, ObjectList.of());
    }
}
