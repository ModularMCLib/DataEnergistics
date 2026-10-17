package com.fish_dan_.data_energistics.integration.viewer.jei.ui;

import com.fish_dan_.data_energistics.client.gui.OrderPackageGhostIngredient;
import com.fish_dan_.data_energistics.client.terminal.TerminalOverlayClient;
import com.fish_dan_.data_energistics.integration.viewer.jei.ingredient.DataResourceJeiIngredient;

import appeng.api.stacks.GenericStack;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import org.jspecify.annotations.NullMarked;

/** Viewer drags configure ghost cells using the terminal's authenticated request stream. */
@NullMarked
public final class PlayerTerminalJeiGhostHandler<T extends AbstractContainerScreen<?>> implements IGhostIngredientHandler<T> {

    @Override
    public <I> ObjectList<Target<I>> getTargetsTyped(T screen, ITypedIngredient<I> ingredient, boolean doStart) {
        Object value = ingredient.getIngredient();
        GenericStack stack = value instanceof DataResourceJeiIngredient data ? data.asGenericStack() : OrderPackageGhostIngredient.toGenericStack(value);
        ObjectList<Target<I>> targets = new ObjectArrayList<>();
        if (stack == null) return targets;
        for (var target : TerminalOverlayClient.ghostTargets(screen)) {
            targets.add(new Target<>() {

                @Override
                public Rect2i getArea() {
                    return target.area();
                }

                @Override
                public void accept(I dropped) {
                    TerminalOverlayClient.drop(screen, target, stack);
                }
            });
        }
        return targets;
    }

    @Override
    public void onComplete() {
        // Drag lifecycle owns no local state; the server snapshot confirms each configured cell.
    }
}
