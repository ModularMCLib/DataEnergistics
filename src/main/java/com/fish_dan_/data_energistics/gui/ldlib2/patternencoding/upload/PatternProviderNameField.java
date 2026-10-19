package com.fish_dan_.data_energistics.gui.ldlib2.patternencoding.upload;

import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;

import org.jspecify.annotations.NullMarked;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/** Middle-click starts editing; only Enter commits, while losing focus discards an unfinished name. */
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
        setTextRegexValidator(".{0,50}");
        setFocusable(false);
        layout(layout -> layout.flex(1).minWidth(0).height(14));
    }

    @Override
    protected void onMouseDown(UIEvent event) {
        if (event.button == 2 && renameable) {
            setFocusable(true);
            focus();
            setCursor(getValue().length());
            setSelection(0, getValue().length());
            event.stopPropagation();
        } else if (isEditable()) {
            super.onMouseDown(event);
            event.stopPropagation();
        }
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
    }
}
