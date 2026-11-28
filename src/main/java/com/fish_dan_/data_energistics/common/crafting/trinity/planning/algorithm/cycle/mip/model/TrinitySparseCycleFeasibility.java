package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.bounds.TrinityCycleObjectiveBounds;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.template.TrinityMipCoefficientTemplate.Coefficient;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanQuality;
import com.fish_dan_.data_energistics.utils.AmountMath;
import com.fish_dan_.data_energistics.utils.FastUtilCollections;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;

/** Constructs an exact conservation candidate by adding firings only where their output is needed. */
final class TrinitySparseCycleFeasibility {

    private static final TrinityCycleObjectiveBounds OBJECTIVE_BOUNDS = TrinityCycleObjectiveBounds.create();

    private TrinitySparseCycleFeasibility() {}

    static @Nullable TrinityCycleFeasibilitySolution solve(
                                                           TrinityCycleFeasibilityRequest request,
                                                           TrinityPlanningControl control) {
        long started = System.nanoTime();
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> bounds = new Object2ObjectLinkedOpenHashMap<>(
                request.demand().requiredNetChangeLowerBounds());
        ObjectSet<AEKey> external = OBJECTIVE_BOUNDS.externalReserveKeys(request);
        ObjectLinkedOpenHashSet<AEKey> keys = new ObjectLinkedOpenHashSet<>(request.coefficientTemplate().touchedKeys());
        keys.addAll(request.demand().finalBalanceLowerBounds().keySet());
        keys.addAll(request.internalKeys());
        keys.addAll(external);
        for (AEKey key : keys) {
            if (!request.producibleInputs().contains(key)) {
                bounds.merge(key, finalBalance(request, key).subtract(available(request, key)), BigInteger::max);
            }
        }
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> net = new Object2ObjectLinkedOpenHashMap<>();
        BigInteger[] firings = new BigInteger[request.variants().size()];
        for (int index = 0; index < firings.length; index++) {
            TrinityPatternVariant variant = request.variants().get(index);
            firings[index] = request.firingBounds().get(variant).lowerInclusive();
            addNet(net, variant, firings[index]);
        }
        ObjectArrayFIFOQueue<AEKey> pending = new ObjectArrayFIFOQueue<>();
        ObjectOpenHashSet<AEKey> queued = new ObjectOpenHashSet<>();
        for (var entry : bounds.object2ObjectEntrySet()) {
            if (net.getOrDefault(entry.getKey(), BigInteger.ZERO).compareTo(entry.getValue()) < 0) {
                queued.add(entry.getKey());
                pending.enqueue(entry.getKey());
            }
        }
        int updates = 0;
        // Repeated updates can chase deficits around a lossy cycle. Leave those requests to the full solver.
        long updateLimit = Math.max(64, 12L * firings.length);
        while (!pending.isEmpty()) {
            if (++updates > updateLimit || control.cancellationRequested() || control.deadlineExceeded()) {
                return null;
            }
            AEKey key = pending.dequeue();
            queued.remove(key);
            BigInteger shortage = bounds.get(key).subtract(net.getOrDefault(key, BigInteger.ZERO));
            if (shortage.signum() <= 0) continue;

            Coefficient producer = chooseProducer(request, firings, key);
            if (producer == null) return null;
            int index = producer.variantIndex();
            TrinityPatternVariant variant = request.variants().get(index);
            BigInteger count = AmountMath.ceilDivideNonNegative(shortage, producer.value());
            var upper = request.firingBounds().get(variant).upperInclusive();
            if (upper.isPresent()) count = count.min(upper.orElseThrow().subtract(firings[index]));
            firings[index] = firings[index].add(count);
            addNet(net, variant, count);
            for (AEKey touched : variant.netChange().keySet()) {
                BigInteger lower = bounds.get(touched);
                if (lower != null && net.get(touched).compareTo(lower) < 0 && queued.add(touched)) {
                    pending.enqueue(touched);
                }
            }
        }

        Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> firingVector = new Object2ObjectLinkedOpenHashMap<>();
        BigInteger total = BigInteger.ZERO;
        for (int index = 0; index < firings.length; index++) {
            if (firings[index].signum() > 0) {
                firingVector.put(request.variants().get(index), firings[index]);
                total = total.add(firings[index]);
            }
        }
        if (total.compareTo(request.firingLowerBound().max(BigInteger.ONE)) < 0) return null;
        return candidate(request, external, net, firingVector, started);
    }

    private static @Nullable Coefficient chooseProducer(
                                                        TrinityCycleFeasibilityRequest request,
                                                        BigInteger[] firings,
                                                        AEKey key) {
        Coefficient preferred = null;
        for (Coefficient coefficient : request.coefficientTemplate().coefficients(key)) {
            int index = coefficient.variantIndex();
            if (coefficient.value().signum() <= 0 || !request.firingBounds().get(request.variants().get(index))
                    .contains(firings[index].add(BigInteger.ONE)))
                continue;
            if (preferred == null || coefficient.value().compareTo(preferred.value()) > 0) preferred = coefficient;
        }
        return preferred;
    }

    private static @Nullable TrinityCycleFeasibilitySolution candidate(
                                                                       TrinityCycleFeasibilityRequest request,
                                                                       ObjectSet<AEKey> externalKeys,
                                                                       Object2ObjectMap<AEKey, BigInteger> net,
                                                                       Object2ObjectMap<TrinityPatternVariant, BigInteger> firings,
                                                                       long started) {
        BigInteger minimumExternal = OBJECTIVE_BOUNDS.minimumFirstExternalInput(request);
        BigInteger externalFloor = request.fixedExternalTotal().orElse(minimumExternal);
        if (externalFloor.compareTo(minimumExternal) < 0) return null;
        var external = reserve(request, externalKeys, net, externalFloor);
        var seed = reserve(request, request.internalKeys(), net,
                request.seedLowerBound().max(OBJECTIVE_BOUNDS.minimumFirstInternalInput(request)));
        if (external == null || seed == null || request.fixedExternalTotal().filter(total -> !total.equals(sum(external))).isPresent()) {
            return null;
        }
        return new TrinityCycleFeasibilitySolution(firings, seed, external, 0,
                System.nanoTime() - started, false, TrinityPlanQuality.VERIFIED_FEASIBLE,
                FastUtilCollections.mapOf(), FastUtilCollections.mapOf(), 0);
    }

    private static @Nullable Object2ObjectMap<AEKey, BigInteger> reserve(
                                                                         TrinityCycleFeasibilityRequest request,
                                                                         ObjectSet<AEKey> keys,
                                                                         Object2ObjectMap<AEKey, BigInteger> net,
                                                                         BigInteger minimum) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> reserve = new Object2ObjectLinkedOpenHashMap<>();
        for (AEKey key : keys) {
            BigInteger required = finalBalance(request, key).subtract(net.getOrDefault(key, BigInteger.ZERO));
            if (required.signum() > 0) reserve.put(key, required);
        }
        BigInteger remaining = minimum.subtract(sum(reserve));
        for (AEKey key : keys) {
            if (remaining.signum() <= 0) break;
            BigInteger current = reserve.getOrDefault(key, BigInteger.ZERO);
            BigInteger extra = request.producibleInputs().contains(key) ?
                    remaining : available(request, key).subtract(current).min(remaining);
            if (extra.signum() > 0) {
                reserve.put(key, current.add(extra));
                remaining = remaining.subtract(extra);
            }
        }
        return remaining.signum() > 0 ? null : reserve;
    }

    private static BigInteger sum(Object2ObjectMap<AEKey, BigInteger> amounts) {
        return amounts.values().stream().reduce(BigInteger.ZERO, BigInteger::add);
    }

    private static BigInteger available(TrinityCycleFeasibilityRequest request, AEKey key) {
        return request.available().getOrDefault(key, BigInteger.ZERO);
    }

    private static BigInteger finalBalance(TrinityCycleFeasibilityRequest request, AEKey key) {
        return request.demand().finalBalanceLowerBounds().getOrDefault(key, BigInteger.ZERO);
    }

    private static void addNet(Object2ObjectMap<AEKey, BigInteger> net, TrinityPatternVariant variant, BigInteger count) {
        if (count.signum() == 0) return;
        variant.netChange().forEach((key, value) -> net.merge(key, value.multiply(count), BigInteger::add));
    }
}
