package com.fish_dan_.data_energistics.blockentity.tower.network.discovery;

import appeng.api.networking.IGridNode;

import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;

/**
 * Complete six-face capability result for one binding anchor.
 *
 * @param exposedNodes identity-de-duplicated nodes authorized through the anchor capability, including tower-only
 *                     mounted-device fallbacks
 * @param grids        identity-de-duplicated target-grid results
 */
public record TowerTargetResolution(ObjectList<IGridNode> exposedNodes, ObjectList<TowerResolvedGrid> grids) {

    /** Defensively copies one target resolution. */
    public TowerTargetResolution {
        exposedNodes = new ObjectImmutableList<>(exposedNodes);
        grids = new ObjectImmutableList<>(grids);
    }

    /**
     * Returns the number of grids that passed local validation.
     *
     * @return accepted grid count
     */
    public long acceptedGridCount() {
        return this.grids.stream().filter(TowerResolvedGrid::accepted).count();
    }
}
