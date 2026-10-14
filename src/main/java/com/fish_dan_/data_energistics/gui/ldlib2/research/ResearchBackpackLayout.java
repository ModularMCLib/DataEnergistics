package com.fish_dan_.data_energistics.gui.ldlib2.research;

/** Sizes the native inventory grid in GUI coordinates so rendering and slot hit areas grow together. */
record ResearchBackpackLayout(int width, int height, int slotSize, int padding, int headerHeight,
                              int inventoryTop, int inventoryWidth, int inventoryHeight, int hotbarGap) {

    static ResearchBackpackLayout forViewport(int viewportWidth, int viewportHeight) {
        int availableWidth = Math.max(1, viewportWidth - 16);
        int availableHeight = Math.max(1, viewportHeight - 16);
        int preferredSlot = viewportWidth >= 620 ? 36 : 27;
        int slotSize = Math.max(1, Math.min(preferredSlot, Math.min(availableWidth / 10, availableHeight / 6)));
        int padding = Math.max(1, slotSize / 3);
        int headerHeight = Math.max(16, slotSize * 2 / 3);
        int hotbarGap = Math.max(2, Math.round(slotSize * 5F / 18F));
        int inventoryWidth = slotSize * 9;
        int inventoryHeight = slotSize * 4 + hotbarGap;
        int inventoryTop = padding + headerHeight + padding / 2;
        return new ResearchBackpackLayout(inventoryWidth + padding * 2, inventoryTop + inventoryHeight + padding,
                slotSize, padding, headerHeight, inventoryTop, inventoryWidth, inventoryHeight, hotbarGap);
    }
}
