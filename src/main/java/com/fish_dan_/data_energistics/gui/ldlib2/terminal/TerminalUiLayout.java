package com.fish_dan_.data_energistics.gui.ldlib2.terminal;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.terminal.TerminalViewportLayout;
import com.fish_dan_.data_energistics.common.terminal.player.workstation.TerminalWorkspace;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.style.Stylesheet;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import dev.vfyjxf.taffy.style.TaffyPosition;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Fixed pixel structure from the terminal design; LSS supplies its surfaces and interaction states. */
@NullMarked
public final class TerminalUiLayout {

    private static final ResourceLocation STYLES = Data_Energistics.id("lss/player_terminal.lss");

    private TerminalUiLayout() {}

    public static UI create() {
        UIElement root = element("terminal_root", "terminal-root");
        root.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).widthPercent(100).heightPercent(100).paddingAll(0));
        root.addChild(icon("entry", "terminal-entry", "icon-entry", 0, 0, 10, 10));
        UIElement window = at(element("window", "terminal-window"), 8, 8, 150, 168);
        UIElement panel = at(element("panel", "terminal-panel"), 12, 0, 126, 168);
        panel.addChildren(at(element("top_edge", "frame-light"), 0, 0, 126, 1),
                at(element("left_edge", "frame-light"), 0, 0, 1, 168),
                at(element("bottom_edge", "frame-dark"), 0, 167, 126, 1));
        window.addChild(panel);
        window.addChildren(at(element("sidebar_top", "sidebar-top"), 0, 0, 15, 15),
                at(element("sidebar_middle", "sidebar-middle"), 0, 15, 15, 70),
                at(element("sidebar_bottom", "sidebar-bottom"), 0, 85, 15, 15));
        String[] controls = { "tasks", "sort", "direction", "display", "type", "network", "magnet", "insert" };
        for (int index = 0; index < controls.length; index++) {
            String id = controls[index];
            window.addChild(icon(id, "terminal-small", "icon-" + id, 3, 3 + index * 12, 10, 10));
        }
        window.addChildren(at(field("search"), 16, 3, 108, 12),
                icon("drag", "terminal-drag", "icon-drag", 125, 3, 10, 10),
                at(element("storage", "terminal-storage"), 16, 18, 108, 144),
                at(storageScrollbar(), 126, 18, 6, 148),
                at(element("workspaces", "terminal-pages"), 30, 108, 108, 58));
        // The texture's first two columns belong to the adjoining frame; LSS crops that shared edge.
        UIElement tabs = at(element("tabs", "terminal-tabs"), 138, 96, TerminalViewportLayout.TAB_WIDTH, 70);
        for (TerminalWorkspace mode : TerminalWorkspace.values())
            tabs.addChild(icon("tab_" + mode.id(), "terminal-tab", "icon-" + mode.id(), 0, mode.ordinal() * 14, TerminalViewportLayout.TAB_WIDTH, 14));
        window.addChildren(tabs, icon("resize", "terminal-resize", "icon-resize", 132, 164, 4, 4));
        root.addChild(window);
        return UI.of(root, ObjectImmutableList.of(stylesheet()));
    }

    public static UIElement page(TerminalWorkspace mode) {
        UIElement root = at(element(mode.id(), "terminal-page"), 0, 0, 108, 58);
        root.addClass("page-" + mode.id());
        UIElement inputs = element("inputs", "page-inputs");
        UIElement result = element("result", "page-result");
        switch (mode) {
            case CRAFTING -> {
                at(inputs, 3, 2, 54, 54);
                at(result, 84, 10, 18, 18);
            }
            case PROCESSING -> {
                at(inputs, 9, 2, 54, 54);
                at(result, 64, 20, 18, 18);
            }
            case SMITHING -> {
                at(inputs, 3, 20, 54, 18);
                at(result, 84, 10, 18, 18);
            }
            case STONECUTTING -> {
                at(inputs, 3, 6, 18, 18);
                at(result, 3, 36, 18, 18);
                root.addChild(at(element("recipes", "stonecutting-recipes"), 25, 7, 72, 36));
            }
            case ANVIL -> {
                at(inputs, 3, 20, 54, 18);
                at(result, 78, 20, 18, 18);
            }
        }
        root.addChildren(inputs, at(element("outputs", "page-outputs"), 84, 2, 18, 54), result);
        root.addChildren(control("recipe_previous", 25, 44, 10, 10),
                at(label("recipe_index"), 36, 44, 49, 10), control("recipe_next", 86, 44, 10, 10));
        if (mode.supportsEncoding()) {
            int modeX = mode == TerminalWorkspace.PROCESSING ? 0 : mode == TerminalWorkspace.STONECUTTING ? 6 : 62;
            root.addChild(icon("encoding", "encoding", "icon-encoding", modeX, mode == TerminalWorkspace.STONECUTTING ? 25 : 23, 10, 10));
            root.addChildren(control("substitutes", 62, 2, 9, 9), control("fluid_substitutes", 73, 2, 9, 9));
            root.addChild(at(element("patterns", "pattern-slots"), 62, 36, 40, 18));
            if (mode == TerminalWorkspace.PROCESSING) {
                at(require(root, "patterns", UIElement.class), 64, 2, 18, 54);
                root.addChild(icon("transfer_processing", "transfer-processing", "icon-transfer", 66, 22, 14, 14));
            } else if (mode == TerminalWorkspace.STONECUTTING) {
                at(require(root, "patterns", UIElement.class), 60, 35, 36, 18);
                at(require(root, "substitutes", UIElement.class), 25, 35, 9, 9);
            }
        } else {
            root.addChild(at(field("name"), 3, 4, 96, 10));
            UIElement boost = at(element("boost_controls", "anvil-boost"), 3, 44, 96, 10);
            boost.addChildren(control("boost_down", 0, 0, 10, 10), at(label("boost"), 11, 0, 74, 10), control("boost_up", 86, 0, 10, 10));
            root.addChild(boost);
        }
        return root;
    }

    public static UIElement popup(String name) {
        UIElement root = at(element(name + "_popup", "terminal-popup"), 0, 0, 190, popupHeight(name));
        root.addChildren(at(label("popup_title"), 5, 4, 166, 10), control("back", 174, 4, 12, 10));
        switch (name) {
            case "network" -> {
                ScrollerView list = at(named(new ScrollerView(), "frequencies", "terminal-list"), 5, 41, 180, 112);
                list.viewPort.layout(layout -> layout.paddingAll(0));
                list.viewPort.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
                list.viewContainer.layout(layout -> layout.widthPercent(100).paddingAll(0));
                root.addChildren(at(label("connection_status"), 5, 19, 180, 20), list);
            }
            case "magnet" -> root.addChildren(control("mode_off", 5, 21, 180, 15), control("mode_inventory", 5, 38, 180, 15),
                    control("mode_network", 5, 55, 180, 15), control("mode_pickup_network", 5, 72, 180, 15),
                    control("restock", 5, 97, 157, 17), icon("filters", "filters", "icon-settings", 167, 97, 18, 17));
            case "filters" -> root.addChildren(control("pickup_whitelist", 5, 20, 162, 12),
                    at(element("pickup", "filter-grid"), 5, 34, 162, 54), control("filter_copy_up", 171, 56, 13, 13),
                    control("filter_swap", 171, 74, 13, 13), control("filter_copy_down", 171, 92, 13, 13),
                    control("insert_whitelist", 5, 107, 162, 12), at(element("insert", "filter-grid"), 5, 122, 162, 54));
            case "quantity" -> root.addChildren(at(element("selected", "quantity-selected"), 7, 23, 18, 18),
                    at(field("amount"), 31, 25, 151, 14), control("minus", 31, 47, 28, 14),
                    control("plus", 62, 47, 28, 14), control("apply_quantity", 97, 47, 85, 14));
        }
        return root;
    }

    public static void resize(UIElement window, TerminalViewportLayout viewport) {
        int panelWidth = viewport.width() - TerminalViewportLayout.TAB_WIDTH - 12;
        at(require(window, "panel", UIElement.class), 12, 0, panelWidth, viewport.height());
        at(require(window, "top_edge", UIElement.class), 0, 0, panelWidth, 1);
        at(require(window, "left_edge", UIElement.class), 0, 0, 1, viewport.height());
        at(require(window, "bottom_edge", UIElement.class), 0, viewport.height() - 1, panelWidth, 1);
        at(require(window, "search", UIElement.class), 16, 3, viewport.width() - 42, 12);
        at(require(window, "drag", UIElement.class), viewport.width() - 25, 3, 10, 10);
        at(require(window, "storage", UIElement.class), 16, 18, viewport.storageWidth(), viewport.storageHeight());
        at(require(window, "storage_scroll", UIElement.class), viewport.width() - 24, 18, 6, viewport.scrollHeight());
        at(require(window, "storage_rail", UIElement.class), 0, 0, 6, viewport.scrollHeight());
        at(require(window, "storage_rail_rim", UIElement.class), 1, 1, 4, viewport.scrollHeight() - 2);
        at(require(window, "storage_rail_center", UIElement.class), 2, 2, 2, viewport.scrollHeight() - 4);
        at(require(window, "workspaces", UIElement.class), viewport.operationLeft(), viewport.operationTop(),
                TerminalViewportLayout.OPERATION_WIDTH, TerminalViewportLayout.OPERATION_HEIGHT);
        at(require(window, "tabs", UIElement.class), viewport.width() - TerminalViewportLayout.TAB_WIDTH, viewport.height() - 72,
                TerminalViewportLayout.TAB_WIDTH, 70);
        at(require(window, "resize", UIElement.class), viewport.width() - 18, viewport.height() - 4, 4, 4);
    }

    public static int popupHeight(String name) {
        return switch (name) {
            case "network" -> 160;
            case "magnet" -> 124;
            case "filters" -> 188;
            case "quantity" -> 88;
            default -> throw new IllegalArgumentException("Unknown terminal popup " + name);
        };
    }

    public static <T extends UIElement> T require(UIElement root, String id, Class<T> type) {
        return root.selectId(id, type).findFirst().orElseThrow(() -> new IllegalStateException("Missing terminal element " + id));
    }

    public static <T extends UIElement> T at(T element, int x, int y, int width, int height) {
        element.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(x).top(y).width(width).height(height).paddingAll(0).flexShrink(0));
        return element;
    }

    private static UIElement element(String id, String style) {
        return named(new UIElement(), id, style);
    }

    private static <T extends UIElement> T named(T element, String id, String style) {
        element.setId(id);
        element.addClass(style);
        return element;
    }

    private static Label label(String id) {
        Label label = named(new Label(), id, "terminal-label");
        label.setText(Component.empty());
        label.textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(false));
        label.setAllowHitTest(false);
        return label;
    }

    private static TextField field(String id) {
        TextField field = named(new TextField(), id, "terminal-field");
        field.setText("", false);
        return field;
    }

    private static Scroller.Vertical storageScrollbar() {
        Scroller.Vertical scrollbar = named(new Scroller.Vertical(), "storage_scroll", "terminal-storage-scroll");
        scrollbar.headButton.setDisplay(false);
        scrollbar.tailButton.setDisplay(false);
        scrollbar.layout(layout -> layout.gapRow(0).gapColumn(0));
        scrollbar.scrollContainer.layout(layout -> layout.paddingAll(0));
        scrollbar.scrollBar.layout(layout -> layout.width(6).height(15).minHeight(15).maxHeight(15).paddingAll(0));
        // Match the groove baked into the existing Trinity panels, preserving its pixel-sized caps.
        UIElement rail = at(element("storage_rail", "scroll-rail"), 0, 0, 6, 148);
        rail.addChildren(at(element("storage_rail_rim", "scroll-rail-rim"), 1, 1, 4, 146),
                at(element("storage_rail_center", "scroll-rail-center"), 2, 2, 2, 144),
                at(element("storage_rail_shadow", "scroll-rail-shadow"), 2, 2, 2, 2));
        rail.selfAndAllChildren().forEach(element -> {
            element.addClass("scrollbar-decoration");
            element.setAllowHitTest(false);
        });
        scrollbar.scrollContainer.addChildAt(rail, 0);
        return scrollbar;
    }

    private static Button control(String id, int x, int y, int width, int height) {
        Button button = at(named(new TerminalActionButton(), id, "terminal-control"), x, y, width, height);
        button.textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(false));
        button.text.layout(layout -> layout.widthPercent(100).heightPercent(100).marginAll(0));
        return button;
    }

    private static Button icon(String id, String style, String glyph, int x, int y, int width, int height) {
        Button button = control(id, x, y, width, height);
        button.noText().addClass(style);
        button.addClass("terminal-icon");
        int glyphSize = style.equals("terminal-tab") ? 9 : Math.min(10, Math.min(width, height));
        UIElement icon = at(element(id + "_icon", "terminal-glyph"), (width - glyphSize) / 2, (height - glyphSize) / 2, glyphSize, glyphSize);
        icon.addClass(glyph);
        icon.setAllowHitTest(false);
        button.addChild(icon);
        return button;
    }

    private static Stylesheet stylesheet() {
        Stylesheet stylesheet = StylesheetManager.INSTANCE.getStylesheet(STYLES);
        if (stylesheet != null) return stylesheet;
        String path = "/assets/data_energistics/" + STYLES.getPath();
        try (InputStream input = TerminalUiLayout.class.getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing " + path);
            Stylesheet loaded = Stylesheet.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            loaded.setName(STYLES.toString());
            StylesheetManager.INSTANCE.registerBuiltinStylesheet(STYLES, loaded);
            return loaded;
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load terminal stylesheet " + path, failure);
        }
    }
}
