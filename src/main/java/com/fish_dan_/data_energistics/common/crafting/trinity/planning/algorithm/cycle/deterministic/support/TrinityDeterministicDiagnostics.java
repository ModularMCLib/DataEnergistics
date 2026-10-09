package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.support;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.opportunity.TrinityPlanningAttempt;
import com.fish_dan_.data_energistics.util.FastUtilCollections;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;

/**
 * Centralises stable translated diagnostics shared by deterministic applicability, firing, and proof stages.
 */
public final class TrinityDeterministicDiagnostics {

    public static final String UNSUPPORTED_PATTERN_KEY = "gui.data_energistics.trinity_planning.diagnostic.unsupported_pattern";
    public static final String NO_EXECUTABLE_ORDER_KEY = "gui.data_energistics.trinity_planning.diagnostic.no_executable_order";

    private static final String CANCELLED_KEY = "gui.data_energistics.trinity_planning.diagnostic.cancelled";
    private static final String TIMEOUT_KEY = "gui.data_energistics.trinity_planning.diagnostic.timeout";
    private static final String SEARCH_LIMIT_KEY = "gui.data_energistics.trinity_planning.diagnostic.search_limit";

    private TrinityDeterministicDiagnostics() {}

    public static StopState stopState(TrinityPlanningControl control) {
        if (control.cancellationRequested()) {
            return StopState.CANCELLED;
        }
        return control.deadlineExceeded() ? StopState.DEADLINE_EXCEEDED : StopState.RUNNING;
    }

    public static <T> TrinityAlgorithmResult<T> stopped(StopState state) {
        return state == StopState.CANCELLED ?
                failure(
                        TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                        CANCELLED_KEY,
                        FastUtilCollections.mapOf()) :
                failure(
                        TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                        TIMEOUT_KEY,
                        FastUtilCollections.mapOf("phase", "deterministic_component"));
    }

    public static <T> TrinityAlgorithmResult<T> unsupported() {
        return failure(
                TrinityPlanningDiagnosticCode.UNSUPPORTED_PATTERN,
                UNSUPPORTED_PATTERN_KEY,
                FastUtilCollections.mapOf("phase", "deterministic_component"));
    }

    public static <T> TrinityPlanningAttempt<T> notApplicable() {
        return TrinityPlanningAttempt.notApplicable(unsupported().diagnostic());
    }

    public static <T> TrinityAlgorithmResult<T> searchLimit(int limit, int states) {
        return failure(
                TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                SEARCH_LIMIT_KEY,
                FastUtilCollections.mapOf("limit", Integer.toString(limit), "states", Integer.toString(states)));
    }

    public static <T> TrinityAlgorithmResult<T> failure(
                                                        TrinityPlanningDiagnosticCode code,
                                                        String translationKey,
                                                        Object2ObjectMap<String, String> metadata) {
        return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                code,
                Component.translatable(translationKey),
                metadata));
    }

    public enum StopState {
        RUNNING,
        CANCELLED,
        DEADLINE_EXCEEDED
    }
}
