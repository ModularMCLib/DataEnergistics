package com.fish_dan_.data_energistics.common.multiblock.preview.projection;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewPredicateKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable variant, repeat, tier, and predicate-candidate choices for one named substructure.
 *
 * @param variantIndex        zero-based shape variant inside the named structure
 * @param repeatCounts        one positive repeat count per MDLib pattern unit
 * @param tierSelections      positive values keyed by stable tier-domain id
 * @param candidateSelections non-negative candidate indexes keyed by unexpanded predicate coordinate
 */
public record SubstructureSelection(int variantIndex,
                                    IntList repeatCounts,
                                    Object2IntMap<String> tierSelections,
                                    Object2IntMap<PreviewPredicateKey> candidateSelections) {

    /**
     * Copies all collections while validating values that do not require a structure definition.
     */
    public SubstructureSelection {
        if (variantIndex < 0) {
            throw new IllegalArgumentException("Preview variant index cannot be negative: " + variantIndex);
        }
        if (repeatCounts == null || tierSelections == null || candidateSelections == null) {
            throw new IllegalArgumentException("Substructure selection collections cannot be null");
        }
        repeatCounts = IntList.of(repeatCounts.toIntArray());
        for (int repeatCount : repeatCounts) {
            if (repeatCount < 1) {
                throw new IllegalArgumentException("Preview repeat counts must be positive: " + repeatCount);
            }
        }
        tierSelections = immutableTierSelections(tierSelections);
        candidateSelections = immutableCandidateSelections(candidateSelections);
    }

    /**
     * Creates a selection for the default single-shape variant.
     */
    public SubstructureSelection(IntList repeatCounts,
                                 Object2IntMap<String> tierSelections,
                                 Object2IntMap<PreviewPredicateKey> candidateSelections) {
        this(0, repeatCounts, tierSelections, candidateSelections);
    }

    /**
     * Replaces the shape variant without changing repeat, tier, or candidate choices.
     *
     * @param variantIndex zero-based shape variant inside the named structure
     * @return updated immutable selection
     */
    public SubstructureSelection withVariantIndex(int variantIndex) {
        return new SubstructureSelection(
                variantIndex,
                this.repeatCounts,
                this.tierSelections,
                this.candidateSelections);
    }

    /**
     * Replaces one unit repeat count without changing tier or candidate choices.
     *
     * @param unitIndex   MDLib pattern unit index
     * @param repeatCount positive replacement count
     * @return updated immutable selection
     */
    public SubstructureSelection withRepeat(int unitIndex, int repeatCount) {
        if (unitIndex < 0 || unitIndex >= this.repeatCounts.size()) {
            throw new IllegalArgumentException("Unknown preview repeat unit index: " + unitIndex);
        }
        if (repeatCount < 1) {
            throw new IllegalArgumentException("Preview repeat counts must be positive: " + repeatCount);
        }
        IntList updated = new IntArrayList(this.repeatCounts);
        updated.set(unitIndex, repeatCount);
        return new SubstructureSelection(this.variantIndex, updated, this.tierSelections, this.candidateSelections);
    }

    /**
     * Replaces one tier-domain value without changing other substructure state.
     *
     * @param domainId stable tier-domain id
     * @param value    positive replacement value
     * @return updated immutable selection
     */
    public SubstructureSelection withTier(String domainId, int value) {
        if (domainId == null || domainId.isBlank()) {
            throw new IllegalArgumentException("Preview tier domain id cannot be blank");
        }
        if (value < 1) {
            throw new IllegalArgumentException("Preview tier values must be positive: " + value);
        }
        if (!this.tierSelections.containsKey(domainId)) {
            throw new IllegalArgumentException("Unknown preview tier domain: " + domainId);
        }
        Object2IntMap<String> updated = new Object2IntLinkedOpenHashMap<>(this.tierSelections);
        updated.put(domainId, value);
        return new SubstructureSelection(this.variantIndex, this.repeatCounts, updated, this.candidateSelections);
    }

    /**
     * Replaces the candidate index selected for one source predicate.
     *
     * @param predicateKey   unexpanded source predicate coordinate
     * @param candidateIndex non-negative candidate index
     * @return updated immutable selection
     */
    public SubstructureSelection withCandidate(PreviewPredicateKey predicateKey, int candidateIndex) {
        if (predicateKey == null) {
            throw new IllegalArgumentException("Preview candidate selection requires a predicate key");
        }
        if (candidateIndex < 0) {
            throw new IllegalArgumentException("Preview candidate index cannot be negative: " + candidateIndex);
        }
        Object2IntMap<PreviewPredicateKey> updated = new Object2IntLinkedOpenHashMap<>(this.candidateSelections);
        updated.put(predicateKey, candidateIndex);
        return new SubstructureSelection(this.variantIndex, this.repeatCounts, this.tierSelections, updated);
    }

    private static Object2IntMap<String> immutableTierSelections(Object2IntMap<String> selections) {
        Object2IntMap<String> copy = new Object2IntLinkedOpenHashMap<>();
        for (Object2IntMap.Entry<String> entry : selections.object2IntEntrySet()) {
            String domainId = entry.getKey();
            int value = entry.getIntValue();
            if (domainId == null || domainId.isBlank()) {
                throw new IllegalArgumentException("Preview tier domain id cannot be blank");
            }
            if (value < 1) {
                throw new IllegalArgumentException("Preview tier values must be positive: " + value);
            }
            copy.put(domainId, value);
        }
        return Object2IntMaps.unmodifiable(copy);
    }

    private static Object2IntMap<PreviewPredicateKey> immutableCandidateSelections(
                                                                                  Object2IntMap<PreviewPredicateKey> selections) {
        Object2IntMap<PreviewPredicateKey> copy = new Object2IntLinkedOpenHashMap<>();
        for (Object2IntMap.Entry<PreviewPredicateKey> entry : selections.object2IntEntrySet()) {
            PreviewPredicateKey predicateKey = entry.getKey();
            int candidateIndex = entry.getIntValue();
            if (predicateKey == null) {
                throw new IllegalArgumentException("Preview candidate selection requires a predicate key");
            }
            if (candidateIndex < 0) {
                throw new IllegalArgumentException("Preview candidate index cannot be negative: " + candidateIndex);
            }
            copy.put(predicateKey, candidateIndex);
        }
        return Object2IntMaps.unmodifiable(copy);
    }
}
