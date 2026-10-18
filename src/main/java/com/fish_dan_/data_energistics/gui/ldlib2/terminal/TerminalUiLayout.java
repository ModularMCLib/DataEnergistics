package com.fish_dan_.data_energistics.gui.ldlib2.terminal;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.terminal.TerminalViewportLayout;
import com.fish_dan_.data_energistics.common.terminal.player.workstation.TerminalWorkspace;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
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
                at(element("sidebar_middle_0", "sidebar-middle"), 0, 15, 15, 15),
                at(element("sidebar_middle_1", "sidebar-middle"), 0, 30, 15, 15),
                at(element("sidebar_middle_2", "sidebar-middle"), 0, 45, 15, 15),
                at(element("sidebar_middle_3", "sidebar-middle"), 0, 60, 15, 15),
                at(element("sidebar_middle_4", "sidebar-middle"), 0, 75, 15, 15),
                at(element("sidebar_bottom", "sidebar-bottom"), 0, 90, 15, 15));
        String[] controls = { "tasks", "sort", "direction", "display", "type", "pins", "magnet", "insert" };
        for (int index = 0; index < controls.length; index++) {
            String id = controls[index];
            window.addChild(icon(id, "terminal-small", "icon-" + id, 3, 3 + index * 12, 10, 10));
        }
        window.addChildren(at(field("search"), 16, 3, 108, 12),
                icon("drag", "terminal-drag", "icon-drag", 125, 3, 10, 10),
                at(element("storage", "terminal-storage"), 16, 18, 108, 144),
                at(element("pinned_resources", "terminal-pins"), 16, 18, 108, 0),
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
        inputs.setOverflowVisible(false);
        UIElement result = element("result", "page-result");
        switch (mode) {
            case CRAFTING -> {
                pageAt(inputs, 3, 2, 54, 54);
                pageAt(result, 84, 10, 18, 18);
            }
            case PROCESSING -> {
                pageAt(inputs, 9, 2, 54, 54);
                pageAt(result, 64, 20, 18, 18);
            }
            case SMITHING -> {
                pageAt(inputs, 3, 20, 54, 18);
                pageAt(result, 84, 10, 18, 18);
            }
            case STONECUTTING -> {
                pageAt(inputs, 3, 6, 18, 18);
                UIElement recipes = element("recipes", "stonecutting-recipes");
                recipes.setOverflowVisible(false);
                root.addChildren(pageAt(recipes, 25, 6, 72, 48), pageAt(stonecuttingScrollbar(), 99, 6, 6, 48));
            }
            case ANVIL -> {
                pageAt(inputs, 3, 20, 54, 18);
                pageAt(result, 78, 20, 18, 18);
            }
        }
        UIElement outputs = element("outputs", "page-outputs");
        outputs.setOverflowVisible(false);
        outputs.setVisible(mode != TerminalWorkspace.STONECUTTING);
        root.addChildren(inputs, pageAt(outputs, 84, 2, 18, 54));
        if (mode != TerminalWorkspace.PROCESSING && mode != TerminalWorkspace.STONECUTTING) root.addChild(result);
        if (mode.supportsEncoding()) {
            int modeX = mode == TerminalWorkspace.STONECUTTING ? 6 : 67;
            root.addChild(pageIcon("encoding", "encoding", "icon-encoding", modeX, 25, 8, 8));
            if (mode == TerminalWorkspace.CRAFTING) {
                root.addChildren(pageIcon("substitutes", "terminal-small", "icon-substitutes", 62, 2, 8, 8),
                        pageIcon("fluid_substitutes", "terminal-small", "icon-fluid-substitutes", 72, 2, 8, 8));
            } else if (mode == TerminalWorkspace.SMITHING) {
                root.addChild(pageIcon("substitutes", "terminal-small", "icon-substitutes", 62, 2, 8, 8));
            } else if (mode == TerminalWorkspace.PROCESSING) {
                root.addChildren(pageIcon("encoding_info", "terminal-small", "icon-encoding-info", 62, 2, 8, 8),
                        pageIcon("transfer_info", "terminal-small", "icon-transfer-info", 72, 2, 8, 8),
                        pageIcon("transfer_processing", "transfer-processing", "icon-transfer", 74, 14, 8, 8),
                        pageAt(processingScrollbar(), 1, 2, 6, 54));
            }
            root.addChild(pageAt(element("encoded", "encoded-pattern"), 84, 36, 18, 18));
            if (mode == TerminalWorkspace.PROCESSING) {
                pageAt(require(root, "encoded", UIElement.class), 64, 38, 18, 18);
            } else if (mode == TerminalWorkspace.STONECUTTING) {
                pageAt(require(root, "encoded", UIElement.class), 3, 36, 18, 18);
            }
        } else {
            root.addChild(pageAt(field("name"), 3, 4, 96, 10));
            UIElement boost = pageAt(element("boost_controls", "anvil-boost"), 3, 44, 96, 10);
            boost.addChildren(pageAt(control("boost_down", 0, 0, 10, 10), 0, 0, 10, 10),
                    pageAt(label("boost"), 11, 0, 74, 10), pageAt(control("boost_up", 86, 0, 10, 10), 86, 0, 10, 10));
            root.addChild(boost);
        }
        return root;
    }

    public static UIElement popup(String name) {
        UIElement root = at(element(name + "_popup", "terminal-popup"), 0, 0, popupWidth(name), popupHeight(name));
        root.addChildren(at(label("popup_title"), 5, 4, popupWidth(name) - 29, 10), control("back", popupWidth(name) - 16, 4, 12, 10));
        switch (name) {
            case "encoding_info" -> root.addChildren(icon("record_type", "terminal-small", "icon-record-type", 5, 21, 10, 10),
                    at(label("record_type_text"), 20, 21, 163, 10), at(label("encoding_details"), 5, 40, 180, 48));
            case "transfer_info" -> root.addChild(at(label("transfer_details"), 5, 21, 180, 88));
        }
        return root;
    }

    public static void resize(UIElement window, TerminalViewportLayout viewport) {
        int panelWidth = viewport.width() - TerminalViewportLayout.TAB_WIDTH - 12;
        atWithin(require(window, "panel", UIElement.class), 12, 0, panelWidth, viewport.height(), viewport.width(), viewport.height());
        atWithin(require(window, "top_edge", UIElement.class), 0, 0, panelWidth, 1, viewport.width(), viewport.height());
        atWithin(require(window, "left_edge", UIElement.class), 0, 0, 1, viewport.height(), viewport.width(), viewport.height());
        atWithin(require(window, "bottom_edge", UIElement.class), 0, viewport.height() - 1, panelWidth, 1, viewport.width(), viewport.height());
        atWithin(require(window, "search", UIElement.class), 16, 3, viewport.width() - 42, 12, viewport.width(), viewport.height());
        atWithin(require(window, "drag", UIElement.class), viewport.width() - 25, 3, 10, 10, viewport.width(), viewport.height());
        atWithin(require(window, "storage", UIElement.class), 16, 18, viewport.storageWidth(), viewport.storageHeight(), viewport.width(), viewport.height());
        atWithin(require(window, "pinned_resources", UIElement.class), 16, 18, viewport.storageWidth(), viewport.pinnedRows() * TerminalViewportLayout.CELL_SIZE, viewport.width(), viewport.height());
        atWithin(require(window, "storage_scroll", UIElement.class), viewport.width() - 24, 18 + viewport.pinnedRows() * TerminalViewportLayout.CELL_SIZE,
                6, viewport.scrollHeight(), viewport.width(), viewport.height());
        atWithin(require(window, "storage_rail", UIElement.class), 0, 0, 6, viewport.scrollHeight(), 6, viewport.scrollHeight());
        atWithin(require(window, "storage_rail_rim", UIElement.class), 1, 1, 4, viewport.scrollHeight() - 2, 6, viewport.scrollHeight());
        atWithin(require(window, "storage_rail_center", UIElement.class), 2, 2, 2, viewport.scrollHeight() - 4, 6, viewport.scrollHeight());
        atWithin(require(window, "workspaces", UIElement.class), viewport.operationLeft(), viewport.operationTop(),
                TerminalViewportLayout.OPERATION_WIDTH, TerminalViewportLayout.OPERATION_HEIGHT, viewport.width(), viewport.height());
        atWithin(require(window, "tabs", UIElement.class), viewport.width() - TerminalViewportLayout.TAB_WIDTH, viewport.height() - 72,
                TerminalViewportLayout.TAB_WIDTH, 70, viewport.width(), viewport.height());
        atWithin(require(window, "resize", UIElement.class), viewport.width() - 18, viewport.height() - 4, 4, 4, viewport.width(), viewport.height());
    }

    public static int popupHeight(String name) {
        return switch (name) {
            case "key_types" -> 160;
            case "magnet" -> 98;
            case "filters" -> 166;
            case "quantity" -> 107;
            case "encoding_info" -> 96;
            case "transfer_info" -> 116;
            case "upload" -> 188;
            default -> throw new IllegalArgumentException("Unknown terminal popup " + name);
        };
    }

    public static <T extends UIElement> T require(UIElement root, String id, Class<T> type) {
        return root.selectId(id, type).findFirst().orElseThrow(() -> new IllegalStateException("Missing terminal element " + id));
    }

    public static int popupWidth(String name) {
        return switch (name) {
            case "quantity", "magnet", "filters" -> 176;
            default -> 190;
        };
    }

    public static <T extends UIElement> T at(T element, int x, int y, int width, int height) {
        element.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(x).top(y).width(width).height(height).paddingAll(0).flexShrink(0));
        return element;
    }

    private static <T extends UIElement> T atWithin(T element, int x, int y, int width, int height, int parentWidth, int parentHeight) {
        element.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
                .leftPercent(parentWidth == 0 ? 0 : x * 100F / parentWidth)
                .topPercent(parentHeight == 0 ? 0 : y * 100F / parentHeight)
                .width(width).height(height).paddingAll(0).flexShrink(0));
        return element;
    }

    private static <T extends UIElement> T pageAt(T element, int x, int y, int width, int height) {
        element.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).leftPercent(x * 100F / 108)
                .topPercent(y * 100F / 58).width(width).height(height).paddingAll(0).flexShrink(0));
        return element;
    }

    private static Button pageIcon(String id, String style, String glyph, int x, int y, int width, int height) {
        return pageAt(icon(id, style, glyph, 0, 0, width, height), x, y, width, height);
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

    private static Scroller.Vertical processingScrollbar() {
        Scroller.Vertical scrollbar = named(new Scroller.Vertical(), "pattern_scroll", "terminal-pattern-scroll");
        scrollbar.headButton.setDisplay(false);
        scrollbar.tailButton.setDisplay(false);
        scrollbar.layout(layout -> layout.gapRow(0).gapColumn(0));
        scrollbar.scrollContainer.layout(layout -> layout.paddingAll(0));
        scrollbar.scrollBar.layout(layout -> layout.width(6).height(15).minHeight(15).maxHeight(15).paddingAll(0));
        UIElement rail = at(element("pattern_rail", "scroll-rail"), 0, 0, 6, 54);
        rail.addChildren(at(element("pattern_rail_rim", "scroll-rail-rim"), 1, 1, 4, 52),
                at(element("pattern_rail_center", "scroll-rail-center"), 2, 2, 2, 50));
        rail.selfAndAllChildren().forEach(element -> {
            element.addClass("scrollbar-decoration");
            element.setAllowHitTest(false);
        });
        scrollbar.scrollContainer.addChildAt(rail, 0);
        return scrollbar;
    }

    private static Scroller.Vertical stonecuttingScrollbar() {
        Scroller.Vertical scrollbar = named(new Scroller.Vertical(), "recipe_scroll", "terminal-stonecutting-scroll");
        scrollbar.headButton.setDisplay(false);
        scrollbar.tailButton.setDisplay(false);
        scrollbar.layout(layout -> layout.gapRow(0).gapColumn(0));
        scrollbar.scrollContainer.layout(layout -> layout.paddingAll(0));
        scrollbar.scrollBar.layout(layout -> layout.width(6).height(15).minHeight(15).maxHeight(15).paddingAll(0));
        return scrollbar;
    }

    private static Button control(String id, int x, int y, int width, int height) {
        Button button = at(named(new TerminalActionButton(), id, "terminal-control"), x, y, width, height);
        button.textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(false));
        button.text.layout(layout -> layout.widthPercent(100).heightPercent(100).marginAll(0));
        return button;
    }

    public static Button icon(String id, String style, String glyph, int x, int y, int width, int height) {
        Button button = control(id, x, y, width, height);
        button.noText().addClass(style);
        button.addClass("terminal-icon");
        int glyphSize = switch (glyph) {
            case "icon-transfer-info", "icon-record-type" -> 8;
            case "icon-filter-list" -> 16;
            default -> style.equals("terminal-tab") ? 9 : Math.min(8, Math.min(width, height));
        };
        UIElement icon = at(element(id + "_icon", "terminal-glyph"), (width - glyphSize) / 2, (height - glyphSize) / 2, glyphSize, glyphSize);
        icon.addClass(glyph);
        icon.setAllowHitTest(false);
        button.addChild(icon);
        ((TerminalActionButton) button).glyph(icon, (height - glyphSize) / 2);
        return button;
    }

    /** AE-style checkbox row: the label is separate from the fixed-size switch glyph. */
    public static TerminalActionButton toggle(String id, Component text, int x, int y, int width) {
        TerminalActionButton row = at(named(new TerminalActionButton(), id, "terminal-toggle"), x, y, width, 14);
        row.noText();
        UIElement glyph = at(element(id + "_icon", "terminal-toggle-glyph"), 0, 1, 22, 12);
        glyph.setAllowHitTest(false);
        Label name = at(label(id + "_label"), 26, 0, Math.max(0, width - 26), 14);
        name.addClass("terminal-toggle-name");
        name.setText(text);
        name.textStyle(style -> style.textWrap(TextWrap.HOVER_ROLL));
        row.addChildren(glyph, name);
        row.glyph(glyph, 1);
        return row;
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
