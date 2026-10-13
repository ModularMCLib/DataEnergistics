package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.proof;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.util.FastUtilCollections;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.Optional;

/**
 * Quantity-independent producer family for one exact output key.
 *
 * @param output     exact produced key
 * @param candidates stable transition-effect candidates
 */
public record TrinityAcyclicRouteFamily(AEKey output, ObjectList<TrinityPatternVariant> candidates) {

    /** Builds a stable semantic family without retaining request quantities or inventory. */
    public static TrinityAcyclicRouteFamily create(AEKey output, ObjectList<TrinityPatternVariant> candidates) {
        ObjectArrayList<TrinityPatternVariant> ordered = new ObjectArrayList<>(candidates);
        ordered.sort(TrinityPatternVariant::compareTo);
        return new TrinityAcyclicRouteFamily(output, FastUtilCollections.immutableList(ordered));
    }

    /**
     * @return the cross-quantity proved route when exactly one transition effect can produce this key
     */
    public Optional<TrinityPatternVariant> provedUniqueProducer() {
        return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }
}
