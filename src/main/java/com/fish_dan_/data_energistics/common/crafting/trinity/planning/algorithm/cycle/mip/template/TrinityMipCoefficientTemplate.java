package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.template;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.util.FastUtilCollections;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.math.BigInteger;

/**
 * Immutable sparse coefficient layout shared by ordinary and radix request-private cycle models.
 *
 * @param variants     stable firing axes
 * @param internalKeys SCC-owned reserve axes
 * @param touchedKeys  stable conservation rows
 * @param netRows      sparse net coefficients by exact key
 */
public record TrinityMipCoefficientTemplate(
                                            ObjectList<TrinityPatternVariant> variants,
                                            ObjectSet<AEKey> internalKeys,
                                            ObjectList<AEKey> touchedKeys,
                                            Object2ObjectMap<AEKey, ObjectList<Coefficient>> netRows) {

    /** Builds sparse rows once from transition effects, without constructing an ojAlgo model. */
    public static TrinityMipCoefficientTemplate create(
                                                       ObjectList<TrinityPatternVariant> variants,
                                                       ObjectList<AEKey> internalKeys) {
        ObjectArrayList<TrinityPatternVariant> orderedVariants = new ObjectArrayList<>(variants);
        orderedVariants.sort(TrinityPatternVariant::compareTo);
        ObjectOpenHashSet<AEKey> internal = new ObjectOpenHashSet<>(internalKeys);
        ObjectOpenHashSet<AEKey> seen = new ObjectOpenHashSet<>();
        ObjectArrayList<AEKey> touched = new ObjectArrayList<>();
        for (TrinityPatternVariant variant : orderedVariants) {
            variant.netChange().keySet().forEach(key -> addStableKey(key, seen, touched));
        }
        internalKeys.forEach(key -> addStableKey(key, seen, touched));
        Object2ObjectLinkedOpenHashMap<AEKey, ObjectList<Coefficient>> rows = new Object2ObjectLinkedOpenHashMap<>();
        for (AEKey key : touched) {
            ObjectArrayList<Coefficient> coefficients = new ObjectArrayList<>();
            for (int index = 0; index < orderedVariants.size(); index++) {
                BigInteger value = orderedVariants.get(index).netChange().getOrDefault(key, BigInteger.ZERO);
                if (value.signum() != 0) {
                    coefficients.add(new Coefficient(index, value));
                }
            }
            rows.put(key, FastUtilCollections.immutableList(coefficients));
        }
        return new TrinityMipCoefficientTemplate(
                FastUtilCollections.immutableList(orderedVariants),
                FastUtilCollections.immutableSet(internal),
                FastUtilCollections.immutableList(touched),
                FastUtilCollections.immutableMap(rows));
    }

    /** Returns a shared immutable empty row when the exact key has no transition coefficient. */
    public ObjectList<Coefficient> coefficients(AEKey key) {
        return netRows.getOrDefault(key, ObjectList.of());
    }

    private static void addStableKey(AEKey key, ObjectSet<AEKey> seen, ObjectList<AEKey> destination) {
        if (seen.add(key)) {
            destination.add(key);
        }
    }

    /** One non-zero coefficient addressed by stable firing-axis index. */
    public record Coefficient(int variantIndex, BigInteger value) {}
}
