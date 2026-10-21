package com.fish_dan_.data_energistics.integration.guide.guideme;

import com.fish_dan_.data_energistics.api.recipe.reassembler.DataRipperReassemblerRecipe;
import com.fish_dan_.data_energistics.integration.guide.guideme.client.DataRipperReassemblerGuideRecipeBody;

import net.neoforged.fml.loading.FMLEnvironment;

import guideme.document.block.LytBlock;
import guideme.document.block.LytParagraph;

final class DataRipperReassemblerGuideRecipeBodyFactory {

    private DataRipperReassemblerGuideRecipeBodyFactory() {}

    static LytBlock create(DataRipperReassemblerRecipe recipe) {
        if (!FMLEnvironment.dist.isClient()) {
            return LytParagraph.of("");
        }

        return new DataRipperReassemblerGuideRecipeBody(recipe);
    }
}
