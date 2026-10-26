package com.fish_dan_.data_energistics.client.gui;

import com.fish_dan_.data_energistics.util.FormattingUtils;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.math.BigDecimal;

public final class GenericStackDisplayHelper {

    private static final float SMALL_OVERLAY_SCALE = 0.5F;

    private GenericStackDisplayHelper() {}

    public static String formatCompactAmount(GenericStack stack) {
        if (stack.what() instanceof AEFluidKey) {
            return formatCompactFluidAmount(stack.amount());
        }
        return formatCompactAmount(stack.amount());
    }

    public static String formatCompactAmount(long amount) {
        return FormattingUtils.format(amount);
    }

    public static String formatCompactFluidAmount(long amount) {
        if (amount > -1_000L && amount < 1_000L) {
            return FormattingUtils.format(amount) + "mB";
        }
        return FormattingUtils.format(BigDecimal.valueOf(amount, 3)) + "B";
    }

    public static Component createAmountTooltip(GenericStack stack) {
        return Component.translatable("tooltip.data_energistics.amount")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(stack.what().formatAmount(stack.amount(), AmountFormat.FULL))
                        .withStyle(ChatFormatting.GRAY));
    }

    public static void renderSmallOverlay(GuiGraphics guiGraphics, int x, int y, String text) {
        Font font = Minecraft.getInstance().font;
        int textWidth = font.width(text);
        float scaledWidth = textWidth * SMALL_OVERLAY_SCALE;
        float scaledHeight = font.lineHeight * SMALL_OVERLAY_SCALE;
        float drawX = x + 18 - 1 - scaledWidth;
        float drawY = y + 18 - 1 - scaledHeight;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 200.0F);
        guiGraphics.pose().scale(SMALL_OVERLAY_SCALE, SMALL_OVERLAY_SCALE, 1.0F);
        guiGraphics.drawString(
                font,
                text,
                Math.round(drawX / SMALL_OVERLAY_SCALE),
                Math.round(drawY / SMALL_OVERLAY_SCALE),
                16777215,
                true);
        guiGraphics.pose().popPose();
    }
}
