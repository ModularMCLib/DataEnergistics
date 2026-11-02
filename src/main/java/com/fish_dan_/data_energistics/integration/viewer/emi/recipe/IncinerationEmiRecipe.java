package com.fish_dan_.data_energistics.integration.viewer.emi.recipe;

import com.fish_dan_.data_energistics.api.recipe.incineration.IncinerationIngredient;
import com.fish_dan_.data_energistics.api.recipe.incineration.IncinerationRecipe;

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
    private static final int SLOT_SIZE = 18;
    private static final int BASE_X = 8;
    private static final int BASE_Y = 8;
    private static final int INPUT_X = 8;
    private static final int INPUT_Y = 34;
    private static final int OUTPUT_X = 122;
    private static final int OUTPUT_Y = 26;

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
        Component baseText = this.recipe.getTransformedBlock()
                .map(block -> Component.translatable("recipe.data_energistics.incineration.base.transformed", new ItemStack(block).getHoverName()))
                .orElseGet(() -> Component.translatable("recipe.data_energistics.incineration.base.not_transformed"));
        widgets.addSlot(this.inputs.getFirst(), BASE_X, BASE_Y)
                .drawBack(true)
                .appendTooltip(baseText);

        for (int index = 1; index < this.inputs.size(); index++) {
            int ingredientIndex = index - 1;
            int x = INPUT_X + ingredientIndex % 5 * SLOT_SIZE;
            int y = INPUT_Y + ingredientIndex / 5 * SLOT_SIZE;
            widgets.addSlot(this.inputs.get(index), x, y);
        }

        widgets.addSlot(this.outputs.getFirst(), OUTPUT_X, OUTPUT_Y).recipeContext(this);
        widgets.addTexture(EmiTexture.EMPTY_ARROW, 83, 27);
        widgets.addText(
                Component.translatable("recipe.data_energistics.incineration.action"),
                79,
                7,
                0x7E7E7E,
                false)
                .horizontalAlign(TextWidget.Alignment.CENTER);
    }
}
