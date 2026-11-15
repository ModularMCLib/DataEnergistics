package com.fish_dan_.data_energistics.common.crafting.trinity.planning.diagnostic;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;

/** Fixed failure causes carry their classification and fallback policy directly. */
public enum TrinityDiagnosticReason {

    CAPTURE_BINDING_LIMIT(TrinityPlanningDiagnosticCode.VARIANT_LIMIT, true),
    CAPTURE_CANCELLED(TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED, false),
    CAPTURE_DEADLINE(TrinityPlanningDiagnosticCode.MIP_TIMEOUT, false),
    CANDIDATE_VERIFICATION(TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT, false),
    COMPONENT_ALIASED_MATERIAL(TrinityPlanningDiagnosticCode.UNSUPPORTED_PATTERN, true),
    CORRECTION_DOMAIN(TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT, false),
    CORRECTION_INTEGER(TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT, false),
    CORRECTION_VERIFICATION(TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT, false),
    EXPRESSION_COUNT(TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT, false),
    INTERNAL_ERROR(TrinityPlanningDiagnosticCode.INTERNAL_ERROR, false),
    NUMERIC_ENVELOPE(TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT, false),
    RADIX_MODEL_LIMIT(TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT, false),
    REQUEST_BINDING_LIMIT(TrinityPlanningDiagnosticCode.VARIANT_LIMIT, true),
    SOLVER_STACK_DEPTH(TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT, false),
    UNBOUNDED_RADIX_VARIABLE(TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT, false),
    VARIABLE_COUNT(TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT, false),
    VARIABLE_DOMAIN(TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT, false);

    public final TrinityPlanningDiagnosticCode code;
    public final boolean legacyPattern;

    TrinityDiagnosticReason(TrinityPlanningDiagnosticCode code, boolean legacyPattern) {
        this.code = code;
        this.legacyPattern = legacyPattern;
    }
}
