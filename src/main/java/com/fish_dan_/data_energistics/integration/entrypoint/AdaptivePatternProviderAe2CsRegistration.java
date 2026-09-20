package com.fish_dan_.data_energistics.integration.entrypoint;

import com.fish_dan_.data_energistics.integration.patternprovider.ae.ae2cs.Ae2CrystalScienceAdaptiveRoute;
import com.fish_dan_.data_energistics.integration.patternprovider.ae.ae2cs.Ae2CrystalScienceMeteoriteRoute;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderCapabilities;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderToolbarActions;
import com.fish_dan_.data_energistics.registry.AdaptivePatternProviderRegistrationFactory;

/** Registers AE2 Crystal Science pattern-provider variants and routes. */
@DataEnergisticsEntrypoint(requiredMods = "ae2cs")
public final class AdaptivePatternProviderAe2CsRegistration implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.adaptivePatternProviders().register(AdaptivePatternProviderRegistrationFactory.fixed(
                "ae2cs/simple",
                AdaptivePatternProviderRegistrationFactory.itemIds(
                        "ae2cs:simple_pattern_provider", "ae2cs:simple_pattern_provider_part"),
                5,
                AdaptivePatternProviderRegistrationFactory.capabilities()));
        registry.adaptivePatternProviders().register(AdaptivePatternProviderRegistrationFactory.fixed(
                "ae2cs/resonating",
                AdaptivePatternProviderRegistrationFactory.itemIds(
                        "ae2cs:resonating_pattern_provider", "ae2cs:resonating_pattern_provider_part"),
                9,
                AdaptivePatternProviderRegistrationFactory.capabilities(
                        AdaptivePatternProviderCapabilities.RESONATING),
                new Ae2CrystalScienceAdaptiveRoute(),
                AdaptivePatternProviderToolbarActions.RESONATING_PULL));
        registry.adaptivePatternProviders().register(AdaptivePatternProviderRegistrationFactory.fixed(
                "ae2cs/extended_resonating",
                AdaptivePatternProviderRegistrationFactory.itemIds(
                        "ae2cs:extended_resonating_pattern_provider",
                        "ae2cs:extended_resonating_pattern_provider_part",
                        "ae2cs:ex_resonating_pattern_provider",
                        "ae2cs:ex_resonating_pattern_provider_part"),
                36,
                AdaptivePatternProviderRegistrationFactory.capabilities(
                        AdaptivePatternProviderCapabilities.RESONATING),
                new Ae2CrystalScienceAdaptiveRoute(),
                AdaptivePatternProviderToolbarActions.RESONATING_PULL));
        registry.adaptivePatternProviders().register(AdaptivePatternProviderRegistrationFactory.fixed(
                "ae2cs/meteorite",
                AdaptivePatternProviderRegistrationFactory.itemIds(
                        "ae2cs:meteorite_pattern_provider", "ae2cs:meteorite_pattern_provider_part"),
                63,
                AdaptivePatternProviderRegistrationFactory.capabilities(
                        AdaptivePatternProviderCapabilities.METEORITE),
                new Ae2CrystalScienceMeteoriteRoute()));
    }
}
