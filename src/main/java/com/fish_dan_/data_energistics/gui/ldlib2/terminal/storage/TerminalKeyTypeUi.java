package com.fish_dan_.data_energistics.gui.ldlib2.terminal.storage;

import com.fish_dan_.data_energistics.client.screen.ae2.KeyTypeSelectionLayout;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalActionButton;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalUiLayout;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.text.TerminalTooltipLines;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.util.KeyTypeSelection;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import org.jspecify.annotations.NullMarked;

/** LDLib2 resource-type subpage with AE2's independent selections, localized descriptions and capped row viewport. */
@NullMarked
public final class TerminalKeyTypeUi {

    private static final int MAX_VISIBLE_ROWS = 6;
    private static final int THUMB_HEIGHT = 15;
    private final UIElement root;
    private final KeyTypeSelection selection;
    private final UIElement viewport = new UIElement();
    private final UIElement content = new UIElement();
    private final Scroller.Vertical scrollbar = TerminalUiLayout.verticalScrollbar(new Scroller.Vertical());
    private final Object2ObjectLinkedOpenHashMap<AEKeyType, TerminalActionButton> rows = new Object2ObjectLinkedOpenHashMap<>();
    private int width;
    private int height;
    private int maximumScroll;

    public TerminalKeyTypeUi(UIElement root, KeyTypeSelection selection) {
        this.root = root;
        this.selection = selection;
        viewport.setOverflowVisible(false);
        viewport.addChild(content);
        viewport.addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (maximumScroll > 0) {
                float step = (float) KeyTypeSelectionLayout.KEY_TYPE_SPACING / maximumScroll;
                scrollbar.setNormalizedValue(scrollbar.getNormalizedValue() + (event.deltaY > 0 ? -step : step));
            }
            event.stopPropagation();
        });
        scrollbar.addClass("terminal-key-type-scroll");
        scrollbar.setRange(0, 1);
        scrollbar.scrollContainer.addChildAt(TerminalUiLayout.scrollbarRail("key_type_rail"), 0);
        scrollbar.setOnValueChanged(value -> content.layout(layout -> layout.top(-Math.round(value * maximumScroll))));
        for (AEKeyType type : AEKeyTypes.getAll()) {
            TerminalActionButton row = TerminalUiLayout.toggle("key_type_" + rows.size(), type.getDescription(),
                    0, rows.size() * KeyTypeSelectionLayout.KEY_TYPE_SPACING, 174);
            row.layout(layout -> layout.widthPercent(100));
            row.style(style -> style.tooltips(TerminalTooltipLines.split(type.getDescription())));
            row.setOnClick(event -> {
                if (event.button != 0) return;
                selection.setEnabled(type, !selection.isEnabled(type));
                refresh();
            });
            rows.put(type, row);
            content.addChild(row);
        }
        root.addChildren(viewport, scrollbar);
        refresh();
    }

    private void refresh() {
        int enabled = selection.enabledSet().size();
        for (var entry : rows.object2ObjectEntrySet()) {
            boolean selected = selection.isEnabled(entry.getKey());
            TerminalActionButton row = entry.getValue();
            if (selected) row.addClass("selected");
            else row.removeClass("selected");
            row.setActive(!selected || enabled > 1);
        }
    }

    public void resize(int screenWidth, int screenHeight) {
        int nextWidth = Math.clamp(screenWidth, 1, 190);
        int available = screenHeight - KeyTypeSelectionLayout.SCREEN_MARGIN - KeyTypeSelectionLayout.CONTENT_TOP - KeyTypeSelectionLayout.BOTTOM_PADDING;
        int visibleRows = Math.min(rows.size(), Math.clamp((available + KeyTypeSelectionLayout.PADDING) / KeyTypeSelectionLayout.KEY_TYPE_SPACING, 1, MAX_VISIBLE_ROWS));
        int viewportHeight = visibleRows * KeyTypeSelectionLayout.KEY_TYPE_SPACING - KeyTypeSelectionLayout.PADDING;
        int nextHeight = Math.clamp(KeyTypeSelectionLayout.CONTENT_TOP + viewportHeight + KeyTypeSelectionLayout.BOTTOM_PADDING, 1, Math.max(1, screenHeight));
        if (width == nextWidth && height == nextHeight) return;
        width = nextWidth;
        height = nextHeight;
        root.layout(layout -> layout.width(width).height(height));
        TerminalUiLayout.at(TerminalUiLayout.require(root, "popup_title", Label.class), 7, 5, Math.max(0, width - 31), 10);
        TerminalUiLayout.at(TerminalUiLayout.require(root, "close", Button.class), Math.max(0, width - 17), 3, 14, 14);
        int listHeight = Math.max(0, height - KeyTypeSelectionLayout.CONTENT_TOP - KeyTypeSelectionLayout.BOTTOM_PADDING);
        int contentHeight = Math.max(0, rows.size() * KeyTypeSelectionLayout.KEY_TYPE_SPACING - KeyTypeSelectionLayout.PADDING);
        maximumScroll = Math.max(0, contentHeight - listHeight);
        boolean scrollable = maximumScroll > 0;
        int listWidth = Math.max(0, width - (scrollable ? 18 : 10));
        TerminalUiLayout.at(viewport, 5, KeyTypeSelectionLayout.CONTENT_TOP, listWidth, listHeight);
        TerminalUiLayout.at(content, 0, 0, listWidth, contentHeight);
        TerminalUiLayout.at(scrollbar, Math.max(0, width - 11), KeyTypeSelectionLayout.CONTENT_TOP, 6, listHeight);
        scrollbar.setDisplay(scrollable);
        // ScrollerView rewrites the thumb as a content fraction. This independent scroller keeps the 10x15 sprite
        // native.
        scrollbar.setScrollBarSize(Math.min(100, 100F * THUMB_HEIGHT / Math.max(1, listHeight)));
        scrollbar.scrollerStyle(style -> style.scrollDelta(scrollable ? (float) KeyTypeSelectionLayout.KEY_TYPE_SPACING / maximumScroll : 1));
        float value = scrollable ? scrollbar.getNormalizedValue() : 0;
        scrollbar.setNormalizedValue(value, false);
        content.layout(layout -> layout.top(-Math.round(value * maximumScroll)));
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }
}
