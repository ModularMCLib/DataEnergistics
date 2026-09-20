package com.fish_dan_.data_energistics.integration.crafting.packaged.botania;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;
import vazkii.botania.api.recipe.PetalApothecaryRecipe;
import vazkii.botania.api.recipe.RunicAltarRecipe;
import vazkii.botania.common.crafting.recipe.RecipeUtils;

import java.util.List;

/** Pure expansion of an exact recipe's selected ingredients into the physical inputs and native returned items. */
public final class BotaniaPatternEncoding {

    private BotaniaPatternEncoding() {}

    /** Keeps the user's primary output prototype for its explicit matching mode, adding only missing returns. */
    static @Nullable ObjectList<@Nullable GenericStack> appendReturned(List<@Nullable GenericStack> original,
                                                                     List<ItemStack> completed, int limit) {
        var declared = new ObjectArrayList<>(completed);
        for (GenericStack output : original) {
            if (output == null) continue;
            if (!(output.what() instanceof AEItemKey item) || output.amount() != completed.getFirst().getCount()) return null;
            declared.set(0, item.toStack((int) output.amount()));
            break;
        }
        return appendMissing(original, declared, limit);
    }

    /** Preserves every existing sparse index and amount; only appends missing physical auxiliary resources. */
    static @Nullable ObjectList<@Nullable GenericStack> appendMissing(List<@Nullable GenericStack> original,
                                                                    List<ItemStack> completed, int limit) {
        var missing = new KeyCounter();
        for (ItemStack stack : completed) missing.add(AEItemKey.of(stack), stack.getCount());
        for (GenericStack stack : original) {
            if (stack == null) continue;
            if (stack.amount() <= 0 || missing.get(stack.what()) < stack.amount()) return null;
            missing.add(stack.what(), -stack.amount());
        }
        var result = new ObjectArrayList<@Nullable GenericStack>(original);
        int tail = result.size();
        while (tail > 0 && result.get(tail - 1) == null) tail--;
        for (var entry : missing) {
            if (entry.getLongValue() == 0) continue;
            if (tail >= limit) return null;
            var stack = new GenericStack(entry.getKey(), entry.getLongValue());
            if (tail < result.size()) result.set(tail, stack);
            else result.add(stack);
            tail++;
        }
        return result;
    }

    public static @Nullable EncodedRecipe augment(ServerLevel level, RecipeHolder<?> holder, List<ItemStack> recipeInputs) {
        return augment(level, holder, recipeInputs, null);
    }

    static @Nullable EncodedRecipe augment(ServerLevel level, RecipeHolder<?> holder, List<ItemStack> recipeInputs,
                                           @Nullable ItemStack preferredReagent) {
        var inputs = new ObjectArrayList<ItemStack>();
        for (ItemStack stack : recipeInputs) {
            if (stack.isEmpty() || stack.getCount() > 64 || inputs.size() + stack.getCount() > 64) return null;
            for (int i = 0; i < stack.getCount(); i++) inputs.add(stack.copyWithCount(1));
        }
        var outputs = new ObjectArrayList<ItemStack>();
        Ingredient reagent;
        if (holder.value() instanceof PetalApothecaryRecipe recipe) {
            var input = RecipeUtils.getInputFromListWithoutUnstacking(inputs);
            if (!recipe.matches(input, level)) return null;
            outputs.add(recipe.assemble(input, level.registryAccess()));
            reagent = recipe.getReagent();
            inputs.add(new ItemStack(Items.WATER_BUCKET));
            outputs.add(new ItemStack(Items.BUCKET));
        } else if (holder.value() instanceof RunicAltarRecipe recipe) {
            var input = RecipeUtils.getInputFromListWithoutUnstacking(inputs);
            if (!recipe.matches(input, level)) {
                if (inputs.size() != recipe.getIngredients().size()) return null;
                for (Ingredient catalyst : recipe.getCatalysts()) {
                    ItemStack candidate = candidate(catalyst);
                    if (candidate.isEmpty()) return null;
                    inputs.add(candidate);
                }
                input = RecipeUtils.getInputFromListWithoutUnstacking(inputs);
                if (!recipe.matches(input, level)) return null;
            }
            outputs.add(recipe.assemble(input, level.registryAccess()));
            for (var remaining : recipe.getRemainingItems(input)) if (!remaining.isEmpty()) outputs.add(remaining.copy());
            reagent = recipe.getReagent();
        } else return null;
        ItemStack selectedReagent = preferredReagent == null ? candidate(reagent) : preferredReagent.copyWithCount(1);
        if (!reagent.test(selectedReagent)) return null;
        if (selectedReagent.isEmpty() || outputs.stream().anyMatch(ItemStack::isEmpty)) return null;
        inputs.add(selectedReagent);
        return new EncodedRecipe(List.copyOf(inputs), List.copyOf(outputs));
    }

    private static ItemStack candidate(Ingredient ingredient) {
        for (ItemStack candidate : ingredient.getItems()) {
            if (!candidate.isEmpty() && ingredient.test(candidate)) return candidate.copyWithCount(1);
        }
        return ItemStack.EMPTY;
    }

    /** Lists belong to the returned value and contain copies; consumers must not mutate their component data. */
    public record EncodedRecipe(List<ItemStack> inputs, List<ItemStack> outputs) {}
}
