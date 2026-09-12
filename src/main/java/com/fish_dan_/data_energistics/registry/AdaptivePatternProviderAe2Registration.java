package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.registry.AdaptivePatternProviderRegistrationFactory;

import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;

/** Registers the native AE2 pattern provider variant. */
@DataEnergisticsEntrypoint
public final class AdaptivePatternProviderAe2Registration implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.adaptivePatternProviders().register(AdaptivePatternProviderRegistrationFactory.fixed(
                "ae2/standard",
                stack -> AEBlocks.PATTERN_PROVIDER.is(stack) || AEParts.PATTERN_PROVIDER.is(stack),
                9,
                AdaptivePatternProviderRegistrationFactory.capabilities()));
    }
}
