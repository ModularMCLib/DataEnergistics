package com.fish_dan_.data_energistics.api.gui.style;

import com.fish_dan_.data_energistics.Data_Energistics;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;
import com.lowdragmc.lowdraglib2.gui.ui.style.Stylesheet;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;

import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Registers the extracted Trinity backpack sheet on both sides; packs may override the same resource. */
public final class SharedBackpackStyles {

    public static final ResourceLocation LOCATION = Data_Energistics.id("lss/shared_backpack.lss");

    private SharedBackpackStyles() {}

    /** Loads the registered stylesheet, using its packaged LSS as the dedicated-server fallback. */
    public static synchronized Stylesheet stylesheet() {
        if (!StylesheetManager.INSTANCE.getAllBuiltinStylesheets().contains(LOCATION)) {
            String path = "/assets/" + LOCATION.getNamespace() + "/" + LOCATION.getPath();
            try (InputStream stream = SharedBackpackStyles.class.getResourceAsStream(path)) {
                if (stream == null) throw new IOException("Missing shared backpack stylesheet: " + path);
                Stylesheet sheet = Stylesheet.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                sheet.setName(LOCATION.toString());
                StylesheetManager.INSTANCE.registerBuiltinStylesheet(LOCATION, sheet);
            } catch (IOException failure) {
                throw new IllegalStateException("Unable to register shared backpack stylesheet " + LOCATION, failure);
            }
        }
        Stylesheet sheet = StylesheetManager.INSTANCE.getStylesheet(LOCATION);
        if (sheet == null) throw new IllegalStateException("Registered backpack stylesheet is unavailable: " + LOCATION);
        return sheet;
    }

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
