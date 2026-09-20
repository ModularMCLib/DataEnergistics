package com.fish_dan_.data_energistics.integration.entrypoint.common;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.registry.AdaptivePatternProviderRegistrationFactory;

/** Registers ExtendedAE pattern provider variants. */
@DataEnergisticsEntrypoint(requiredMods = "extendedae")
public final class AdaptivePatternProviderExtendedAeRegistration implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.adaptivePatternProviders().register(AdaptivePatternProviderRegistrationFactory.fixed(
                "extendedae/extended",
                AdaptivePatternProviderRegistrationFactory.itemIds(
                        "extendedae:ex_pattern_provider",
                        "extendedae:ex_pattern_provider_part",
                        "extendedae:wireless_ex_pat"),
                36,
                AdaptivePatternProviderRegistrationFactory.capabilities()));
    }
}
