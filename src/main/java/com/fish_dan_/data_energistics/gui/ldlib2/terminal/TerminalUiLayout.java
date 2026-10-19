package com.fish_dan_.data_energistics.gui.ldlib2.terminal;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.terminal.TerminalViewportLayout;
import com.fish_dan_.data_energistics.common.terminal.player.workstation.TerminalWorkspace;
import com.fish_dan_.data_energistics.gui.ldlib2.patternencoding.upload.PatternUploadUi;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.crafting.TerminalCraftingRecipesUi;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.encoding.TerminalProcessingControlsUi;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.stonecutting.TerminalStonecuttingRecipeCell;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.text.TerminalSearchField;

import com.lowdragmc.lowdraglib2.gui.ui.Style;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
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
    private static final int STORAGE_SCROLL_WIDTH = 10;
    private static final int ACTION_BUTTON_SIZE = 14;

    private TerminalUiLayout() {}

    public static UI create() {
        int width = TerminalViewportLayout.MIN_WIDTH;
        int panelWidth = width - TerminalViewportLayout.TAB_WIDTH - 12;
        int encodingCenter = width - TerminalViewportLayout.TAB_WIDTH - TerminalViewportLayout.ENCODING_WIDTH / 2;
        UIElement root = element("terminal_root", "terminal-root");
        root.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).widthPercent(100).heightPercent(100).paddingAll(0));
        root.addChild(icon("entry", "terminal-entry", "icon-entry", 0, 0, TerminalViewportLayout.ENTRY_SIZE, TerminalViewportLayout.ENTRY_SIZE));
        UIElement window = at(element("window", "terminal-window"), 8, 8, width, 168);
        UIElement panel = at(element("panel", "terminal-panel"), 12, 0, panelWidth, 168);
        panel.addChildren(at(element("top_edge", "frame-light"), 0, 0, panelWidth, 1),
                at(element("left_edge", "frame-light"), 0, 0, 1, 168),
                at(element("bottom_edge", "frame-dark"), 0, 167, panelWidth, 1));
        window.addChild(panel);
        String[] controls = { "tasks", "sort", "direction", "display", "type", "pins", "magnet", "destination" };
        UIElement sidebar = at(element("sidebar", "terminal-sidebar"), 0, -1, 15, 16 + (controls.length - 1) * 12);
        // Backing tiles overlap; the cropped bottom cap closes below the final button at the panel edge.
        for (int index = 0; index < controls.length; index++) {
            boolean bottom = index == controls.length - 1;
            String surface = index == 0 ? "sidebar-top" : bottom ? "sidebar-bottom" : "sidebar-middle";
            UIElement backing = at(element("sidebar_" + index, surface), 0, index * 12 + (bottom ? 1 : 0), bottom ? 13 : 15, 15);
            backing.setAllowHitTest(false);
            sidebar.addChild(backing);
        }
        for (int index = 0; index < controls.length; index++) {
            String id = controls[index];
            sidebar.addChild(icon(id, "terminal-small", "icon-" + id, 3, 3 + index * 12, 10, 10));
        }
        window.addChild(sidebar);
        window.addChildren(at(field("search"), searchLeft(width),
                3, TerminalViewportLayout.OPERATION_WIDTH, 12),
                icon("drag", "terminal-drag", "icon-drag", encodingCenter - ACTION_BUTTON_SIZE / 2, 2, ACTION_BUTTON_SIZE, ACTION_BUTTON_SIZE),
                at(element("storage", "terminal-storage"), 16, 18, 108, 144),
                at(element("pinned_resources", "terminal-pins"), 16, 18, 108, 0),
                at(storageScrollbar(), storageScrollLeft(width), 18, STORAGE_SCROLL_WIDTH, 148),
                at(element("workspaces", "terminal-pages"), width - TerminalViewportLayout.TAB_WIDTH - operationWidth(TerminalWorkspace.CRAFTING) + 1, 108,
                        operationWidth(TerminalWorkspace.CRAFTING), 58));
        // The texture's first two columns belong to the adjoining frame; LSS crops that shared edge.
        UIElement tabs = at(element("tabs", "terminal-tabs"), width - TerminalViewportLayout.TAB_WIDTH, 97, TerminalViewportLayout.TAB_WIDTH, 70);
        for (TerminalWorkspace mode : TerminalWorkspace.values())
            tabs.addChild(icon("tab_" + mode.id(), "terminal-tab", "icon-" + mode.id(), 0, mode.ordinal() * 14, TerminalViewportLayout.TAB_WIDTH, 14));
        window.addChildren(tabs, icon("resize", "terminal-resize", "icon-resize", width - TerminalViewportLayout.TAB_WIDTH - 5, 163, 4, 4));
        root.addChild(window);
        return UI.of(root, ObjectImmutableList.of(stylesheet()));
    }

    public static UIElement page(TerminalWorkspace mode) {
        int width = pageWidth(mode);
        UIElement root = at(element(mode.id(), "terminal-page"), 0, 0, width, 58);
        root.setOverflowVisible(true);
        root.addClass("page-" + mode.id());
        UIElement background = at(element("page_surface", "terminal-page-surface"), -1, 0, width + 2, 58);
        background.setAllowHitTest(false);
        root.addChild(background);
        UIElement inputs = element("inputs", "page-inputs");
        inputs.setOverflowVisible(false);
        UIElement result = element("result", "page-result");
        switch (mode) {
            case CRAFTING -> {
                pageAt(inputs, 2, 1, 54, 54);
                pageAt(result, 83, 19, 18, 18);
                root.addChild(recipeArrow(58));
                root.addChild(TerminalCraftingRecipesUi.create());
            }
            case PROCESSING -> {
                UIElement surface = at(element("processing_surface", "terminal-processing-surface"), 1, 1, 82, 56);
                surface.setAllowHitTest(false);
                root.addChild(surface);
                pageAt(inputs, 9, 1, 54, 54);
                pageAt(result, 64, 19, 18, 18);
            }
            case SMITHING, ANVIL -> {
                pageAt(inputs, 9, 19, 54, 18);
                pageAt(result, 96, 19, 18, 18);
                root.addChild(recipeArrow(69));
            }
            case STONECUTTING -> {
                int recipesWidth = TerminalStonecuttingRecipeCell.COLUMNS * TerminalStonecuttingRecipeCell.WIDTH;
                pageAt(inputs, 2, 19, 18, 18);
                UIElement frame = pageAt(element("recipes_frame", "stonecutting-recipes-frame"), 21, 6, recipesWidth + 5, 46);
                frame.setAllowHitTest(false);
                UIElement recipes = element("recipes", "stonecutting-recipes");
                recipes.setOverflowVisible(false);
                root.addChildren(frame, pageAt(recipes, 23, 6, recipesWidth, TerminalStonecuttingRecipeCell.ROWS * TerminalStonecuttingRecipeCell.HEIGHT),
                        pageAt(recipeScrollbar("recipe_scroll"), 27 + recipesWidth, 6, 6, 46));
            }
        }
        UIElement outputs = element("outputs", "page-outputs");
        outputs.setOverflowVisible(false);
        outputs.setVisible(mode != TerminalWorkspace.STONECUTTING);
        root.addChildren(inputs, pageAt(outputs, 84, 1, 18, 54));
        if (mode == TerminalWorkspace.PROCESSING) pageAt(outputs, 96, 1, 18, 54);
        if (mode != TerminalWorkspace.PROCESSING && mode != TerminalWorkspace.STONECUTTING) root.addChild(result);
        UIElement actions = at(element("page_actions", "terminal-page-actions"), width, 0, TerminalViewportLayout.ENCODING_WIDTH, 58);
        if (mode.supportsEncoding()) {
            actions.addChildren(at(element("blank_pattern", "blank-pattern"), 3, 1, 18, 18),
                    icon("encoding", "terminal-encode", "icon-encoding", 5, 22, ACTION_BUTTON_SIZE, ACTION_BUTTON_SIZE),
                    at(element("encoded", "encoded-pattern"), 3, 37, 18, 18));
            if (mode == TerminalWorkspace.CRAFTING) {
                root.addChildren(pageIcon("clear", "terminal-pattern-option", "icon-clear-pattern", 60, 2, 8, 8),
                        pageIcon("substitutes", "terminal-pattern-option", "icon-substitutes", 70, 2, 8, 8),
                        pageIcon("fluid_substitutes", "terminal-pattern-option", "icon-fluid-substitutes", 80, 2, 8, 8));
            } else if (mode == TerminalWorkspace.SMITHING) {
                root.addChildren(pageIcon("clear", "terminal-pattern-option", "icon-clear-pattern", 9, 8, 8, 8),
                        pageIcon("substitutes", "terminal-pattern-option", "icon-substitutes", 19, 8, 8, 8));
            } else if (mode == TerminalWorkspace.PROCESSING) {
                root.addChildren(pageIcon("clear", "terminal-pattern-option", "icon-clear-pattern", 64, 2, 8, 8),
                        pageIcon("record_type", "terminal-pattern-option", "icon-record-type", 64, 12, 8, 8),
                        pageAt(processingScrollbar(), 2, 2, 6, 54), TerminalProcessingControlsUi.create());
            }
        } else {
            root.addChild(pageAt(field("name"), 9, 4, width - 18, 10));
            actions.addChildren(pageIcon("boost_up", "anvil-boost-step", "icon-boost-up", 5, 14, ACTION_BUTTON_SIZE, ACTION_BUTTON_SIZE),
                    pageIcon("boost_down", "anvil-boost-step", "icon-boost-down", 5, 30, ACTION_BUTTON_SIZE, ACTION_BUTTON_SIZE));
            Label status = pageAt(label("anvil_status"), 9, 42, width - 18, 10);
            status.addClass("anvil-status");
            status.setText(Component.translatable("gui.data_energistics.me_digital_anvil.invalid"));
            status.textStyle(style -> style.textWrap(TextWrap.HOVER_ROLL));
            status.setAllowHitTest(true);
            root.addChild(status);
        }
        root.addChild(actions);
        return root;
    }

    private static UIElement recipeArrow(int x) {
        UIElement arrow = pageAt(element("recipe_arrow", "terminal-recipe-arrow"), x, 19, 22, 18);
        arrow.setAllowHitTest(false);
        return arrow;
    }

    public static UIElement popup(String name) {
        UIElement root = at(element(name + "_popup", "terminal-popup"), 0, 0, popupWidth(name), popupHeight(name));
        Label title = at(label("popup_title"), 7, 5, popupWidth(name) - 31, 10);
        title.addClass("terminal-popup-title");
        root.addChildren(title, icon("close", "terminal-window-handle", "icon-close", popupWidth(name) - 17, 3, 14, 14));
        return root;
    }

    public static int operationWidth(TerminalWorkspace mode) {
        return pageWidth(mode) + TerminalViewportLayout.ENCODING_WIDTH;
    }

    private static int pageWidth(TerminalWorkspace mode) {
        return switch (mode) {
            case CRAFTING, PROCESSING, SMITHING, ANVIL -> TerminalViewportLayout.PROCESSING_WIDTH;
            case STONECUTTING -> TerminalViewportLayout.STONECUTTING_WIDTH;
        };
    }

    public static void resize(UIElement window, TerminalViewportLayout viewport) {
        int panelWidth = viewport.width() - TerminalViewportLayout.TAB_WIDTH - 12;
        int encodingCenter = viewport.width() - TerminalViewportLayout.TAB_WIDTH - TerminalViewportLayout.ENCODING_WIDTH / 2;
        atWithin(require(window, "panel", UIElement.class), 12, 0, panelWidth, viewport.height(), viewport.width(), viewport.height());
        atWithin(require(window, "top_edge", UIElement.class), 0, 0, panelWidth, 1, viewport.width(), viewport.height());
        atWithin(require(window, "left_edge", UIElement.class), 0, 0, 1, viewport.height(), viewport.width(), viewport.height());
        atWithin(require(window, "bottom_edge", UIElement.class), 0, viewport.height() - 1, panelWidth, 1, viewport.width(), viewport.height());
        atWithin(require(window, "search", UIElement.class), searchLeft(viewport.width()),
                3, TerminalViewportLayout.OPERATION_WIDTH, 12, viewport.width(), viewport.height());
        atWithin(require(window, "drag", UIElement.class), encodingCenter - ACTION_BUTTON_SIZE / 2, 2,
                ACTION_BUTTON_SIZE, ACTION_BUTTON_SIZE, viewport.width(), viewport.height());
        atWithin(require(window, "storage", UIElement.class), 16, 18, viewport.storageWidth(), viewport.storageHeight(), viewport.width(), viewport.height());
        atWithin(require(window, "pinned_resources", UIElement.class), 16, 18, viewport.storageWidth(), viewport.pinnedRows() * TerminalViewportLayout.CELL_SIZE, viewport.width(), viewport.height());
        atWithin(require(window, "storage_scroll", UIElement.class), storageScrollLeft(viewport.width()),
                TerminalViewportLayout.STORAGE_TOP + viewport.pinnedRows() * TerminalViewportLayout.CELL_SIZE,
                STORAGE_SCROLL_WIDTH, viewport.scrollHeight(), viewport.width(), viewport.height());
        atWithin(require(window, "storage_rail", UIElement.class), 0, 0, STORAGE_SCROLL_WIDTH, viewport.scrollHeight(), STORAGE_SCROLL_WIDTH, viewport.scrollHeight());
        atWithin(require(window, "storage_rail_rim", UIElement.class), 1, 1, STORAGE_SCROLL_WIDTH - 2, viewport.scrollHeight() - 2, STORAGE_SCROLL_WIDTH, viewport.scrollHeight());
        atWithin(require(window, "storage_rail_center", UIElement.class), 2, 2, STORAGE_SCROLL_WIDTH - 4, viewport.scrollHeight() - 4, STORAGE_SCROLL_WIDTH, viewport.scrollHeight());
        atWithin(require(window, "workspaces", UIElement.class), viewport.operationLeft(), viewport.operationTop(),
                viewport.operationWidth(), TerminalViewportLayout.OPERATION_HEIGHT, viewport.width(), viewport.height());
        atWithin(require(window, "tabs", UIElement.class), viewport.width() - TerminalViewportLayout.TAB_WIDTH, viewport.height() - 71,
                TerminalViewportLayout.TAB_WIDTH, 70, viewport.width(), viewport.height());
        atWithin(require(window, "resize", UIElement.class), viewport.width() - TerminalViewportLayout.TAB_WIDTH - 5, viewport.height() - 5, 4, 4, viewport.width(), viewport.height());
    }

    private static int storageScrollLeft(int width) {
        // Keep the rail centered below the drag handle, independently of the storage column count.
        return width - TerminalViewportLayout.TAB_WIDTH - (TerminalViewportLayout.ENCODING_WIDTH + STORAGE_SCROLL_WIDTH) / 2;
    }

    private static int searchLeft(int width) {
        return width - TerminalViewportLayout.TAB_WIDTH - TerminalViewportLayout.OPERATION_WIDTH - TerminalViewportLayout.ENCODING_WIDTH;
    }

    public static int popupHeight(String name) {
        return switch (name) {
            case "key_types" -> 160;
            case "magnet" -> 122;
            case "filters" -> 166;
            case "quantity" -> 107;
            case "upload" -> PatternUploadUi.MIN_HEIGHT;
            default -> throw new IllegalArgumentException("Unknown terminal popup " + name);
        };
    }

    public static <T extends UIElement> T require(UIElement root, String id, Class<T> type) {
        return root.selectId(id, type).findFirst().orElseThrow(() -> new IllegalStateException("Missing terminal element " + id));
    }

    public static int popupWidth(String name) {
        return switch (name) {
            case "magnet" -> 200;
            case "upload" -> PatternUploadUi.MIN_WIDTH;
            case "quantity", "filters" -> 176;
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
        return at(element, x, y, width, height);
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
        TextField field = named(id.equals("search") ? new TerminalSearchField() : new TextField(), id, "terminal-field");
        if (id.equals("search")) field.addClass("terminal-search-field");
        field.setText("", false);
        return field;
    }

    private static Scroller.Vertical storageScrollbar() {
        Scroller.Vertical scrollbar = named(verticalScrollbar(new Scroller.Vertical()), "storage_scroll", "terminal-storage-scroll");
        scrollbar.layout(layout -> layout.width(STORAGE_SCROLL_WIDTH));
        scrollbar.scrollBar.layout(layout -> layout.left(0).flexShrink(0));
        scrollbar.scrollContainer.addChildAt(scrollbarRail("storage_rail"), 0);
        return scrollbar;
    }

    public static UIElement scrollbarRail(String id) {
        // Match the groove baked into the existing Trinity panels, preserving its pixel-sized caps.
        UIElement rail = railLayer(id, "scroll-rail", 0);
        rail.addChildren(railLayer(id + "_rim", "scroll-rail-rim", 1),
                railLayer(id + "_center", "scroll-rail-center", 2),
                at(element(id + "_shadow", "scroll-rail-shadow"), 2, 2, 2, 2));
        rail.selfAndAllChildren().forEach(element -> {
            element.addClass("scrollbar-decoration");
            element.setAllowHitTest(false);
        });
        return rail;
    }

    private static UIElement railLayer(String id, String style, int inset) {
        UIElement layer = element(id, style);
        layer.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(inset).top(inset).right(inset).bottom(inset));
        return layer;
    }

    private static Scroller.Vertical processingScrollbar() {
        Scroller.Vertical scrollbar = named(new Scroller.Vertical(), "pattern_scroll", "terminal-pattern-scroll");
        silentScrollbar(scrollbar);
        scrollbar.headButton.setDisplay(false);
        scrollbar.tailButton.setDisplay(false);
        scrollbar.layout(layout -> layout.gapRow(0).gapColumn(0));
        scrollbar.scrollContainer.layout(layout -> layout.paddingAll(0));
        scrollbar.scrollBar.layout(layout -> layout.width(6).height(15).minHeight(15).maxHeight(15).paddingAll(0));
        return scrollbar;
    }

    public static Scroller.Vertical recipeScrollbar(String id) {
        Scroller.Vertical scrollbar = named(processingScrollbar(), id, "terminal-recipe-scroll");
        scrollbar.scrollContainer.addChildAt(scrollbarRail(id + "_rail"), 0);
        return scrollbar;
    }

    /** Keeps the supplied 10-by-15 thumb at native size, centered on the six-pixel rail. */
    public static <T extends Scroller> T verticalScrollbar(T scrollbar) {
        silentScrollbar(scrollbar);
        scrollbar.addClass("terminal-scrollbar");
        scrollbar.headButton.setDisplay(false);
        scrollbar.tailButton.setDisplay(false);
        scrollbar.setOverflowVisible(true);
        scrollbar.layout(layout -> layout.width(6).paddingAll(0).gapAll(0));
        scrollbar.scrollContainer.setOverflowVisible(true);
        scrollbar.scrollContainer.layout(layout -> layout.paddingAll(0));
        scrollbar.scrollBar.layout(layout -> layout.left(-2).width(10).minWidth(10).maxWidth(10)
                .height(15).minHeight(15).maxHeight(15).paddingAll(0));
        scrollbar.scrollContainer.addEventListener(UIEvents.LAYOUT_CHANGED, event -> {
            float railHeight = scrollbar.scrollContainer.getContentHeight();
            if (railHeight > 0)
                Style.importantPipeline(scrollbar.getScrollerStyle(), style -> style.scrollBarSize(1500F / railHeight));
        });
        return scrollbar;
    }

    /** Configure a newly constructed scroller once: AE scroll thumbs do not play button sounds. */
    public static void silentScrollbar(Scroller scrollbar) {
        // LDLib2 prepends listeners: the original Button press handler follows the scroller's drag handler.
        var thumb = scrollbar.scrollBar;
        thumb.removeEventListener(UIEvents.MOUSE_DOWN, thumb.getBubbleListeners(UIEvents.MOUSE_DOWN).getLast());
    }

    private static Button control(String id, int x, int y, int width, int height) {
        Button button = at(named(new TerminalActionButton(), id, "terminal-control"), x, y, width, height);
        button.textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(false));
        button.text.layout(layout -> layout.widthPercent(100).heightPercent(100).marginAll(0));
        return button;
    }

    public static Button smallButton(String id, Component text, int x, int y) {
        Button button = control(id, x, y, 8, 8);
        button.addClass("terminal-processing-step");
        button.setText(text);
        ((TerminalActionButton) button).glyph(button.text, 0);
        return button;
    }

    public static Button icon(String id, String style, String glyph, int x, int y, int width, int height) {
        Button button = control(id, x, y, width, height);
        button.noText().addClass(style);
        button.addClass("terminal-icon");
        int glyphSize = switch (glyph) {
            case "icon-filter-list" -> 16;
            case "icon-entry", "icon-drag", "icon-boost-up", "icon-boost-down", "icon-encoding" -> 10;
            default -> style.equals("terminal-tab") ? 9 : Math.min(8, Math.min(width, height));
        };
        int glyphHeight = glyph.equals("icon-close") ? 9 : glyphSize;
        int glyphTop = (height - glyphHeight) / 2;
        UIElement icon = at(element(id + "_icon", "terminal-glyph"), (width - glyphSize) / 2, glyphTop, glyphSize, glyphHeight);
        icon.addClass(glyph);
        icon.setAllowHitTest(false);
        button.addChild(icon);
        ((TerminalActionButton) button).glyph(icon, glyphTop);
        return button;
    }

    /** AE-style checkbox row: the label is separate from the fixed-size switch glyph. */
    public static TerminalActionButton toggle(String id, Component text, int x, int y, int width) {
        TerminalActionButton row = at(named(new TerminalActionButton(), id, "terminal-toggle"), x, y, width, 14);
        row.noText();
        UIElement glyph = at(element(id + "_icon", "terminal-toggle-glyph"), 0, 0, 22, 12);
        glyph.setAllowHitTest(false);
        Label name = at(label(id + "_label"), 0, 0, width, 14);
        name.layout(layout -> layout.widthPercent(100).paddingLeft(26).paddingRight(2));
        name.addClass("terminal-toggle-name");
        name.setText(text);
        name.textStyle(style -> style.textWrap(TextWrap.HOVER_ROLL));
        row.addChildren(glyph, name);
        return row;
    }

    public static Stylesheet stylesheet() {
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
