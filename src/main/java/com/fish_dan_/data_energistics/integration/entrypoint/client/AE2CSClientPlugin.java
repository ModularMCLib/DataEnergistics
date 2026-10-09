package com.fish_dan_.data_energistics.integration.entrypoint.client;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.client.DataEnergisticsClientPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.client.DataEnergisticsClientRegistry;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderToolbarActions;
import com.fish_dan_.data_energistics.api.registry.adaptive.client.AdaptivePatternProviderToolbarButton;
import com.fish_dan_.data_energistics.client.widget.AecsPullModeButton;

/**
 * Client-only AE2CS target-storage extraction control for Adaptive providers.
 */
@DataEnergisticsEntrypoint(clientOnly = true, requiredMods = "ae2cs")
public final class AdaptivePatternProviderAe2CsToolbar implements DataEnergisticsClientPlugin {

    @Override
    public void register(DataEnergisticsClientRegistry registry) {
        var toolbar = registry.adaptivePatternProviderToolbar();
        toolbar.register(AdaptivePatternProviderToolbarActions.RESONATING_PULL, 700, context -> {
            var menu = context.menu();
            var button = new AecsPullModeButton(
                    "button.data_energistics.adaptive_pattern_provider.resonating_pull",
                    "button.data_energistics.adaptive_pattern_provider.resonating_pull.enabled",
                    "button.data_energistics.adaptive_pattern_provider.resonating_pull.disabled",
                    menu::sendSetResonatingPullEnabled);
            return new AdaptivePatternProviderToolbarButton(button,
                    () -> button.setState(menu.isResonatingPullEnabled()));
        });
    }
}
