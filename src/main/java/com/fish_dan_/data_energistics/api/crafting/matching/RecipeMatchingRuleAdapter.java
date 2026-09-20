package com.fish_dan_.data_energistics.api.crafting.matching;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Ingredient;

import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Supplies complete native ingredient roles to global matching consumers, independently of a machine or provider.
 * This abstraction exists because activation materials and retained catalysts can be absent from the ordinary
 * recipe ingredient list. Instances are registered once and shared for the server lifetime: keep them stateless,
 * retain no level/recipe references, and resolve read-only on the server thread. It does not authorize substitution.
 */
@NullMarked
public interface RecipeMatchingRuleAdapter {

    /**
     * Stable non-null registration identity; duplicate IDs are rejected before a plugin transaction commits.
     */
    ResourceLocation id();

    /**
     * Resolves an exact non-null recipe ID without loading chunks or changing world state. Returns null when this
     * adapter does not handle the recipe or it is absent; otherwise returns an immutable list without null members.
     * Each position represents one native ingredient role, duplicates included, with all component constraints
     * intact. Consumers must not mutate its ingredients. Invalid recipe data should report its native exception,
     * not fabricate unconstrained roles. The registry falls back to Recipe.getIngredients when no adapter handles it.
     */
    @Nullable
    ObjectList<Ingredient> inputIngredients(ServerLevel level, ResourceLocation recipeId);

    /**
     * Declares native output roles whose identity is expressed by an Ingredient, such as returned catalysts.
     * Runs under the same server-thread, read-only contract as inputIngredients. Null means not handled;
     * an empty list means no declared output tags. Ordinary fixed recipe outputs do not imply tag rules.
     */
    default @Nullable ObjectList<Ingredient> outputIngredients(ServerLevel level, ResourceLocation recipeId) {
        return null;
    }
}
