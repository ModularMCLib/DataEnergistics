package com.fish_dan_.data_energistics.integration.viewer.emi.ui;

import com.fish_dan_.data_energistics.client.terminal.TerminalOverlayClient;

import net.minecraft.client.gui.screens.Screen;

import dev.emi.emi.api.EmiExclusionArea;
import dev.emi.emi.api.widget.Bounds;
import org.jspecify.annotations.NullMarked;

import java.util.function.Consumer;

@NullMarked
public final class PlayerTerminalEmiExclusionArea implements EmiExclusionArea<Screen> {

    @Override
    public void addExclusionArea(Screen screen, Consumer<Bounds> consumer) {
        for (var area : TerminalOverlayClient.exclusionAreas(screen)) {
            consumer.accept(new Bounds(area.getX(), area.getY(), area.getWidth(), area.getHeight()));
        }
    }
}
