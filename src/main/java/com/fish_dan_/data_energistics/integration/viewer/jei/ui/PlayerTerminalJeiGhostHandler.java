package com.fish_dan_.data_energistics.integration.viewer.jei.ui;

import com.fish_dan_.data_energistics.client.gui.OrderPackageGhostIngredient;
import com.fish_dan_.data_energistics.client.terminal.TerminalOverlayClient;
import com.fish_dan_.data_energistics.integration.viewer.jei.ingredient.DataResourceJeiIngredient;

import appeng.api.stacks.GenericStack;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.gui.ghost.GhostIngredientDrag;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/** Viewer drags configure ghost cells using the terminal's authenticated request stream. */
@NullMarked
public final class PlayerTerminalJeiGhostHandler<T extends AbstractContainerScreen<?>> implements IGhostIngredientHandler<T> {

    @Override
    public <I> ObjectList<Target<I>> getTargetsTyped(T screen, ITypedIngredient<I> ingredient, boolean doStart) {
        GenericStack stack = resource(ingredient);
        ObjectList<Target<I>> targets = new ObjectArrayList<>();
        if (stack == null) return targets;
        for (var target : TerminalOverlayClient.ghostTargets(screen)) {
            if (!target.accepts(stack)) continue;
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

    /** Retains JEI's configured highlight colors and ingredient renderer at the terminal's foreground layer. */
    public static void preview(Screen screen, ITypedIngredient<?> ingredient, int mouseX, int mouseY, @Nullable Consumer<GuiGraphics> cursor) {
        GenericStack stack = resource(ingredient);
        ObjectList<Rect2i> areas = new ObjectArrayList<>();
        if (stack != null) {
            for (var target : TerminalOverlayClient.ghostTargets(screen)) {
                if (target.accepts(stack)) areas.add(target.area());
            }
        }
        if (areas.isEmpty() && (cursor == null || !TerminalOverlayClient.contains(screen, mouseX, mouseY))) return;
        TerminalOverlayClient.previewGhostDrag(screen, foreground -> GhostIngredientDrag.drawTargets(foreground, mouseX, mouseY, areas), cursor);
    }

    private static @Nullable GenericStack resource(ITypedIngredient<?> ingredient) {
        Object value = ingredient.getIngredient();
        return value instanceof DataResourceJeiIngredient data ? data.asGenericStack() : OrderPackageGhostIngredient.toGenericStack(value);
    }

    @Override
    public void onComplete() {
        // Drag lifecycle owns no local state; the server snapshot confirms each configured cell.
    }
}
