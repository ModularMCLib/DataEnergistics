package com.fish_dan_.data_energistics.common.multiblock.preview.projection;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntAVLTreeMap;

import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewPredicateKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewSelection;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Structured deterministic identity of every selection field that can change an ordinary recipe projection.
 *
 * @param controllerId        controller-level owner identity
 * @param definitionRevision  active definition generation
 * @param structureKey        active named structure identity
 * @param variantIndex        shape variant inside the named structure
 * @param repeatCounts        selected repeat count per pattern unit
 * @param tierSelections      selected value per stable tier domain
 * @param candidateSelections selected candidate per unexpanded predicate coordinate
 */
public record ProjectionFingerprint(ResourceLocation controllerId,
                                    long definitionRevision,
                                    JsonMultiBlockStructureKey structureKey,
                                    int variantIndex,
                                    IntList repeatCounts,
                                    Object2IntMap<String> tierSelections,
                                    Object2IntMap<PreviewPredicateKey> candidateSelections) {

    private static final Comparator<PreviewPredicateKey> PREDICATE_ORDER = Comparator
            .comparingInt(PreviewPredicateKey::sourceLayer)
            .thenComparingInt(PreviewPredicateKey::y)
            .thenComparingInt(PreviewPredicateKey::x);

    public ProjectionFingerprint {
        if (controllerId == null || structureKey == null) {
            throw new IllegalArgumentException("Projection fingerprint identities cannot be null");
        }
        if (!controllerId.equals(structureKey.machineId())) {
            throw new IllegalArgumentException("Projection fingerprint structure does not belong to its controller");
        }
        if (definitionRevision < 0L) {
            throw new IllegalArgumentException("Projection fingerprint revision cannot be negative: " +
                    definitionRevision);
        }
        SubstructureSelection selection = new SubstructureSelection(
                variantIndex,
                repeatCounts,
                tierSelections,
                candidateSelections);
        repeatCounts = selection.repeatCounts();
        tierSelections = sortedTiers(selection.tierSelections());
        candidateSelections = sortedCandidates(selection.candidateSelections());
    }

    /**
     * Captures the active recipe-affecting choices from a validated preview session.
     *
     * @param selection revision-bound session selection
     * @return deterministic active projection identity
     */
    public static ProjectionFingerprint from(PreviewSelection selection) {
        if (selection == null) {
            throw new IllegalArgumentException("Projection fingerprint requires a preview selection");
        }
        SubstructureSelection active = selection.activeSelection();
        return new ProjectionFingerprint(
                selection.controllerId(),
                selection.definitionRevision(),
                selection.activeStructureKey(),
                active.variantIndex(),
                active.repeatCounts(),
                active.tierSelections(),
                active.candidateSelections());
    }

    private static Object2IntMap<String> sortedTiers(Object2IntMap<String> tiers) {
        return Object2IntMaps.unmodifiable(new Object2IntLinkedOpenHashMap<>(new Object2IntAVLTreeMap<>(tiers)));
    }

    private static Object2IntMap<PreviewPredicateKey> sortedCandidates(
                                                                      Object2IntMap<PreviewPredicateKey> candidates) {
        Object2IntMap<PreviewPredicateKey> sorted = new Object2IntAVLTreeMap<>(PREDICATE_ORDER);
        sorted.putAll(candidates);
        return Object2IntMaps.unmodifiable(new Object2IntLinkedOpenHashMap<>(sorted));
    }
}
