package com.fish_dan_.data_energistics.integration.entrypoint.common;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderRuntimeRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.menu.PatternProviderMenuRegistration;
import com.fish_dan_.data_energistics.common.entrypoint.provider.AdaptivePatternProviderRegistrationFactory;

import appeng.api.upgrades.IUpgradeableObject;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.implementations.PatternProviderMenu;

/** Registers the native AE2 pattern provider variant. */
@DataEnergisticsEntrypoint
public final class AdaptivePatternProviderAe2Registration implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.patternProviders().registerRuntime(PatternProviderRuntimeRegistration.<IUpgradeableObject>builder()
                .registrationId(Data_Energistics.id("ae2/upgrade_inventory"))
                .providerType(IUpgradeableObject.class)
                .upgradeInventorySource(IUpgradeableObject::getUpgrades)
                .build());
        registry.patternProviders().registerRuntime(PatternProviderRuntimeRegistration.<PatternProviderLogicHost>builder()
                .registrationId(Data_Energistics.id("ae2/provider_logic_upgrades"))
                .providerType(PatternProviderLogicHost.class)
                .priority(-100)
                .upgradeInventorySource(host -> host.getLogic() instanceof IUpgradeableObject logic ? logic.getUpgrades() : null)
                .build());
        registry.patternProviders().registerMenu(PatternProviderMenuRegistration.redstoneTuning(PatternProviderMenu.class));
        registry.adaptivePatternProviders().register(AdaptivePatternProviderRegistrationFactory.fixed(
                "ae2/standard",
                stack -> AEBlocks.PATTERN_PROVIDER.is(stack) || AEParts.PATTERN_PROVIDER.is(stack),
                9,
                AdaptivePatternProviderRegistrationFactory.capabilities()));
    }
}
