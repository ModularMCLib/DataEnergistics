package com.fish_dan_.data_energistics.integration.guide.guideme;

import com.fish_dan_.data_energistics.api.recipe.incineration.IncinerationIngredient;
import com.fish_dan_.data_energistics.api.recipe.incineration.IncinerationRecipe;
import com.fish_dan_.data_energistics.common.data.DERecipes;
import com.fish_dan_.data_energistics.integration.guide.guideme.client.IncinerationGuideScene;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.fml.loading.FMLEnvironment;

import guideme.compiler.tags.RecipeTypeMappingSupplier;
import guideme.document.LytSize;
import guideme.document.block.AlignItems;
import guideme.document.block.LytGuiSprite;
import guideme.document.block.LytHBox;
import guideme.document.block.LytParagraph;
import guideme.document.block.LytSlotGrid;
import guideme.document.block.recipes.LytStandardRecipeBox;
import guideme.render.GuiAssets;

import java.util.Arrays;

public final class IncinerationGuideRecipeMappings implements RecipeTypeMappingSupplier {

    @Override
    public void collect(RecipeTypeMappings mappings) {
        mappings.add(DERecipes.INCINERATION_TYPE.get(), IncinerationGuideRecipeMappings::createRecipe);
    }

    private static LytStandardRecipeBox<IncinerationRecipe> createRecipe(RecipeHolder<IncinerationRecipe> holder) {
        IncinerationRecipe recipe = holder.value();
        LytHBox row = new LytHBox();
        row.setGap(2);
        row.setAlignItems(AlignItems.CENTER);
        row.setWrap(false);
        row.append(LytSlotGrid.column(recipe.getItemInputs().stream()
                .map(IncinerationGuideRecipeMappings::withCount)
                .toList(), true));
        row.append(arrow());
        row.append(FMLEnvironment.dist.isClient() ? new IncinerationGuideScene(recipe) : LytParagraph.of(""));
        row.append(arrow());
        row.append(LytSlotGrid.columnFromStack(recipe.getResults(), true));

        return LytStandardRecipeBox.builder()
                .title(Component.translatable("recipe.data_energistics.incineration.action").getString())
                .icon(recipe.getBaseBlock())
                .customBody(row)
                .build(holder);
    }

    private static LytGuiSprite arrow() {
        return new LytGuiSprite(GuiAssets.ARROW, new LytSize(24, 17));
    }

    private static Ingredient withCount(IncinerationIngredient input) {
        return Ingredient.of(Arrays.stream(input.ingredient().getItems()).map(stack -> {
            ItemStack copy = stack.copy();
            copy.setCount(input.count());
            return copy;
        }));
    }
}
