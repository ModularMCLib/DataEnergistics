package com.fish_dan_.data_energistics.client.gui.patternencoding.upload;

import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;

import org.jspecify.annotations.NullMarked;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/** A binding callback starts editing; Enter commits, while losing focus discards an unfinished name. */
@NullMarked
final class PatternProviderNameField extends TextField {

    private final Consumer<String> commit;
    private final boolean renameable;
    private String savedName;

    PatternProviderNameField(String name, boolean renameable, Consumer<String> commit) {
        this.savedName = name;
        this.renameable = renameable;
        this.commit = commit;
        addClass("terminal-field");
        addClass("terminal-upload-name");
        setText(name, false);
        setCursor(0);
        setTextRegexValidator(".{0,50}");
        setFocusable(false);
    }

    @Override
    protected void onMouseDown(UIEvent event) {
        if (isEditable()) {
            super.onMouseDown(event);
            event.stopPropagation();
        }
    }

    boolean beginEditing() {
        if (!renameable) return false;
        setFocusable(true);
        focus();
        setCursor(getValue().length());
        setSelection(0, getValue().length());
        return true;
    }

    @Override
    protected void onKeyDown(UIEvent event) {
        if (event.keyCode == GLFW.GLFW_KEY_ENTER || event.keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            savedName = getValue();
            blur();
            commit.accept(savedName);
            event.stopPropagation();
        } else if (event.keyCode == GLFW.GLFW_KEY_ESCAPE) {
            blur();
            event.stopPropagation();
        } else super.onKeyDown(event);
    }

    @Override
    protected void onBlur(UIEvent event) {
        super.onBlur(event);
        setFocusable(false);
        setText(savedName, false);
        setCursor(0);
    }
}
