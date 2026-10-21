package com.fish_dan_.data_energistics.integration.ae.appflux.upgrades;

import com.fish_dan_.data_energistics.common.data.DEBlocks;
import com.fish_dan_.data_energistics.common.data.DEItems;

import appeng.api.upgrades.Upgrades;

import com.glodblock.github.appflux.common.AFSingletons;
import org.jspecify.annotations.NullMarked;

/** Loaded during common setup only when Applied Flux is present. */
@NullMarked
public final class AppliedFluxUpgradeRegistration {

    private AppliedFluxUpgradeRegistration() {}

    /** Registers upgrade associations once, after item and block registration has completed. */
    public static void register() {
        Upgrades.add(AFSingletons.INDUCTION_CARD, DEBlocks.DATA_SANCTUM_INTERFACE.get(), 1, "block.data_energistics.data_sanctum_interface");
        Upgrades.add(AFSingletons.INDUCTION_CARD, DEItems.DATA_SANCTUM_INTERFACE_PART.get(), 1, "item.data_energistics.data_sanctum_interface_part");
        Upgrades.add(AFSingletons.INDUCTION_CARD, DEBlocks.ADAPTIVE_PATTERN_PROVIDER.get(), 1, "block.data_energistics.adaptive_pattern_provider");
        Upgrades.add(AFSingletons.INDUCTION_CARD, DEItems.ADAPTIVE_PATTERN_PROVIDER_PART.get(), 1, "block.data_energistics.adaptive_pattern_provider");
    }
}
