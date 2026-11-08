package com.fish_dan_.data_energistics.config.client;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.config.ConfigurationFiles;
import com.fish_dan_.data_energistics.config.client.crafting.CraftingPlanTreePreferences;
import com.fish_dan_.data_energistics.config.client.hud.OrbitalHudPreferences;
import com.fish_dan_.data_energistics.config.client.hud.TacticalHudConfiguration;
import com.fish_dan_.data_energistics.config.client.patternencoding.PatternEncodingClientPreferences;
import com.fish_dan_.data_energistics.config.client.patternencoding.PatternEncodingConfiguration;
import com.fish_dan_.data_energistics.config.client.terminal.TerminalPreferences;
import com.fish_dan_.data_energistics.config.persistence.JsonConfiguration;

import java.time.Clock;

/** Registers and directly initializes client files using the same typed schema and persistence contract. */
public final class ClientConfigurations {

    public static final JsonConfiguration<CraftingPlanTreePreferences> CRAFTING_PLAN_TREE = new JsonConfiguration<>(
            ConfigurationFiles.CRAFTING_PLAN_TREE, CraftingPlanTreePreferences.CODEC, () -> CraftingPlanTreePreferences.DEFAULT);
    public static final JsonConfiguration<TerminalPreferences> PLAYER_TERMINAL = new JsonConfiguration<>(
            ConfigurationFiles.PLAYER_TERMINAL, TerminalPreferences.CODEC, () -> TerminalPreferences.DEFAULT);
    public static final JsonConfiguration<OrbitalHudPreferences> ORBITAL_HUD = new JsonConfiguration<>(
            ConfigurationFiles.ORBITAL_HUD, OrbitalHudPreferences.CODEC, () -> OrbitalHudPreferences.DEFAULT);
    public static final JsonConfiguration<TacticalHudConfiguration> TACTICAL_HUD = new JsonConfiguration<>(
            ConfigurationFiles.TACTICAL_HUD, TacticalHudConfiguration.CODEC, () -> TacticalHudConfiguration.DEFAULT);
    public static final JsonConfiguration<PatternEncodingConfiguration> PATTERN_ENCODING = new JsonConfiguration<>(
            ConfigurationFiles.PATTERN_ENCODING, PatternEncodingConfiguration.CODEC, () -> PatternEncodingConfiguration.DEFAULT);
    public static final PatternEncodingClientPreferences PATTERN_ENCODING_PREFERENCES = new PatternEncodingClientPreferences(
            PATTERN_ENCODING, Data_Energistics::isClientThread, Clock.systemUTC());

    private ClientConfigurations() {}
}
