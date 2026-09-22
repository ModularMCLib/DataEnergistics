package com.fish_dan_.data_energistics.integration.magic.botania.packaged;

import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.EncodedProcessingPattern;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;
import vazkii.botania.api.brew.BrewContainer;
import vazkii.botania.api.recipe.BotanicalBreweryRecipe;
import vazkii.botania.common.crafting.recipe.RecipeUtils;

import java.util.List;

/** Adds the real brew container and native returned items to a brewery processing pattern. */
final class BotanicalBreweryPatternEncoding {

    private BotanicalBreweryPatternEncoding() {}

    static @Nullable ItemStack complete(ServerLevel level, RecipeHolder<?> holder, ItemStack encodedPattern) {
        if (!(holder.value() instanceof BotanicalBreweryRecipe recipe)) return encodedPattern;
        var processing = encodedPattern.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (processing == null) return null;

        var selectedInputs = expand(processing.sparseInputs());
        var selectedOutputs = processing.sparseOutputs();
        if (selectedInputs == null || selectedOutputs.stream().filter(stack -> stack != null).count() != 1) return null;

        ItemStack container = findContainer(recipe, selectedInputs, selectedOutputs);
        if (container.isEmpty()) return null;
        selectedInputs.removeIf(stack -> stack.getItem() instanceof BrewContainer);

        var ingredients = fillIngredients(recipe, selectedInputs, level);
        if (ingredients == null) return null;
        var nativeInputs = new ObjectArrayList<ItemStack>();
        nativeInputs.add(container);
        nativeInputs.addAll(ingredients);
        var input = RecipeUtils.getInputFromListWithoutUnstacking(nativeInputs);
        if (!recipe.matches(input, level)) return null;

        var completedInputs = new ObjectArrayList<ItemStack>(nativeInputs);
        var completedOutputs = new ObjectArrayList<ItemStack>();
        completedOutputs.add(recipe.getOutput(container));
        for (var remaining : recipe.getRemainingItems(input)) if (!remaining.isEmpty()) completedOutputs.add(remaining.copy());
        if (completedOutputs.stream().anyMatch(ItemStack::isEmpty)) return null;

        var inputs = BotaniaPatternEncoding.appendMissing(processing.sparseInputs(), completedInputs, 81);
        var outputs = BotaniaPatternEncoding.appendMissing(selectedOutputs, completedOutputs, 27);
        if (inputs == null || outputs == null) return null;
        var copy = encodedPattern.copy();
        copy.set(AEComponents.ENCODED_PROCESSING_PATTERN, new EncodedProcessingPattern(inputs, outputs));
        return copy;
    }

    private static @Nullable ObjectList<ItemStack> fillIngredients(BotanicalBreweryRecipe recipe,
                                                                   List<ItemStack> selected,
                                                                   ServerLevel level) {
        var result = new ObjectArrayList<ItemStack>();
        var remaining = new ObjectArrayList<ItemStack>();
        selected.forEach(stack -> remaining.add(stack.copyWithCount(1)));
        if (!assign(recipe.getIngredients(), remaining, result, new int[] { 4096 })) return null;
        return result;
    }

    private static boolean assign(List<net.minecraft.world.item.crafting.Ingredient> ingredients,
                                  ObjectArrayList<ItemStack> remaining,
                                  ObjectArrayList<ItemStack> result, int[] budget) {
        if (--budget[0] < 0) return false;
        if (result.size() == ingredients.size()) return remaining.isEmpty();
        var ingredient = ingredients.get(result.size());
        for (int index = 0; index < remaining.size(); index++) {
            var candidate = remaining.get(index);
            if (!ingredient.test(candidate)) continue;
            remaining.remove(index);
            result.add(candidate);
            if (assign(ingredients, remaining, result, budget)) return true;
            result.removeLast();
            remaining.add(index, candidate);
        }
        for (var candidate : ingredient.getItems()) {
            if (candidate.isEmpty()) continue;
            result.add(candidate.copyWithCount(1));
            if (assign(ingredients, remaining, result, budget)) return true;
            result.removeLast();
        }
        return false;
    }

    private static @Nullable ItemStack findContainer(BotanicalBreweryRecipe recipe,
                                                     ObjectArrayList<ItemStack> selectedInputs,
                                                     List<@Nullable GenericStack> selectedOutputs) {
        ItemStack inputContainer = ItemStack.EMPTY;
        for (var stack : selectedInputs) {
            if (!(stack.getItem() instanceof BrewContainer)) continue;
            if (!inputContainer.isEmpty() && !ItemStack.isSameItemSameComponents(inputContainer, stack)) return ItemStack.EMPTY;
            inputContainer = stack.copyWithCount(1);
        }

        GenericStack declared = selectedOutputs.stream().filter(stack -> stack != null).findFirst().orElse(null);
        if (declared == null || !(declared.what() instanceof AEItemKey)) return ItemStack.EMPTY;
        if (!inputContainer.isEmpty()) {
            var output = recipe.getOutput(inputContainer);
            return output.getCount() == declared.amount() && AEItemKey.of(output).equals(declared.what()) ? inputContainer : ItemStack.EMPTY;
        }
        for (var candidate : RecipeUtils.getBrewContainerIngredient().getItems()) {
            var output = recipe.getOutput(candidate);
            if (!output.isEmpty() && output.getCount() == declared.amount() && AEItemKey.of(output).equals(declared.what()))
                return candidate.copyWithCount(1);
        }
        return ItemStack.EMPTY;
    }

    private static @Nullable ObjectArrayList<ItemStack> expand(List<@Nullable GenericStack> stacks) {
        var result = new ObjectArrayList<ItemStack>();
        long total = 0;
        for (var generic : stacks) {
            if (generic == null || !(generic.what() instanceof AEItemKey item) || generic.amount() <= 0 || generic.amount() > 64)
                continue;
            total += generic.amount();
            if (total > 64) return null;
            for (long index = 0; index < generic.amount(); index++) result.add(item.toStack());
        }
        return result;
    }
}
