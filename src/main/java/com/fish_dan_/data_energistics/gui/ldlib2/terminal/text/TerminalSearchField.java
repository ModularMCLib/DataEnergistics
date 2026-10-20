package com.fish_dan_.data_energistics.gui.ldlib2.terminal.text;

import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.utils.TextUtilities;

import net.minecraft.Util;
import net.minecraft.network.chat.Component;

import com.mojang.blaze3d.systems.RenderSystem;
import org.jspecify.annotations.NullMarked;

/** Keeps LDLib2 editing and terminal field styling, with AE's blinking underscore caret. */
@NullMarked
public final class TerminalSearchField extends TextField {

    private static final int PLACEHOLDER_COLOR = 0xFFF2F2F2;
    private long focusedTime;

    public TerminalSearchField() {
        textFieldStyle(style -> style.cursorColor(0));
        addEventListener(UIEvents.FOCUS, event -> focusedTime = Util.getMillis());
    }

    public static Component placeholder(Component value) {
        return value.copy().withStyle(style -> style.withColor(PLACEHOLDER_COLOR));
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        super.drawBackgroundAdditional(context);
        if (!isFocused() || (Util.getMillis() - focusedTime) / 300 % 2 != 0) return;
        var font = getFont();
        var style = getTextFieldStyle();
        float scale = style.fontSize() / font.lineHeight;
        float cursorX = getContentX() + font.getSplitter().stringWidth(TextUtilities.truncateStyled(getStyledLine(), getCursorPos())) * scale - getDisplayOffset();
        float lineY = getContentY() + (getContentHeight() - style.fontSize()) / 2;
        context.graphics.flush();
        RenderSystem.depthMask(false);
        context.pose.pushPose();
        context.pose.translate(cursorX, lineY, 0);
        context.pose.scale(scale, scale, 1);
        context.graphics.drawString(font, "_", 0, 0, style.textColor(), style.textShadow());
        context.graphics.flush();
        context.pose.popPose();
        RenderSystem.depthMask(true);
    }
}
