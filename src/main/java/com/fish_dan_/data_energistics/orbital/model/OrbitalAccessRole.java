package com.fish_dan_.data_energistics.orbital.model;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

/**
 * Delegated access roles. Ownership remains a separate weapon-record field and is never represented by this enum.
 */
public enum OrbitalAccessRole {

    OPERATOR(ObjectSet.of(
            StellarErasureDeviceAction.VIEW_STATUS,
            StellarErasureDeviceAction.AIM,
            StellarErasureDeviceAction.FIRE,
            StellarErasureDeviceAction.CANCEL_WARNING_ATTACK,
            StellarErasureDeviceAction.EMERGENCY_ABORT)),
    OBSERVER(ObjectSet.of(StellarErasureDeviceAction.VIEW_STATUS));

    private final ObjectSet<StellarErasureDeviceAction> allowedActions;

    OrbitalAccessRole(ObjectSet<StellarErasureDeviceAction> allowedActions) {
        this.allowedActions = ObjectSets.unmodifiable(new ObjectOpenHashSet<>(allowedActions));
    }

    /**
     * Returns whether this delegated role permits the requested server action.
     */
    public boolean allows(StellarErasureDeviceAction action) {
        return this.allowedActions.contains(action);
    }
}
