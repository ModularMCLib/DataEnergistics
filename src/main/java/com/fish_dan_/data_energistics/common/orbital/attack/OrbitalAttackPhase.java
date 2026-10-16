package com.fish_dan_.data_energistics.common.orbital.attack;

/**
 * Persisted phases used by the server attack scheduler.
 */
public enum OrbitalAttackPhase {
    RESERVED_WARNING,
    COMMITTED,
    DELIVERY,
    ABORTED,
    COOLDOWN,
    FAULTED
}
