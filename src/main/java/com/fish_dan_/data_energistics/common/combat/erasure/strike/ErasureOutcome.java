package com.fish_dan_.data_energistics.common.combat.erasure.strike;

/** Actual per-subject result; only terminal results are persisted after an execution returns. */
public enum ErasureOutcome {
    IN_PROGRESS,
    EXEMPT,
    INACTIVE,
    ALREADY_HANDLED,
    ENTITY_REMOVED,
    LIVING_DEATH_COMPLETED,
    PLAYER_DEATH_COMPLETED,
    GUARDIAN_ENCOUNTER_COMPLETED,
    FAILED_BEFORE_COMMIT,
    PARTIAL_FAILURE
}
