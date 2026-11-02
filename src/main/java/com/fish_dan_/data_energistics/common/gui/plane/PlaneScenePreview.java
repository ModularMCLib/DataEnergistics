package com.fish_dan_.data_energistics.common.gui.plane;

import net.minecraft.world.item.ItemStack;

/**
 * Keeps client scene rendering outside the shared container tree. One preview belongs to one open UI and is
 * refreshed only on the client thread; the scene element owns renderer cleanup when that UI closes.
 */
public interface PlaneScenePreview {

    /**
     * Refreshes the fixed-size, non-null filter array and placement bitmask after server synchronization. The
     * array belongs to the UI, must not be mutated by the preview, and contains empty stacks for disabled cells.
     */
    void refresh(ItemStack[] filters, int entityModes);
}
