package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.search;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.util.FastUtilCollections;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;

/**
 * Creates the stable translated diagnostics shared by radix orchestration and objective probing.
 */
public final class TrinityRadixDiagnostics {

    private TrinityRadixDiagnostics() {}

    /**
     * Reports a decoded integer candidate that failed exact replay.
     */
    public static <T> TrinityAlgorithmResult<T> inexact(String constraint, String value) {
        return failure(
                TrinityPlanningDiagnosticCode.MIP_INEXACT_RESULT,
                "gui.data_energistics.trinity_planning.diagnostic.inexact_result",
                FastUtilCollections.mapOf("constraint", constraint, "value", value));
    }

    /**
     * Creates a typed failed result without duplicating translation construction.
     */
    public static <T> TrinityAlgorithmResult<T> failure(
                                                        TrinityPlanningDiagnosticCode code,
                                                        String translationKey,
                                                        Object2ObjectMap<String, String> metadata) {
        return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                code,
                Component.translatable(translationKey),
                metadata));
    }

    /**
     * Reports the exact objective and digit at which the shared planning deadline was exhausted.
     */
    public static <T> TrinityAlgorithmResult<T> timeout(
                                                        TrinityRadixSolverMetrics metrics,
                                                        String state,
                                                        String objective,
                                                        int digit) {
        return failure(
                TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                "gui.data_energistics.trinity_planning.mip.timeout",
                FastUtilCollections.mapOf(
                        "passes", Integer.toString(metrics.passes()),
                        "state", state,
                        "objective", objective,
                        "digit", Integer.toString(digit)));
    }
}
