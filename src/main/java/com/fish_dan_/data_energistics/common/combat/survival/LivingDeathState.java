package com.fish_dan_.data_energistics.common.combat.survival;

import org.jspecify.annotations.NullMarked;

/**
 * The existing living-entity mixin exposes its pending death flag to the survival boundary through this
 * contract. Server-thread callers may use it only after protection succeeds, before death settlement;
 * it must never recover an orbital-erased or completed life.
 */
@NullMarked
public interface LivingDeathState {

    /** Clears the pending death flag on the current instance; it does not restore health or create a new life. */
    void dataEnergistics$cancelPendingDeath();

    /** Reports whether vanilla has entered its death body on this instance, without changing state. */
    boolean dataEnergistics$deathPending();
}
