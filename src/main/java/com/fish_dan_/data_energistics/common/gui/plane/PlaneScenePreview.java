package com.fish_dan_.data_energistics.common.gui.plane;

import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * Keeps client scene rendering outside the shared container tree. One preview belongs to one open UI and is
 * refreshed only on the client thread; the scene element owns renderer cleanup when that UI closes.
 */
public interface PlaneScenePreview {

    /**
     * Refreshes the fixed-size filter array and item-mode indices after server synchronization. Both inputs
     * belong to the UI, must not be mutated by the preview, and contain only validated working-cell indices.
     */
    void refresh(ItemStack[] filters, IntSet itemModes);

    /** Displays a zero-based depth layer in front of the working face, clearing the previous selection. */
    void showLayer(int layer);
}
