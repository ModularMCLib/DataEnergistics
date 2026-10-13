package com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.sameitem.TrinitySameItemPolicy;
import com.fish_dan_.data_energistics.util.FastUtilCollections;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.math.BigInteger;

/**
 * One immutable, fully bound transition in the Trinity crafting hypergraph.
 *
 * @param patternIdentity      stable parent pattern semantics
 * @param primaryOutput        primary output used to resolve the live provider pattern on the server thread
 * @param ordinal              deterministic Cartesian binding ordinal for that pattern
 * @param alternativeOrdinals  selected alternative index for every ordered input slot
 * @param bindings             exact physical input bindings retained for live pattern selection
 * @param inputs               logical per-firing consumption used by planning balances
 * @param declaredOutputs      exact pattern-declared outputs, excluding input remainders
 * @param outputs              logical declared outputs plus remaining keys used by planning balances
 * @param netChange            logical signed {@code outputs - inputs}
 * @param physicalInputs       exact component-aware consumption represented by {@code bindings}
 * @param physicalOutputs      exact declared outputs plus exact input remainders
 * @param requiresExactBinding whether execution must retain this complete server-captured assignment instead of
 *                             decoding a legacy ordinal
 */
public record TrinityPatternVariant(
                                    TrinityPatternIdentity patternIdentity,
                                    AEKey primaryOutput,
                                    int ordinal,
                                    IntList alternativeOrdinals,
                                    ObjectList<TrinityBoundPatternInput> bindings,
                                    Object2ObjectMap<AEKey, BigInteger> inputs,
                                    Object2ObjectMap<AEKey, BigInteger> declaredOutputs,
                                    Object2ObjectMap<AEKey, BigInteger> outputs,
                                    Object2ObjectMap<AEKey, BigInteger> netChange,
                                    Object2ObjectMap<AEKey, BigInteger> physicalInputs,
                                    Object2ObjectMap<AEKey, BigInteger> physicalOutputs,
                                    boolean requiresExactBinding,
                                    Object2ObjectMap<AEKey, BigInteger> lifetimeTools)
        implements Comparable<TrinityPatternVariant> {

    /**
     * Copies all planner values and verifies the retained transition equation.
     */
    public TrinityPatternVariant {
        if (ordinal < 0 || alternativeOrdinals.size() != bindings.size()) {
            throw new IllegalArgumentException("A Trinity pattern variant requires one legal binding per input slot");
        }
        alternativeOrdinals = IntLists.unmodifiable(new IntArrayList(alternativeOrdinals));
        bindings = FastUtilCollections.immutableList(bindings);
        for (int slot = 0; slot < bindings.size(); slot++) {
            TrinityBoundPatternInput binding = bindings.get(slot);
            int alternative = alternativeOrdinals.getInt(slot);
            if (binding.slotIndex() != slot ||
                    binding.alternativeIndex() != alternative) {
                throw new IllegalArgumentException("A Trinity pattern variant binding order is inconsistent");
            }
        }
        inputs = copyPositive(inputs, "inputs");
        declaredOutputs = copyPositive(declaredOutputs, "declared outputs");
        outputs = copyPositive(outputs, "outputs");
        physicalInputs = copyPositive(physicalInputs, "physical inputs");
        physicalOutputs = copyPositive(physicalOutputs, "physical outputs");
        lifetimeTools = copyPositive(lifetimeTools, "lifetime tool reservations");
        if (!declaredOutputs.containsKey(primaryOutput)) {
            throw new IllegalArgumentException("A Trinity pattern variant must retain its primary output");
        }
        for (Object2ObjectMap.Entry<AEKey, BigInteger> entry : declaredOutputs.object2ObjectEntrySet()) {
            BigInteger total = physicalOutputs.get(entry.getKey());
            if (total == null || total.compareTo(entry.getValue()) < 0) {
                throw new IllegalArgumentException(
                        "Trinity declared outputs must be contained in the complete transition outputs");
            }
        }
        netChange = copySignedNonZero(netChange);
        if (!netChange.equals(calculateNetChange(inputs, outputs))) {
            throw new IllegalArgumentException("A Trinity pattern variant net change must equal outputs minus inputs");
        }
    }

    /**
     * Constructs one exact transition from its selected bindings and declared pattern outputs.
     *
     * @param patternIdentity     stable parent pattern semantics
     * @param primaryOutput       primary declared pattern output
     * @param ordinal             stable Cartesian binding ordinal
     * @param alternativeOrdinals selected alternative indexes
     * @param bindings            selected immutable bindings
     * @param declaredOutputs     immutable declared pattern outputs
     * @return validated exact transition
     */
    public static TrinityPatternVariant create(TrinityPatternIdentity patternIdentity,
                                               AEKey primaryOutput,
                                               int ordinal,
                                               IntList alternativeOrdinals,
                                               ObjectList<TrinityBoundPatternInput> bindings,
                                               ObjectList<GenericStack> declaredOutputs) {
        return create(patternIdentity, primaryOutput, ordinal, alternativeOrdinals, bindings, declaredOutputs, false);
    }

    /** Creates either a legacy Cartesian variant or an explicitly frozen complete assignment. */
    public static TrinityPatternVariant create(TrinityPatternIdentity patternIdentity,
                                               AEKey primaryOutput,
                                               int ordinal,
                                               IntList alternativeOrdinals,
                                               ObjectList<TrinityBoundPatternInput> bindings,
                                               ObjectList<GenericStack> declaredOutputs,
                                               boolean requiresExactBinding) {
        return create(patternIdentity, primaryOutput, ordinal, alternativeOrdinals, bindings, declaredOutputs,
                requiresExactBinding, FastUtilCollections.mapOf());
    }

    public static TrinityPatternVariant create(TrinityPatternIdentity patternIdentity, AEKey primaryOutput,
                                               int ordinal, IntList alternativeOrdinals,
                                               ObjectList<TrinityBoundPatternInput> bindings, ObjectList<GenericStack> declaredOutputs,
                                               boolean requiresExactBinding, Object2ObjectMap<AEKey, BigInteger> reservations) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> inputs = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> declared = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> outputs = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> tools = new Object2ObjectLinkedOpenHashMap<>();
        for (TrinityBoundPatternInput binding : bindings) {
            if (binding.lifetimeBudget()) {
                merge(tools, binding.template().what(), binding.consumedAmount());
                continue;
            }
            merge(inputs, binding.template().what(), binding.consumedAmount());
            if (binding.remainingKey() != null) {
                merge(outputs, binding.remainingKey(), binding.remainingAmount());
            }
            for (GenericStack byproduct : binding.byproducts()) {
                merge(outputs, byproduct.what(), BigInteger.valueOf(byproduct.amount()).multiply(binding.consumedAmount()));
            }
        }
        for (GenericStack output : declaredOutputs) {
            if (output == null || output.what() == null || output.amount() <= 0L) {
                throw new IllegalArgumentException("A Trinity pattern variant cannot contain an invalid output");
            }
            BigInteger amount = BigInteger.valueOf(output.amount());
            merge(declared, output.what(), amount);
            merge(outputs, output.what(), amount);
        }
        var physicalInputs = new Object2ObjectLinkedOpenHashMap<>(inputs);
        var physicalOutputs = new Object2ObjectLinkedOpenHashMap<>(outputs);
        tools.replaceAll((key, held) -> held.max(reservations.getOrDefault(key, BigInteger.ZERO)));
        tools.forEach((key, held) -> {
            merge(inputs, key, held);
            merge(outputs, key, held);
        });
        return new TrinityPatternVariant(
                patternIdentity,
                primaryOutput,
                ordinal,
                alternativeOrdinals,
                bindings,
                inputs,
                declared,
                outputs,
                calculateNetChange(inputs, outputs),
                physicalInputs,
                physicalOutputs,
                requiresExactBinding,
                tools);
    }

    /**
     * Returns a transition whose solver-facing balances use authorised same-item representatives while all physical
     * identity, binding, declared-output and remainder data remains unchanged.
     */
    public TrinityPatternVariant normalized(TrinitySameItemPolicy policy) {
        ObjectLinkedOpenHashSet<AEItemKey> exactTools = new ObjectLinkedOpenHashSet<>();
        for (TrinityBoundPatternInput binding : this.bindings) {
            if (binding.reusableRule() != null) {
                exactTools.add(binding.reusableRule().initialKey());
                if (binding.remainingKey() instanceof AEItemKey successor) {
                    exactTools.add(successor);
                }
            }
        }
        policy = policy.preservingExactItems(exactTools);
        Object2ObjectMap<AEKey, BigInteger> normalizedInputs = policy.normalizeAmounts(this.physicalInputs);
        Object2ObjectMap<AEKey, BigInteger> normalizedOutputs = policy.normalizeAmounts(this.physicalOutputs);
        if (!lifetimeTools.isEmpty()) {
            normalizedInputs = new Object2ObjectLinkedOpenHashMap<>(normalizedInputs);
            normalizedOutputs = new Object2ObjectLinkedOpenHashMap<>(normalizedOutputs);
            for (var tool : lifetimeTools.entrySet()) {
                normalizedInputs.merge(tool.getKey(), tool.getValue(), BigInteger::add);
                normalizedOutputs.merge(tool.getKey(), tool.getValue(), BigInteger::add);
            }
        }
        if (normalizedInputs.equals(this.inputs) && normalizedOutputs.equals(this.outputs)) {
            return this;
        }
        return new TrinityPatternVariant(
                this.patternIdentity,
                this.primaryOutput,
                this.ordinal,
                this.alternativeOrdinals,
                this.bindings,
                normalizedInputs,
                this.declaredOutputs,
                normalizedOutputs,
                calculateNetChange(normalizedInputs, normalizedOutputs),
                this.physicalInputs,
                this.physicalOutputs,
                this.requiresExactBinding,
                this.lifetimeTools);
    }

    /** Returns exact physical input remainders without mixing them with logical planning representatives. */
    public Object2ObjectMap<AEKey, BigInteger> physicalRemainingOutputs() {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> remaining = new Object2ObjectLinkedOpenHashMap<>();
        this.physicalOutputs.forEach((key, amount) -> {
            BigInteger remainder = amount.subtract(this.declaredOutputs.getOrDefault(key, BigInteger.ZERO));
            if (remainder.signum() > 0) {
                remaining.put(key, remainder);
            }
        });
        return FastUtilCollections.immutableMap(remaining);
    }

    /**
     * Returns outputs that can create a downstream planning dependency.
     *
     * <p>
     * An unchanged reusable binding is physically returned by the recipe, but it is an input reservation rather
     * than a producer of the material. Treating that remainder as a producer would connect every such recipe through
     * the retained tool and turn an otherwise acyclic route family into a large artificial cycle. The complete
     * {@link #outputs()} map remains available for conservation and execution accounting.
     * </p>
     *
     * @return declared and changing remainder outputs, excluding unchanged reusable reservations
     */
    public Object2ObjectMap<AEKey, BigInteger> dependencyOutputs() {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> dependencyOutputs = new Object2ObjectLinkedOpenHashMap<>(this.outputs);
        for (TrinityBoundPatternInput binding : this.bindings) {
            if (binding.lifetimeBudget() || binding.reusableRule() == null || binding.remainingKey() == null ||
                    !binding.remainingKey().equals(binding.template().what())) {
                continue;
            }
            BigInteger remaining = dependencyOutputs.get(binding.remainingKey());
            if (remaining == null) {
                continue;
            }
            remaining = remaining.subtract(binding.remainingAmount());
            if (remaining.signum() > 0) {
                dependencyOutputs.put(binding.remainingKey(), remaining);
            } else {
                dependencyOutputs.remove(binding.remainingKey());
            }
        }
        return FastUtilCollections.immutableMap(dependencyOutputs);
    }

    @Override
    public int compareTo(TrinityPatternVariant other) {
        int patternOrder = this.patternIdentity.compareTo(other.patternIdentity);
        return patternOrder != 0 ? patternOrder : Integer.compare(this.ordinal, other.ordinal);
    }

    private static Object2ObjectMap<AEKey, BigInteger> copyPositive(Object2ObjectMap<AEKey, BigInteger> source, String role) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> copied = new Object2ObjectLinkedOpenHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("Trinity pattern variant " + role + " must be positive");
            }
            copied.put(key, amount);
        });
        return FastUtilCollections.immutableMap(copied);
    }

    private static Object2ObjectMap<AEKey, BigInteger> copySignedNonZero(Object2ObjectMap<AEKey, BigInteger> source) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> copied = new Object2ObjectLinkedOpenHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() == 0) {
                throw new IllegalArgumentException("Trinity pattern variant net change must be non-zero");
            }
            copied.put(key, amount);
        });
        return FastUtilCollections.immutableMap(copied);
    }

    private static Object2ObjectMap<AEKey, BigInteger> calculateNetChange(Object2ObjectMap<AEKey, BigInteger> inputs,
                                                                          Object2ObjectMap<AEKey, BigInteger> outputs) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> net = new Object2ObjectLinkedOpenHashMap<>();
        inputs.forEach((key, amount) -> merge(net, key, amount.negate()));
        outputs.forEach((key, amount) -> merge(net, key, amount));
        net.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        return FastUtilCollections.immutableMap(net);
    }

    private static void merge(Object2ObjectMap<AEKey, BigInteger> amounts, AEKey key, BigInteger amount) {
        amounts.merge(key, amount, BigInteger::add);
    }
}
