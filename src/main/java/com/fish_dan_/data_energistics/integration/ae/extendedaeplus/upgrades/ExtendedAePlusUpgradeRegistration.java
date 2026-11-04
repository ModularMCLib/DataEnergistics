package com.fish_dan_.data_energistics.integration.ae.extendedaeplus.upgrades;

import com.fish_dan_.data_energistics.common.data.DEItems;

import appeng.api.upgrades.Upgrades;

import com.extendedae_plus.init.ModItems;
import org.jspecify.annotations.NullMarked;

/** Loaded during common setup only when ExtendedAE Plus is present. */
@NullMarked
public final class ExtendedAePlusUpgradeRegistration {

    private ExtendedAePlusUpgradeRegistration() {}

    /** Registers each machine's single multiplication-card allowance. */
    public static void register() {
        var multiplicationCard = DEItems.MULTIPLICATION_CARD.get();
        Upgrades.add(multiplicationCard, ModItems.CIRCUIT_CUTTER_PLUS.get(), 1, "block.extendedae_plus.circuit_cutter_plus");
        Upgrades.add(multiplicationCard, ModItems.CRYSTAL_ASSEMBLER_PLUS.get(), 1, "block.extendedae_plus.crystal_assembler_plus");
    }
}
