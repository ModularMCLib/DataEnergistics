package com.fish_dan_.data_energistics.integration.ae.advancedae.upgrades;

import com.fish_dan_.data_energistics.common.data.DEItems;

import appeng.api.upgrades.Upgrades;

import net.pedroksl.advanced_ae.common.definitions.AAEBlocks;
import net.pedroksl.advanced_ae.common.definitions.AAEItems;

import org.jspecify.annotations.NullMarked;

/** Loaded during common setup only when AdvancedAE is present. */
@NullMarked
public final class AdvancedAeUpgradeRegistration {

    private AdvancedAeUpgradeRegistration() {}

    /** Registers upgrade associations once, after item and block registration has completed. */
    public static void register() {
        var redstoneTuningCard = DEItems.REDSTONE_TUNING_CARD.get();
        Upgrades.add(redstoneTuningCard, AAEBlocks.SMALL_ADV_PATTERN_PROVIDER, 1, "block.advanced_ae.small_adv_pattern_provider");
        Upgrades.add(redstoneTuningCard, AAEItems.SMALL_ADV_PATTERN_PROVIDER, 1, "block.advanced_ae.small_adv_pattern_provider");
        Upgrades.add(redstoneTuningCard, AAEBlocks.ADV_PATTERN_PROVIDER, 1, "block.advanced_ae.adv_pattern_provider");
        Upgrades.add(redstoneTuningCard, AAEItems.ADV_PATTERN_PROVIDER, 1, "block.advanced_ae.adv_pattern_provider");
    }
}
