package com.fish_dan_.data_energistics.recipe.matching;

import net.minecraft.world.item.crafting.Ingredient;

/**
 * Describes one item ingredient and the number of matching items consumed by a recipe.
 *
 * <p>
 * Implementations own their validation and persistence format. This contract only supplies the common matching
 * view used by recipe machines; it does not define a physical slot or a reservation lifetime.
 * </p>
 */
public interface CountedItemRequirement {

    /**
     * Returns the ingredient predicate applied to an aggregated item stack.
     */
    Ingredient ingredient();

    /**
     * Returns the positive number of items consumed for this requirement.
     */
    int count();
}
