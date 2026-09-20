package com.fish_dan_.data_energistics.integration.crafting.packaged.magic.mysticalagriculture;

import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedIngredientAssignment;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedOutputMatching;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import com.blakebr0.mysticalagriculture.api.crafting.IAwakeningRecipe;
import com.blakebr0.mysticalagriculture.api.crafting.IInfusionRecipe;
import com.blakebr0.mysticalagriculture.init.ModRecipeTypes;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

/** One exact physical cycle and the number of identical cycles owned by the accepted envelope. */
record AltarRecipePlan(NonNullList<ItemStack> inputs, NonNullList<ItemStack> remaining,
                       ItemStack result, long cycles) {

    static @Nullable AltarRecipePlan prepare(ServerLevel level, Recipe<CraftingInput> recipe,
                                             IPatternDetails pattern, KeyCounter[] supplied) {
        ObjectList<Ingredient> ingredients = new ObjectArrayList<>();
        NonNullList<ItemStack> essences = NonNullList.create();
        if (recipe instanceof IInfusionRecipe infusion) {
            ingredients.add(infusion.getAltarIngredient());
            ingredients.addAll(infusion.getIngredients());
            if (ingredients.size() < 2 || ingredients.size() > 9) return null;
        } else if (recipe instanceof IAwakeningRecipe awakening) {
            if (awakening.getIngredients().size() != 8 || awakening.getEssences().size() != 4) return null;
            ingredients.add(awakening.getAltarIngredient());
            // MA exposes alternating essence/pedestal ingredients; its machine inventory groups the four vessels last.
            for (int index = 1; index < 8; index += 2) ingredients.add(awakening.getIngredients().get(index));
            for (ItemStack essence : awakening.getEssences()) {
                if (essence.isEmpty() || essence.getCount() > 40) return null;
                essences.add(essence.copy());
            }
        } else return null;
        if (ingredients.stream().anyMatch(Ingredient::isEmpty)) return null;

        long perCycle = ingredients.size();
        for (ItemStack essence : essences) perCycle += essence.getCount();
        var totals = new Object2LongOpenHashMap<AEItemKey>();
        long total = 0;
        for (KeyCounter counter : supplied) {
            for (var entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey key) || entry.getLongValue() <= 0) return null;
                long amount = entry.getLongValue();
                if (amount > Long.MAX_VALUE - total || amount > Long.MAX_VALUE - totals.getLong(key)) return null;
                totals.addTo(key, amount);
                total += amount;
            }
        }
        if (total == 0 || total % perCycle != 0) return null;
        long cycles = total / perCycle;
        var perCycleInputs = new KeyCounter();
        for (var entry : totals.object2LongEntrySet()) {
            if (entry.getLongValue() % cycles != 0) return null;
            perCycleInputs.add(entry.getKey(), entry.getLongValue() / cycles);
        }
        for (ItemStack essence : essences) {
            AEItemKey key = AEItemKey.of(essence);
            if (perCycleInputs.get(key) < essence.getCount()) return null;
            perCycleInputs.remove(key, essence.getCount());
        }
        perCycleInputs.removeZeros();
        ObjectList<ItemStack> assigned = PackagedIngredientAssignment.match(ingredients, new KeyCounter[] { perCycleInputs });
        if (assigned == null) return null;
        NonNullList<ItemStack> stacks = NonNullList.withSize(9, ItemStack.EMPTY);
        for (int index = 0; index < assigned.size(); index++) stacks.set(index, assigned.get(index));
        for (int index = 0; index < essences.size(); index++) stacks.set(index + 5, essences.get(index));
        CraftingInput input = CraftingInput.of(3, 3, stacks);
        if (!recipe.matches(input, level)) return null;
        if (recipe instanceof IAwakeningRecipe awakening && !awakening.hasRequiredEssences(essences)) return null;
        ItemStack result = recipe.assemble(input, level.registryAccess());
        if (result.isEmpty()) return null;
        NonNullList<ItemStack> recipeRemaining = recipe.getRemainingItems(input);
        if (recipeRemaining.size() != input.size()) return null;
        NonNullList<ItemStack> remaining = NonNullList.withSize(9, ItemStack.EMPTY);
        for (int index = 0; index < recipeRemaining.size(); index++) remaining.set(index, recipeRemaining.get(index));
        // The physical tile chooses its own recipe. Reject ambiguous matches before accepting materials.
        var chosen = recipe instanceof IInfusionRecipe ?
                level.getRecipeManager().getRecipeFor(ModRecipeTypes.INFUSION.get(), input, level) :
                level.getRecipeManager().getRecipeFor(ModRecipeTypes.AWAKENING.get(), input, level);
        if (chosen.isEmpty() || chosen.get().value() != recipe) return null;
        if (!outputsMatch(pattern, result, remaining, cycles)) return null;
        return new AltarRecipePlan(stacks, remaining, result, cycles);
    }

    private static boolean outputsMatch(IPatternDetails pattern, ItemStack result,
                                        NonNullList<ItemStack> remaining, long cycles) {
        var actual = new Object2LongOpenHashMap<AEItemKey>();
        var outputs = new ObjectArrayList<>(remaining);
        outputs.add(result);
        for (ItemStack stack : outputs) {
            if (stack.isEmpty()) continue;
            if (cycles > Long.MAX_VALUE / stack.getCount()) return false;
            long amount = cycles * stack.getCount();
            AEItemKey key = AEItemKey.of(stack);
            if (amount > Long.MAX_VALUE - actual.getLong(key)) return false;
            actual.addTo(key, amount);
        }
        return PackagedOutputMatching.matches(pattern, actual);
    }
}
