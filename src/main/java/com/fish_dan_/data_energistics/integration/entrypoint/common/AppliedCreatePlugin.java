package com.fish_dan_.data_energistics.integration.entrypoint.common;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderCapabilities;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderRuntimeRegistration;
import com.fish_dan_.data_energistics.common.entrypoint.provider.AdaptivePatternProviderRegistrationFactory;
import com.fish_dan_.data_energistics.integration.ae.ae2.patternprovider.redstone.NativeRedstoneTuningDispatch;
import com.fish_dan_.data_energistics.integration.ae.appliedcreate.patternprovider.AppliedCreateAdaptiveRoute;

import com.loliball.appliedcreate.patternprovider.MechanicalCraftingPatternLogic;

/** Registers Applied Create mechanical pattern provider variants. */
@DataEnergisticsEntrypoint(requiredMods = "appliedcreate")
public final class AppliedCreatePlugin implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.patternProviders().registerRuntime(PatternProviderRuntimeRegistration.<MechanicalCraftingPatternLogic>builder()
                .registrationId(Data_Energistics.id("appliedcreate/mechanical_dispatch"))
                .providerType(MechanicalCraftingPatternLogic.class)
                .dispatchHooks(NativeRedstoneTuningDispatch::requesting)
                .build());
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
