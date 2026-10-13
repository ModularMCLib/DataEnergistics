package com.fish_dan_.data_energistics.blockentity.tower.equalization;

import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

/**
 * Ordered, immutable collection of endpoint states captured before an equalization pass.
 *
 * <p>
 * Endpoint order is significant: it provides deterministic extraction order and resolves indivisible FE rounding
 * ties. An endpoint identity may therefore occur at most once.
 * </p>
 *
 * @param endpoints endpoint states in stable caller-defined order
 */
public record TowerEnergyEqualizationSnapshot(ObjectList<TowerEnergyEndpointSnapshot> endpoints) {

    /**
     * Defensively copies the endpoint order and rejects ambiguous duplicate identities.
     *
     * @param endpoints endpoint states in stable caller-defined order
     */
    public TowerEnergyEqualizationSnapshot {
        endpoints = new ObjectImmutableList<>(endpoints);
        ObjectSet<TowerEnergyEndpointId> identities = new ObjectOpenHashSet<>();
        for (TowerEnergyEndpointSnapshot endpoint : endpoints) {
            if (!identities.add(endpoint.endpoint())) {
                throw new IllegalArgumentException("Endpoint snapshot contains a duplicate identity: " + endpoint.endpoint());
            }
        }
    }
}
