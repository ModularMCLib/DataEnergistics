package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityBoundPatternInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.util.FastUtilCollections;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.math.BigInteger;

/**
 * Retains one stable representative for variants with exactly identical executable transition effects.
 * <p>
 * This compaction is deliberately stricter than net-change equality: primary output, exact consumption, declared
 * output and complete output including input remainders must all match.
 */
public final class TrinityTransitionEffectCompactor {

    /**
     * @return stateless exact compactor
     */
    public static TrinityTransitionEffectCompactor create() {
        return new TrinityTransitionEffectCompactor();
    }

    /**
     * Returns stable representatives in the natural variant order used by the final identity objective.
     */
    public ObjectList<TrinityPatternVariant> compact(ObjectList<TrinityPatternVariant> variants) {
        if (variants == null) {
            throw new IllegalArgumentException("Trinity transition compaction requires variants");
        }
        Object2ObjectLinkedOpenHashMap<TransitionEffect, TrinityPatternVariant> representatives = new Object2ObjectLinkedOpenHashMap<>();
        variants.stream().sorted().forEach(variant -> representatives.putIfAbsent(
                TransitionEffect.from(variant),
                variant));
        return FastUtilCollections.immutableList(representatives.values());
    }

    private record TransitionEffect(
                                    AEKey primaryOutput,
                                    Object2ObjectMap<AEKey, BigInteger> inputs,
                                    Object2ObjectMap<AEKey, BigInteger> declaredOutputs,
                                    Object2ObjectMap<AEKey, BigInteger> outputs,
                                    Object2ObjectMap<AEKey, BigInteger> physicalInputs,
                                    Object2ObjectMap<AEKey, BigInteger> physicalOutputs,
                                    ObjectList<TrinityBoundPatternInput> exactBindings) {

        private static TransitionEffect from(TrinityPatternVariant variant) {
            if (variant == null) {
                throw new IllegalArgumentException("A Trinity transition effect requires a variant");
            }
            return new TransitionEffect(
                    variant.primaryOutput(),
                    variant.inputs(),
                    variant.declaredOutputs(),
                    variant.outputs(),
                    variant.physicalInputs(),
                    variant.physicalOutputs(),
                    variant.requiresExactBinding() ? variant.bindings() : ObjectList.of());
        }
    }
}
