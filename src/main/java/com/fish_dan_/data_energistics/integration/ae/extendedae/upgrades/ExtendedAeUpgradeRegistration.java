package com.fish_dan_.data_energistics.integration.ae.extendedae.upgrades;

import com.fish_dan_.data_energistics.common.data.DEItems;

import appeng.api.upgrades.Upgrades;

import com.glodblock.github.extendedae.common.EAESingletons;
import org.jspecify.annotations.NullMarked;

/** Loaded during common setup only when ExtendedAE is present. */
@NullMarked
public final class ExtendedAeUpgradeRegistration {

    private ExtendedAeUpgradeRegistration() {}

    /** Registers upgrade associations once, after item and block registration has completed. */
    public static void register() {
        var redstoneTuningCard = DEItems.REDSTONE_TUNING_CARD.get();
        Upgrades.add(redstoneTuningCard, EAESingletons.EX_PATTERN_PROVIDER, 1, "block.extendedae.ex_pattern_provider");
        Upgrades.add(redstoneTuningCard, EAESingletons.EX_PATTERN_PROVIDER_PART, 1, "block.extendedae.ex_pattern_provider");
    }
}
