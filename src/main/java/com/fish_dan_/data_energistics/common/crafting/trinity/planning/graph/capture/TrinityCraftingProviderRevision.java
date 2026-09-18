package com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.capture;

/**
 * Exposes a monotonic planning-model revision for AE2's grid-local crafting-provider index.
 *
 * <p>
 * Read on the server thread after complete provider refresh operations. Equivalent remove/add cycles preserve
 * the model generation; changed patterns or non-empty provider membership invalidate it. This value does not
 * replace the publication revision used to reject stale dispatch IDs and capacity observations.
 * </p>
 */
public interface TrinityCraftingProviderRevision {

    /**
     * @return non-negative revision advanced when the settled planning model changes
     */
    long data_energistics$trinityCraftingProviderRevision();
}
