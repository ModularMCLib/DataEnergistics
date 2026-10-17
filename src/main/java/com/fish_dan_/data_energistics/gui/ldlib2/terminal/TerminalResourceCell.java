package com.fish_dan_.data_energistics.gui.ldlib2.terminal;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.UISoundUtils;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Snapshot-backed clickable resource display; never installs native slots on the host menu.
 */
@NullMarked
public final class TerminalResourceCell extends Button {

    private @Nullable GenericStack stack;
    private boolean craftable;
    private boolean configurable;
    private boolean storageCell;
    private @Nullable UIEventListener resourceClick;

    public TerminalResourceCell() {
        addClass("terminal-cell");
        noText();
        layout(layout -> layout.width(18).height(18).paddingAll(0).flexShrink(0));
        addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            if (stack == null) return;
            ObjectList<Component> lines = new ObjectArrayList<>(AEKeyRendering.getTooltip(stack.what()));
            lines.add(Component.literal(stack.what().formatAmount(stack.amount(), AmountFormat.FULL)));
            if (craftable) lines.add(Component.translatable("gui.data_energistics.player_terminal.craftable_hint"));
            if (configurable) lines.add(Component.translatable("gui.data_energistics.player_terminal.ghost_hint"));
            if (storageCell && !(stack.what() instanceof AEItemKey))
                lines.add(Component.translatable("gui.data_energistics.player_terminal.container_hint"));
            event.hoverTooltips = new HoverTooltips(lines, null, null, null);
        });
    }

    public @Nullable GenericStack stack() {
        return stack;
    }

    public boolean craftable() {
        return craftable;
    }

    public void configurable(boolean value) {
        configurable = value;
    }

    public void storageCell(boolean value) {
        storageCell = value;
    }

    public void onResourceClick(UIEventListener listener) {
        resourceClick = listener;
    }

    @Override
    protected void onMouseDown(UIEvent event) {
        if (!isActive() || event.button < 0 || event.button > (storageCell ? 2 : 1)) return;
        UISoundUtils.playButtonClickSound();
        if (resourceClick != null) resourceClick.handleEvent(event);
        setButtonState(State.PRESSED);
    }

    public void entry(@Nullable GenericStack value, boolean canCraft) {
        stack = value;
        craftable = canCraft;
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        super.drawBackgroundAdditional(context);
        if (stack == null) return;
        context.pose.pushPose();
        context.pose.translate(getContentX() + 1, getContentY() + 1, 0);
        AEKeyRendering.drawInGui(context.mc, context.graphics, 0, 0, stack.what());
        if (stack.amount() > 1 || craftable) {
            String amount = stack.amount() > 1 ? stack.what().formatAmount(stack.amount(), AmountFormat.SLOT) : "+";
            context.pose.translate(0, 10, 200);
            context.pose.scale(0.5F, 0.5F, 1);
            context.graphics.drawString(context.mc.font, amount, 32 - context.mc.font.width(amount), 0, 0xFFFFFFFF);
        }
        context.pose.popPose();
    }
}
