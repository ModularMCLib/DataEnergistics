package com.fish_dan_.data_energistics.integration.entrypoint.client;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.client.DataEnergisticsClientPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.client.DataEnergisticsClientRegistry;
import com.fish_dan_.data_energistics.api.registry.provider.client.PatternProviderScreenRegistration;
import com.fish_dan_.data_energistics.client.screen.AePatternProviderSlotStylePatch;
import com.fish_dan_.data_energistics.client.screen.patternprovider.RedstoneTuningScreenBinding;

import appeng.menu.implementations.PatternProviderMenu;

/** Registers AE2's native provider screen extension. */
@DataEnergisticsEntrypoint(clientOnly = true)
public final class Ae2ClientPlugin implements DataEnergisticsClientPlugin {

    @Override
    public void register(DataEnergisticsClientRegistry registry) {
        var slotStyle = AePatternProviderSlotStylePatch.inlineSingleUpgrade("ae2:pattern_provider");
        registry.patternProviderScreens().register(new PatternProviderScreenRegistration(
                PatternProviderMenu.class, context -> RedstoneTuningScreenBinding.create(context, slotStyle)));
    }
}
