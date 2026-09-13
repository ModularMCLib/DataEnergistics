package com.fish_dan_.data_energistics.integration.ae.appliedcreate;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderCapabilities;
import com.fish_dan_.data_energistics.registry.AdaptivePatternProviderRegistrationFactory;

/** Registers Applied Create mechanical pattern provider variants. */
@DataEnergisticsEntrypoint(requiredMods = "appliedcreate")
public final class AdaptivePatternProviderAppliedCreateRegistration implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.adaptivePatternProviders().register(AdaptivePatternProviderRegistrationFactory.fixed(
                "appliedcreate/andesite",
                AdaptivePatternProviderRegistrationFactory.itemIds("appliedcreate:andesite_pattern_provider"),
                9,
                AdaptivePatternProviderRegistrationFactory.capabilities(
                        AdaptivePatternProviderCapabilities.MECHANICAL_CRAFTING),
                new AppliedCreateAdaptiveRoute()));
        registry.adaptivePatternProviders().register(AdaptivePatternProviderRegistrationFactory.fixed(
                "appliedcreate/brass",
                AdaptivePatternProviderRegistrationFactory.itemIds("appliedcreate:brass_pattern_provider"),
                36,
                AdaptivePatternProviderRegistrationFactory.capabilities(
                        AdaptivePatternProviderCapabilities.MECHANICAL_CRAFTING),
                new AppliedCreateAdaptiveRoute()));
    }
}
