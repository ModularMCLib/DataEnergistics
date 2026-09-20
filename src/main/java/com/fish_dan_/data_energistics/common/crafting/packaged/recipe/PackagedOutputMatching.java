package com.fish_dan_.data_energistics.common.crafting.packaged.recipe;

import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import com.fish_dan_.data_energistics.common.crafting.dynamic.EncodedPatternDynamicOutput;

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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

/** Pattern-authorized output comparison shared by admission, native recipe checks and physical collection. */
public final class PackagedOutputMatching {

    private static final String RULES = "pattern_outputs";

    private PackagedOutputMatching() {}

    /** Freezes the accepted pattern's sparse-slot rules; later pattern edits cannot change an owned operation. */
    public static void save(IPatternDetails pattern, CompoundTag progress, HolderLookup.Provider registries) {
        var encoded = new ListTag();
        for (var rule : rules(pattern)) {
            var tag = GenericStack.writeTag(registries, rule.stack());
            tag.putBoolean("same_item", rule.sameItem());
            encoded.add(tag);
        }
        progress.put(RULES, encoded);
    }

    /** Reserves exact declarations before assigning remaining variants to marked output slots. */
    public static boolean matches(IPatternDetails pattern, List<ItemStack> actual) {
        return matches(pattern, amounts(actual));
    }

    /** Keeps batch quantities as longs instead of converting them to an ItemStack count. */
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
        var exact = new Object2LongLinkedOpenHashMap<AEKey>();
        var flexible = new Object2LongLinkedOpenHashMap<Item>();
        for (var rule : rules(pattern)) {
            var key = rule.stack().what();
            if (rule.sameItem() && key instanceof AEItemKey item) flexible.mergeLong(item.getItem(), rule.stack().amount(), Math::addExact);
            else exact.mergeLong(key, rule.stack().amount(), Math::addExact);
        }
        return matches(exact, flexible, actual);
    }

    /** Counts remain exact even when the accepted output slot ignores components. */
    public static boolean matches(PackagedMachineOperation operation, ItemStack expected, ItemStack actual) {
        return expected.getCount() == actual.getCount() && sameKey(operation, expected, actual);
    }

    /** For split item entities; callers must check the aggregate count before collecting. Legacy jobs stay exact. */
    public static boolean sameKey(PackagedMachineOperation operation, ItemStack expected, ItemStack actual) {
        if (ItemStack.isSameItemSameComponents(expected, actual)) return true;
        return !expected.isEmpty() && !actual.isEmpty() && expected.is(actual.getItem()) &&
                allowsSameItem(operation, AEItemKey.of(expected));
    }

    /** Compares complete cycle results, including already recovered containers, without rewriting physical keys. */
    public static boolean matches(PackagedMachineOperation operation, List<ItemStack> expected, List<ItemStack> actual) {
        return matches(operation, amounts(expected), amounts(actual), true);
    }

    /** Allows an incomplete arrival only when every received item fits the cycle's output quantities and rules. */
    public static boolean acceptsPartial(PackagedMachineOperation operation, List<ItemStack> expected, List<ItemStack> actual) {
        return matches(operation, amounts(expected), amounts(actual), false);
    }

    /** Matches ordered output ports; quantities, fluid keys and chemical keys remain exact. */
    public static boolean matchesResources(PackagedMachineOperation operation, List<GenericStack> expected,
                                           List<GenericStack> actual) {
        if (expected.size() != actual.size()) return false;
        for (int index = 0; index < expected.size(); index++) {
            if (!matches(operation, expected.get(index), actual.get(index))) return false;
        }
        return true;
    }

    private static boolean matches(PackagedMachineOperation operation, KeyCounter expected, KeyCounter actual,
                                   boolean complete) {
        var exact = new Object2LongLinkedOpenHashMap<AEKey>();
        var flexible = new Object2LongLinkedOpenHashMap<Item>();
        for (var entry : expected) {
            var key = entry.getKey();
            if (key instanceof AEItemKey item && allowsSameItem(operation, item)) {
                flexible.mergeLong(item.getItem(), entry.getLongValue(), Math::addExact);
            } else {
                exact.mergeLong(key, entry.getLongValue(), Math::addExact);
            }
        }
        return matches(exact, flexible, actual, complete);
    }

    /** Non-item resources retain their exact key contract. */
    public static boolean matches(PackagedMachineOperation operation, GenericStack expected, GenericStack actual) {
        if (expected.amount() != actual.amount()) return false;
        if (expected.what().equals(actual.what())) return true;
        return expected.what() instanceof AEItemKey expectedItem && actual.what() instanceof AEItemKey actualItem &&
                expectedItem.getItem() == actualItem.getItem() && allowsSameItem(operation, expectedItem);
    }

    private static boolean allowsSameItem(PackagedMachineOperation operation, AEItemKey expected) {
        var encoded = operation.progress().getList(RULES, Tag.TAG_COMPOUND);
        boolean marked = false;
        for (int index = 0; index < encoded.size(); index++) {
            var tag = encoded.getCompound(index);
            var stack = GenericStack.readTag(operation.level().registryAccess(), tag);
            if (stack == null || stack.amount() <= 0) throw new IllegalArgumentException("Invalid packaged output rule");
            if (!(stack.what() instanceof AEItemKey key)) continue;
            // An exact declaration for this prototype takes precedence over a same-item declaration.
            if (!tag.getBoolean("same_item") && key.equals(expected)) return false;
            if (tag.getBoolean("same_item") && key.getItem() == expected.getItem()) marked = true;
        }
        return marked;
    }

    private static boolean matches(Object2LongLinkedOpenHashMap<AEKey> exact,
                                   Object2LongLinkedOpenHashMap<Item> flexible, KeyCounter actual) {
        return matches(exact, flexible, actual, true);
    }

    private static boolean matches(Object2LongLinkedOpenHashMap<AEKey> exact,
                                   Object2LongLinkedOpenHashMap<Item> flexible, KeyCounter actual, boolean complete) {
        var remaining = new Object2LongLinkedOpenHashMap<AEKey>();
        for (var entry : actual) {
            if (entry.getLongValue() <= 0) return false;
            remaining.mergeLong(entry.getKey(), entry.getLongValue(), Math::addExact);
        }
        for (var entry : exact.object2LongEntrySet()) {
            long available = remaining.getLong(entry.getKey());
            if (complete && available < entry.getLongValue()) return false;
            remaining.put(entry.getKey(), available - Math.min(available, entry.getLongValue()));
        }
        var remainingItems = new Object2LongLinkedOpenHashMap<Item>();
        for (var entry : remaining.object2LongEntrySet()) {
            if (entry.getLongValue() == 0) continue;
            if (!(entry.getKey() instanceof AEItemKey item)) return false;
            remainingItems.mergeLong(item.getItem(), entry.getLongValue(), Math::addExact);
        }
        if (complete) return remainingItems.equals(flexible);
        for (var entry : remainingItems.object2LongEntrySet()) {
            if (entry.getLongValue() > flexible.getLong(entry.getKey())) return false;
        }
        return true;
    }

    private static KeyCounter amounts(List<ItemStack> stacks) {
        var amounts = new KeyCounter();
        for (var stack : stacks) {
            if (!stack.isEmpty()) amounts.add(AEItemKey.of(stack), stack.getCount());
        }
        return amounts;
    }

    private static List<Rule> rules(IPatternDetails pattern) {
        var encoded = pattern.getDefinition().get(AEComponents.ENCODED_PROCESSING_PATTERN);
        var outputs = encoded == null ? pattern.getOutputs() : encoded.sparseOutputs();
        var rules = new ObjectArrayList<Rule>();
        for (int slot = 0; slot < outputs.size(); slot++) {
            var output = outputs.get(slot);
            if (output == null) continue;
            if (output.amount() <= 0) throw new IllegalArgumentException("Invalid packaged pattern output amount");
            rules.add(new Rule(output, EncodedPatternDynamicOutput.isMarked(pattern.getDefinition(), -1, slot)));
        }
        return rules;
    }

    private record Rule(GenericStack stack, boolean sameItem) {}
}
