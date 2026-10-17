package com.fish_dan_.data_energistics.gui.ldlib2.research;

/** Fixed GUI-space inventory geometry; resizing the screen only changes the floating window's position. */
record ResearchBackpackLayout(int width, int height, int slotSize, int padding, int headerHeight,
                              int inventoryTop, int inventoryWidth, int inventoryHeight, int hotbarGap) {

    static ResearchBackpackLayout fixed() {
        int slotSize = 27;
        int padding = slotSize / 3;
        int headerHeight = 12;
        int hotbarGap = Math.round(slotSize * 5F / 18F);
        int inventoryWidth = slotSize * 9;
        int inventoryHeight = slotSize * 4 + hotbarGap;
        int inventoryTop = padding / 2 + headerHeight + 2;
        return new ResearchBackpackLayout(inventoryWidth + padding * 2, inventoryTop + inventoryHeight + padding,
                slotSize, padding, headerHeight, inventoryTop, inventoryWidth, inventoryHeight, hotbarGap);
    }
}
