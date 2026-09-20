package com.fish_dan_.data_energistics.integration.entrypoint;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.client.DataEnergisticsClientPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.client.DataEnergisticsClientRegistry;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderToolbarActions;
import com.fish_dan_.data_energistics.api.registry.adaptive.client.AdaptivePatternProviderToolbarButton;
import com.fish_dan_.data_energistics.client.widget.DataExtractorToggleButton;

import appeng.client.gui.Icon;

/**
 * Client-only Advanced AE input-filter control for Adaptive providers.
 */
@DataEnergisticsEntrypoint(clientOnly = true, requiredMods = "advanced_ae")
public final class AdaptivePatternProviderAdvancedAeToolbar implements DataEnergisticsClientPlugin {

    @Override
    public void register(DataEnergisticsClientRegistry registry) {
        var toolbar = registry.adaptivePatternProviderToolbar();
        toolbar.register(AdaptivePatternProviderToolbarActions.FILTERED_IMPORT, 600, context -> {
            var menu = context.menu();
            var button = new DataExtractorToggleButton(Icon.FILTER_ON_EXTRACT_ENABLED, Icon.FILTER_ON_EXTRACT_DISABLED,
                    "button.data_energistics.adaptive_pattern_provider.filtered_import",
                    "button.data_energistics.adaptive_pattern_provider.filtered_import.enabled",
                    "button.data_energistics.adaptive_pattern_provider.filtered_import.disabled",
                    menu::sendSetAdvancedAeFilteredImport);
            return new AdaptivePatternProviderToolbarButton(button,
                    () -> button.setState(menu.isAdvancedAeFilteredImportEnabled()));
        });
    }
}
