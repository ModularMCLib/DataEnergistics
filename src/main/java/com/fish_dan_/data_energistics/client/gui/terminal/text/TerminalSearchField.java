package com.fish_dan_.data_energistics.client.gui.terminal.text;

import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.utils.TextUtilities;

import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Tuple;

import com.mojang.blaze3d.systems.RenderSystem;
import org.jspecify.annotations.NullMarked;

/** Keeps LDLib2 editing and terminal field styling, with AE's blinking underscore caret. */
@NullMarked
public final class TerminalSearchField extends TextField {

    private static final int PLACEHOLDER_COLOR = 0xFFF2F2F2;
    private static final Tuple<FormattedCharSequence, Float> EMPTY_LINE = new Tuple<>(FormattedCharSequence.EMPTY, 0F);
    private long focusedTime;

    public TerminalSearchField() {
        textFieldStyle(style -> style.cursorColor(0));
        addEventListener(UIEvents.FOCUS, event -> focusedTime = Util.getMillis());
    }

    public static Component placeholder(Component value) {
        return value.copy().withStyle(style -> style.withColor(PLACEHOLDER_COLOR));
    }

    @Override
    public Tuple<FormattedCharSequence, Float> getFormattedLine() {
        // LDLib2 caches the placeholder with the text; focus must hide it without changing the search value.
        return isFocused() && getRawText().isEmpty() ? EMPTY_LINE : super.getFormattedLine();
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        var font = getFont();
        var style = getTextFieldStyle();
        float textY = getContentY() + (getContentHeight() - style.fontSize()) / 2;
        float lineY = Math.round(textY);
        context.pose.pushPose();
        context.pose.translate(0, lineY - textY, 0);
        super.drawBackgroundAdditional(context);
        context.pose.popPose();
        if (!isFocused() || (Util.getMillis() - focusedTime) / 300 % 2 != 0) return;
        float scale = style.fontSize() / font.lineHeight;
        float cursorX = getContentX() + font.getSplitter().stringWidth(TextUtilities.truncateStyled(getStyledLine(), getCursorPos())) * scale - getDisplayOffset();
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
