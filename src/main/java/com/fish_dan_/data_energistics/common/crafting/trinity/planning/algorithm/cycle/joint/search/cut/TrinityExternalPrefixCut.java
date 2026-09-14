package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.search.cut;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.search.TrinityFiringBox;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityFiringBounds;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Derives sound box cuts from optimistic external-key reachability without expanding logical firing counts.
 * <p>
 * Saturating dependency closure. It deliberately overestimates available external resources, so only negative
 * reachability conclusions become cuts.
 */
public final class TrinityExternalPrefixCut {

    /**
     * @return stateless dependency-closure cut generator
     */
    public static TrinityExternalPrefixCut create() {
        return new TrinityExternalPrefixCut();
    }

    /**
     * Every external key is optimistically granted {@code cap} units, internal seed is unlimited, consumption is not
     * deducted, and reachable outputs saturate downstream input thresholds. A transition absent even from this
     * over-approximation is therefore impossible in every real schedule within the cap.
     *
     * @return exact box partition, or empty when the closure proves no transition unreachable
     */
    public Optional<TrinityExternalPrefixPartition> partition(
                                                              TrinityFiringBox box,
                                                              Set<AEKey> internalKeys,
                                                              BigInteger cap) {
        if (box == null || internalKeys == null || internalKeys.isEmpty() || cap == null || cap.signum() < 0) {
            throw new IllegalArgumentException("A Trinity external-prefix cut request is incomplete");
        }
        Map<AEKey, BigInteger> thresholds = externalInputThresholds(box, internalKeys);
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> optimisticAmounts = new Object2ObjectLinkedOpenHashMap<>();
        thresholds.keySet().forEach(key -> optimisticAmounts.put(key, cap));
        ObjectLinkedOpenHashSet<TrinityPatternVariant> reachable = new ObjectLinkedOpenHashSet<>();

        boolean changed;
        do {
            changed = false;
            for (int index = 0; index < box.variants().size(); index++) {
                TrinityPatternVariant variant = box.variants().get(index);
                if (!box.bounds().get(index).permitsPositive() || reachable.contains(variant) ||
                        !canTrigger(variant, internalKeys, optimisticAmounts)) {
                    continue;
                }
                reachable.add(variant);
                variant.outputs().forEach((key, amount) -> {
                    if (!internalKeys.contains(key) && amount.signum() > 0) {
                        BigInteger saturated = thresholds.getOrDefault(key, amount).max(amount);
                        optimisticAmounts.merge(key, saturated, BigInteger::max);
                    }
                });
                changed = true;
            }
        } while (changed);

        IntArrayList unreachableAxes = new IntArrayList();
        for (int index = 0; index < box.variants().size(); index++) {
            if (box.bounds().get(index).permitsPositive() &&
                    !reachable.contains(box.variants().get(index))) {
                unreachableAxes.add(index);
            }
        }
        return unreachableAxes.isEmpty() ? Optional.empty() : Optional.of(partition(box, unreachableAxes));
    }

    private static TrinityExternalPrefixPartition partition(
                                                            TrinityFiringBox box,
                                                            IntList unreachableAxes) {
        ObjectArrayList<TrinityFiringBounds> remaining = new ObjectArrayList<>(box.bounds());
        ObjectArrayList<TrinityFiringBox> aboveCap = new ObjectArrayList<>();
        boolean zeroBranch = true;
        for (int index : unreachableAxes) {
            TrinityFiringBounds parent = remaining.get(index);
            ObjectArrayList<TrinityFiringBounds> positive = new ObjectArrayList<>(remaining);
            positive.set(index, new TrinityFiringBounds(
                    parent.lowerInclusive().max(BigInteger.ONE),
                    parent.upperInclusive()));
            aboveCap.add(new TrinityFiringBox(box.variants(), positive));
            if (parent.lowerInclusive().signum() > 0) {
                zeroBranch = false;
                break;
            }
            remaining.set(index, TrinityFiringBounds.fixed(BigInteger.ZERO));
        }
        Optional<TrinityFiringBox> withinCap = zeroBranch ?
                Optional.of(new TrinityFiringBox(box.variants(), remaining)) : Optional.empty();
        return new TrinityExternalPrefixPartition(withinCap, aboveCap);
    }

    private static Map<AEKey, BigInteger> externalInputThresholds(
                                                                  TrinityFiringBox box,
                                                                  Set<AEKey> internalKeys) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> thresholds = new Object2ObjectLinkedOpenHashMap<>();
        for (int index = 0; index < box.variants().size(); index++) {
            if (!box.bounds().get(index).permitsPositive()) {
                continue;
            }
            box.variants().get(index).inputs().forEach((key, amount) -> {
                if (!internalKeys.contains(key)) {
                    thresholds.merge(key, amount, BigInteger::max);
                }
            });
        }
        return thresholds;
    }

    private static boolean canTrigger(
                                      TrinityPatternVariant variant,
                                      Set<AEKey> internalKeys,
                                      Map<AEKey, BigInteger> optimisticAmounts) {
        return variant.inputs().entrySet().stream()
                .filter(entry -> !internalKeys.contains(entry.getKey()))
                .allMatch(entry -> optimisticAmounts
                        .getOrDefault(entry.getKey(), BigInteger.ZERO)
                        .compareTo(entry.getValue()) >= 0);
    }
}
