package com.fish_dan_.data_energistics.mixin.technology.avaritia.compat.emi;

import net.minecraft.world.item.crafting.RecipeHolder;

import committee.nova.mods.avaritia.common.crafting.recipe.ExtremeSmithingRecipe;
import committee.nova.mods.avaritia.init.compat.emi.category.ExtremeSmithingRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Supplies the five displayed smithing inputs which the native recipe's default ingredient list omits. */
@Mixin(value = ExtremeSmithingRecipeCategory.class, remap = false)
abstract class ExtremeSmithingEmiInputsMixin {

    @Shadow
    public abstract RecipeHolder<ExtremeSmithingRecipe> recipe();

    @Inject(method = "getInputs", at = @At("HEAD"), cancellable = true)
    private void transferDisplayedInputs(CallbackInfoReturnable<List<EmiIngredient>> cir) {
        var recipe = recipe().value();
        var inputs = new ObjectArrayList<EmiIngredient>(5);
        inputs.add(EmiIngredient.of(recipe.template));
        inputs.add(EmiIngredient.of(recipe.base));
        var additions = recipe.additions.getItems();
        // The category draws these same first three examples in top/right/bottom order. Each is one
        // physical input, not three alternatives for one input as a generic ingredient list would imply.
        if (additions.length < 3) throw new IllegalStateException("Avaritia smithing category requires three displayed additions");
        for (int index = 0; index < 3; index++) inputs.add(EmiStack.of(additions[index].copyWithCount(1)));
        cir.setReturnValue(inputs);
    }
}
