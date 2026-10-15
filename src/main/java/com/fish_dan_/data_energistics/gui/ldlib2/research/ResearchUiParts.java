package com.fish_dan_.data_energistics.gui.ldlib2.research;

import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SDFRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.UISoundUtils;

import net.minecraft.network.chat.Component;

import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyDisplay;
import dev.vfyjxf.taffy.style.TaffyPosition;

/** Shared geometry and text treatment for the research station's compact panels. */
final class ResearchUiParts {

    static final int SURFACE = 0xFFADB0C4;
    static final int TEXT = 0xFF141D30;
    static final int NODE_BACKGROUND = 0xFFCBCCD4;
    static final int NODE_SECTION = 0xFF46DBC2;
    static final int CORNER_RADIUS = 6;
    static final int NODE_BORDER = 0xFFF2F2F2;
    static final int NODE_COMPLETED = 0xFF1C7653;
    static final int NODE_RUNNING = 0xFF9CD9FF;
    static final int NODE_SELECTED = 0xFF9CD9FF;
    static final int NODE_BLOCKED = 0xFFE06B75;
    static final IGuiTexture PANEL = framed(0xFFEFF1F8, 0xFF53617D);
    private static final IGuiTexture INPUT = framed(0xFFF7F9FF, 0xFF4B5979);
    private static final IGuiTexture BUTTON = roundedFrame(0xFFF7F9FF, 0xFF4B5979);
    private static final IGuiTexture BUTTON_HOVER = roundedFrame(0xFFD9FFF0, 0xFF1C7653);
    private static final IGuiTexture BUTTON_PRESSED = roundedFrame(0xFFBCE7D6, 0xFF174934);
    static final String PREFIX = "gui.data_energistics.research.";

    private ResearchUiParts() {}

    static SDFRectTexture rounded(int color) {
        return SDFRectTexture.of(color).setRadius(CORNER_RADIUS);
    }

    static IGuiTexture roundedFrame(int background, int border) {
        return rounded(background).setStroke(1).setBorderColor(border);
    }

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
        panel.style(style -> style.backgroundTexture(PANEL));
        panel.setAllowHitTest(false);
        return panel;
    }

    static Label label(Component text) {
        Label label = new Label();
        label.setText(text);
        label.textStyle(style -> style.fontSize(10).textColor(TEXT).textShadow(false)
                .textWrap(TextWrap.WRAP).adaptiveWidth(false).adaptiveHeight(true));
        label.layout(layout -> layout.widthPercent(100).minHeight(12).flexShrink(0));
        label.setAllowHitTest(false);
        return label;
    }

    static Button button(Component text, Runnable action) {
        Button button = button(text);
        button.setOnClick(event -> action.run());
        return button;
    }

    /** Canvas actions fire on release so a press can become a pan before any research request is sent. */
    static Button graphButton(Component text, Runnable action) {
        Button button = button(text);
        button.addEventListener(UIEvents.CLICK, event -> {
            if (event.button == 0 && button.isActive()) {
                UISoundUtils.playButtonClickSound();
                action.run();
                event.stopPropagation();
            }
        });
        return button;
    }

    private static Button button(Component text) {
        Button button = new Button();
        button.text.setText(text);
        button.buttonStyle(style -> style.baseTexture(BUTTON).hoverTexture(BUTTON_HOVER).pressedTexture(BUTTON_PRESSED));
        button.text.layout(layout -> layout.flex(1).heightPercent(100));
        button.text.textStyle(style -> style.fontSize(10).textColor(TEXT).textShadow(false)
                .adaptiveWidth(false).adaptiveHeight(false).textWrap(TextWrap.HOVER_ROLL)
                .textAlignHorizontal(Horizontal.CENTER).textAlignVertical(Vertical.CENTER));
        button.layout(layout -> layout.widthPercent(100).height(22).minHeight(22).flexShrink(0));
        return button;
    }

    static ScrollerView scroll(float x, float y, float width, float height) {
        ScrollerView scroll = at(new ScrollerView(), x, y, width, height);
        scroll.scrollerStyle(style -> style.mode(ScrollerMode.VERTICAL).adaptiveHeight(false).adaptiveWidth(false));
        scroll.viewPort.style(style -> style.backgroundTexture(PANEL));
        scroll.verticalScroller.scrollBar.buttonStyle(style -> style.baseTexture(BUTTON)
                .hoverTexture(BUTTON_HOVER).pressedTexture(BUTTON_PRESSED));
        scroll.verticalScroller.scrollContainer.style(style -> style.backgroundTexture(new ColorRectTexture(SURFACE)));
        scroll.viewContainer(view -> view.layout(layout -> layout.flexDirection(FlexDirection.COLUMN)
                .gapAll(3).paddingAll(3)));
        return scroll;
    }

    static void input(TextField field) {
        field.style(style -> style.backgroundTexture(INPUT));
        field.textFieldStyle(style -> style.fontSize(10).textColor(TEXT).cursorColor(0xFF1C7653)
                .textShadow(false).focusOverlay(new ColorBorderTexture(-1, 0xFF1C7653)));
    }

    private static IGuiTexture framed(int background, int border) {
        return GuiTextureGroup.of(new ColorRectTexture(background), new ColorBorderTexture(-1, border));
    }

    static void show(UIElement element, boolean visible) {
        element.setVisible(visible);
        element.setActive(visible);
        element.setAllowHitTest(visible);
        element.layout(layout -> layout.display(visible ? TaffyDisplay.FLEX : TaffyDisplay.NONE));
    }
}
