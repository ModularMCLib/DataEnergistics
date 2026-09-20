package com.fish_dan_.data_energistics.common.crafting.packaged.recipe;

import com.fish_dan_.data_energistics.api.crafting.matching.ItemMatchingRule;
import com.fish_dan_.data_energistics.api.crafting.matching.ProcessingMatchMode;
import com.fish_dan_.data_energistics.api.crafting.matching.ResourceCapacityMatching;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import com.fish_dan_.data_energistics.common.crafting.pattern.matching.EncodedPatternMatching;

import appeng.api.crafting.IPatternDetails;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;

import java.math.BigInteger;
import java.util.List;

/**
 * Pattern-authorized output capacity assignment shared by admission and native physical collection.
 */
@NullMarked
public final class PackagedOutputMatching {

    private static final String RULES = "pattern_outputs";

    private PackagedOutputMatching() {}

    /**
     * Freezes every sparse output's template, quantity and global matching domain at admission.
     */
    public static void save(IPatternDetails pattern, CompoundTag progress, HolderLookup.Provider registries) {
        var encoded = new ListTag();
        for (Rule rule : rules(pattern)) {
            var tag = GenericStack.writeTag(registries, rule.stack());
            tag.merge(rule.matching().save());
            encoded.add(tag);
        }
        progress.put(RULES, encoded);
    }

    public static boolean matches(IPatternDetails pattern, List<ItemStack> actual) {
        return matches(pattern, amounts(actual));
    }

    public static boolean matches(IPatternDetails pattern, Object2LongMap<? extends AEKey> actual) {
        var amounts = new KeyCounter();
        for (var entry : actual.object2LongEntrySet()) amounts.add(entry.getKey(), entry.getLongValue());
        return matches(pattern, amounts);
    }

    public static boolean matches(IPatternDetails pattern, ItemStack actual, long amount) {
        var amounts = new KeyCounter();
        if (!actual.isEmpty()) amounts.add(AEItemKey.of(actual), amount);
        return matches(pattern, amounts);
    }

    public static boolean matches(IPatternDetails pattern, KeyCounter actual) {
        var rules = rules(pattern);
        return assign(rules, rules.stream().map(Rule::stack).collect(ObjectArrayList.toList()), stacks(actual), true);
    }

    /** Requires the primary product exactly; every declared secondary output must be a real native return. */
    public static boolean matchesWithAdditionalReturns(IPatternDetails pattern, ItemStack produced, List<ItemStack> returns) {
        var declared = rules(pattern);
        if (declared.isEmpty() || produced.isEmpty()) return false;
        var primary = declared.getFirst();
        if (primary.stack().amount() != produced.getCount() || !primary.accepts(AEItemKey.of(produced))) return false;
        var available = stacks(amounts(returns));
        var secondary = declared.subList(1, declared.size());
        long[] required = secondary.stream().mapToLong(rule -> rule.stack().amount()).toArray();
        return ResourceCapacityMatching.accepts(available.stream().mapToLong(GenericStack::amount).toArray(),
                required, required, (a, r) -> secondary.get(r).accepts(available.get(a).what()), (r, a) -> r.equals(a));
    }

    /**
     * A single-port preflight; quantities are exact and explicit prototype declarations take priority.
     */
    public static boolean matches(IPatternDetails pattern, GenericStack expected, GenericStack actual) {
        return expected.amount() == actual.amount() && sameKey(rules(pattern), expected.what(), actual.what());
    }

    public static boolean matches(PackagedMachineOperation operation, ItemStack expected, ItemStack actual) {
        return expected.getCount() == actual.getCount() && sameKey(operation, expected, actual);
    }

    public static boolean sameKey(PackagedMachineOperation operation, ItemStack expected, ItemStack actual) {
        if (expected.isEmpty() || actual.isEmpty()) return expected.isEmpty() && actual.isEmpty();
        return sameKey(rules(operation), AEItemKey.of(expected), AEItemKey.of(actual));
    }

    public static boolean matches(PackagedMachineOperation operation, List<ItemStack> expected, List<ItemStack> actual) {
        return assign(rules(operation), stacks(amounts(expected)), stacks(amounts(actual)), true);
    }

    public static boolean acceptsPartial(PackagedMachineOperation operation, List<ItemStack> expected, List<ItemStack> actual) {
        return assign(rules(operation), stacks(amounts(expected)), stacks(amounts(actual)), false);
    }

    public static boolean matchesResources(PackagedMachineOperation operation, List<GenericStack> expected,
                                           List<GenericStack> actual) {
        if (expected.size() != actual.size()) return false;
        for (int i = 0; i < expected.size(); i++) if (!matches(operation, expected.get(i), actual.get(i))) return false;
        return assign(rules(operation), expected, actual, true);
    }

    public static boolean matches(PackagedMachineOperation operation, GenericStack expected, GenericStack actual) {
        return expected.amount() == actual.amount() && sameKey(rules(operation), expected.what(), actual.what());
    }

    private static boolean sameKey(List<Rule> rules, AEKey expected, AEKey actual) {
        var matching = rules.stream().filter(rule -> rule.stack().what().equals(expected)).collect(ObjectArrayList.toList());
        if (matching.isEmpty()) matching = rules.stream().filter(rule -> rule.accepts(expected)).collect(ObjectArrayList.toList());
        if (matching.isEmpty()) return rules.isEmpty() && expected.equals(actual);
        return matching.stream().allMatch(rule -> rule.accepts(actual));
    }

    private static boolean assign(List<Rule> rules, List<GenericStack> expected, List<GenericStack> actual, boolean complete) {
        if (expected.stream().anyMatch(stack -> stack.amount() <= 0) || actual.stream().anyMatch(stack -> stack.amount() <= 0))
            return false;
        BigInteger expectedTotal = total(expected);
        BigInteger actualTotal = total(actual);
        if (actualTotal.compareTo(expectedTotal) > 0 || complete && !actualTotal.equals(expectedTotal)) return false;
        if (rules.isEmpty()) {
            rules = expected.stream().map(stack -> new Rule(stack, ItemMatchingRule.EXACT)).collect(ObjectArrayList.toList());
        }
        BigInteger declaredTotal = total(rules.stream().map(Rule::stack).collect(ObjectArrayList.toList()));
        if (declaredTotal.signum() == 0) return actualTotal.signum() == 0;
        long[] capacities = new long[rules.size()];
        for (int i = 0; i < capacities.length; i++) {
            var scaled = BigInteger.valueOf(rules.get(i).stack().amount()).multiply(expectedTotal).divideAndRemainder(declaredTotal);
            if (scaled[1].signum() != 0 || scaled[0].compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) return false;
            capacities[i] = scaled[0].longValueExact();
        }
        var domains = rules;
        return ResourceCapacityMatching.accepts(expected.stream().mapToLong(GenericStack::amount).toArray(), capacities,
                actual.stream().mapToLong(GenericStack::amount).toArray(),
                (e, r) -> domains.get(r).accepts(expected.get(e).what()),
                (r, a) -> domains.get(r).accepts(actual.get(a).what()));
    }

    private static BigInteger total(List<GenericStack> stacks) {
        BigInteger total = BigInteger.ZERO;
        for (GenericStack stack : stacks) total = total.add(BigInteger.valueOf(stack.amount()));
        return total;
    }

    private static KeyCounter amounts(List<ItemStack> stacks) {
        var amounts = new KeyCounter();
        for (ItemStack stack : stacks) if (!stack.isEmpty()) amounts.add(AEItemKey.of(stack), stack.getCount());
        return amounts;
    }

    private static List<GenericStack> stacks(KeyCounter counts) {
        var result = new ObjectArrayList<GenericStack>();
        for (var entry : counts) result.add(new GenericStack(entry.getKey(), entry.getLongValue()));
        return result;
    }

    private static List<Rule> rules(IPatternDetails pattern) {
        if (pattern instanceof PackagedBatchPattern batch) {
            return rules(batch.original()).stream().map(rule -> new Rule(
                    new GenericStack(rule.stack().what(), Math.multiplyExact(rule.stack().amount(), batch.count())), rule.matching()))
                    .collect(ObjectArrayList.toList());
        }
        var definition = pattern.getDefinition();
        var encoded = definition.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        var outputs = encoded == null ? pattern.getOutputs() : encoded.sparseOutputs();
        var rules = new ObjectArrayList<Rule>();
        for (int slot = 0; slot < outputs.size(); slot++) {
            var output = outputs.get(slot);
            if (output == null) continue;
            if (output.amount() <= 0) throw new IllegalArgumentException("Invalid packaged pattern output amount");
            var mode = EncodedPatternMatching.outputMode(definition, slot);
            rules.add(new Rule(output, new ItemMatchingRule(mode,
                    mode == ProcessingMatchMode.TAG ? EncodedPatternMatching.outputTags(definition, slot) : ObjectList.of())));
        }
        return rules;
    }

    private static List<Rule> rules(PackagedMachineOperation operation) {
        var result = new ObjectArrayList<Rule>();
        var encoded = operation.progress().getList(RULES, Tag.TAG_COMPOUND);
        for (int i = 0; i < encoded.size(); i++) {
            var tag = encoded.getCompound(i);
            var stack = GenericStack.readTag(operation.level().registryAccess(), tag);
            if (stack == null || stack.amount() <= 0)
                throw new IllegalArgumentException("Invalid packaged output rule");
            result.add(new Rule(stack, ItemMatchingRule.load(tag)));
        }
        return result;
    }

    private record Rule(GenericStack stack, ItemMatchingRule matching) {

        boolean accepts(AEKey actual) {
            return !(stack.what() instanceof AEItemKey) ? stack.what().equals(actual) : matching.matches(stack.what(), actual);
        }
    }
}
