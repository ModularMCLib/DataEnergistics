package com.fish_dan_.data_energistics.common.crafting.trinity.planning.diagnostic;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic.InputRequirement;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic.PartialPlan;
import com.fish_dan_.data_energistics.utils.FastUtilCollections;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.math.BigInteger;

/**
 * Request-local accounting used to continue graph diagnosis around one fully proved cycle without making it
 * executable.
 *
 * @param evidence          exact compressed cycle proof
 * @param actualInputs      real inventory that may be reserved for the diagnostic branch
 * @param inputRequirements conclusive finite-input shortages
 * @param boundaryInputs    complete inputs that an earlier graph component must produce
 */
public record TrinityCycleDiagnosticOutcome(
                                            TrinityCycleDiagnosticEvidence evidence,
                                            Object2ObjectMap<AEKey, BigInteger> actualInputs,
                                            Object2ObjectMap<AEKey, InputRequirement> inputRequirements,
                                            Object2ObjectMap<AEKey, BigInteger> boundaryInputs) {

    /**
     * Retains the allocation partition produced by the planner as immutable snapshots.
     */
    public TrinityCycleDiagnosticOutcome {
        actualInputs = FastUtilCollections.immutableMap(actualInputs);
        boundaryInputs = FastUtilCollections.immutableMap(boundaryInputs);
        inputRequirements = FastUtilCollections.immutableMap(inputRequirements);
    }

    /**
     * Splits the proved schedule inputs using the same all-or-produce-upstream policy as graph demand aggregation.
     */
    public static TrinityCycleDiagnosticOutcome create(
                                                       TrinityCycleDiagnosticEvidence evidence,
                                                       Object2ObjectMap<AEKey, BigInteger> available,
                                                       ObjectSet<AEKey> producibleInputs) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> actual = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, InputRequirement> shortages = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> boundary = new Object2ObjectLinkedOpenHashMap<>();
        evidence.initialInputs().forEach((key, required) -> {
            BigInteger stored = available.getOrDefault(key, BigInteger.ZERO);
            if (stored.compareTo(required) >= 0) {
                actual.put(key, required);
            } else if (producibleInputs.contains(key)) {
                boundary.put(key, required);
            } else {
                if (stored.signum() > 0) {
                    actual.put(key, stored);
                }
                BigInteger missing = required.subtract(stored);
                shortages.put(key, new InputRequirement(required, stored, missing));
            }
        });
        return new TrinityCycleDiagnosticOutcome(evidence, actual, shortages, boundary);
    }

    /**
     * Produces the non-executable material view attached to a terminal diagnostic.
     */
    public PartialPlan materials() {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> missing = new Object2ObjectLinkedOpenHashMap<>();
        this.inputRequirements.forEach((key, requirement) -> missing.put(key, requirement.missing()));
        return new PartialPlan(
                this.actualInputs,
                this.evidence.emittedItems(),
                missing,
                this.inputRequirements,
                ObjectList.of());
    }
}
