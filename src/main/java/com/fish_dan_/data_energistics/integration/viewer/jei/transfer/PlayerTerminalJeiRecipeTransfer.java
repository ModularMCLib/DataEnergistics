package com.fish_dan_.data_energistics.integration.viewer.jei.transfer;

import com.fish_dan_.data_energistics.client.gui.OrderPackageGhostIngredient;
import com.fish_dan_.data_energistics.client.terminal.TerminalOverlayClient;
import com.fish_dan_.data_energistics.client.terminal.encoding.TerminalViewerRecipeTransfer;
import com.fish_dan_.data_energistics.integration.viewer.jei.ingredient.DataResourceJeiIngredient;

import appeng.api.stacks.GenericStack;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.common.transfer.RecipeTransferErrorInternal;
import mezz.jei.gui.recipes.RecipesGui;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/** Uses JEI's real input/output roles and the existing recipe identity bridge for the overlay. */
@NullMarked
public final class PlayerTerminalJeiRecipeTransfer {

    private PlayerTerminalJeiRecipeTransfer() {}

    public static boolean recipeScreen(@Nullable Screen screen) {
        return screen instanceof RecipesGui;
    }

    public static Optional<IRecipeTransferError> transfer(AbstractContainerMenu menu, IRecipeLayoutDrawable<?> layout, boolean submit) {
        Recipe<?> recipe = layout.getRecipe() instanceof RecipeHolder<?> holder ? holder.value() :
                layout.getRecipe() instanceof Recipe<?> value ? value : null;
        var context = JeiPatternTransferContextBridge.resolve(layout);
        var data = TerminalViewerRecipeTransfer.prepare(menu, recipe,
                stacks(layout, RecipeIngredientRole.INPUT), stacks(layout, RecipeIngredientRole.OUTPUT),
                context.recipeTypeId(), JeiPatternTransferContextBridge.resolveRecipeId(layout));
        if (data == null) return Optional.of(RecipeTransferErrorInternal.INSTANCE);
        if (submit) TerminalOverlayClient.transferRecipe(menu, data);
        return Optional.empty();
    }

    private static ObjectList<@Nullable GenericStack> stacks(IRecipeLayoutDrawable<?> layout, RecipeIngredientRole role) {
        ObjectList<@Nullable GenericStack> result = new ObjectArrayList<>();
        for (var slot : layout.getRecipeSlotsView().getSlotViews(role)) {
            Object value = slot.getDisplayedIngredient().map(ingredient -> ingredient.getIngredient()).orElse(null);
            result.add(value == null ? null : value instanceof DataResourceJeiIngredient resource ? resource.asGenericStack() : OrderPackageGhostIngredient.toGenericStack(value));
        }
        return result;
    }
}
