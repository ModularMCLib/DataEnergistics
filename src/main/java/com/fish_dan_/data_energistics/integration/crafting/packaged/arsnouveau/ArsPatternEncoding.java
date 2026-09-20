package com.fish_dan_.data_energistics.integration.crafting.packaged.arsnouveau;

import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedIngredientAssignment;

import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import com.hollingsworth.arsnouveau.common.crafting.recipes.ImbuementRecipe;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

/**
 * Validates borrowed pedestal inputs without declaring their custody return as recipe production.
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
        ItemStack result = recipe.getOutput();
        if (result.isEmpty()) return null;
        boolean found = false;
        for (var output : processing.sparseOutputs()) {
            if (output == null) continue;
            if (found || output.amount() != result.getCount() || !output.what().equals(AEItemKey.of(result))) return null;
            found = true;
        }
        // Old patterns listing catalyst outputs must be encoded again; never move their matching-mode slots.
        return found ? pattern : null;
    }
}
