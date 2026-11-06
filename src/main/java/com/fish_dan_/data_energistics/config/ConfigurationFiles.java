package com.fish_dan_.data_energistics.config;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

/** Single file-location registry. Safe during early bootstrap; does not register schemas or perform I/O. */
public final class ConfigurationFiles {

    public static final String MAIN = Data_Energistics.MOD_ID + "/" + Data_Energistics.MOD_ID;
    public static final String EXTRACTOR_RULES_ID = Data_Energistics.MOD_ID + "_data_extractor_rules";
    public static final String EXTRACTOR_RULES = Data_Energistics.MOD_ID + "/data_extractor_rules";

    public static final Path ROOT = Data_Energistics.getGameDir().resolve(FMLPaths.CONFIGDIR.relative());
    public static final Path DIRECTORY = ROOT.resolve(Data_Energistics.MOD_ID);
    public static final Path PATTERN_ENCODING = DIRECTORY.resolve("client_preferences.json");
    public static final Path PLAYER_TERMINAL = DIRECTORY.resolve("player_terminal.json");
    public static final Path CRAFTING_PLAN_TREE = DIRECTORY.resolve("crafting_plan_tree.json");
    public static final Path TACTICAL_HUD = DIRECTORY.resolve("tactical_hud.json");
    public static final Path ORBITAL_HUD = DIRECTORY.resolve("orbital_hud.json");

    private ConfigurationFiles() {}
}
