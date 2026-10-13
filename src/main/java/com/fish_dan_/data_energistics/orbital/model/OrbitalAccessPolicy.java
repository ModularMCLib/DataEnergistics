package com.fish_dan_.data_energistics.orbital.model;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

import java.util.UUID;

/**
 * Evaluates owner and delegated-role access without trusting client-side UI state.
 */
public final class OrbitalAccessPolicy {

    private OrbitalAccessPolicy() {}

    /**
     * Resolves one action against the immutable owner identity and current delegated-role snapshot.
     */
    public static boolean canPerform(
                                     UUID ownerId,
                                     Object2ObjectMap<UUID, OrbitalAccessRole> delegatedRoles,
                                     UUID playerId,
                                     StellarErasureDeviceAction action) {
        if (ownerId.equals(playerId)) {
            return true;
        }
        OrbitalAccessRole role = delegatedRoles.get(playerId);
        return role != null && role.allows(action);
    }

    /**
     * Captures every currently authorized UUID for attack damage exemption. Later role changes do not mutate the
     * returned snapshot.
     */
    public static ObjectSet<UUID> damageExemptionSnapshot(
                                                          UUID ownerId,
                                                          Object2ObjectMap<UUID, OrbitalAccessRole> delegatedRoles) {
        ObjectSet<UUID> exemptions = new ObjectOpenHashSet<>(delegatedRoles.keySet());
        exemptions.add(ownerId);
        return ObjectSets.unmodifiable(exemptions);
    }
}
