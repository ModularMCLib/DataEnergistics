package com.fish_dan_.data_energistics.common.gui.plane;

import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** A local phantom icon accepts carried-stack copies and recipe-viewer drag-and-drop without moving inventory. */
final class PlaneGhostSlot extends ItemSlot {

    private final Player player;

    PlaneGhostSlot(Player player) {
        this.player = player;
        xeiPhantom();
    }

    @Override
    protected void onMouseDown(UIEvent event) {
        if (event.button == 0) setValue(player.containerMenu.getCarried().copyWithCount(1));
        else if (event.button == 1) setValue(ItemStack.EMPTY);
        event.hasHandler = true;
        event.stopPropagation();
    }
}
