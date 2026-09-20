package com.fish_dan_.data_energistics.api.registry.matching;

import com.fish_dan_.data_energistics.api.crafting.matching.RecipeMatchingRuleAdapter;

import org.jspecify.annotations.NullMarked;

/**
 * Transaction-local global recipe matching declarations, available only during plugin registration.
 */
@NullMarked
public interface RecipeMatchingRegistry {

    /**
     * Registers a non-null stateless adapter and its unique ID. Called during common-setup registration only;
     * duplicate IDs or a closed transaction throw, and failed plugin transactions publish no declarations.
     */
    void register(RecipeMatchingRuleAdapter adapter);
}
