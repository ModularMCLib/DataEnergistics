package com.fish_dan_.data_energistics.integration.crafting.packaged.draconicevolution;

import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import com.fish_dan_.data_energistics.common.crafting.dynamic.EncodedPatternDynamicOutput;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.draconicevolution.api.crafting.IFusionRecipe;
import com.brandon3055.draconicevolution.api.crafting.StackIngredient;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;

/** Exact repeated-cycle assignment, including native retained and crafting-remainder behavior. */
final class FusionRecipePlan {

    private static final int ASSIGNMENT_BUDGET = 4096;

    private FusionRecipePlan() {}

    static @Nullable Plan prepare(ServerLevel level, IFusionRecipe recipe, TechLevel minimumTier,
                                  IPatternDetails pattern, KeyCounter[] supplied) {
        var available = new Object2LongLinkedOpenHashMap<AEItemKey>();
        long total = 0;
        for (KeyCounter counter : supplied) {
            for (var entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey key) || entry.getLongValue() <= 0) return null;
                try {
                    total = Math.addExact(total, entry.getLongValue());
                    available.addTo(key, entry.getLongValue());
                } catch (ArithmeticException exception) {
                    return null;
                }
            }
        }
        int catalystCount = ingredientCount(recipe.getCatalyst());
        if (catalystCount <= 0) return null;
        long perCycle = catalystCount;
        long retainedTotal = 0;
        for (var ingredient : recipe.fusionIngredients()) {
            int count = ingredientCount(ingredient.get());
            // DE's standard completion path consumes one item from each injector, irrespective of custom counts.
            try {
                if (ingredient.consume()) perCycle = Math.addExact(perCycle, count);
                else retainedTotal = Math.addExact(retainedTotal, count);
            } catch (ArithmeticException exception) {
                return null;
            }
        }
        if (total <= retainedTotal || perCycle <= 0 || (total - retainedTotal) % perCycle != 0) return null;
        long cycles = (total - retainedTotal) / perCycle;
        if (cycles <= 0) return null;

        var groups = new ObjectArrayList<Group>();
        groups.add(new Group(recipe.getCatalyst(), catalystCount, true, true));
        for (var ingredient : recipe.fusionIngredients()) {
            groups.add(new Group(ingredient.get(), ingredientCount(ingredient.get()), ingredient.consume(), false));
        }
        var keys = new ObjectArrayList<>(available.keySet());
        long[] amounts = new long[keys.size()];
        for (int index = 0; index < amounts.length; index++) amounts[index] = available.getLong(keys.get(index));
        var assigned = new ObjectArrayList<ItemStack>();
        int[] budget = { ASSIGNMENT_BUDGET };
        if (!assign(groups, keys, amounts, cycles, assigned, budget)) return null;

        ItemStack catalyst = assigned.getFirst();
        var injectors = new ObjectArrayList<PlannedIngredient>();
        var actualOutputs = new Object2LongLinkedOpenHashMap<AEItemKey>();
        for (int index = 1; index < assigned.size(); index++) {
            ItemStack input = assigned.get(index);
            boolean consumed = groups.get(index).consumed();
            ItemStack remaining = remaining(input, consumed, groups.get(index).count());
            injectors.add(new PlannedIngredient(input, remaining, !consumed, groups.get(index).count()));
            if (!remaining.isEmpty()) {
                long multiplier = consumed ?
                        Math.multiplyExact(cycles, groups.get(index).count()) : 1;
                actualOutputs.addTo(AEItemKey.of(remaining), Math.multiplyExact(remaining.getCount(), multiplier));
            }
        }
        var inventory = FusionSnapshot.inventory(catalyst, injectors, minimumTier);
        if (!recipe.matches(inventory, level) || !recipe.canStartCraft(inventory, level, null)) return null;
        ItemStack result = recipe.assemble(inventory, level.registryAccess());
        if (result.isEmpty()) return null;
        actualOutputs.addTo(AEItemKey.of(result), Math.multiplyExact(result.getCount(), cycles));
        if (!outputsMatch(pattern, actualOutputs)) return null;
        return new Plan(catalyst, injectors, result, cycles, recipe.getEnergyCost(), recipe.getRecipeTier().name());
    }

    static ItemStack deliveredCatalyst(Plan plan) {
        return multiplied(plan.catalyst(), plan.cycles());
    }

    static ItemStack deliveredInjector(Plan plan, PlannedIngredient ingredient) {
        return ingredient.retained() ? ingredient.input().copy() : multiplied(ingredient.input(), plan.cycles());
    }

    private static ItemStack multiplied(ItemStack stack, long multiplier) {
        long amount = Math.multiplyExact(stack.getCount(), multiplier);
        if (amount > stack.getMaxStackSize()) {
            throw new IllegalArgumentException("Draconic fusion input exceeds one physical slot");
        }
        ItemStack result = stack.copy();
        result.setCount(Math.toIntExact(amount));
        return result;
    }

    private static boolean assign(ObjectList<Group> groups, ObjectList<AEItemKey> keys, long[] available,
                                  long cycles, ObjectList<ItemStack> assigned, int[] budget) {
        if (assigned.size() == groups.size()) {
            for (long amount : available) if (amount != 0) return false;
            return true;
        }
        if (--budget[0] < 0) return false;
        Group group = groups.get(assigned.size());
        long multiplier = group.consumed() ? cycles : 1;
        long required;
        try {
            required = Math.multiplyExact(group.count(), multiplier);
        } catch (ArithmeticException exception) {
            return false;
        }
        for (int index = 0; index < keys.size(); index++) {
            if (available[index] < required) continue;
            // Keep the complete AE key, including custom components, when adapting its count
            // to a StackIngredient. Some AE key implementations do not preserve components
            // through the counted toStack overload.
            ItemStack candidate = keys.get(index).toStack();
            candidate.setCount(group.count());
            if (!matches(group.ingredient(), candidate)) continue;
            available[index] -= required;
            assigned.add(candidate);
            if (assign(groups, keys, available, cycles, assigned, budget)) return true;
            assigned.removeLast();
            available[index] += required;
        }
        return false;
    }

    private static boolean matches(Ingredient ingredient, ItemStack candidate) {
        if (ingredient.test(candidate)) return true;
        for (ItemStack example : ingredient.getItems()) {
            if (example.getItem() == candidate.getItem() && example.getComponents().isEmpty()) return true;
        }
        return false;
    }

    private static int ingredientCount(Ingredient ingredient) {
        return ingredient.getCustomIngredient() instanceof StackIngredient stackIngredient ?
                stackIngredient.getCount() : 1;
    }

    private static ItemStack remaining(ItemStack input, boolean consumed, int ingredientCount) {
        if (!consumed) return input.copy();
        if (ingredientCount <= 0 || input.getCount() < ingredientCount) {
            throw new IllegalArgumentException("Invalid Draconic fusion ingredient count");
        }
        if (input.hasCraftingRemainingItem()) {
            // DE's native completion path passes the complete injector stack to the
            // remainder hook, but only consumes one physical item. Keep the
            // single-item remainder here; the adapter multiplies it by the
            // StackIngredient count when it settles a native cycle.
            ItemStack unit = input.copy();
            unit.setCount(1);
            ItemStack remainder = input.getItem().getCraftingRemainingItem(unit);
            if (remainder.isEmpty()) return remainder;
            return remainder;
        }
        ItemStack remaining = input.copy();
        remaining.shrink(ingredientCount);
        return remaining;
    }

    private static boolean outputsMatch(IPatternDetails pattern,
                                        Object2LongLinkedOpenHashMap<AEItemKey> actual) {
        var actualItems = new Object2LongLinkedOpenHashMap<Item>();
        for (var entry : actual.object2LongEntrySet()) {
            actualItems.addTo(entry.getKey().getItem(), entry.getLongValue());
        }
        var declaredExact = new Object2LongLinkedOpenHashMap<AEItemKey>();
        var declaredItems = new Object2LongLinkedOpenHashMap<Item>();
        int outputIndex = 0;
        for (var output : pattern.getOutputs()) {
            if (!(output.what() instanceof AEItemKey key) || output.amount() <= 0) return false;
            if (EncodedPatternDynamicOutput.isMarked(pattern.getDefinition(), -1, outputIndex++)) {
                declaredItems.addTo(key.getItem(), output.amount());
            } else {
                declaredExact.addTo(key, output.amount());
            }
        }
        for (var entry : declaredExact.object2LongEntrySet()) {
            if (actual.getLong(entry.getKey()) != entry.getLongValue()) return false;
        }
        for (var entry : declaredItems.object2LongEntrySet()) {
            if (actualItems.getLong(entry.getKey()) != entry.getLongValue()) return false;
        }
        long declaredTotal = declaredExact.values().longStream().sum() + declaredItems.values().longStream().sum();
        long actualTotal = actual.values().longStream().sum();
        return declaredTotal == actualTotal;
    }

    static CompoundTag save(Plan plan, HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.put("catalyst", plan.catalyst().save(registries));
        tag.put("result", plan.result().save(registries));
        tag.putLong("cycles", plan.cycles());
        tag.putLong("energy", plan.energy());
        tag.putString("tier", plan.tier());
        var injectors = new ListTag();
        for (PlannedIngredient ingredient : plan.injectors()) {
            var encoded = new CompoundTag();
            encoded.put("input", ingredient.input().save(registries));
            encoded.put("remaining", ingredient.remaining().saveOptional(registries));
            encoded.putBoolean("retained", ingredient.retained());
            encoded.putInt("count", ingredient.count());
            injectors.add(encoded);
        }
        tag.put("injectors", injectors);
        return tag;
    }

    static Plan load(PackagedMachineOperation operation) {
        CompoundTag tag = operation.progress();
        ItemStack catalyst = read(operation, tag, "catalyst", false);
        ItemStack result = read(operation, tag, "result", false);
        long cycles = tag.getLong("cycles");
        long energy = tag.getLong("energy");
        String tier = tag.getString("tier");
        ListTag encoded = tag.getList("injectors", Tag.TAG_COMPOUND);
        var injectors = new ObjectArrayList<PlannedIngredient>();
        for (int index = 0; index < encoded.size(); index++) {
            CompoundTag ingredient = encoded.getCompound(index);
            injectors.add(new PlannedIngredient(
                    read(operation, ingredient, "input", false),
                    read(operation, ingredient, "remaining", true),
                    ingredient.getBoolean("retained"),
                    Math.max(1, ingredient.getInt("count"))));
        }
        if (cycles <= 0 || energy < 0 || tier.isEmpty() || injectors.isEmpty()) {
            throw new IllegalArgumentException("Invalid persisted Draconic fusion plan");
        }
        return new Plan(catalyst, injectors, result, cycles, energy, tier);
    }

    private static ItemStack read(PackagedMachineOperation operation, CompoundTag tag, String key,
                                  boolean optional) {
        return (optional ? ItemStack.parseOptional(operation.level().registryAccess(), tag.getCompound(key)) :
                ItemStack.parse(operation.level().registryAccess(), tag.getCompound(key))
                        .orElseThrow(() -> new IllegalArgumentException("Missing Draconic fusion " + key)));
    }

    static void requireAvailable(PackagedMachineOperation operation, ItemStack stack) {
        if (operation.available(AEItemKey.of(stack)).compareTo(BigInteger.valueOf(stack.getCount())) < 0) {
            throw new IllegalStateException("Draconic fusion plan exceeds remaining provider inputs");
        }
    }

    record Plan(ItemStack catalyst, ObjectList<PlannedIngredient> injectors,
                ItemStack result, long cycles, long energy, String tier) {}

    record PlannedIngredient(ItemStack input, ItemStack remaining, boolean retained, int count) {}

    private record Group(Ingredient ingredient, int count, boolean consumed, boolean catalyst) {}
}
