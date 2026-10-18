package com.fish_dan_.data_energistics.integration.viewer.emi.transfer;

import com.fish_dan_.data_energistics.client.terminal.TerminalOverlayClient;
import com.fish_dan_.data_energistics.client.terminal.encoding.TerminalViewerRecipeTransfer;

import appeng.api.stacks.GenericStack;
import appeng.integration.modules.emi.EmiStackHelper;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.AbstractContainerMenu;

import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.screen.RecipeScreen;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Selects the overlay only while its encoder is visible; the host's ordinary recipe handlers keep ownership otherwise.
 */
@NullMarked
public final class PlayerTerminalEmiRecipeTransfer<T extends AbstractContainerMenu> implements EmiRecipeHandler<T> {

    public static boolean recipeScreen(@Nullable Screen screen) {
        return screen instanceof RecipeScreen;
    }

    @Override
    public EmiPlayerInventory getInventory(AbstractContainerScreen<T> screen) {
        return new EmiPlayerInventory(ObjectList.of());
    }

    @Override
    public boolean supportsRecipe(EmiRecipe recipe) {
        return true;
    }

    @Override
    public boolean alwaysDisplaySupport(EmiRecipe recipe) {
        return false;
    }

    @Override
    public boolean canCraft(EmiRecipe recipe, EmiCraftContext<T> context) {
        return prepare(recipe, context) != null;
    }

    @Override
    public boolean craft(EmiRecipe recipe, EmiCraftContext<T> context) {
        CompoundTag data = prepare(recipe, context);
        if (data == null) return false;
        TerminalOverlayClient.transferRecipe(context.getScreenHandler(), data);
        return true;
    }

    private @Nullable CompoundTag prepare(EmiRecipe recipe, EmiCraftContext<T> context) {
        var holder = recipe.getBackingRecipe();
        ObjectList<@Nullable GenericStack> inputs = new ObjectArrayList<>();
        for (EmiIngredient ingredient : recipe.getInputs()) {
            GenericStack chosen = null;
            for (var stack : ingredient.getEmiStacks()) {
                chosen = EmiStackHelper.toGenericStack(stack);
                if (chosen != null) {
                    chosen = new GenericStack(chosen.what(), ingredient.getAmount());
                    break;
                }
            }
            inputs.add(chosen);
        }
        ObjectList<@Nullable GenericStack> outputs = new ObjectArrayList<>();
        for (var stack : recipe.getOutputs()) outputs.add(EmiStackHelper.toGenericStack(stack));
        return TerminalViewerRecipeTransfer.prepare(context.getScreenHandler(), holder == null ? null : holder.value(), inputs, outputs,
                EmiPatternTransferContextBridge.resolve(recipe).recipeTypeId(), EmiPatternTransferContextBridge.resolveRecipeId(holder, recipe));
    }
}
