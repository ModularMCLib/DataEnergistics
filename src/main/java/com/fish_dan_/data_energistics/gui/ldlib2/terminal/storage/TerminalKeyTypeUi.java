package com.fish_dan_.data_energistics.gui.ldlib2.terminal.storage;

import com.fish_dan_.data_energistics.client.screen.ae2.KeyTypeSelectionLayout;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalActionButton;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalUiLayout;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.util.KeyTypeSelection;
import appeng.client.gui.widgets.AECheckbox;

import dev.vfyjxf.taffy.style.FlexDirection;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import org.jspecify.annotations.NullMarked;

/** LDLib2 resource-type subpage with AE2's independent selections, localized descriptions and capped row viewport. */
@NullMarked
public final class TerminalKeyTypeUi {

    private final UIElement root;
    private final KeyTypeSelection selection;
    private final ScrollerView list = new ScrollerView();
    private final Object2ObjectLinkedOpenHashMap<AEKeyType, TerminalActionButton> rows = new Object2ObjectLinkedOpenHashMap<>();
    private int width;
    private int height;

    public TerminalKeyTypeUi(UIElement root, KeyTypeSelection selection) {
        this.root = root;
        this.selection = selection;
        list.addClass("terminal-key-type-list");
        list.scrollerStyle(style -> style.mode(ScrollerMode.VERTICAL).horizontalScrollDisplay(ScrollDisplay.NEVER)
                .verticalScrollDisplay(ScrollDisplay.AUTO));
        list.viewPort.layout(layout -> layout.paddingAll(0));
        list.viewPort.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        list.viewContainer.layout(layout -> layout.flexDirection(FlexDirection.COLUMN).widthPercent(100).paddingAll(0).gapAll(KeyTypeSelectionLayout.PADDING));
        list.verticalScroller.addClass("terminal-key-type-scroll");
        list.verticalScroller.layout(layout -> layout.width(6).paddingAll(0).gapAll(0));
        list.verticalScroller.headButton.setDisplay(false);
        list.verticalScroller.tailButton.setDisplay(false);
        list.verticalScroller.scrollContainer.layout(layout -> layout.paddingAll(0));
        list.verticalScroller.scrollBar.layout(layout -> layout.width(6).minHeight(15));
        for (AEKeyType type : AEKeyTypes.getAll()) {
            TerminalActionButton row = new TerminalActionButton();
            row.noText();
            row.addClass("terminal-key-type-row");
            row.layout(layout -> layout.widthPercent(100).height(AECheckbox.SIZE).paddingAll(0).flexShrink(0));
            row.style(style -> style.tooltips(type.getDescription()));
            UIElement glyph = TerminalUiLayout.at(new UIElement(), 0, 1, 22, 12);
            glyph.addClass("terminal-key-type-glyph");
            glyph.setAllowHitTest(false);
            Label name = new Label();
            name.setText(type.getDescription());
            name.addClass("terminal-key-type-name");
            name.layout(layout -> layout.widthPercent(100).height(AECheckbox.SIZE).paddingLeft(26).paddingRight(2));
            name.textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(false).textWrap(TextWrap.HOVER_ROLL));
            name.setAllowHitTest(false);
            row.addChildren(glyph, name);
            row.glyph(glyph, 1);
            row.setOnClick(event -> {
                if (event.button != 0) return;
                selection.setEnabled(type, !selection.isEnabled(type));
                refresh();
            });
            rows.put(type, row);
            list.addScrollViewChild(row);
        }
        root.addChild(list);
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
        int nextWidth = Math.min(190, Math.max(1, screenWidth));
        int minimumHeight = KeyTypeSelectionLayout.CONTENT_TOP + KeyTypeSelectionLayout.KEY_TYPE_SPACING + KeyTypeSelectionLayout.BOTTOM_PADDING;
        int maximumHeight = Math.min(screenHeight, Math.max(minimumHeight, screenHeight - KeyTypeSelectionLayout.SCREEN_MARGIN));
        int nextHeight = Math.min(KeyTypeSelectionLayout.contentHeight(rows.size()), Math.max(1, maximumHeight));
        if (width == nextWidth && height == nextHeight) return;
        width = nextWidth;
        height = nextHeight;
        root.layout(layout -> layout.width(width).height(height));
        TerminalUiLayout.at(TerminalUiLayout.require(root, "popup_title", Label.class), 5, 4, Math.max(0, width - 26), 10);
        TerminalUiLayout.at(TerminalUiLayout.require(root, "close", Button.class), Math.max(0, width - 17), 3, 14, 14);
        TerminalUiLayout.at(list, 5, KeyTypeSelectionLayout.CONTENT_TOP, Math.max(0, width - 10),
                Math.max(0, height - KeyTypeSelectionLayout.CONTENT_TOP - KeyTypeSelectionLayout.BOTTOM_PADDING));
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }
}
