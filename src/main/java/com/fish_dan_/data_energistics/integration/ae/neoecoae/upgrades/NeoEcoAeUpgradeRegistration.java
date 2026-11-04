package com.fish_dan_.data_energistics.integration.ae.neoecoae.upgrades;

import com.fish_dan_.data_energistics.common.data.DEItems;

import appeng.api.upgrades.Upgrades;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import org.jspecify.annotations.NullMarked;

/** Registers workstation upgrades only after Neo ECO AE Extension has loaded its blocks. */
@NullMarked
public final class NeoEcoAeUpgradeRegistration {

    private NeoEcoAeUpgradeRegistration() {}

    public static void register() {
        Upgrades.add(DEItems.MULTIPLICATION_CARD.get(), NEBlocks.INTEGRATED_WORKING_STATION.get(), 1,
                "block.neoecoae.integrated_working_station");
    }
}
