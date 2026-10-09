package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic.InputRequirement;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCyclePlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.seed.TrinityCycleSeedRequirement;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityCompressedSchedule;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityDeterministicRepeatScheduler;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.diagnostic.TrinityCycleDiagnosticEvidence;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.diagnostic.TrinityCycleDiagnosticOutcome;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.util.AmountMath;
import com.fish_dan_.data_energistics.util.FastUtilCollections;

import appeng.api.stacks.AEKey;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.math.BigInteger;
import java.util.Optional;

/**
 * Solves a stable deterministic cycle by closed-form net effect and maximum prefix deficit.
 * <p>
 * Exact block-prefix implementation for self multiplication and deterministic multi-step multiplication.
 */
public final class TrinityDeterministicCyclePlanner {

    /**
     * @return planner using the exact compressed scheduler
     */
    public static TrinityDeterministicCyclePlanner create() {
        return new TrinityDeterministicCyclePlanner(TrinityDeterministicRepeatScheduler.create());
    }

    private final TrinityDeterministicRepeatScheduler scheduler;

    TrinityDeterministicCyclePlanner(TrinityDeterministicRepeatScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Retains the component identity so a conclusive shortage can carry a non-executable schedule proof.
     */
    public TrinityAlgorithmResult<TrinityCyclePlan> plan(
                                                         int componentIndex,
                                                         TrinityCycleDemand diagnosticDemand,
                                                         ObjectList<TrinityVariantFiring> oneCycleOrder,
                                                         AEKey target,
                                                         BigInteger requestedAmount,
                                                         CraftingQuantityMode quantityMode,
                                                         Object2ObjectMap<AEKey, BigInteger> available,
                                                         ObjectSet<AEKey> producibleInputs,
                                                         int maxScheduleStates,
                                                         TrinityPlanningControl control) {
        if (componentIndex < 0) {
            throw new IllegalArgumentException("A Trinity deterministic cycle component index cannot be negative");
        }
        if (oneCycleOrder.isEmpty() || requestedAmount.signum() <= 0 || maxScheduleStates <= 0) {
            throw new IllegalArgumentException("A Trinity deterministic cycle request is incomplete");
        }
        Object2ObjectMap<AEKey, BigInteger> inventory = copyAvailable(available);
        Object2ObjectMap<AEKey, BigInteger> oneCycleNet = cycleNetChange(oneCycleOrder);
        BigInteger targetEffect = oneCycleNet.getOrDefault(target, BigInteger.ZERO);
        if (targetEffect.signum() <= 0) {
            return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                    TrinityPlanningDiagnosticCode.NO_PRODUCTIVE_CYCLE,
                    Component.translatable("gui.data_energistics.trinity_planning.diagnostic.no_productive_cycle"),
                    FastUtilCollections.mapOf("target_effect", targetEffect.toString())));
        }

        BigInteger requiredNet = quantityMode == CraftingQuantityMode.NET_NEW ?
                requestedAmount :
                requestedAmount.subtract(inventory.getOrDefault(target, BigInteger.ZERO)).max(BigInteger.ZERO);
        BigInteger repetitions = AmountMath.ceilDivideNonNegative(requiredNet, targetEffect);
        if (quantityMode == CraftingQuantityMode.FINAL_TOTAL) {
            repetitions = repetitions.max(BigInteger.ONE);
        }
        Object2ObjectMap<AEKey, BigInteger> minimumSeed = TrinityCycleSeedRequirement.repeatedMinimumInputs(
                oneCycleOrder,
                repetitions);
        Object2ObjectMap<AEKey, BigInteger> netChange = multiply(oneCycleNet, repetitions);
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> initialInputs = new Object2ObjectLinkedOpenHashMap<>(minimumSeed);
        if (quantityMode == CraftingQuantityMode.FINAL_TOTAL) {
            BigInteger targetContribution = requestedAmount
                    .subtract(netChange.getOrDefault(target, BigInteger.ZERO))
                    .max(BigInteger.ZERO);
            if (targetContribution.signum() > 0) {
                initialInputs.merge(target, targetContribution, BigInteger::max);
            }
        }
        Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> aggregateFirings = new Object2ObjectLinkedOpenHashMap<>();
        for (TrinityVariantFiring firing : oneCycleOrder) {
            aggregateFirings.merge(
                    firing.variant(),
                    firing.count().multiply(repetitions),
                    BigInteger::add);
        }
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> usedInputs = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> missingInputs = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, InputRequirement> shortages = new Object2ObjectLinkedOpenHashMap<>();
        for (Object2ObjectMap.Entry<AEKey, BigInteger> input : initialInputs.object2ObjectEntrySet()) {
            BigInteger required = input.getValue();
            BigInteger allocated = required.min(inventory.getOrDefault(input.getKey(), BigInteger.ZERO));
            BigInteger missing = required.subtract(allocated);
            if (allocated.signum() > 0) {
                usedInputs.put(input.getKey(), allocated);
            }
            if (missing.signum() > 0 && !producibleInputs.contains(input.getKey())) {
                missingInputs.put(input.getKey(), missing);
                shortages.put(input.getKey(), new InputRequirement(required, allocated, missing));
            }
        }
        TrinityAlgorithmResult<TrinityCompressedSchedule> schedule = this.scheduler.schedule(
                oneCycleOrder,
                repetitions,
                initialInputs,
                maxScheduleStates,
                control);
        if (control.cancellationRequested()) {
            return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                    TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                    Component.translatable("gui.data_energistics.trinity_planning.diagnostic.cancelled"),
                    FastUtilCollections.mapOf()));
        }
        if (!shortages.isEmpty()) {
            if (!schedule.successful() &&
                    schedule.diagnostic().code() == TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED) {
                return TrinityAlgorithmResult.failure(schedule.diagnostic());
            }
            Optional<TrinityCycleDiagnosticOutcome> diagnosticOutcome = Optional.empty();
            if (schedule.successful()) {
                TrinityCyclePlan provedPlan = new TrinityCyclePlan(
                        oneCycleOrder,
                        repetitions,
                        aggregateFirings,
                        minimumSeed,
                        initialInputs,
                        netChange,
                        schedule.value());
                TrinityCycleDiagnosticEvidence evidence = TrinityCycleDiagnosticEvidence.fromDeterministicPlan(
                        componentIndex,
                        diagnosticDemand,
                        provedPlan);
                diagnosticOutcome = Optional.of(TrinityCycleDiagnosticOutcome.create(
                        evidence,
                        inventory,
                        producibleInputs));
            }
            return insufficientInputs(
                    target,
                    minimumSeed,
                    netChange,
                    aggregateFirings,
                    usedInputs,
                    missingInputs,
                    shortages,
                    diagnosticOutcome,
                    schedule.successful() ? Optional.empty() : Optional.of(schedule.diagnostic()));
        }
        if (!schedule.successful()) {
            return TrinityAlgorithmResult.failure(schedule.diagnostic());
        }
        return TrinityAlgorithmResult.success(new TrinityCyclePlan(
                oneCycleOrder,
                repetitions,
                aggregateFirings,
                minimumSeed,
                initialInputs,
                netChange,
                schedule.value()));
    }

    private static Object2ObjectMap<AEKey, BigInteger> cycleNetChange(ObjectList<TrinityVariantFiring> order) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> net = new Object2ObjectLinkedOpenHashMap<>();
        for (TrinityVariantFiring firing : order) {
            firing.variant().netChange().forEach(
                    (key, amount) -> net.merge(key, amount.multiply(firing.count()), BigInteger::add));
        }
        net.values().removeIf(amount -> amount.signum() == 0);
        return FastUtilCollections.immutableMap(net);
    }

    private static Object2ObjectMap<AEKey, BigInteger> multiply(Object2ObjectMap<AEKey, BigInteger> amounts,
                                                                BigInteger multiplier) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> multiplied = new Object2ObjectLinkedOpenHashMap<>();
        amounts.forEach((key, amount) -> {
            BigInteger result = amount.multiply(multiplier);
            if (result.signum() != 0) {
                multiplied.put(key, result);
            }
        });
        return FastUtilCollections.immutableMap(multiplied);
    }

    private static Object2ObjectMap<AEKey, BigInteger> copyAvailable(Object2ObjectMap<AEKey, BigInteger> source) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> copied = new Object2ObjectLinkedOpenHashMap<>();
        source.forEach((key, amount) -> {
            if (amount.signum() < 0) {
                throw new IllegalArgumentException("Trinity cycle inventory cannot be negative");
            }
            if (amount.signum() > 0) {
                copied.put(key, amount);
            }
        });
        return FastUtilCollections.immutableMap(copied);
    }

    private static <T> TrinityAlgorithmResult<T> insufficientInputs(
                                                                    AEKey target,
                                                                    Object2ObjectMap<AEKey, BigInteger> minimumSeed,
                                                                    Object2ObjectMap<AEKey, BigInteger> netChange,
                                                                    Object2ObjectMap<TrinityPatternVariant, BigInteger> aggregateFirings,
                                                                    Object2ObjectMap<AEKey, BigInteger> usedInputs,
                                                                    Object2ObjectMap<AEKey, BigInteger> missingInputs,
                                                                    Object2ObjectMap<AEKey, InputRequirement> shortages,
                                                                    Optional<TrinityCycleDiagnosticOutcome> diagnosticOutcome,
                                                                    Optional<TrinityPlanningDiagnostic> proofFailure) {
        Object2ObjectLinkedOpenHashMap<String, String> metadata = new Object2ObjectLinkedOpenHashMap<>();
        metadata.put("shortageKinds", Integer.toString(shortages.size()));
        metadata.put("diagnosticProvedCycles", diagnosticOutcome.isPresent() ? "1" : "0");
        proofFailure.ifPresent(diagnostic -> {
            metadata.put("diagnosticCycleProofStop", diagnostic.code().name());
            diagnostic.metadata().forEach((key, value) -> metadata.put("cycleProof." + key, value));
        });
        Component message = Component.translatable(
                "gui.data_energistics.trinity_planning.diagnostic.insufficient_input");
        if (shortages.size() == 1) {
            Object2ObjectMap.Entry<AEKey, InputRequirement> shortage = shortages.object2ObjectEntrySet().iterator().next();
            AEKey key = shortage.getKey();
            InputRequirement requirement = shortage.getValue();
            BigInteger netConsumed = netChange.getOrDefault(key, BigInteger.ZERO).negate().max(BigInteger.ZERO);
            InputRole role;
            if (key.equals(target) &&
                    requirement.available().compareTo(minimumSeed.getOrDefault(key, BigInteger.ZERO)) < 0) {
                role = InputRole.TARGET_CYCLE_SEED;
            } else if (netConsumed.signum() > 0) {
                role = InputRole.NET_CONSUMED_EXTERNAL_INPUT;
            } else {
                role = InputRole.CYCLE_WORKING_SEED;
            }
            message = Component.translatable(
                    role.translationKey,
                    key.getDisplayName(),
                    requirement.required().toString(),
                    requirement.available().toString(),
                    requirement.missing().toString());
            metadata.put("key", key.toString());
            metadata.put("input_role", role.metadataValue);
            metadata.put("required", requirement.required().toString());
            metadata.put("available", requirement.available().toString());
            metadata.put("missing", requirement.missing().toString());
            metadata.put("net_consumed", netConsumed.toString());
        }
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> emitted = new Object2ObjectLinkedOpenHashMap<>();
        aggregateFirings.forEach((variant, count) -> variant.outputs().forEach(
                (key, amount) -> emitted.merge(key, amount.multiply(count), BigInteger::add)));
        TrinityPlanningDiagnostic.PartialPlan materials = diagnosticOutcome
                .map(TrinityCycleDiagnosticOutcome::materials)
                .orElseGet(() -> new TrinityPlanningDiagnostic.PartialPlan(
                        usedInputs,
                        emitted,
                        missingInputs,
                        shortages,
                        ObjectList.of()));
        TrinityPlanningDiagnostic.Detail detail = diagnosticOutcome.<TrinityPlanningDiagnostic.Detail>map(outcome -> new TrinityPlanningDiagnostic.CompositeEvidence(
                materials,
                ObjectList.of(outcome.evidence())))
                .orElse(materials);
        TrinityPlanningDiagnostic diagnostic = new TrinityPlanningDiagnostic(
                TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT,
                message,
                metadata,
                detail);
        return TrinityAlgorithmResult.failure(diagnostic);
    }

    private enum InputRole {

        TARGET_CYCLE_SEED(
                "target_cycle_seed",
                "gui.data_energistics.trinity_planning.missing_target_cycle_seed"),
        NET_CONSUMED_EXTERNAL_INPUT(
                "net_consumed_external_input",
                "gui.data_energistics.trinity_planning.missing_external_input"),
        CYCLE_WORKING_SEED(
                "cycle_working_seed",
                "gui.data_energistics.trinity_planning.missing_cycle_working_seed");

        private final String metadataValue;
        private final String translationKey;

        InputRole(String metadataValue, String translationKey) {
            this.metadataValue = metadataValue;
            this.translationKey = translationKey;
        }
    }
}
