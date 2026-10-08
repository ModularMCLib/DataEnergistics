package com.fish_dan_.data_energistics.util;

import com.fish_dan_.data_energistics.registry.DEItems;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.security.IActionSource;

import net.minecraft.server.level.ServerLevel;

import org.jspecify.annotations.Nullable;

import java.util.List;

public final class RedstoneTuningUtils {

    private RedstoneTuningUtils() {}

    public static void requestPrimaryOutputs(ServerLevel level,
                                             IGrid grid,
                                             IActionSource actionSource,
                                             List<IPatternDetails> patterns) {
        if (grid == null || actionSource == null || patterns == null || patterns.isEmpty()) {
            return;
        }

        var craftingService = grid.getCraftingService();
        for (var pattern : patterns) {
            if (pattern == null) {
                continue;
            }

            var primaryOutput = pattern.getPrimaryOutput();
            if (primaryOutput == null || primaryOutput.what() == null || primaryOutput.amount() <= 0) {
                continue;
            }

            try {
                var planFuture = craftingService.beginCraftingCalculation(
                        level,
                        () -> actionSource,
                        primaryOutput.what(),
                        Math.max(1L, primaryOutput.amount()),
                        CalculationStrategy.CRAFT_LESS);
                var server = level.getServer();

                Thread.ofVirtual().name("data-energistics-redstone-auto-request").start(() -> {
                    try {
                        var plan = planFuture.get();
                        if (plan == null || plan.simulation()) {
                            return;
                        }

                        server.execute(() -> craftingService.submitJob(plan, null, null, true, actionSource));
                    } catch (Exception ignored) {}
                });
            } catch (Exception ignored) {}
        }
    }

    public static boolean hasRedstoneTuningCard(Object host, @Nullable IUpgradeInventory fallbackInventory) {
        if (containsCard(fallbackInventory)) {
            return true;
        }

        IUpgradeInventory hostInventory = resolveHostUpgradeInventory(host);
        return hostInventory != fallbackInventory && containsCard(hostInventory);
    }

    public static @Nullable IUpgradeInventory resolveHostUpgradeInventory(Object host) {
        if (host instanceof IUpgradeableObject upgradeableObject) {
            return upgradeableObject.getUpgrades();
        }

        IUpgradeInventory directInventory = invokeUpgradeInventoryMethod(host, "getUpgrades");
        if (directInventory != null) {
            return directInventory;
        }

        Object logic = ReflectionAccess.invokeNoArg(host, "getLogic");
        if (logic == null) {
            return null;
        }

        if (logic instanceof IUpgradeableObject upgradeableLogic) {
            return upgradeableLogic.getUpgrades();
        }

        return invokeUpgradeInventoryMethod(logic, "getUpgrades");
    }

    private static @Nullable IUpgradeInventory invokeUpgradeInventoryMethod(
            Object target, String methodName) {
        Object result = ReflectionAccess.invokeNoArg(target, methodName);
        return result instanceof IUpgradeInventory upgradeInventory ? upgradeInventory : null;
    }

    private static boolean containsCard(@Nullable IUpgradeInventory inventory) {
        return inventory != null && inventory.getInstalledUpgrades(DEItems.REDSTONE_TUNING_CARD.get()) > 0;
    }
}
