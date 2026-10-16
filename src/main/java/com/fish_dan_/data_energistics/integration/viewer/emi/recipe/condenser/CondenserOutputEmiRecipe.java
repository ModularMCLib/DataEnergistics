package com.fish_dan_.data_energistics.integration.viewer.emi.recipe.condenser;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.viewer.xei.recipe.condenser.CondenserOutputRecipeView;
import com.fish_dan_.data_energistics.recipe.condenser.CondenserOutputRecipe;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;

import java.util.List;

public final class CondenserOutputEmiRecipe extends BasicEmiRecipe {

    private static final ResourceLocation BACKGROUND = Data_Energistics.id("textures/guis/recipe/condenser.png");
    private static final ResourceLocation PROGRESS = Data_Energistics.id("textures/guis/progress/processing.png");
    private static final ResourceLocation TRASH_ICON = Data_Energistics.id("textures/guis/states/background/trash.png");
    private static final ResourceLocation MODE_BACKGROUND = Data_Energistics.id("textures/guis/states/small_top_surface/mode_button.png");
    private static final ResourceLocation AGGREGATION_MODE_ICON = Data_Energistics.id("textures/guis/states/top_surface/aggregation_mode.png");
    private static final int WIDTH = 96;
    private static final int HEIGHT = 48;
    private static final int STORAGE_X = 52;
    private static final int STORAGE_Y = 0;
    private static final int OUTPUT_X = 56;
    private static final int OUTPUT_Y = 26;
    private static final int MODE_X = 80;
    private static final int MODE_Y = 28;

    private final CondenserOutputRecipeView recipe;

    public CondenserOutputEmiRecipe(EmiRecipeCategory category, RecipeHolder<CondenserOutputRecipe> holder) {
        this(category, CondenserOutputRecipeView.from(holder));
    }

    private CondenserOutputEmiRecipe(EmiRecipeCategory category, CondenserOutputRecipeView recipe) {
        super(category, recipe.id(), WIDTH, HEIGHT);
        this.recipe = recipe;
        this.catalysts.add(EmiIngredient.of(recipe.storageCandidates().stream().map(EmiStack::of).toList()));
        this.outputs.add(EmiStack.of(recipe.result()));
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addTexture(BACKGROUND, 0, 0, WIDTH, HEIGHT, 0, 0, WIDTH, HEIGHT, WIDTH, HEIGHT);
        widgets.addTexture(TRASH_ICON, 4, 28, 14, 14, 0, 0, 14, 14, 14, 14);
        widgets.addTexture(MODE_BACKGROUND, MODE_X, MODE_Y, 16, 16, 0, 0, 16, 16, 16, 16);
        widgets.addTexture(AGGREGATION_MODE_ICON, MODE_X, MODE_Y, 16, 16, 0, 0, 16, 16, 16, 16);
        widgets.addAnimatedTexture(PROGRESS, 72, 0, 6, 18, 0, 0,
                6, 18, 6, 18, 2000, false, true, false);
        widgets.addSlot(this.catalysts.getFirst(), STORAGE_X, STORAGE_Y).catalyst(true).drawBack(false);
        widgets.addSlot(this.outputs.getFirst(), OUTPUT_X, OUTPUT_Y).drawBack(false).recipeContext(this);
        widgets.addTooltipText(
                List.of(
                        Component.translatable(
                                "recipe.data_energistics.condenser_output.item_aggregation",
                                this.recipe.requiredPower(),
                                this.recipe.result().getHoverName())),
                MODE_X,
                MODE_Y,
                16,
                16);
    }

    @Override
    public ResourceLocation getId() {
        return this.recipe.id();
    }
}
