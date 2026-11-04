package com.fish_dan_.data_energistics.integration.viewer.emi.recipe;

import com.fish_dan_.data_energistics.api.recipe.incineration.IncinerationIngredient;
import com.fish_dan_.data_energistics.api.recipe.incineration.IncinerationRecipe;
import com.fish_dan_.data_energistics.integration.viewer.xei.recipe.incineration.IncinerationRecipeDisplay;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.TextWidget;
import dev.emi.emi.api.widget.WidgetHolder;

public final class IncinerationEmiRecipe extends BasicEmiRecipe {

    private static final int WIDTH = 148;
    private static final int HEIGHT = 72;

    private final IncinerationRecipe recipe;

    public IncinerationEmiRecipe(RecipeHolder<IncinerationRecipe> holder) {
        super(TimeShiftEmiRecipe.CATEGORY, holder.id(), WIDTH, HEIGHT);
        this.recipe = holder.value();
        this.inputs.add(EmiStack.of(new ItemStack(this.recipe.getBaseBlock())));
        for (IncinerationIngredient ingredient : this.recipe.getItemInputs()) {
            this.inputs.add(EmiIngredient.of(ingredient.ingredient(), ingredient.count()));
        }
        this.outputs.add(EmiStack.of(this.recipe.getResults().getFirst().copy()));
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        for (int index = 1; index < this.inputs.size(); index++) {
            int ingredientIndex = index - 1;
            int x = IncinerationRecipeDisplay.inputX(ingredientIndex);
            int y = IncinerationRecipeDisplay.inputY(this.recipe.getItemInputs().size(), ingredientIndex);
            widgets.addSlot(this.inputs.get(index), x - 1, y - 1);
        }

        widgets.addSlot(this.outputs.getFirst(), IncinerationRecipeDisplay.OUTPUT_X - 1, IncinerationRecipeDisplay.OUTPUT_Y - 1)
                .recipeContext(this);
        widgets.addTexture(EmiTexture.EMPTY_ARROW, IncinerationRecipeDisplay.ARROW_LEFT_X, IncinerationRecipeDisplay.ARROW_Y);
        widgets.addTexture(EmiTexture.EMPTY_ARROW, IncinerationRecipeDisplay.ARROW_RIGHT_X, IncinerationRecipeDisplay.ARROW_Y);
        widgets.addDrawable(IncinerationRecipeDisplay.SCENE_X, IncinerationRecipeDisplay.SCENE_Y,
                IncinerationRecipeDisplay.SCENE_WIDTH, IncinerationRecipeDisplay.SCENE_HEIGHT,
                (graphics, mouseX, mouseY, delta) -> IncinerationRecipeDisplay.renderScene(graphics, this.recipe, 0, 0));
        widgets.addTooltipText(IncinerationRecipeDisplay.sceneTooltip(this.recipe),
                IncinerationRecipeDisplay.SCENE_X, IncinerationRecipeDisplay.SCENE_Y,
                IncinerationRecipeDisplay.SCENE_WIDTH, IncinerationRecipeDisplay.SCENE_HEIGHT);
        widgets.addText(
                Component.translatable("recipe.data_energistics.incineration.action"),
                IncinerationRecipeDisplay.CENTER_X,
                IncinerationRecipeDisplay.ACTION_Y,
                0x7E7E7E,
                false)
                .horizontalAlign(TextWidget.Alignment.CENTER);
    }
}
