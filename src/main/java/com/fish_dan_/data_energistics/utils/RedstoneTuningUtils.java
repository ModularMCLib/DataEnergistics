package com.fish_dan_.data_energistics.utils;

import com.fish_dan_.data_energistics.common.data.DEItems;
import com.fish_dan_.data_energistics.common.entrypoint.provider.LivePatternProviderBindingRegistry;

import appeng.api.upgrades.IUpgradeInventory;

import org.jspecify.annotations.Nullable;

public final class RedstoneTuningUtils {

    private RedstoneTuningUtils() {}

    public static boolean hasRedstoneTuningCard(Object host, @Nullable IUpgradeInventory fallbackInventory) {
        if (containsCard(fallbackInventory)) {
            return true;
        }

        IUpgradeInventory hostInventory = LivePatternProviderBindingRegistry.get().runtimeAdapters().upgradeInventory(host);
        return hostInventory != fallbackInventory && containsCard(hostInventory);
    }

    private static boolean containsCard(@Nullable IUpgradeInventory inventory) {
        return inventory != null && inventory.getInstalledUpgrades(DEItems.REDSTONE_TUNING_CARD.get()) > 0;
    }
}
