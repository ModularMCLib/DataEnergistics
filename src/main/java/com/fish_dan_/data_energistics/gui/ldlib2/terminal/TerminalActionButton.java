package com.fish_dan_.data_energistics.gui.ldlib2.terminal;

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

    public TerminalActionButton() {
        setText(Component.empty());
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
        if (action != null) action.handleEvent(event);
        setButtonState(State.PRESSED);
    }
}
