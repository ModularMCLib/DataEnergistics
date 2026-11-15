package com.fish_dan_.data_energistics.common.crafting.trinity.planning;

/**
 * Stable machine-readable reasons for Trinity planning fallback, rejection or runtime suspension.
 */
public enum TrinityPlanningDiagnosticCode {

    NO_PRODUCTIVE_CYCLE("gui.data_energistics.trinity_planning.diagnostic.no_productive_cycle", false),
    SCC_KEY_LIMIT("gui.data_energistics.trinity_planning.diagnostic.scc_key_limit", false),
    VARIANT_LIMIT("gui.data_energistics.trinity_planning.diagnostic.variant_limit", false),
    MIP_TIMEOUT("gui.data_energistics.trinity_planning.diagnostic.timeout", true),
    MIP_NO_INTEGER_SOLUTION("gui.data_energistics.trinity_planning.diagnostic.no_integer_solution", false),
    MIP_INEXACT_RESULT("gui.data_energistics.trinity_planning.diagnostic.inexact_result", false),
    NO_EXECUTABLE_ORDER("gui.data_energistics.trinity_planning.diagnostic.no_executable_order", false),
    ORDER_SEARCH_LIMIT("gui.data_energistics.trinity_planning.diagnostic.search_limit", false),
    INSUFFICIENT_INPUT("gui.data_energistics.trinity_planning.diagnostic.insufficient_input", false),
    ARITHMETIC_OVERFLOW("gui.data_energistics.trinity_planning.diagnostic.arithmetic_overflow", false),
    STALE_GRAPH("gui.data_energistics.trinity_planning.graph_unavailable", false),
    NO_ELIGIBLE_TRINITY_CPU("gui.data_energistics.trinity_planning.large_amount_requires_trinity_cpu", false),
    PLANNER_QUEUE_FULL("gui.data_energistics.trinity_planning.diagnostic.planner_queue_full", true),
    UNSUPPORTED_PATTERN("gui.data_energistics.trinity_planning.diagnostic.unsupported_pattern", false),
    CALCULATION_CANCELLED("gui.data_energistics.trinity_planning.diagnostic.cancelled", true),
    RUNTIME_DEADLOCK("gui.data_energistics.trinity_planning.diagnostic.no_executable_order", false),
    INTERNAL_ERROR("gui.data_energistics.trinity_planning.diagnostic.internal_error", true);

    public final String translationKey;

    /**
     * Identifies operationally transient failures that may succeed when the same immutable request is retried later.
     * All failed planning results stay out of completed computation caches regardless of this classification.
     *
     */
    public final boolean transientPlanningFailure;

    TrinityPlanningDiagnosticCode(String translationKey, boolean transientPlanningFailure) {
        this.translationKey = translationKey;
        this.transientPlanningFailure = transientPlanningFailure;
    }
}
