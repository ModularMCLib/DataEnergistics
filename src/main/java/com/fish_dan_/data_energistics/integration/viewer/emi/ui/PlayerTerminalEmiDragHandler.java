package com.fish_dan_.data_energistics.integration.viewer.emi.ui;

import com.fish_dan_.data_energistics.client.gui.terminal.TerminalOverlayUi;
import com.fish_dan_.data_energistics.client.terminal.TerminalOverlayClient;

import appeng.api.stacks.GenericStack;
import appeng.integration.modules.emi.EmiStackHelper;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class PlayerTerminalEmiDragHandler implements EmiDragDropHandler<Screen> {

    @Override
    public boolean dropStack(Screen screen, EmiIngredient ingredient, int x, int y) {
        for (var target : TerminalOverlayClient.ghostTargets(screen)) {
            if (!target.area().contains(x, y)) continue;
            for (var candidate : ingredient.getEmiStacks()) {
                var stack = EmiStackHelper.toGenericStack(candidate);
                if (stack == null || !target.accepts(stack)) continue;
                TerminalOverlayClient.drop(screen, target, stack);
                return true;
            }
        }
        return false;
    }

    @Override
    public void render(Screen screen, EmiIngredient dragged, GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        ObjectList<GenericStack> candidates = new ObjectArrayList<>();
        for (var ingredient : dragged.getEmiStacks()) {
            var stack = EmiStackHelper.toGenericStack(ingredient);
            if (stack != null) candidates.add(stack);
        }
        ObjectList<TerminalOverlayUi.GhostTarget> targets = new ObjectArrayList<>(TerminalOverlayClient.ghostTargets(screen));
        targets.removeIf(target -> candidates.stream().noneMatch(target::accepts));
        if (targets.isEmpty() && !TerminalOverlayClient.contains(screen, mouseX, mouseY)) return;
        TerminalOverlayClient.previewGhostDrag(screen, foreground -> {
            for (var target : targets) {
                var area = target.area();
                foreground.fill(area.getX(), area.getY(), area.getX() + area.getWidth(), area.getY() + area.getHeight(), 0x8822BB33);
            }
        }, foreground -> dragged.render(foreground, mouseX - 8, mouseY - 8, delta, EmiIngredient.RENDER_ICON));
    }
}
