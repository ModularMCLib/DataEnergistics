package com.fish_dan_.data_energistics.integration.ae.megacells.upgrades;

import com.fish_dan_.data_energistics.common.data.DEItems;

import appeng.api.upgrades.Upgrades;

import gripe._90.megacells.definition.MEGABlocks;
import org.jspecify.annotations.NullMarked;

/** Loaded during common setup only when MEGA Cells is present. */
@NullMarked
public final class MegaCellsUpgradeRegistration {

    private MegaCellsUpgradeRegistration() {}

    /** Registers upgrade associations once, after item and block registration has completed. */
    public static void register() {
        Upgrades.add(DEItems.REDSTONE_TUNING_CARD.get(), MEGABlocks.MEGA_PATTERN_PROVIDER, 1, "block.megacells.mega_pattern_provider");
    }
}
