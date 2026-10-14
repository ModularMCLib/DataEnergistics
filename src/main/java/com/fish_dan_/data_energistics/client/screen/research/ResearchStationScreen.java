package com.fish_dan_.data_energistics.client.screen.research;

import com.fish_dan_.data_energistics.menu.research.ResearchStationMenu;

import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Container shell; LDLib2's screen mixin renders and routes the entire research UI. */
public final class ResearchStationScreen extends AbstractContainerScreen<ResearchStationMenu> {

    public ResearchStationScreen(ResearchStationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        ModularUI ui = mountedUi(menu);
        imageWidth = (int) ui.getWidth();
        imageHeight = (int) ui.getHeight();
        super.init();
        setFocused(ui.getWidget());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {}

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {}

    private static ModularUI mountedUi(AbstractContainerMenu menu) {
        if (menu instanceof IModularUIHolderMenu holder) {
            ModularUI ui = holder.getModularUI();
            if (ui != null) return ui;
        }
        throw new IllegalStateException("Research station screen requires a mounted LDLib2 UI");
    }
}
