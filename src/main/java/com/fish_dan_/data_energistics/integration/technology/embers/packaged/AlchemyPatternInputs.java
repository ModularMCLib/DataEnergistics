package com.fish_dan_.data_energistics.integration.technology.embers.packaged;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Ingredient;

import com.rekindled.embers.recipe.IAlchemyRecipe;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

/** Completes transferred ghost inputs using the server world's actual alchemy code. */
public final class AlchemyPatternInputs {

    private AlchemyPatternInputs() {}

    public static @Nullable ObjectList<GenericStack> complete(ServerLevel level, ResourceLocation recipeId, ObjectList<GenericStack> original) {
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !(holder.get().value() instanceof IAlchemyRecipe recipe)) return null;
        var remaining = new KeyCounter();
        for (var stack : original) remaining.add(stack.what(), stack.amount());
        var inputs = new ObjectArrayList<GenericStack>();
        // Reserve real ingredients first: an aspect item may also be an ordinary recipe ingredient.
        if (!takeIngredient(recipe.getCenterInput(), original, remaining, inputs)) return null;
        for (var ingredient : recipe.getInputs()) {
            if (!takeIngredient(ingredient, original, remaining, inputs)) return null;
        }
        for (var entry : remaining) {
            if (entry.getLongValue() <= 0) continue;
            if (entry.getKey() instanceof AEItemKey item && recipe.getAspects().stream().anyMatch(aspect -> aspect.test(item.toStack()))) continue;
            add(inputs, new GenericStack(entry.getKey(), entry.getLongValue()));
        }
        for (var aspect : recipe.getCode(level.getSeed())) {
            var choices = aspect.getItems();
            if (choices.length == 0) return null;
            add(inputs, new GenericStack(AEItemKey.of(choices[0]), 1));
        }
        return new ObjectImmutableList<>(inputs);
    }

    private static boolean takeIngredient(Ingredient ingredient, ObjectList<GenericStack> original,
                                          KeyCounter remaining, ObjectList<GenericStack> inputs) {
        for (var stack : original) {
            if (stack.what() instanceof AEItemKey item && remaining.get(item) > 0 && ingredient.test(item.toStack())) {
                remaining.remove(item, 1);
                add(inputs, new GenericStack(item, 1));
                return true;
            }
        }
        return false;
    }

    private static void add(ObjectList<GenericStack> inputs, GenericStack stack) {
        for (int index = 0; index < inputs.size(); index++) {
            var existing = inputs.get(index);
            if (existing.what().equals(stack.what())) {
                inputs.set(index, new GenericStack(existing.what(), Math.addExact(existing.amount(), stack.amount())));
                return;
            }
        }
        inputs.add(stack);
    }
}
