package com.fish_dan_.data_energistics.gui.ldlib2.terminal.encoding;

import com.fish_dan_.data_energistics.gui.ldlib2.terminal.text.TerminalTooltipLines;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AmountFormat;
import appeng.client.gui.Icon;
import appeng.client.gui.me.common.StackSizeRenderer;
import appeng.core.definitions.AEItems;
import appeng.util.ReadableNumberConverter;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.NullMarked;

/** Network-backed blank-pattern display matching the injected AE terminal slot, without a local inventory. */
@NullMarked
public final class TerminalBlankPatternCell extends Button {

    private static final AEItemKey KEY = AEItemKey.of(AEItems.BLANK_PATTERN);

    private final UIEventListener click;
    private long amount;
    private boolean craftable;

    public TerminalBlankPatternCell(UIEventListener click) {
        this.click = click;
        addClass("terminal-cell");
        noText();
        addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            var lines = new ObjectArrayList<>(AEKeyRendering.getTooltip(KEY));
            lines.add(Component.literal(KEY.formatAmount(amount, AmountFormat.FULL)));
            if (craftable) lines.add(Component.translatable("gui.data_energistics.player_terminal.craftable_hint"));
            event.hoverTooltips = new HoverTooltips(TerminalTooltipLines.split(lines), null, null, null);
        });
    }

    public void entry(long amount, boolean craftable) {
        this.amount = amount;
        this.craftable = craftable;
    }

    @Override
    protected void onMouseDown(UIEvent event) {
        if (!isActive() || event.button < 0 || event.button > 2) return;
        setButtonState(State.PRESSED);
        click.handleEvent(event);
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        super.drawBackgroundAdditional(context);
        context.pose.pushPose();
        context.pose.translate(getContentX() + 1, getContentY() + 1, 0);
        if (amount > 0) {
            var display = AEItems.BLANK_PATTERN.stack();
            context.graphics.renderItem(display, 0, 0);
            context.graphics.renderItemDecorations(context.mc.font, display, 0, 0, "");
            StackSizeRenderer.renderSizeLabel(context.graphics, context.mc.font, 0, 0, ReadableNumberConverter.format(amount, 4));
        } else Icon.BACKGROUND_ENCODED_PATTERN.getBlitter().dest(0, 0).blit(context.graphics);
        if (craftable) {
            context.pose.translate(0, 0, 200);
            StackSizeRenderer.renderSizeLabel(context.graphics, context.mc.font, -11, -11, "+", false);
        }
        context.pose.popPose();
    }
}
