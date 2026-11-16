package com.fish_dan_.data_energistics.common.pattern.provider.redstone;

import com.fish_dan_.data_energistics.Data_Energistics;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;

import net.minecraft.server.level.ServerLevel;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/** Per-output calculation and submission boundary for redstone-triggered provider requests. */
@NullMarked
public final class RedstoneTuningCraftingRequests {

    private RedstoneTuningCraftingRequests() {}

    public static void requestPrimaryOutputs(ServerLevel level, @Nullable IGrid grid,
                                             IActionSource actionSource, List<IPatternDetails> patterns) {
        if (grid == null) {
            return;
        }
        var crafting = grid.getCraftingService();
        for (var pattern : patterns) {
            try {
                var output = pattern.getPrimaryOutput();
                if (output == null || output.what() == null || output.amount() <= 0) {
                    Data_Energistics.LOGGER.error("Cannot request invalid primary output from pattern {} in dimension {}",
                            pattern.getClass().getName(), level.dimension().location());
                    continue;
                }
                var calculation = crafting.beginCraftingCalculation(level, () -> actionSource,
                        output.what(), output.amount(), CalculationStrategy.CRAFT_LESS);
                Thread.ofVirtual().name("data-energistics-redstone-auto-request").start(
                        () -> awaitPlan(level, crafting, actionSource, output, calculation));
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error("Failed to schedule redstone crafting request for pattern {} in dimension {}",
                        pattern.getClass().getName(), level.dimension().location(), exception);
            }
        }
    }

    private static void awaitPlan(ServerLevel level, ICraftingService crafting, IActionSource source,
                                  GenericStack output, Future<ICraftingPlan> calculation) {
        try {
            var plan = calculation.get();
            if (plan == null || plan.simulation()) {
                return;
            }
            level.getServer().execute(() -> {
                try {
                    crafting.submitJob(plan, null, null, true, source);
                } catch (RuntimeException exception) {
                    Data_Energistics.LOGGER.error("Failed to submit redstone crafting request for {} x {} in dimension {}",
                            output.amount(), output.what(), level.dimension().location(), exception);
                }
            });
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            Data_Energistics.LOGGER.warn("Interrupted redstone crafting calculation for {} x {} in dimension {}",
                    output.amount(), output.what(), level.dimension().location(), exception);
        } catch (ExecutionException | RuntimeException exception) {
            Data_Energistics.LOGGER.error("Failed redstone crafting calculation for {} x {} in dimension {}",
                    output.amount(), output.what(), level.dimension().location(), exception);
        }
    }
}
