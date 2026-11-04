package com.fish_dan_.data_energistics.common.gui.plane;

import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * Keeps client scene rendering outside the shared container tree. One preview belongs to one open UI and is
 * refreshed only on the client thread; the scene element owns renderer cleanup when that UI closes.
 */
public interface PlaneScenePreview {

    /**
     * Refreshes configured previews throughout the volume after server synchronization. Both inputs
     * belong to the UI, must not be mutated by the preview, and contain only validated working-cell indices.
     */
    void refresh(ItemStack[] filters, IntSet itemModes);

    /** Selects a zero-based editable depth layer, clearing selection while keeping all configured previews visible. */
    void showLayer(int layer);
}
