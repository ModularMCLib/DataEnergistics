package com.fish_dan_.data_energistics.integration.guide.guideme;

import com.fish_dan_.data_energistics.api.recipe.reassembler.DataRipperReassemblerRecipe;
import com.fish_dan_.data_energistics.common.data.DEBlocks;
import com.fish_dan_.data_energistics.common.data.DERecipes;

import net.minecraft.world.item.crafting.RecipeHolder;

import guideme.compiler.tags.RecipeTypeMappingSupplier;
import guideme.document.block.recipes.LytStandardRecipeBox;

public final class DataRipperReassemblerGuideRecipeMappings implements RecipeTypeMappingSupplier {

    @Override
    public void collect(RecipeTypeMappings mappings) {
        mappings.add(
                DERecipes.DATA_RIPPER_REASSEMBLER_TYPE.get(),
                DataRipperReassemblerGuideRecipeMappings::createRecipe);
    }

    private static LytStandardRecipeBox<DataRipperReassemblerRecipe> createRecipe(
                                                                                  RecipeHolder<DataRipperReassemblerRecipe> holder) {
        var recipe = holder.value();

        return LytStandardRecipeBox.builder()
                .title("Data Reassembler")
                .icon(DEBlocks.DATA_RIPPER_REASSEMBLER.get())
                .customBody(DataRipperReassemblerGuideRecipeBodyFactory.create(recipe))
                .build(holder);
    }
}
