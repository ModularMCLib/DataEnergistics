package com.fish_dan_.data_energistics.client.screen.machine.anvil;

import com.fish_dan_.data_energistics.common.menu.machine.anvil.MeDigitalAnvilMenu;

import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Container lifecycle shell; LDLib2 renders and routes the mounted anvil composition. */
public final class MeDigitalAnvilScreen extends AbstractContainerScreen<MeDigitalAnvilMenu> {

    public MeDigitalAnvilScreen(MeDigitalAnvilMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        ModularUI ui = ((IModularUIHolderMenu) (Object) menu).getModularUI();
        if (ui == null) throw new IllegalStateException("ME digital anvil screen requires a mounted UI");
        imageWidth = (int) ui.getWidth();
        imageHeight = (int) ui.getHeight();
        super.init();
        leftPos = Math.round(ui.getLeftPos());
        topPos = Math.round(ui.getTopPos());
        setFocused(ui.getWidget());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {}

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {}
}
