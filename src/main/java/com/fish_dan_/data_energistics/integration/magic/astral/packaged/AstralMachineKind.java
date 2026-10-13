package com.fish_dan_.data_energistics.integration.magic.astral.packaged;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

import hellfirepvp.astralsorcery.common.lib.RecipeTypesAS;
import hellfirepvp.astralsorcery.common.recipe.altar.AltarRecipe;
import hellfirepvp.astralsorcery.common.recipe.infusion.InfusionRecipe;

public enum AstralMachineKind {

    ALTAR(ResourceLocation.fromNamespaceAndPath("astralsorcery", "altar_crafting")),
    INFUSION(ResourceLocation.fromNamespaceAndPath("astralsorcery", "infusion"));

    final ResourceLocation recipeType;

    AstralMachineKind(ResourceLocation recipeType) {
        this.recipeType = recipeType;
    }

    boolean accepts(Recipe<?> recipe) {
        return switch (this) {
            case ALTAR -> recipe instanceof AltarRecipe && recipe.getType() == RecipeTypesAS.ALTAR_CRAFTING_TYPE.get();
            case INFUSION -> recipe instanceof InfusionRecipe && recipe.getType() == RecipeTypesAS.INFUSION_TYPE.get();
        };
    }
}
