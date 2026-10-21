package com.fish_dan_.data_energistics.integration.ae.ae2.accessor.patternprovider;

import appeng.api.upgrades.IUpgradeInventory;

public interface PatternProviderHostAccessor extends RedstoneTuningAwareHost {

    IUpgradeInventory dataEnergistics$getRedstoneTuningUpgrades();
}
