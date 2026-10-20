package com.fish_dan_.data_energistics.integration.viewer.jei.ui;

import com.fish_dan_.data_energistics.client.terminal.TerminalOverlayClient;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;

import it.unimi.dsi.fastutil.objects.ObjectList;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import org.jspecify.annotations.NullMarked;

/** JEI receives the cached visible terminal rectangles for every container screen. */
@NullMarked
public final class PlayerTerminalJeiGuiHandler<T extends AbstractContainerScreen<?>> implements IGuiContainerHandler<T> {

    @Override
    public ObjectList<Rect2i> getGuiExtraAreas(T screen) {
        return TerminalOverlayClient.exclusionAreas(screen);
    }
}
