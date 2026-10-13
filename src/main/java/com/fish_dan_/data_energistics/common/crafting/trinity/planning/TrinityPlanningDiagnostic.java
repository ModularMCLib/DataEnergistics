package com.fish_dan_.data_energistics.common.crafting.trinity.planning;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.diagnostic.TrinityCycleDiagnosticEvidence;
import com.fish_dan_.data_energistics.util.FastUtilCollections;

import appeng.api.stacks.AEKey;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

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
 */
public record TrinityPlanningDiagnostic(
                                        TrinityPlanningDiagnosticCode code,
                                        Component message,
                                        Object2ObjectMap<String, String> metadata,
                                        Detail detail) {

    public TrinityPlanningDiagnostic(
                                     TrinityPlanningDiagnosticCode code,
                                     Component message,
                                     Object2ObjectMap<String, String> metadata) {
        this(code, message, metadata, NoDetail.INSTANCE);
    }

    public TrinityPlanningDiagnostic(
                                     TrinityPlanningDiagnosticCode code,
                                     Component message,
                                     Object2ObjectMap<String, String> metadata,
                                     InputShortage inputShortage) {
        this(code, message, metadata, (Detail) inputShortage);
    }

    /**
     * Validates and owns the retained planning result.
     */
    public TrinityPlanningDiagnostic {
        message = message.copy();
        Object2ObjectAVLTreeMap<String, String> orderedMetadata = new Object2ObjectAVLTreeMap<>();
        metadata.forEach((key, value) -> {
            if (key.isBlank()) {
                throw new IllegalArgumentException("Trinity planning diagnostic metadata must be named");
            }
            orderedMetadata.put(key, value);
        });
        metadata = FastUtilCollections.immutableMap(orderedMetadata);
    }

    /**
     * Creates a player-visible diagnostic whose message is resolved through the active language.
     *
     * @param code           stable reason
     * @param translationKey player-facing translation key
     * @return immutable diagnostic
     */
    public static TrinityPlanningDiagnostic ofTranslationKey(TrinityPlanningDiagnosticCode code,
                                                             String translationKey) {
        if (translationKey.isBlank()) {
            throw new IllegalArgumentException("A Trinity planning diagnostic requires a translation key");
        }
        return new TrinityPlanningDiagnostic(code, Component.translatable(translationKey), FastUtilCollections.mapOf());
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
        return new TrinityPlanningDiagnostic(this.code, this.message, this.metadata, value);
    }

    /**
     * Closed diagnostic-detail family keeps typed planner evidence separate from string log metadata.
     */
    public sealed interface Detail permits InputShortage, PartialPlan, CompositeEvidence, NoDetail {}

    private enum NoDetail implements Detail {
        INSTANCE
    }

    /**
     * Exact immutable shortage retained separately from localized text and string log metadata.
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
            implements Detail {

        public InputShortage {
            if (required.signum() <= 0 || available.signum() < 0 ||
                    !required.subtract(available).equals(missing) || missing.signum() <= 0) {
                throw new IllegalArgumentException("A Trinity input shortage must be exact and positive");
            }
        }
    }

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
            usedItems = validatePositiveAmounts(usedItems, "used");
            emittedItems = validatePositiveAmounts(emittedItems, "emitted");
            missingItems = validatePositiveAmounts(missingItems, "missing");
            for (Object2ObjectMap.Entry<AEKey, InputRequirement> requirement : inputRequirements.object2ObjectEntrySet()) {
                if (!requirement.getValue().missing().equals(missingItems.get(requirement.getKey()))) {
                    throw new IllegalArgumentException(
                            "A Trinity exact input requirement must match its projected missing amount");
                }
            }
            inputRequirements = FastUtilCollections.immutableMap(inputRequirements);
            // Variant/count records are immutable. Reusing a retained snapshot does not copy this list again.
            selectedFirings = FastUtilCollections.immutableList(selectedFirings);
        }

        private static Object2ObjectMap<AEKey, BigInteger> validatePositiveAmounts(
                                                                                   Object2ObjectMap<AEKey, BigInteger> source,
                                                                                   String role) {
            source.forEach((key, amount) -> {
                if (amount.signum() <= 0) {
                    throw new IllegalArgumentException("Trinity partial " + role + " amounts must be positive");
                }
            });
            return FastUtilCollections.immutableMap(source);
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
            IntSet components = new IntOpenHashSet();
            for (TrinityCycleDiagnosticEvidence cycle : ordered) {
                if (!components.add(cycle.componentIndex())) {
                    throw new IllegalArgumentException(
                            "Composite Trinity diagnostic evidence requires unique cycles");
                }
            }
            cycles = FastUtilCollections.immutableList(ordered);
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
                                   BigInteger missing) {

        public InputRequirement {
            if (required.signum() <= 0 || available.signum() < 0 || missing.signum() <= 0 ||
                    !required.equals(available.add(missing))) {
                throw new IllegalArgumentException(
                        "A Trinity input requirement must preserve required = available + missing");
            }
        }
    }
}
