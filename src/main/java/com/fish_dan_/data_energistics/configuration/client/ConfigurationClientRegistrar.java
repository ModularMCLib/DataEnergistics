package com.fish_dan_.data_energistics.configuration.client;

import com.fish_dan_.data_energistics.configuration.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.configuration.DataExtractorRulesConfiguration;
import com.fish_dan_.data_energistics.configuration.rules.schema.DataProductionRuleConfigurationAdapter;
import com.fish_dan_.data_energistics.configuration.rules.schema.ExtractorOutputEntry;
import com.fish_dan_.data_energistics.configuration.rules.schema.MimeticCarrierEntry;

import dev.toma.configuration.client.ConfigurationClient;
import dev.toma.configuration.config.adapter.TypeMatcher;

/** Adds client-only presentation behavior to the Configuration screen. */
public final class ConfigurationClientRegistrar {

    private ConfigurationClientRegistrar() {}

    public static void register() {
        ConfigurationClient.setCustomConfigTheme(
                DataEnergisticsConfiguration.HOLDER,
                theme -> theme.registerDisplayAdapter(
                        TypeMatcher.matchEnum(),
                        new CraftingQuantityModeDisplayAdapter()));
        ConfigurationClient.setCustomConfigTheme(
                DataExtractorRulesConfiguration.HOLDER,
                theme -> {
                    theme.registerDisplayAdapter(
                            TypeMatcher.matchObject(),
                            new StructuredRuleEntryDisplayAdapter.EntryDisplayAdapter());
                    theme.registerDisplayAdapter(
                            StructuredRuleEntryDisplayAdapter.arrayMatcher(
                                    DataProductionRuleConfigurationAdapter.MIMETIC_ARRAY_MATCHER,
                                    MimeticCarrierEntry[].class),
                            new StructuredRuleEntryDisplayAdapter<>(
                                    DataProductionRuleConfigurationAdapter.mimeticDefinition()));
                    theme.registerDisplayAdapter(
                            StructuredRuleEntryDisplayAdapter.arrayMatcher(
                                    DataProductionRuleConfigurationAdapter.EXTRACTOR_ARRAY_MATCHER,
                                    ExtractorOutputEntry[].class),
                            new StructuredRuleEntryDisplayAdapter<>(
                                    DataProductionRuleConfigurationAdapter.extractorDefinition()));
                });
    }
}
