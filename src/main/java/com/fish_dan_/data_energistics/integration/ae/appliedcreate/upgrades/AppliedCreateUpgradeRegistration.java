package com.fish_dan_.data_energistics.integration.ae.appliedcreate.upgrades;

import com.fish_dan_.data_energistics.common.data.DEItems;

import appeng.api.upgrades.Upgrades;

import com.loliball.appliedcreate.AppliedCreate;
import org.jspecify.annotations.NullMarked;

/** Loaded during common setup only when AppliedCreate is present. */
@NullMarked
public final class AppliedCreateUpgradeRegistration {

    private AppliedCreateUpgradeRegistration() {}

    /** Registers upgrade associations once, after item and block registration has completed. */
    public static void register() {
        var redstoneTuningCard = DEItems.REDSTONE_TUNING_CARD.get();
        var definitions = AppliedCreate.Companion;
        Upgrades.add(redstoneTuningCard, definitions.getANDESITE_PATTERN_PROVIDER_BLOCK().get(), 1, "block.appliedcreate.andesite_pattern_provider");
        Upgrades.add(redstoneTuningCard, definitions.getANDESITE_PATTERN_PROVIDER_PART_ITEM().get(), 1, "block.appliedcreate.andesite_pattern_provider");
        Upgrades.add(redstoneTuningCard, definitions.getBRASS_PATTERN_PROVIDER_BLOCK().get(), 1, "block.appliedcreate.brass_pattern_provider");
        Upgrades.add(redstoneTuningCard, definitions.getBRASS_PATTERN_PROVIDER_PART_ITEM().get(), 1, "block.appliedcreate.brass_pattern_provider");
    }
}
