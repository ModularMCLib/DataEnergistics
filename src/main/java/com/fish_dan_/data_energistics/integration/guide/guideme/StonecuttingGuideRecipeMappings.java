package com.fish_dan_.data_energistics.integration.guide.guideme;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.StonecutterRecipe;

import guideme.compiler.tags.RecipeTypeMappingSupplier;
import guideme.document.block.LytSlotGrid;
import guideme.document.block.recipes.LytStandardRecipeBox;

public final class StonecuttingGuideRecipeMappings implements RecipeTypeMappingSupplier {

    @Override
    public void collect(RecipeTypeMappings mappings) {
        mappings.add(RecipeType.STONECUTTING, StonecuttingGuideRecipeMappings::createRecipe);
    }

    private static LytStandardRecipeBox<StonecutterRecipe> createRecipe(RecipeHolder<StonecutterRecipe> holder) {
        StonecutterRecipe recipe = holder.value();
        return LytStandardRecipeBox.builder()
                .title(Component.translatable("container.stonecutter").getString())
                .icon(Items.STONECUTTER)
                .input(LytSlotGrid.row(recipe.getIngredients(), true))
                .outputFromResultOf(holder)
                .build(holder);
    }
}
