package com.fish_dan_.data_energistics.gui.ldlib2.research;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;

import net.minecraft.network.chat.Component;

import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyDisplay;
import dev.vfyjxf.taffy.style.TaffyPosition;

/** Shared geometry and text treatment for the research station's compact panels. */
final class ResearchUiParts {

    static final int SURFACE = 0xFFADB0C4;
    static final String PREFIX = "gui.data_energistics.research.";

    private ResearchUiParts() {}

    static Component text(String key, Object... arguments) {
        return Component.translatable(PREFIX + key, arguments);
    }

    static <T extends UIElement> T at(T element, float x, float y, float width, float height) {
        element.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
                .left(x).top(y).width(width).height(height));
        return element;
    }

    static UIElement panel(float x, float y, float width, float height) {
        UIElement panel = at(new UIElement(), x, y, width, height);
        panel.style(style -> style.backgroundTexture(new ColorRectTexture(SURFACE)));
        panel.setAllowHitTest(false);
        return panel;
    }

    static Label label(Component text) {
        Label label = new Label();
        label.setText(text);
        label.textStyle(style -> style.fontSize(9).textColor(0xFF202331).textShadow(false)
                .textWrap(TextWrap.WRAP).adaptiveWidth(false).adaptiveHeight(true));
        label.layout(layout -> layout.widthPercent(100).minHeight(12).flexShrink(0));
        label.setAllowHitTest(false);
        return label;
    }

    static Button button(Component text, Runnable action) {
        Button button = new Button();
        button.text.setText(text);
        button.text.textStyle(style -> style.fontSize(9).textColor(0xFF202331).textShadow(false));
        button.layout(layout -> layout.height(16).minHeight(16).flexShrink(0));
        button.setOnClick(event -> action.run());
        return button;
    }

    static ScrollerView scroll(float x, float y, float width, float height) {
        ScrollerView scroll = at(new ScrollerView(), x, y, width, height);
        scroll.viewContainer(view -> view.layout(layout -> layout.flexDirection(FlexDirection.COLUMN)
                .gapAll(3).paddingAll(3)));
        return scroll;
    }

    static void show(UIElement element, boolean visible) {
        element.setVisible(visible);
        element.setActive(visible);
        element.setAllowHitTest(visible);
        element.layout(layout -> layout.display(visible ? TaffyDisplay.FLEX : TaffyDisplay.NONE));
    }
}
