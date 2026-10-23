package com.fish_dan_.data_energistics.api.gui.style;

import com.fish_dan_.data_energistics.Data_Energistics;

import com.lowdragmc.lowdraglib2.gui.ui.style.Stylesheet;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;

import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Exact LDLib2 resource lookups preserve pack overrides; packaged sheets also support server-side menus. */
public enum DEStylesheets {

    CRAFTING_PLAN_TREE("crafting_plan_tree"),
    PLAYER_TERMINAL("player_terminal"),
    SHARED_BACKPACK("shared_backpack"),
    TRINITY("trinity_ui"),
    WEAPON_PLATFORM("weapon_platform");

    private final ResourceLocation location;

    DEStylesheets(String name) {
        location = Data_Energistics.id("lss/" + name + ".lss");
    }

    /** Resolve on the calling UI thread; only a missing pack/builtin entry is loaded from the mod JAR. */
    public synchronized Stylesheet get() {
        Stylesheet sheet = StylesheetManager.INSTANCE.getStylesheet(location);
        if (sheet != null) return sheet;
        String path = "/assets/" + location.getNamespace() + "/" + location.getPath();
        try (InputStream stream = DEStylesheets.class.getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing stylesheet: " + path);
            sheet = Stylesheet.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            sheet.setName(location.toString());
            StylesheetManager.INSTANCE.registerBuiltinStylesheet(location, sheet);
            return sheet;
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to load stylesheet " + location, failure);
        }
    }
}
