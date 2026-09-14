package com.fish_dan_.data_energistics.common.trinity.autobuild;

import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockPreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewPredicateKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewSelection;
import com.fish_dan_.data_energistics.common.multiblock.preview.projection.ProjectionFingerprint;
import com.fish_dan_.data_energistics.common.multiblock.preview.projection.SubstructurePreviewSpec;
import com.fish_dan_.data_energistics.registry.DEVerticalMultiBlocks;

import com.modularmc.mdl.api.multiblock.RepeatRange;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;

import java.util.List;

/**
 * Converts an untrusted revision-bound UI submission into the existing atomic builder request.
 */
public final class TrinityAutoBuildSubmissionResolver {

    /**
     * Reconstructs every recipe-affecting field against the current server specification.
     *
     * @param spec       current Trinity preview specification from the atomic catalog snapshot
     * @param submission untrusted hosted-window submission
     * @return validated request understood by the existing builder
     */
    public TrinityAutoBuildRequest resolve(MultiblockPreviewSpec spec, TrinityAutoBuildSubmission submission) {
        if (spec == null || submission == null) {
            throw new IllegalArgumentException("Trinity auto-build resolution arguments cannot be null");
        }
        ProjectionFingerprint fingerprint = submission.projectionFingerprint();
        if (!DEVerticalMultiBlocks.trinityDataCoreId().equals(spec.controllerId()) ||
                !spec.controllerId().equals(fingerprint.controllerId())) {
            throw new IllegalArgumentException("Trinity auto-build submission belongs to another controller");
        }
        if (fingerprint.definitionRevision() != spec.definitionRevision()) {
            throw new IllegalArgumentException("Trinity auto-build definition revision is stale: " +
                    fingerprint.definitionRevision() + ", current " + spec.definitionRevision());
        }
        if (fingerprint.variantIndex() != 0) {
            throw new IllegalArgumentException("Trinity auto-build currently supports only explicit variant 0");
        }
        String structureName = fingerprint.structureKey().structureName();
        SubstructurePreviewSpec structure = spec.substructure(structureName);
        JsonMultiBlockStructureKey expectedKey = structure.definition(fingerprint.variantIndex()).key();
        if (!expectedKey.equals(fingerprint.structureKey())) {
            throw new IllegalArgumentException("Trinity auto-build structure key does not match the current spec: " +
                    fingerprint.structureKey());
        }

        PreviewSelection selection = PreviewSelection.initial(spec).select(structureName);
        List<RepeatRange> repeatRanges = structure.repeatRanges(fingerprint.variantIndex());
        if (fingerprint.repeatCounts().size() != repeatRanges.size()) {
            throw new IllegalArgumentException("Trinity auto-build repeat selection count does not match " +
                    structureName);
        }
        for (int unitIndex = 0; unitIndex < fingerprint.repeatCounts().size(); unitIndex++) {
            selection = selection.withRepeat(unitIndex, fingerprint.repeatCounts().getInt(unitIndex));
        }
        if (fingerprint.tierSelections().size() != structure.tierDomains().size()) {
            throw new IllegalArgumentException("Trinity auto-build tier selections do not match " + structureName);
        }
        for (Object2IntMap.Entry<String> tier : fingerprint.tierSelections().object2IntEntrySet()) {
            selection = selection.withTier(tier.getKey(), tier.getIntValue());
        }
        for (Object2IntMap.Entry<PreviewPredicateKey> candidate : fingerprint.candidateSelections().object2IntEntrySet()) {
            selection = selection.withCandidate(candidate.getKey(), candidate.getIntValue());
        }
        ProjectionFingerprint rebuilt = ProjectionFingerprint.from(selection);
        if (!rebuilt.equals(fingerprint)) {
            throw new IllegalArgumentException("Trinity auto-build projection fingerprint did not reconstruct exactly");
        }

        int structureIndex = structureIndex(structureName);
        int repeatCount = resolveBuilderRepeat(structureName, repeatRanges, fingerprint.repeatCounts());
        String tierCategory = TrinityAutoBuildBlockMap.categoryForStructure(structureIndex);
        if (fingerprint.tierSelections().size() != 1 || !fingerprint.tierSelections().containsKey(tierCategory)) {
            throw new IllegalArgumentException("Trinity auto-build structure " + structureName +
                    " requires exactly tier category " + tierCategory);
        }
        return new TrinityAutoBuildRequest(
                structureIndex,
                new TrinityAutoBuildOptions(
                        submission.buildRequested(),
                        repeatCount,
                        Object2IntMaps.singleton(tierCategory, fingerprint.tierSelections().getInt(tierCategory)),
                        fingerprint.candidateSelections()));
    }

    private static int resolveBuilderRepeat(String structureName,
                                            List<RepeatRange> ranges,
                                            IntList repeats) {
        int variableUnit = -1;
        for (int unitIndex = 0; unitIndex < ranges.size(); unitIndex++) {
            RepeatRange range = ranges.get(unitIndex);
            if (range.min() != range.max()) {
                if (variableUnit >= 0) {
                    throw new IllegalArgumentException("Trinity auto-build structure has multiple variable units: " +
                            structureName);
                }
                variableUnit = unitIndex;
            }
        }
        if (JsonMultiBlockStructureKey.DEFAULT_STRUCTURE_NAME.equals(structureName)) {
            if (variableUnit >= 0) {
                throw new IllegalArgumentException("Trinity main auto-build structure must have fixed repeats");
            }
            for (int repeat : repeats) {
                if (repeat != 1) {
                    throw new IllegalArgumentException("Trinity main auto-build repeats must all equal one");
                }
            }
            return 1;
        }
        if (variableUnit < 0) {
            throw new IllegalArgumentException("Trinity child auto-build structure requires one variable unit: " +
                    structureName);
        }
        return repeats.getInt(variableUnit);
    }

    private static int structureIndex(String structureName) {
        if (JsonMultiBlockStructureKey.DEFAULT_STRUCTURE_NAME.equals(structureName)) {
            return TrinityAutoBuildRequest.MAIN_STRUCTURE_INDEX;
        }
        if (DEVerticalMultiBlocks.TRINITY_DATA_CORE_CPU_STRUCTURE_NAME.equals(structureName)) {
            return TrinityAutoBuildRequest.CPU_STRUCTURE_INDEX;
        }
        if (DEVerticalMultiBlocks.TRINITY_DATA_CORE_CRAFTING_STRUCTURE_NAME.equals(structureName)) {
            return TrinityAutoBuildRequest.CRAFTING_STRUCTURE_INDEX;
        }
        throw new IllegalArgumentException("Unknown Trinity auto-build structure: " + structureName);
    }
}
