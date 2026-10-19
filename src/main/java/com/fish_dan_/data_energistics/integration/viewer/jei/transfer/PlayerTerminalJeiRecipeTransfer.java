package com.fish_dan_.data_energistics.integration.viewer.jei.transfer;

import com.fish_dan_.data_energistics.client.gui.OrderPackageGhostIngredient;
import com.fish_dan_.data_energistics.client.terminal.TerminalOverlayClient;
import com.fish_dan_.data_energistics.client.terminal.encoding.TerminalViewerRecipeTransfer;
import com.fish_dan_.data_energistics.integration.viewer.jei.ingredient.DataResourceJeiIngredient;
import com.fish_dan_.data_energistics.mixin.viewer.jei.BasicRecipeTransferHandlerAccessor;

import appeng.api.stacks.GenericStack;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
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
                stacks(menu, layout, RecipeIngredientRole.INPUT), stacks(menu, layout, RecipeIngredientRole.OUTPUT),
                context.recipeTypeId(), JeiPatternTransferContextBridge.resolveRecipeId(layout));
        if (data == null) return Optional.of(RecipeTransferErrorInternal.INSTANCE);
        if (submit) TerminalOverlayClient.transferRecipe(menu, data);
        return Optional.empty();
    }

    /** The cast restores the same C/R parameters that JEI used to register the basic handler. */
    @SuppressWarnings("unchecked")
    public static <C extends AbstractContainerMenu, R> @Nullable CompoundTag prepareHost(
                                                                                         C menu, IRecipeLayoutDrawable<R> layout, IRecipeTransferHandler<C, R> handler) {
        if (!(handler instanceof BasicRecipeTransferHandlerAccessor<?, ?>)) return null;
        var info = ((BasicRecipeTransferHandlerAccessor<C, R>) handler).dataEnergistics$getTransferInfo();
        if (!info.canHandle(menu, layout.getRecipe())) return null;
        Recipe<?> recipe = layout.getRecipe() instanceof RecipeHolder<?> holder ? holder.value() :
                layout.getRecipe() instanceof Recipe<?> value ? value : null;
        return TerminalViewerRecipeTransfer.hostTargets(menu, recipe, new ObjectArrayList<>(info.getRecipeSlots(menu, layout.getRecipe())),
                stacks(menu, layout, RecipeIngredientRole.INPUT));
    }

    private static ObjectList<@Nullable GenericStack> stacks(AbstractContainerMenu menu, IRecipeLayoutDrawable<?> layout, RecipeIngredientRole role) {
        ObjectList<@Nullable GenericStack> result = new ObjectArrayList<>();
        for (var slot : layout.getRecipeSlotsView().getSlotViews(role)) {
            if (role == RecipeIngredientRole.OUTPUT) {
                Object value = slot.getDisplayedIngredient().map(ingredient -> ingredient.getIngredient()).orElse(null);
                result.add(value == null ? null : value instanceof DataResourceJeiIngredient resource ? resource.asGenericStack() : OrderPackageGhostIngredient.toGenericStack(value));
                continue;
            }
            ObjectList<GenericStack> alternatives = new ObjectArrayList<>();
            slot.getAllIngredients().forEach(ingredient -> {
                Object value = ingredient.getIngredient();
                GenericStack stack = value instanceof DataResourceJeiIngredient resource ? resource.asGenericStack() : OrderPackageGhostIngredient.toGenericStack(value);
                if (stack != null) alternatives.add(stack);
            });
            result.add(TerminalViewerRecipeTransfer.choose(menu, alternatives, -1));
        }
        return result;
    }
}
