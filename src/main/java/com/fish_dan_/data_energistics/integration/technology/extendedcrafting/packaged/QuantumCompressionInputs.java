package com.fish_dan_.data_energistics.integration.technology.extendedcrafting.packaged;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.CraftingInput;

import com.blakebr0.extendedcrafting.api.crafting.ICompressorRecipe;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** The exact material and one retained catalyst accepted by a single native compression. */
@NullMarked
record QuantumCompressionInputs(AEItemKey material, AEItemKey catalyst, int required) {

    static @Nullable Completion completeEncoding(ServerLevel level, ICompressorRecipe recipe,
                                                 List<GenericStack> sparseInputs) {
        int required = recipe.getCount(0);
        if (required <= 0) return null;
        var completedInputs = new ObjectArrayList<@Nullable GenericStack>(sparseInputs);
        GenericStack first = null;
        GenericStack second = null;
        int firstSlot = -1;
        int secondSlot = -1;
        for (int slot = 0; slot < completedInputs.size(); slot++) {
            var input = completedInputs.get(slot);
            if (input == null) continue;
            if (first == null) {
                first = input;
                firstSlot = slot;
            } else if (second == null) {
                second = input;
                secondSlot = slot;
            } else {
                var supplied = new KeyCounter();
                for (var stack : completedInputs) {
                    if (stack == null) continue;
                    if (stack.amount() <= 0 || stack.amount() > (long) required + 1) return null;
                    supplied.add(stack.what(), stack.amount());
                }
                var plan = match(level, recipe, new KeyCounter[] { supplied });
                return plan == null ? null : new Completion(plan, completedInputs);
            }
        }
        if (first == null || !(first.what() instanceof AEItemKey firstKey)) return null;
        if (second == null) {
            var combined = new QuantumCompressionInputs(firstKey, firstKey, required);
            if (first.amount() == (long) required + 1 && recipe.matches(combined.craftingInput(), level))
                return new Completion(combined, completedInputs);
            if (!isMaterialAmount(first, required)) return null;
            for (var example : recipe.getCatalyst().getItems()) {
                if (example.isEmpty()) continue;
                AEItemKey catalyst = AEItemKey.of(example);
                if (catalyst == null) continue;
                var plan = new QuantumCompressionInputs(firstKey, catalyst, required);
                if (!recipe.matches(plan.craftingInput(), level)) continue;
                if (firstKey.equals(catalyst)) {
                    completedInputs.set(firstSlot, new GenericStack(firstKey, (long) required + 1));
                } else {
                    completedInputs.set(firstSlot, new GenericStack(firstKey, required));
                    if (!addCatalyst(completedInputs, catalyst)) return null;
                }
                return new Completion(plan, completedInputs);
            }
            return null;
        }
        if (!(second.what() instanceof AEItemKey secondKey)) return null;

        // JEI can show a one-item example for a counted ingredient. Preserve both selected keys and their slots.
        var selected = new QuantumCompressionInputs(firstKey, secondKey, required);
        if (isMaterialAmount(first, required) && second.amount() == 1 && recipe.matches(selected.craftingInput(), level)) {
            completedInputs.set(firstSlot, new GenericStack(firstKey, required));
            return new Completion(selected, completedInputs);
        }
        var reversed = selected.reversed();
        if (isMaterialAmount(second, required) && first.amount() == 1 && recipe.matches(reversed.craftingInput(), level)) {
            completedInputs.set(secondSlot, new GenericStack(secondKey, required));
            return new Completion(reversed, completedInputs);
        }
        return null;
    }

    private static boolean addCatalyst(ObjectList<@Nullable GenericStack> inputs, AEItemKey catalyst) {
        int slot = inputs.indexOf(null);
        if (slot >= 0) {
            inputs.set(slot, new GenericStack(catalyst, 1));
            return true;
        }
        if (inputs.size() >= 81) return false;
        inputs.add(new GenericStack(catalyst, 1));
        return true;
    }

    private static boolean isMaterialAmount(GenericStack input, int required) {
        return input.amount() == required || input.amount() == 1;
    }

    static @Nullable QuantumCompressionInputs match(ServerLevel level, ICompressorRecipe recipe, KeyCounter[] inputs) {
        int required = recipe.getCount(0);
        if (required <= 0) return null;
        long expected = (long) required + 1;
        long total = 0;
        var counts = new Object2LongLinkedOpenHashMap<AEItemKey>(2);
        for (var counter : inputs) {
            for (var entry : counter) {
                long amount = entry.getLongValue();
                if (!(entry.getKey() instanceof AEItemKey item) || amount <= 0 || amount > expected - total) return null;
                counts.addTo(item, amount);
                total += amount;
            }
        }
        if (total != expected || counts.size() > 2) return null;
        var keys = counts.keySet().iterator();
        AEItemKey first = keys.next();
        if (!keys.hasNext()) {
            return recipe.matches(craftingInput(first, first), level) ? new QuantumCompressionInputs(first, first, required) : null;
        }
        AEItemKey second = keys.next();
        if (counts.getLong(second) == 1 && recipe.matches(craftingInput(first, second), level)) {
            return new QuantumCompressionInputs(first, second, required);
        }
        if (counts.getLong(first) == 1 && recipe.matches(craftingInput(second, first), level)) {
            return new QuantumCompressionInputs(second, first, required);
        }
        return null;
    }

    boolean hasSeparateCatalyst(ServerLevel level, ICompressorRecipe recipe) {
        // AE condenses identical keys, and a one-unit recipe can accept both keys in either role.
        return !material.equals(catalyst) && (required != 1 || !recipe.matches(craftingInput(catalyst, material), level));
    }

    QuantumCompressionInputs reversed() {
        return new QuantumCompressionInputs(catalyst, material, required);
    }

    CraftingInput craftingInput() {
        return craftingInput(material, catalyst);
    }

    private static CraftingInput craftingInput(AEItemKey material, AEItemKey catalyst) {
        return CraftingInput.of(2, 1, List.of(material.toStack(), catalyst.toStack()));
    }

    record Completion(QuantumCompressionInputs plan, ObjectList<@Nullable GenericStack> sparseInputs) {}
}
