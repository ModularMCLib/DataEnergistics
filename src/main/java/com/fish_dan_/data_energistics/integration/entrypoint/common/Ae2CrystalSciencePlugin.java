package com.fish_dan_.data_energistics.integration.entrypoint.common;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderCapabilities;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderToolbarActions;
import com.fish_dan_.data_energistics.api.registry.machine.capacity.CraftingMachineCapacityRegistration;
import com.fish_dan_.data_energistics.integration.ae.ae2cs.patternprovider.Ae2CrystalScienceAdaptiveRoute;
import com.fish_dan_.data_energistics.integration.ae.ae2cs.patternprovider.Ae2CrystalScienceCapacity;
import com.fish_dan_.data_energistics.integration.ae.ae2cs.patternprovider.Ae2CrystalScienceMeteoriteRoute;
import com.fish_dan_.data_energistics.registry.AdaptivePatternProviderRegistrationFactory;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectList;

/** Registers AE2 Crystal Science pattern-provider variants and routes. */
@DataEnergisticsEntrypoint(requiredMods = "ae2cs")
public final class Ae2CrystalSciencePlugin implements DataEnergisticsPlugin {

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
        for (String id : ObjectList.of("circuit_etcher", "crystal_aggregator", "crystal_pulverizer",
                "quartz_grindstone", "crystal_growth_chamber", "entropy_variation_reaction_chamber")) {
            registry.craftingMachines().registerCapacity(CraftingMachineCapacityRegistration.blockEntity(
                    Data_Energistics.id("ae2cs_" + id + "_capacity"),
                    ResourceLocation.fromNamespaceAndPath("ae2cs", id),
                    Ae2CrystalScienceCapacity::capture));
        }
    }
}
