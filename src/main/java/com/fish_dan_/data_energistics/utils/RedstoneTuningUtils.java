package com.fish_dan_.data_energistics.utils;

import com.fish_dan_.data_energistics.common.data.DEItems;
import com.fish_dan_.data_energistics.common.entrypoint.provider.PatternProviderRuntimeBindings;

import appeng.api.upgrades.IUpgradeInventory;

import org.jspecify.annotations.Nullable;

public final class RedstoneTuningUtils {

    private RedstoneTuningUtils() {}

    public static boolean hasRedstoneTuningCard(Object host, @Nullable IUpgradeInventory fallbackInventory) {
        if (containsCard(fallbackInventory)) {
            return true;
        }

        IUpgradeInventory hostInventory = resolveHostUpgradeInventory(host);
        return hostInventory != fallbackInventory && containsCard(hostInventory);
    }

    public static @Nullable IUpgradeInventory resolveHostUpgradeInventory(Object host) {
        return PatternProviderRuntimeBindings.upgradeInventory(host);
    }

    private static boolean containsCard(@Nullable IUpgradeInventory inventory) {
        return inventory != null && inventory.getInstalledUpgrades(DEItems.REDSTONE_TUNING_CARD.get()) > 0;
    }
}
