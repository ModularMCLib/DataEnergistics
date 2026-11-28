package com.fish_dan_.data_energistics.integration.magic.arsnouveau.packaged;

import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedIngredientAssignment;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedPatternEncoding;

import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import com.hollingsworth.arsnouveau.common.crafting.recipes.ApparatusRecipeInput;
import com.hollingsworth.arsnouveau.common.crafting.recipes.EnchantingApparatusRecipe;
import com.hollingsworth.arsnouveau.common.crafting.recipes.ImbuementRecipe;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

/**
 * Records pedestal returns when the pattern uses ordinary material accounting.
 */
final class ArsPatternEncoding {

    private ArsPatternEncoding() {}

    static @Nullable ItemStack imbuement(ImbuementRecipe recipe, ItemStack pattern) {
        var processing = pattern.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (processing == null) return null;
        var ingredients = new ObjectArrayList<Ingredient>();
        ingredients.add(recipe.getInput());
        ingredients.addAll(recipe.getPedestalItems());
        var assigned = PackagedIngredientAssignment.match(ingredients, PackagedPatternEncoding.suppliedInputs(processing));
        if (assigned == null) return null;
        ItemStack result = recipe.getOutput();
        if (result.isEmpty()) return null;
        var outputs = processing.sparseOutputs().stream().filter(stack -> stack != null).toList();
        if (outputs.isEmpty() || !outputs.getFirst().what().equals(AEItemKey.of(result)) ||
                outputs.getFirst().amount() < result.getCount())
            return null;
        var pedestals = assigned.subList(1, assigned.size());
        // A condensed AE slot shared by the center and a pedestal needs an ordinary, counted return.
        if (outputs.size() == 1 && outputs.getFirst().amount() == result.getCount() &&
                pedestals.stream().noneMatch(recipe.getInput()::test))
            return pattern;
        return PackagedPatternEncoding.withReturns(pattern, processing, result.getCount(),
                PackagedPatternEncoding.returnedItems(pedestals, 1));
    }

    static @Nullable ItemStack apparatus(ServerLevel level, EnchantingApparatusRecipe recipe, ItemStack pattern) {
        var processing = pattern.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (processing == null) return null;
        var supplied = PackagedPatternEncoding.suppliedInputs(processing);
        int cycleSize = recipe.pedestalItems().size() + 1;
        var units = ArsInputAssignment.units(supplied, cycleSize);
        if (units == null) return pattern;
        for (int index = 0; index < units.size(); index++) {
            var pedestals = new ObjectArrayList<>(units);
            var input = new ApparatusRecipeInput(pedestals.remove(index), pedestals, null);
            if (!recipe.matches(input, level)) continue;
            var product = recipe.assemble(input, level.registryAccess());
            if (product.isEmpty()) continue;
            long cycles = ArsInputAssignment.cycles(supplied, cycleSize);
            var returns = pedestals.stream().map(ArsMachineKind.APPARATUS::remainder).toList();
            return PackagedPatternEncoding.withReturns(pattern, processing, Math.multiplyExact(product.getCount(), cycles),
                    PackagedPatternEncoding.returnedItems(returns, cycles));
        }
        return pattern;
    }
}
