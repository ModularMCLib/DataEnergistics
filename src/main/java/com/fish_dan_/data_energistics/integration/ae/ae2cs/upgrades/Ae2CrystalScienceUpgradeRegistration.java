package com.fish_dan_.data_energistics.integration.ae.ae2cs.upgrades;

import com.fish_dan_.data_energistics.common.data.DEBlocks;
import com.fish_dan_.data_energistics.common.data.DEItems;

import appeng.api.upgrades.Upgrades;

import io.github.lounode.ae2cs.common.init.AECSBlocks;
import io.github.lounode.ae2cs.common.init.AECSItems;
import io.github.lounode.ae2cs.common.init.AECSParts;
import org.jspecify.annotations.NullMarked;

/** Loaded during common setup only when AE2 Crystal Science is present. */
@NullMarked
public final class Ae2CrystalScienceUpgradeRegistration {

    private Ae2CrystalScienceUpgradeRegistration() {}

    /** Registers upgrade associations once, after item and block registration has completed. */
    public static void register() {
        var redstoneTuningCard = DEItems.REDSTONE_TUNING_CARD.get();
        Upgrades.add(redstoneTuningCard, AECSBlocks.SIMPLE_PATTERN_PROVIDER_BLOCK.get(), 1, "block.ae2cs.simple_pattern_provider");
        Upgrades.add(redstoneTuningCard, AECSParts.SIMPLE_PATTERN_PROVIDER_PART.get(), 1, "block.ae2cs.simple_pattern_provider");
        Upgrades.add(redstoneTuningCard, AECSBlocks.RESONATING_PATTERN_PROVIDER_BLOCK.get(), 1, "block.ae2cs.resonating_pattern_provider");
        Upgrades.add(redstoneTuningCard, AECSParts.RESONATING_PATTERN_PROVIDER_PART.get(), 1, "block.ae2cs.resonating_pattern_provider");
        Upgrades.add(redstoneTuningCard, AECSBlocks.EX_RESONATING_PATTERN_PROVIDER_BLOCK.get(), 1, "block.ae2cs.resonating_pattern_provider");
        Upgrades.add(redstoneTuningCard, AECSParts.EX_RESONATING_PATTERN_PROVIDER_PART.get(), 1, "block.ae2cs.resonating_pattern_provider");
        Upgrades.add(redstoneTuningCard, AECSBlocks.METEORITE_PATTERN_PROVIDER_BLOCK.get(), 1, "block.ae2cs.meteorite_pattern_provider");
        Upgrades.add(redstoneTuningCard, AECSParts.METEORITE_PATTERN_PROVIDER_PART.get(), 1, "block.ae2cs.meteorite_pattern_provider");
        Upgrades.add(AECSItems.crystalGrowthCard.get(), DEBlocks.DATA_SANCTUM_INTERFACE.get(), 1, "block.data_energistics.data_sanctum_interface");
        Upgrades.add(AECSItems.crystalGrowthCard.get(), DEItems.DATA_SANCTUM_INTERFACE_PART.get(), 1, "item.data_energistics.data_sanctum_interface_part");
    }
}
