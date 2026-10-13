package com.fish_dan_.data_energistics.integration.magic.astral.packaged;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

import hellfirepvp.astralsorcery.common.lib.RecipeTypesAS;
import hellfirepvp.astralsorcery.common.recipe.altar.AltarRecipe;

public enum AstralMachineKind {

    ALTAR(ResourceLocation.fromNamespaceAndPath("astralsorcery", "altar_crafting"));

    final ResourceLocation recipeType;

    AstralMachineKind(ResourceLocation recipeType) {
        this.recipeType = recipeType;
    }

    boolean accepts(Recipe<?> recipe) {
        return recipe instanceof AltarRecipe && recipe.getType() == RecipeTypesAS.ALTAR_CRAFTING_TYPE.get();
    }
}
