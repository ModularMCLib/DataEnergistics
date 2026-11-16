package com.fish_dan_.data_energistics.integration.entrypoint.common;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderCapabilities;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderToolbarActions;
import com.fish_dan_.data_energistics.api.registry.provider.callback.PatternProviderMenuOpenResult;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderRuntimeRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.menu.PatternProviderMenuRegistration;
import com.fish_dan_.data_energistics.common.entrypoint.provider.AdaptivePatternProviderRegistrationFactory;
import com.fish_dan_.data_energistics.integration.ae.advancedae.patternprovider.AdvancedAeAdaptiveRoute;

import appeng.menu.locator.MenuLocators;
import appeng.parts.AEBasePart;

import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost;
import net.pedroksl.advanced_ae.gui.advpatternprovider.AdvPatternProviderMenu;

/** Registers Advanced AE pattern provider variants. */
@DataEnergisticsEntrypoint(requiredMods = "advanced_ae")
public final class AdvancedAePlugin implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.patternProviders().registerMenu(PatternProviderMenuRegistration.redstoneTuning(AdvPatternProviderMenu.class));
        registry.patternProviders().registerRuntime(PatternProviderRuntimeRegistration
                .<AdvPatternProviderLogicHost>builder()
                .registrationId(Data_Energistics.id("advanced_ae/provider_runtime"))
                .providerType(AdvPatternProviderLogicHost.class)
                .menuOpener((host, player) -> {
                    var sourceMenu = player.containerMenu;
                    var locator = host instanceof AEBasePart part ?
                            MenuLocators.forPart(part) : MenuLocators.forBlockEntity(host.getBlockEntity());
                    host.openMenu(player, locator);
                    return player.containerMenu != sourceMenu ?
                            PatternProviderMenuOpenResult.OPENED : PatternProviderMenuOpenResult.DENIED;
                })
                .build());
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
