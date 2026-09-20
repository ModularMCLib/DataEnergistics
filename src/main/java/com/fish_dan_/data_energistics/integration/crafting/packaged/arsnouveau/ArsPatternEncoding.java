package com.fish_dan_.data_energistics.integration.crafting.packaged.arsnouveau;

import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedIngredientAssignment;

import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.EncodedProcessingPattern;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import com.hollingsworth.arsnouveau.common.crafting.recipes.ImbuementRecipe;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

/**
 * Encodes pedestal catalysts as borrowed inputs and explicit returns, keeping the primary output first.
 */
final class ArsPatternEncoding {

    private ArsPatternEncoding() {}

    static @Nullable ItemStack imbuement(ImbuementRecipe recipe, ItemStack pattern) {
        var processing = pattern.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (processing == null) return null;
        var supplied = new KeyCounter();
        for (var input : processing.sparseInputs()) {
            if (input == null) continue;
            if (!(input.what() instanceof AEItemKey) || input.amount() <= 0) return null;
            supplied.add(input.what(), input.amount());
        }
        var ingredients = new ObjectArrayList<Ingredient>();
        ingredients.add(recipe.getInput());
        ingredients.addAll(recipe.getPedestalItems());
        var assigned = PackagedIngredientAssignment.match(ingredients, new KeyCounter[] { supplied });
        if (assigned == null) return null;
        var outputs = new ObjectArrayList<>(processing.sparseOutputs());
        var missing = new KeyCounter();
        ItemStack result = recipe.getOutput();
        if (result.isEmpty()) return null;
        missing.add(AEItemKey.of(result), result.getCount());
        for (int index = 1; index < assigned.size(); index++) {
            ItemStack catalyst = assigned.get(index);
            missing.add(AEItemKey.of(catalyst), catalyst.getCount());
        }
        for (var output : outputs) {
            if (output == null) continue;
            if (output.amount() <= 0 || missing.get(output.what()) < output.amount()) return null;
            missing.add(output.what(), -output.amount());
        }
        int tail = outputs.size();
        while (tail > 0 && outputs.get(tail - 1) == null) tail--;
        for (var entry : missing) {
            if (entry.getLongValue() == 0) continue;
            if (tail >= 27) return null;
            var output = new GenericStack(entry.getKey(), entry.getLongValue());
            if (tail < outputs.size()) outputs.set(tail, output);
            else outputs.add(output);
            tail++;
        }
        ItemStack completed = pattern.copy();
        completed.set(AEComponents.ENCODED_PROCESSING_PATTERN, new EncodedProcessingPattern(processing.sparseInputs(), outputs));
        return completed;
    }
}
