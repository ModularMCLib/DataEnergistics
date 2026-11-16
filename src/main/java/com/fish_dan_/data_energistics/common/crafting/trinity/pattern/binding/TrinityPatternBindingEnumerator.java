package com.fish_dan_.data_energistics.common.crafting.trinity.pattern.binding;

import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature;
import com.fish_dan_.data_energistics.utils.FastUtilCollections;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.ints.IntImmutableList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;

import java.math.BigInteger;

/**
 * Enumerates semantically distinct pattern bindings while retaining the first legal Cartesian representative.
 *
 */
@NullMarked
public final class TrinityPatternBindingEnumerator {

    /**
     * @return stateless exact binding enumerator shared by planning and runtime selection
     */
    public static TrinityPatternBindingEnumerator create() {
        return new TrinityPatternBindingEnumerator();
    }

    /**
     * Result of one bounded exact enumeration.
     */
    public sealed interface Result permits Enumerated, LimitExceeded, ArithmeticOverflow {}

    /**
     * @param bindings distinct bindings in first-representative Cartesian order
     */
    public record Enumerated(ObjectList<Binding> bindings) implements Result {

        /**
         * Freezes the canonical binding sequence.
         */
        public Enumerated {
            bindings = FastUtilCollections.immutableList(bindings);
        }
    }

    /**
     * @param required proven distinct binding count already reached
     * @param limit    configured distinct binding limit
     */
    public record LimitExceeded(BigInteger required, int limit) implements Result {}

    /**
     * @param axis exact internal representation that exceeded the executable plan domain
     */
    public record ArithmeticOverflow(TrinityPatternArithmetic axis) implements Result {}

    /**
     * @param cartesianOrdinal    first raw Cartesian ordinal with this aggregate effect
     * @param alternativeOrdinals selected alternative index for every ordered input slot
     */
    public record Binding(int cartesianOrdinal, IntList alternativeOrdinals) {

        /**
         * Retains the immutable choice vector produced by enumeration or runtime ordinal decoding.
         */
        public Binding {
            if (!(alternativeOrdinals instanceof IntImmutableList)) {
                alternativeOrdinals = new IntImmutableList(alternativeOrdinals);
            }
        }
    }

    /**
     * Collapses assignments with identical aggregate consumption and remainder effects.
     *
     * @param inputs      ordered immutable pattern inputs
     * @param maxBindings maximum distinct effects accepted before returning a limit result
     * @return complete enumeration or an exact failure boundary
     */
    public Result enumerate(ObjectList<TrinityPatternPublicationSignature.Input> inputs, int maxBindings) {
        if (maxBindings <= 0) {
            throw new IllegalArgumentException("A Trinity binding enumeration requires inputs and a positive limit");
        }
        BigInteger[] strides = cartesianStrides(inputs);
        ObjectCollection<PartialBinding> current = ObjectList.of(PartialBinding.EMPTY);
        for (int slot = 0; slot < inputs.size(); slot++) {
            TrinityPatternPublicationSignature.Input input = inputs.get(slot);
            Object2ObjectLinkedOpenHashMap<BindingEffect, PartialBinding> distinct = new Object2ObjectLinkedOpenHashMap<>();
            for (PartialBinding prefix : current) {
                for (int alternativeIndex = 0; alternativeIndex < input.alternatives().size(); alternativeIndex++) {
                    PartialBinding candidate = prefix.append(
                            alternativeIndex,
                            strides[slot],
                            input);
                    distinct.putIfAbsent(candidate.effect(), candidate);
                    if (distinct.size() > maxBindings) {
                        return new LimitExceeded(BigInteger.valueOf(distinct.size()), maxBindings);
                    }
                }
            }
            current = distinct.values();
        }

        ObjectArrayList<Binding> bindings = new ObjectArrayList<>(current.size());
        try {
            for (PartialBinding binding : current) {
                bindings.add(new Binding(binding.cartesianOrdinal().intValueExact(), binding.alternatives()));
            }
        } catch (ArithmeticException overflow) {
            return new ArithmeticOverflow(TrinityPatternArithmetic.CARTESIAN_ORDINAL);
        }
        return new Enumerated(bindings);
    }

    private static BigInteger[] cartesianStrides(ObjectList<TrinityPatternPublicationSignature.Input> inputs) {
        BigInteger[] strides = new BigInteger[inputs.size()];
        BigInteger stride = BigInteger.ONE;
        for (int slot = inputs.size() - 1; slot >= 0; slot--) {
            strides[slot] = stride;
            stride = stride.multiply(BigInteger.valueOf(inputs.get(slot).alternatives().size()));
        }
        return strides;
    }

    /**
     * One first-representative prefix and its aggregate transition effect.
     */
    private record PartialBinding(
                                  BigInteger cartesianOrdinal,
                                  IntList alternatives,
                                  BindingEffect effect) {

        private static final PartialBinding EMPTY = new PartialBinding(BigInteger.ZERO, IntList.of(), BindingEffect.EMPTY);

        private PartialBinding append(
                                      int alternativeIndex,
                                      BigInteger stride,
                                      TrinityPatternPublicationSignature.Input input) {
            TrinityPatternPublicationSignature.Alternative alternative = input.alternatives().get(alternativeIndex);
            int[] selected = new int[this.alternatives.size() + 1];
            this.alternatives.getElements(0, selected, 0, this.alternatives.size());
            selected[this.alternatives.size()] = alternativeIndex;
            Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> consumed = new Object2ObjectLinkedOpenHashMap<>(this.effect.consumed());
            consumed.merge(
                    alternative.stack().what(),
                    BigInteger.valueOf(alternative.stack().amount())
                            .multiply(BigInteger.valueOf(input.multiplier())),
                    BigInteger::add);
            Object2ObjectMap<AEKey, BigInteger> remainders = this.effect.remainders();
            AEKey remainingKey = alternative.remainingKey();
            if (remainingKey != null) {
                Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> updated = new Object2ObjectLinkedOpenHashMap<>(remainders);
                updated.merge(
                        remainingKey,
                        BigInteger.valueOf(input.multiplier()),
                        BigInteger::add);
                remainders = Object2ObjectMaps.unmodifiable(updated);
            }
            return new PartialBinding(
                    this.cartesianOrdinal.add(stride.multiply(BigInteger.valueOf(alternativeIndex))),
                    IntImmutableList.of(selected),
                    new BindingEffect(Object2ObjectMaps.unmodifiable(consumed), remainders));
        }
    }

    /**
     * Exact aggregate effects used as the semantic equivalence key.
     */
    private record BindingEffect(Object2ObjectMap<AEKey, BigInteger> consumed, Object2ObjectMap<AEKey, BigInteger> remainders) {

        private static final BindingEffect EMPTY = new BindingEffect(FastUtilCollections.mapOf(), FastUtilCollections.mapOf());
    }
}
