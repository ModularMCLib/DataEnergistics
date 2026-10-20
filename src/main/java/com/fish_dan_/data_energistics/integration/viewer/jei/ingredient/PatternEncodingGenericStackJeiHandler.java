package com.fish_dan_.data_energistics.integration.viewer.jei.ingredient;

import com.fish_dan_.data_energistics.client.screen.GenericStackLookupScreen;

import appeng.client.gui.StackWithBounds;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.runtime.IClickableIngredient;

import java.util.Optional;

public final class PatternEncodingGenericStackJeiHandler<T extends AbstractContainerScreen<?>>
                                                        implements IGuiContainerHandler<T> {

    @Override
    public Optional<? extends IClickableIngredient<?>> getClickableIngredientUnderMouse(
                                                                                        IClickableIngredientFactory builder, T containerScreen, double mouseX, double mouseY) {
        if (!(containerScreen instanceof GenericStackLookupScreen lookupScreen)) {
            return Optional.empty();
        }

        StackWithBounds hovered = lookupScreen.dataEnergistics$getGenericStackUnderMouse(mouseX, mouseY);
        if (hovered == null) {
            return Optional.empty();
        }

        return createClickableIngredient(builder, hovered);
    }

    /** Uses the same registered AE-key converters for native screens and the foreground player terminal. */
    public static Optional<IClickableIngredient<?>> createClickableIngredient(IClickableIngredientFactory builder, StackWithBounds hovered) {
        return createClickableIngredient(
                builder,
                JeiGenericStackIngredientResolver.resolve(hovered.stack()),
                hovered);
    }

    private static <I> Optional<IClickableIngredient<?>> createClickableIngredient(
                                                                                   IClickableIngredientFactory builder,
                                                                                   JeiGenericStackIngredientResolver.ResolvedIngredient<I> ingredient,
                                                                                   StackWithBounds hovered) {
        return builder.createBuilder(ingredient.type(), ingredient.ingredient())
                .buildWithArea(hovered.bounds()).map(clickable -> clickable);
    }
}
