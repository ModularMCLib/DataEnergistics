package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.optimization;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.inventory.TrinityPlanningInventory;

import appeng.api.stacks.AEKey;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;

/**
 * Expands one stable acyclic diagnostic route without numeric solver bounds or recursive demand traversal.
 * All state belongs to one invocation. The resulting firing vector must pass exact diagnostic verification;
 * it is neither an executable plan nor a proof of minimum missing material across alternative routes.
 */
final class TrinityAcyclicShortagePropagator {

    private TrinityAcyclicShortagePropagator() {}

    /**
     * Variants arrive in stable identity order. Every output precedes all of its producer's inputs, so shared
     * input demand is accumulated before inventory is allocated. Target inventory is already accounted for in
     * requiredTargetNet and must not be allocated again, including for NET_NEW requests.
     */
    static TrinityAlgorithmResult<Object2ObjectMap<TrinityPatternVariant, BigInteger>> expand(
                                                                                              Iterable<TrinityPatternVariant> variants,
                                                                                              AEKey target,
                                                                                              BigInteger requiredTargetNet,
                                                                                              TrinityPlanningInventory inventory,
                                                                                              TrinityPlanningControl control) {
        Object2ObjectLinkedOpenHashMap<AEKey, TrinityPatternVariant> producers = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, ObjectLinkedOpenHashSet<AEKey>> dependencies = new Object2ObjectLinkedOpenHashMap<>();
        Object2IntLinkedOpenHashMap<AEKey> incoming = new Object2IntLinkedOpenHashMap<>();
        for (TrinityPatternVariant variant : variants) {
            TrinityPlanningDiagnostic stopped = stopDiagnostic(control);
            if (stopped != null) {
                return TrinityAlgorithmResult.failure(stopped);
            }
            for (AEKey output : variant.outputs().keySet()) {
                producers.putIfAbsent(output, variant);
                incoming.putIfAbsent(output, 0);
                ObjectLinkedOpenHashSet<AEKey> inputs = dependencies.computeIfAbsent(
                        output, ignored -> new ObjectLinkedOpenHashSet<>());
                for (AEKey input : variant.inputs().keySet()) {
                    if (inputs.add(input)) {
                        incoming.addTo(input, 1);
                    }
                }
            }
        }

        ObjectArrayFIFOQueue<AEKey> ready = new ObjectArrayFIFOQueue<>();
        for (var entry : incoming.object2IntEntrySet()) {
            if (entry.getIntValue() == 0) {
                ready.enqueue(entry.getKey());
            }
        }
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> need = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> firings = new Object2ObjectLinkedOpenHashMap<>();
        need.put(target, requiredTargetNet);
        int visited = 0;
        while (!ready.isEmpty()) {
            TrinityPlanningDiagnostic stopped = stopDiagnostic(control);
            if (stopped != null) {
                return TrinityAlgorithmResult.failure(stopped);
            }
            AEKey key = ready.dequeue();
            visited++;
            BigInteger required = need.getOrDefault(key, BigInteger.ZERO).max(BigInteger.ZERO);
            BigInteger available = key.equals(target) ?
                    BigInteger.ZERO : inventory.availableUpTo(key, required);
            BigInteger remaining = required.subtract(available);
            if (remaining.signum() > 0 && producers.containsKey(key)) {
                TrinityPatternVariant producer = producers.get(key);
                BigInteger[] division = remaining.divideAndRemainder(producer.outputs().get(key));
                BigInteger count = division[1].signum() == 0 ? division[0] : division[0].add(BigInteger.ONE);
                firings.merge(producer, count, BigInteger::add);
                producer.netChange().forEach((changedKey, amount) -> need.merge(changedKey, amount.multiply(count).negate(), BigInteger::add));
            }
            if (dependencies.containsKey(key)) {
                for (AEKey input : dependencies.get(key)) {
                    if (incoming.addTo(input, -1) == 1) {
                        ready.enqueue(input);
                    }
                }
            }
        }
        if (visited != incoming.size()) {
            Object2ObjectLinkedOpenHashMap<String, String> metadata = new Object2ObjectLinkedOpenHashMap<>();
            metadata.put("phase", "large_shortage");
            metadata.put("constraint", "acyclic_dependencies");
            return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                    TrinityPlanningDiagnosticCode.MIP_INEXACT_RESULT,
                    Component.translatable("gui.data_energistics.trinity_planning.diagnostic.inexact_result"),
                    metadata));
        }
        return TrinityAlgorithmResult.success(Object2ObjectMaps.unmodifiable(firings));
    }

    private static @Nullable TrinityPlanningDiagnostic stopDiagnostic(TrinityPlanningControl control) {
        TrinityPlanningDiagnosticCode code;
        String translationKey;
        if (control.cancellationRequested()) {
            code = TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED;
            translationKey = "gui.data_energistics.trinity_planning.diagnostic.cancelled";
        } else if (control.deadlineExceeded()) {
            code = TrinityPlanningDiagnosticCode.MIP_TIMEOUT;
            translationKey = "gui.data_energistics.trinity_planning.diagnostic.timeout";
        } else {
            return null;
        }
        return new TrinityPlanningDiagnostic(code, Component.translatable(translationKey),
                Object2ObjectMaps.singleton("phase", "large_shortage"));
    }
}
