package com.fish_dan_.data_energistics.api.gui.style;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;

/** Semantic classes for the extracted Trinity backpack; DEStylesheets owns its resource loading. */
public final class SharedBackpackStyles {

    private SharedBackpackStyles() {}

    /** Applies semantic classes only; textures, dimensions and hover rules remain in the registered LSS. */
    public static void inventory(InventorySlots inventory) {
        inventory.addClass("shared-backpack");
        for (int row = 0; row < inventory.rows.length; row++) {
            for (int column = 0; column < inventory.rows[row].slots.length; column++) {
                String vertical = row == 0 ? "top" : row == 2 ? "bottom" : "middle";
                String horizontal = column == 0 ? "left" : column == 8 ? "right" : "center";
                cell(inventory.rows[row].slots[column], vertical + "-" + horizontal);
            }
        }
        for (int column = 0; column < inventory.hotbar.slots.length; column++) {
            cell(inventory.hotbar.slots[column], "hotbar-" + (column == 0 ? "left" : column == 8 ? "right" : "center"));
        }
    }

    public static void standalone(ItemSlot slot) {
        cell(slot, "standalone");
    }

    /** Uses Trinity's original root surface for a floating inventory, without installing its unrelated UI theme. */
    public static void window(UIElement window, Label title) {
        window.addClass("shared-backpack-window");
        title.addClass("shared-backpack-title");
        // Release caller theme overrides so the registered sheet supplies the actual shared appearance.
        title.getTextStyle().setInline(PropertyRegistry.TEXT_COLOR, null);
        title.getTextStyle().setInline(PropertyRegistry.TEXT_SHADOW, null);
        title.getTextStyle().setInline(PropertyRegistry.ADAPTIVE_WIDTH, null);
        title.getTextStyle().setInline(PropertyRegistry.ADAPTIVE_HEIGHT, null);
    }

    private static void cell(ItemSlot slot, String position) {
        slot.addClass("shared-backpack-slot");
        slot.addClass("shared-backpack-" + position);
    }
}
