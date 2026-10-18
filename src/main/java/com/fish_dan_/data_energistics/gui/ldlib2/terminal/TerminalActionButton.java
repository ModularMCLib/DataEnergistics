package com.fish_dan_.data_energistics.gui.ldlib2.terminal;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;
import com.lowdragmc.lowdraglib2.gui.util.UISoundUtils;

import net.minecraft.network.chat.Component;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Client-side controls with distinct left/right gestures; LDLib's normal Button only dispatches left clicks. */
@NullMarked
public final class TerminalActionButton extends Button {

    private @Nullable UIEventListener action;
    private @Nullable UIElement glyph;
    private int glyphTop;

    public void glyph(UIElement element, int top) {
        glyph = element;
        glyphTop = top;
    }

    @Override
    protected void setButtonState(State state) {
        if (getState() == state) return;
        super.setButtonState(state);
        if (state == State.HOVERED) addClass("hovered");
        else removeClass("hovered");
        if (glyph != null) glyph.layout(layout -> layout.top(glyphTop + (state == State.PRESSED ? 1 : 0)));
    }

    public TerminalActionButton() {
        setText(Component.empty());
    }

    void resetInteraction() {
        setButtonState(State.DEFAULT);
    }

    @Override
    public TerminalActionButton setOnClick(@Nullable UIEventListener action) {
        this.action = action;
        return this;
    }

    @Override
    protected void onMouseDown(UIEvent event) {
        if (!isActive() || event.button < 0 || event.button > 1) return;
        UISoundUtils.playButtonClickSound();
        setButtonState(State.PRESSED);
        if (action != null) action.handleEvent(event);
    }
}
