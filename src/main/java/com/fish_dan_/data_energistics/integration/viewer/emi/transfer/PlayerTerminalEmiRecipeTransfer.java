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
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;
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

    private final @Nullable EmiRecipeHandler<T> host;

    public PlayerTerminalEmiRecipeTransfer(@Nullable EmiRecipeHandler<T> host) {
        this.host = host;
    }

    public static boolean recipeScreen(@Nullable Screen screen) {
        return screen instanceof RecipeScreen;
    }

    @Override
    public EmiPlayerInventory getInventory(AbstractContainerScreen<T> screen) {
        return host == null ? new EmiPlayerInventory(ObjectList.of()) : host.getInventory(screen);
    }

    @Override
    public boolean supportsRecipe(EmiRecipe recipe) {
        return host == null || host.supportsRecipe(recipe);
    }

    @Override
    public boolean alwaysDisplaySupport(EmiRecipe recipe) {
        return false;
    }

    @Override
    public boolean canCraft(EmiRecipe recipe, EmiCraftContext<T> context) {
        return hostTargets(recipe, context) != null || host != null && host.canCraft(recipe, context) || prepare(recipe, context) != null;
    }

    @Override
    public boolean craft(EmiRecipe recipe, EmiCraftContext<T> context) {
        CompoundTag data = prepare(recipe, context);
        CompoundTag targets = hostTargets(recipe, context);
        if (targets != null) {
            if (data != null) TerminalOverlayClient.transferRecipe(context.getScreenHandler(), data);
            TerminalOverlayClient.supplyRecipe(context.getScreenHandler(), targets);
            return true;
        }
        boolean transferred = host != null && host.canCraft(recipe, context) && host.craft(recipe, context);
        if (data != null) TerminalOverlayClient.transferRecipe(context.getScreenHandler(), data);
        return transferred || data != null;
    }

    private ObjectList<@Nullable GenericStack> inputs(EmiRecipe recipe, T menu) {
        ObjectList<@Nullable GenericStack> inputs = new ObjectArrayList<>();
        for (EmiIngredient ingredient : recipe.getInputs()) {
            ObjectList<GenericStack> alternatives = new ObjectArrayList<>();
            for (var stack : ingredient.getEmiStacks()) {
                GenericStack value = EmiStackHelper.toGenericStack(stack);
                if (value != null) alternatives.add(value);
            }
            inputs.add(TerminalViewerRecipeTransfer.choose(menu, alternatives, ingredient.getAmount()));
        }
        return inputs;
    }

    private @Nullable CompoundTag hostTargets(EmiRecipe recipe, EmiCraftContext<T> context) {
        if (!TerminalOverlayClient.canSupplyRecipe(context.getScreenHandler()) || !(host instanceof StandardRecipeHandler<T> standard)) return null;
        var holder = recipe.getBackingRecipe();
        return TerminalViewerRecipeTransfer.hostTargets(context.getScreenHandler(), holder == null ? null : holder.value(),
                new ObjectArrayList<>(standard.getCraftingSlots(recipe, context.getScreenHandler())), inputs(recipe, context.getScreenHandler()));
    }

    private @Nullable CompoundTag prepare(EmiRecipe recipe, EmiCraftContext<T> context) {
        var holder = recipe.getBackingRecipe();
        ObjectList<@Nullable GenericStack> outputs = new ObjectArrayList<>();
        for (var stack : recipe.getOutputs()) outputs.add(EmiStackHelper.toGenericStack(stack));
        return TerminalViewerRecipeTransfer.prepare(context.getScreenHandler(), holder == null ? null : holder.value(), inputs(recipe, context.getScreenHandler()), outputs,
                EmiPatternTransferContextBridge.resolve(recipe).recipeTypeId(), EmiPatternTransferContextBridge.resolveRecipeId(holder, recipe));
    }
}
