package com.fish_dan_.data_energistics.integration.viewer.emi.ui;

import com.fish_dan_.data_energistics.client.terminal.TerminalOverlayClient;

import appeng.integration.modules.emi.EmiStackHelper;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class PlayerTerminalEmiDragHandler implements EmiDragDropHandler<Screen> {

    @Override
    public boolean dropStack(Screen screen, EmiIngredient ingredient, int x, int y) {
        for (var target : TerminalOverlayClient.ghostTargets(screen)) {
            if (!target.area().contains(x, y)) continue;
            for (var candidate : ingredient.getEmiStacks()) {
                var stack = EmiStackHelper.toGenericStack(candidate);
                if (stack == null) continue;
                TerminalOverlayClient.drop(screen, target, stack);
                return true;
            }
        }
        return false;
    }

    @Override
    public void render(Screen screen, EmiIngredient dragged, GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        for (var target : TerminalOverlayClient.ghostTargets(screen)) {
            var area = target.area();
            graphics.fill(area.getX(), area.getY(), area.getX() + area.getWidth(), area.getY() + area.getHeight(), 0x6644CC99);
        }
    }
}
