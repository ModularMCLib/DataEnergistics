package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.template;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.utils.FastUtilCollections;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.math.BigInteger;

/**
 * Immutable sparse coefficient layout shared by the cycle planner and its request-local integer model.
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

    /** Builds the indexed balance rows in firing order. */
    public static TrinityMipCoefficientTemplate create(
                                                       ObjectList<TrinityPatternVariant> variants,
                                                       ObjectList<AEKey> internalKeys) {
        ObjectArrayList<TrinityPatternVariant> orderedVariants = new ObjectArrayList<>(variants);
        orderedVariants.sort(TrinityPatternVariant::compareTo);
        ObjectOpenHashSet<AEKey> internal = new ObjectOpenHashSet<>(internalKeys);
        Object2ObjectLinkedOpenHashMap<AEKey, ObjectArrayList<Coefficient>> sparseRows = new Object2ObjectLinkedOpenHashMap<>();
        for (int index = 0; index < orderedVariants.size(); index++) {
            for (var effect : orderedVariants.get(index).netChange().object2ObjectEntrySet()) {
                var row = sparseRows.computeIfAbsent(effect.getKey(), ignored -> new ObjectArrayList<>());
                if (effect.getValue().signum() != 0) row.add(new Coefficient(index, effect.getValue()));
            }
        }
        internalKeys.forEach(key -> sparseRows.computeIfAbsent(key, ignored -> new ObjectArrayList<>()));
        ObjectArrayList<AEKey> touched = new ObjectArrayList<>(sparseRows.keySet());
        Object2ObjectLinkedOpenHashMap<AEKey, ObjectList<Coefficient>> rows = new Object2ObjectLinkedOpenHashMap<>();
        sparseRows.forEach((key, coefficients) -> rows.put(key, FastUtilCollections.immutableList(coefficients)));
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

    /** One non-zero coefficient addressed by stable firing-axis index. */
    public record Coefficient(int variantIndex, BigInteger value) {}
}
