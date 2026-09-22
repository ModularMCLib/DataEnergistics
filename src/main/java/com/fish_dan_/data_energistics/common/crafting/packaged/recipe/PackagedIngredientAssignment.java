package com.fish_dan_.data_energistics.common.crafting.packaged.recipe;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

/** Exact assignment of one physical recipe, with bounded backtracking for overlapping ingredient alternatives. */
public final class PackagedIngredientAssignment {

    private PackagedIngredientAssignment() {}

    public static @Nullable ObjectList<ItemStack> match(ObjectList<Ingredient> ingredients, KeyCounter[] inputs) {
        var available = new Object2LongLinkedOpenHashMap<AEItemKey>();
        long total = 0;
        for (var input : inputs) {
            for (var entry : input) {
                if (!(entry.getKey() instanceof AEItemKey item) || entry.getLongValue() <= 0 ||
                        entry.getLongValue() > ingredients.size())
                    return null;
                available.addTo(item, entry.getLongValue());
                total += entry.getLongValue();
            }
        }
        if (total != ingredients.size()) return null;
        var keys = new ObjectArrayList<>(available.keySet());
        long[] counts = new long[keys.size()];
        for (int index = 0; index < counts.length; index++) counts[index] = available.getLong(keys.get(index));
        var slots = new ObjectArrayList<ItemStack>();
        int[] budget = { 4096 };
        return assign(ingredients, keys, counts, slots, budget) ? slots : null;
    }

    /** Matches one recipe after dividing a counted batch into identical per-craft ingredient sets. */
    public static @Nullable ObjectList<ItemStack> matchBatch(ObjectList<Ingredient> ingredients, KeyCounter[] inputs,
                                                             long batch) {
        if (batch <= 0) return null;
        var normalized = new KeyCounter[inputs.length];
        for (int index = 0; index < inputs.length; index++) {
            normalized[index] = new KeyCounter();
            for (var entry : inputs[index]) {
                long amount = entry.getLongValue();
                if (amount <= 0 || amount % batch != 0) return null;
                normalized[index].add(entry.getKey(), amount / batch);
            }
        }
        var matched = match(ingredients, normalized);
        if (matched == null) return null;
        for (var stack : matched) stack.setCount(Math.toIntExact(Math.multiplyExact(stack.getCount(), batch)));
        return matched;
    }

    private static boolean assign(ObjectList<Ingredient> ingredients, ObjectList<AEItemKey> keys, long[] counts,
                                  ObjectList<ItemStack> slots, int[] budget) {
        if (slots.size() == ingredients.size()) return true;
        if (--budget[0] < 0) return false;
        Ingredient ingredient = ingredients.get(slots.size());
        for (int index = 0; index < keys.size(); index++) {
            ItemStack candidate = keys.get(index).toStack();
            if (counts[index] == 0 || !matches(ingredient, candidate)) continue;
            counts[index]--;
            slots.add(candidate);
            if (assign(ingredients, keys, counts, slots, budget)) return true;
            slots.removeLast();
            counts[index]++;
        }
        return false;
    }

    private static boolean matches(Ingredient ingredient, ItemStack candidate) {
        if (ingredient.test(candidate)) return true;
        // A component-free ingredient explicitly ignores the candidate's components.
        // Keep component-constrained ingredients exact.
        for (ItemStack example : ingredient.getItems()) {
            if (example.getItem() == candidate.getItem() && example.getComponents().isEmpty()) return true;
        }
        return false;
    }

    /** Declared output may include machine-returned containers, but must exactly match the real result multiset. */
    public static boolean outputsMatch(IPatternDetails pattern, ObjectList<ItemStack> actual) {
        return PackagedOutputMatching.matches(pattern, actual);
    }
}
