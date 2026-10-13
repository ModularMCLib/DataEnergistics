package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.util.FastUtilCollections;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.math.BigInteger;
import java.util.Collections;

/**
 * Complete exact firing identity over one stable, sorted variant domain.
 */
public final class TrinityFiringVector implements Comparable<TrinityFiringVector> {

    private final ObjectList<TrinityPatternVariant> variants;
    private final ObjectList<BigInteger> counts;

    private TrinityFiringVector(ObjectList<TrinityPatternVariant> variants, ObjectList<BigInteger> counts) {
        this.variants = variants;
        this.counts = counts;
    }

    /**
     * Builds a complete vector, inserting exact zeroes for omitted variants.
     *
     * @param domain  complete variant domain
     * @param firings sparse or complete non-negative firing map
     * @return immutable complete numeric vector
     */
    public static TrinityFiringVector from(
                                           ObjectList<TrinityPatternVariant> domain,
                                           Object2ObjectMap<TrinityPatternVariant, BigInteger> firings) {
        if (domain == null || domain.isEmpty() || firings == null) {
            throw new IllegalArgumentException("A Trinity firing vector requires a non-empty domain and firing map");
        }
        ObjectList<TrinityPatternVariant> orderedVariants = new ObjectArrayList<>(domain);
        if (orderedVariants.stream().anyMatch(variant -> variant == null)) {
            throw new IllegalArgumentException("A Trinity firing vector domain cannot contain null variants");
        }
        Collections.sort(orderedVariants);
        ObjectSet<TrinityPatternVariant> uniqueVariants = new ObjectOpenHashSet<>(orderedVariants);
        if (uniqueVariants.size() != orderedVariants.size() || !uniqueVariants.containsAll(firings.keySet())) {
            throw new IllegalArgumentException("A Trinity firing vector requires a unique complete variant domain");
        }
        ObjectList<BigInteger> orderedCounts = new ObjectArrayList<>(orderedVariants.size());
        for (TrinityPatternVariant variant : orderedVariants) {
            BigInteger count = firings.getOrDefault(variant, BigInteger.ZERO);
            if (count == null || count.signum() < 0) {
                throw new IllegalArgumentException("Trinity firing counts must be non-negative exact integers");
            }
            orderedCounts.add(count);
        }
        return new TrinityFiringVector(FastUtilCollections.immutableList(orderedVariants), FastUtilCollections.immutableList(orderedCounts));
    }

    /**
     * @return complete stable variant order
     */
    public ObjectList<TrinityPatternVariant> variants() {
        return this.variants;
    }

    /**
     * @return exact counts aligned with {@link #variants()}
     */
    public ObjectList<BigInteger> counts() {
        return this.counts;
    }

    @Override
    public int compareTo(TrinityFiringVector other) {
        if (!this.variants.equals(other.variants)) {
            throw new IllegalArgumentException("Trinity firing vectors must share the same complete variant domain");
        }
        for (int index = 0; index < this.counts.size(); index++) {
            int compared = this.counts.get(index).compareTo(other.counts.get(index));
            if (compared != 0) {
                return compared;
            }
        }
        return 0;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TrinityFiringVector vector)) {
            return false;
        }
        return this.variants.equals(vector.variants) && this.counts.equals(vector.counts);
    }

    @Override
    public int hashCode() {
        return 31 * this.variants.hashCode() + this.counts.hashCode();
    }
}
