package com.fish_dan_.data_energistics.integration.ae.advancedae;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderCapabilities;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderToolbarActions;
import com.fish_dan_.data_energistics.registry.AdaptivePatternProviderRegistrationFactory;

/** Registers Advanced AE pattern provider variants. */
@DataEnergisticsEntrypoint(requiredMods = "advanced_ae")
public final class AdaptivePatternProviderAdvancedAeRegistration implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.adaptivePatternProviders().register(AdaptivePatternProviderRegistrationFactory.fixed(
                "advanced_ae/small",
                AdaptivePatternProviderRegistrationFactory.itemIds(
                        "advanced_ae:small_adv_pattern_provider",
                        "advanced_ae:small_adv_pattern_provider_part"),
                9,
                AdaptivePatternProviderRegistrationFactory.capabilities(
                        AdaptivePatternProviderCapabilities.ADVANCED_PATTERN,
                        AdaptivePatternProviderCapabilities.FILTERED_IMPORT),
                new AdvancedAeAdaptiveRoute(),
                AdaptivePatternProviderToolbarActions.FILTERED_IMPORT));
        registry.adaptivePatternProviders().register(AdaptivePatternProviderRegistrationFactory.fixed(
                "advanced_ae/extended",
                AdaptivePatternProviderRegistrationFactory.itemIds(
                        "advanced_ae:adv_pattern_provider", "advanced_ae:adv_pattern_provider_part"),
                36,
                AdaptivePatternProviderRegistrationFactory.capabilities(
                        AdaptivePatternProviderCapabilities.ADVANCED_PATTERN,
                        AdaptivePatternProviderCapabilities.FILTERED_IMPORT),
                new AdvancedAeAdaptiveRoute(),
                AdaptivePatternProviderToolbarActions.FILTERED_IMPORT));
    }
}
