package com.fish_dan_.data_energistics.integration.ae.ae2.gui.resource;

import com.fish_dan_.data_energistics.util.FormattingUtils;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.utils.TextUtilities;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;

/**
 * Shared read-only resource row for storage and cost lists. Update it only on its owning UI thread;
 * resource amounts are already validated by the caller's synchronized model.
 */
public final class AeKeyAmountRow extends UIElement {

    /** Fixed row height shared with paged viewports. */
    public static final int HEIGHT = 18;
    private static final int ICON_SIZE = 16;
    private static final int PADDING = 1;
    private static final int GAP = 3;
    private static final float ICON_SCALE = 0.875F;
    private static final float ICON_INSET = ICON_SIZE * (1 - ICON_SCALE) / 2;

    private final Label name = new Label();
    private final Label amount = new Label();
    private @Nullable AEKey key;
    private String formattedAmount = "";
    private String exactAmount = "";

    /** Creates an empty, hidden row which becomes visible when an entry is supplied. */
    public AeKeyAmountRow() {
        setOverflowVisible(false);
        layout(layout -> layout.widthPercent(100).height(HEIGHT).minHeight(HEIGHT).paddingAll(PADDING)
                .gapAll(GAP).flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).flexShrink(0));

        KeyIcon icon = new KeyIcon();
        icon.setAllowHitTest(false);
        icon.layout(layout -> layout.width(ICON_SIZE).height(ICON_SIZE).flexShrink(0));

        configureText(name);
        name.layout(layout -> layout.flex(1).minWidth(0).heightPercent(100));
        name.textStyle(style -> style.textColor(0xFF404052).textShadow(false).textWrap(TextWrap.HOVER_ROLL));

        configureText(amount);
        amount.layout(layout -> layout.heightPercent(100).flexShrink(0));
        amount.textStyle(style -> style.adaptiveWidth(true).textColor(0xFFFFFFFF).textShadow(true)
                .textAlignHorizontal(Horizontal.RIGHT).textWrap(TextWrap.NONE));

        addChildren(icon, name, amount);
        addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            if (key != null) {
                event.hoverTooltips = new HoverTooltips(tooltip(key), null, null, null);
            }
        });
        setVisible(false);
    }

    private static void configureText(Label label) {
        label.setText(Component.empty());
        label.setAllowHitTest(false);
        label.setOverflowVisible(false);
        label.textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(false).fontSize(6)
                .textAlignHorizontal(Horizontal.LEFT).textAlignVertical(Vertical.CENTER));
    }

    /** Adjusts both text columns without changing the icon size or row height. */
    public void setFontSize(float fontSize) {
        name.textStyle(style -> style.fontSize(fontSize));
        amount.textStyle(style -> style.fontSize(fontSize));
    }

    /** Displays one validated key and its exact native-unit amount; does not move any resources. */
    public void setEntry(AEKey key, BigInteger amount) {
        this.key = key;
        this.formattedAmount = FormattingUtils.format(amount);
        this.exactAmount = amount.toString();
        name.setText(key.getDisplayName());
        this.amount.setText(Component.literal(formattedAmount));
        setVisible(true);
    }

    /** Releases the previous entry when a pooled row no longer has a visible resource. */
    public void clearEntry() {
        key = null;
        formattedAmount = "";
        exactAmount = "";
        name.setText(Component.empty());
        amount.setText(Component.empty());
        setVisible(false);
    }

    /** Natural text width for content-sized containers; narrower rows scroll long names on hover. */
    public float preferredWidth() {
        return PADDING * 2 + ICON_SIZE + GAP * 2 + textWidth(name) + textWidth(amount);
    }

    private static float textWidth(Label label) {
        var style = label.getTextStyle();
        float width = 0;
        for (var line : TextUtilities.computeFormattedLines(label.getFont(),
                TextUtilities.withFont(label.getText(), style.font()), style.fontSize(), Float.MAX_VALUE)) {
            width = Math.max(width, line.getB());
        }
        return width;
    }

    private ObjectList<Component> tooltip(AEKey key) {
        ObjectList<Component> lines = new ObjectArrayList<>(AEKeyRendering.getTooltip(key));
        if (lines.isEmpty()) lines.add(key.getDisplayName());
        lines.add(Component.translatable("gui.data_energistics.resource_list.amount", formattedAmount)
                .withStyle(ChatFormatting.GRAY));
        if (!formattedAmount.equals(exactAmount)) {
            lines.add(Component.translatable("gui.data_energistics.resource_list.amount_exact", exactAmount)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    private final class KeyIcon extends UIElement {

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawBackgroundAdditional(GUIContext context) {
            AEKey renderedKey = key;
            if (renderedKey == null) return;
            context.graphics.flush();
            context.pose.pushPose();
            context.pose.translate(getContentX() + ICON_INSET, getContentY() + ICON_INSET, 0);
            context.pose.scale(ICON_SCALE, ICON_SCALE, 1);
            AEKeyRendering.drawInGui(context.mc, context.graphics, 0, 0, renderedKey);
            context.pose.popPose();
        }
    }
}
