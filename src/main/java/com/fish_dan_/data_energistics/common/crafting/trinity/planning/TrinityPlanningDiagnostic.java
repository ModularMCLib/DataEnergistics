package com.fish_dan_.data_energistics.common.crafting.trinity.planning;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.diagnostic.TrinityCycleDiagnosticEvidence;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.diagnostic.TrinityDiagnosticField;
import com.fish_dan_.data_energistics.utils.FastUtilCollections;

import appeng.api.stacks.AEKey;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.Optional;

/**
 * Immutable UI and log diagnostic retained when Trinity planning cannot produce an executable plan.
 *
 * @param code     stable programmatic reason
 * @param message  player-facing explanation
 * @param metadata deterministic structured details for logs and confirmation UI
 * @param detail   exact typed detail when Trinity has completely solved a diagnostic boundary
 * @param work     typed internal accounting; boundaries without solver or schedule work retain zero counters
 */
public record TrinityPlanningDiagnostic(
                                        TrinityPlanningDiagnosticCode code,
                                        Component message,
                                        Object2ObjectMap<TrinityDiagnosticField, Object> metadata,
                                        Detail detail,
                                        SolverWork work) {

    public TrinityPlanningDiagnostic(TrinityPlanningDiagnosticCode code) {
        this(code, Component.translatable(code.translationKey), FastUtilCollections.mapOf());
    }

    public TrinityPlanningDiagnostic(
                                     TrinityPlanningDiagnosticCode code,
                                     Component message,
                                     Object2ObjectMap<TrinityDiagnosticField, Object> metadata) {
        this(code, message, metadata, NoDetail.INSTANCE, SolverWork.NONE);
    }

    public TrinityPlanningDiagnostic(
                                     TrinityPlanningDiagnosticCode code,
                                     Component message,
                                     Object2ObjectMap<TrinityDiagnosticField, Object> metadata,
                                     Detail detail) {
        this(code, message, metadata, detail, SolverWork.NONE);
    }

    public TrinityPlanningDiagnostic(TrinityPlanningDiagnosticCode code, Component message,
                                     Object2ObjectMap<TrinityDiagnosticField, Object> metadata, SolverWork work) {
        this(code, message, metadata, NoDetail.INSTANCE, work);
    }

    /**
     * Owns mutable input once and shares retained metadata when adding diagnostic details.
     */
    public TrinityPlanningDiagnostic {
        message = message.copy();
        metadata = FastUtilCollections.immutableMap(metadata);
    }

    /**
     * Prevents callers from mutating the retained component through a concrete mutable implementation.
     */
    @Override
    public Component message() {
        return this.message.copy();
    }

    /**
     * @return exact typed shortage when this diagnostic conclusively resolved an unavailable input
     */
    public Optional<InputShortage> inputShortage() {
        return this.detail instanceof InputShortage shortage ? Optional.of(shortage) : Optional.empty();
    }

    /**
     * @return the material projection accumulated before planning stopped
     */
    public Optional<PartialPlan> partialPlan() {
        return switch (this.detail) {
            case PartialPlan partial -> Optional.of(partial);
            case CompositeEvidence evidence -> Optional.of(evidence.materials());
            default -> Optional.empty();
        };
    }

    /**
     * @return fully scheduled non-executable cycles retained by this diagnostic in stable component order
     */
    public ObjectList<TrinityCycleDiagnosticEvidence> cycleEvidence() {
        return this.detail instanceof CompositeEvidence evidence ? evidence.cycles() : ObjectList.of();
    }

    /**
     * Retains the diagnostic identity and message while attaching typed planner evidence.
     */
    public TrinityPlanningDiagnostic withDetail(Detail value) {
        return new TrinityPlanningDiagnostic(this.code, this.message, this.metadata, value, this.work);
    }

    /** Immutable work counters maintained by the backend that owns the visited-state budget. */
    public record SolverWork(int states, int passes, long nanos) {

        public static final SolverWork NONE = new SolverWork(0, 0, 0L);
    }

    /**
     * Closed diagnostic-detail family keeps exact planner evidence separate from general diagnostic attributes.
     */
    public sealed interface Detail permits InputShortage, PartialPlan, CompositeEvidence, NoDetail {}

    private enum NoDetail implements Detail {
        INSTANCE
    }

    /**
     * Exact immutable shortage retained separately from localized text and general diagnostic attributes.
     *
     * @param key       immutable AE key that cannot meet the solved initial requirement
     * @param required  exact required amount
     * @param available exact amount captured at planning start
     * @param missing   exact positive difference between required and available
     */
    public record InputShortage(
                                AEKey key,
                                BigInteger required,
                                BigInteger available,
                                BigInteger missing)
            implements Detail {}

    /**
     * Immutable material view accumulated along the selected planning branch before a terminal boundary was reached.
     *
     * @param usedItems         network inventory already reserved by the partial branch
     * @param emittedItems      outputs of recipe firings already selected by the partial branch
     * @param missingItems      positive demands that remained unresolved when planning stopped
     * @param inputRequirements exact external-input allocations proven for the retained branch
     * @param selectedFirings   actual selected variants and counts not represented by separate cycle evidence;
     *                          these retain input bindings but do not assert a complete executable schedule
     */
    public record PartialPlan(
                              Object2ObjectMap<AEKey, BigInteger> usedItems,
                              Object2ObjectMap<AEKey, BigInteger> emittedItems,
                              Object2ObjectMap<AEKey, BigInteger> missingItems,
                              Object2ObjectMap<AEKey, InputRequirement> inputRequirements,
                              ObjectList<TrinityVariantFiring> selectedFirings)
            implements Detail {

        public PartialPlan {
            usedItems = FastUtilCollections.immutableMap(usedItems);
            emittedItems = FastUtilCollections.immutableMap(emittedItems);
            missingItems = FastUtilCollections.immutableMap(missingItems);
            inputRequirements = FastUtilCollections.immutableMap(inputRequirements);
            selectedFirings = FastUtilCollections.immutableList(selectedFirings);
        }
    }

    /**
     * Immutable combination of the complete material projection and every cycle whose firing vector and compressed
     * execution order were independently verified.
     *
     * @param materials accumulated material view
     * @param cycles    fully proved diagnostic cycles
     */
    public record CompositeEvidence(
                                    PartialPlan materials,
                                    ObjectList<TrinityCycleDiagnosticEvidence> cycles)
            implements Detail {

        public CompositeEvidence {
            ObjectArrayList<TrinityCycleDiagnosticEvidence> ordered = new ObjectArrayList<>(cycles);
            ordered.sort(Comparator.comparingInt(TrinityCycleDiagnosticEvidence::componentIndex));
            cycles = ObjectLists.unmodifiable(ordered);
        }
    }

    /**
     * Exact allocation result for one external input after the complete selected route has been propagated.
     *
     * @param required  total amount required by the selected route
     * @param available amount actually allocated from the captured network inventory
     * @param missing   positive amount still absent from that inventory
     */
    public record InputRequirement(
                                   BigInteger required,
                                   BigInteger available,
                                   BigInteger missing) {}
}
