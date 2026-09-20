package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.ae2.patternprovider.packaged.PackagedAdaptiveRoute;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;

/** Registers the digital packaged provider as one adaptive profile handling all installed machine adapters. */
@DataEnergisticsEntrypoint
public final class PackagedPatternProviderRegistration implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.adaptivePatternProviders().register(AdaptivePatternProviderRegistrationFactory.fixed(
                "digital_packaged", AdaptivePatternProviderRegistrationFactory.itemIds("data_energistics:digital_packaged_pattern_provider"),
                36, AdaptivePatternProviderRegistrationFactory.capabilities(), new PackagedAdaptiveRoute()));
    }
}
