package com.fish_dan_.data_energistics.integration.viewer.emi.ingredient;

import com.fish_dan_.data_energistics.client.screen.GenericStackLookupScreen;

import appeng.client.gui.StackWithBounds;

import net.minecraft.client.gui.screens.Screen;

import dev.emi.emi.api.EmiStackProvider;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;

public final class PatternEncodingGenericStackEmiProvider implements EmiStackProvider<Screen> {

    @Override
    public EmiStackInteraction getStackAt(Screen screen, int x, int y) {
        if (!(screen instanceof GenericStackLookupScreen lookupScreen)) {
            return EmiStackInteraction.EMPTY;
        }

        StackWithBounds hovered = lookupScreen.dataEnergistics$getGenericStackUnderMouse(x, y);
        if (hovered == null) {
            return EmiStackInteraction.EMPTY;
        }

        return createInteraction(hovered);
    }

    /** Shares registered AE-key conversion between native screens and the foreground player terminal. */
    public static EmiStackInteraction createInteraction(StackWithBounds hovered) {
        EmiStack emiStack = EmiGenericStackIngredientResolver.resolve(hovered.stack());
        return new EmiStackInteraction(emiStack, null, true);
    }
}
