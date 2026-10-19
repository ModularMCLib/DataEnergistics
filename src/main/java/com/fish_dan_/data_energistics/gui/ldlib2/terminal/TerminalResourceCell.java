package com.fish_dan_.data_energistics.gui.ldlib2.terminal;

import com.fish_dan_.data_energistics.gui.ldlib2.terminal.text.TerminalTooltipLines;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.Icon;
import appeng.crafting.pattern.EncodedPatternItem;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

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
    private @Nullable GenericStack dragPreview;
    private boolean craftable;
    private boolean configurable;
    private boolean storageCell;
    private boolean encodedPattern;
    private @Nullable UIEventListener resourceClick;
    private @Nullable Component actionHint;
    private @Nullable UIElement gridOutline;
    private int outlineEdges;

    public TerminalResourceCell() {
        addClass("terminal-cell");
        noText();
        layout(layout -> layout.width(18).height(18).paddingAll(0).flexShrink(0));
        addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            if (stack == null) {
                if (actionHint != null) {
                    ObjectList<Component> lines = new ObjectArrayList<>();
                    lines.add(actionHint);
                    event.hoverTooltips = new HoverTooltips(TerminalTooltipLines.split(lines), null, null, null);
                }
                return;
            }
            ObjectList<Component> lines = new ObjectArrayList<>(AEKeyRendering.getTooltip(stack.what()));
            if (!encodedPattern) lines.add(Component.literal(stack.what().formatAmount(stack.amount(), AmountFormat.FULL)));
            if (craftable) lines.add(Component.translatable("gui.data_energistics.player_terminal.craftable_hint"));
            if (configurable) lines.add(Component.translatable("gui.data_energistics.player_terminal.ghost_hint"));
            if (actionHint != null) lines.add(actionHint);
            if (storageCell && !(stack.what() instanceof AEItemKey))
                lines.add(Component.translatable("gui.data_energistics.player_terminal.container_hint"));
            event.hoverTooltips = new HoverTooltips(TerminalTooltipLines.split(lines), null, null, null);
        });
    }

    public @Nullable GenericStack stack() {
        return stack;
    }

    public boolean craftable() {
        return craftable;
    }

    public void dragPreview(@Nullable GenericStack value) {
        dragPreview = value;
    }

    public void configurable(boolean value) {
        configurable = value;
    }

    public void storageCell(boolean value) {
        storageCell = value;
    }

    /** Uses the same primary-output display as AE's encoded-pattern slot, retaining the actual pattern for input. */
    public void encodedPattern() {
        encodedPattern = true;
    }

    /** Updates the exposed grid edges when viewport geometry changes; the middle texture supplies cell separators. */
    public void gridOutline(boolean top, boolean right, boolean bottom, boolean left) {
        int edges = (top ? 1 : 0) | (right ? 2 : 0) | (bottom ? 4 : 0) | (left ? 8 : 0);
        if (outlineEdges == edges) return;
        outlineEdges = edges;
        if (gridOutline == null) {
            gridOutline = TerminalUiLayout.at(new UIElement(), 0, 0, 18, 18);
            gridOutline.setAllowHitTest(false);
            addChild(gridOutline);
        }
        gridOutline.clearAllChildren();
        if (top) gridOutline.addChild(gridEdge(0, 0, 18, 1));
        if (right) gridOutline.addChild(gridEdge(17, 0, 1, 18));
        if (bottom) gridOutline.addChild(gridEdge(0, 17, 18, 1));
        if (left) gridOutline.addChild(gridEdge(0, 0, 1, 18));
    }

    private static UIElement gridEdge(int x, int y, int width, int height) {
        UIElement edge = TerminalUiLayout.at(new UIElement(), x, y, width, height);
        edge.addClass("terminal-grid-edge");
        edge.setAllowHitTest(false);
        return edge;
    }

    public void onResourceClick(UIEventListener listener) {
        resourceClick = listener;
    }

    public void actionHint(@Nullable Component value) {
        actionHint = value;
    }

    @Override
    protected void onMouseDown(UIEvent event) {
        boolean pick = event.button >= 0 && Minecraft.getInstance().options.keyPickItem.matchesMouse(event.button);
        if (!isActive() || event.button < 0 || !pick && event.button > (storageCell || configurable ? 2 : 1)) return;
        setButtonState(State.PRESSED);
        if (resourceClick != null) resourceClick.handleEvent(event);
    }

    public void entry(@Nullable GenericStack value, boolean canCraft) {
        stack = value;
        craftable = canCraft;
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        super.drawBackgroundAdditional(context);
        if (encodedPattern) {
            drawEncodedPattern(context);
            return;
        }
        GenericStack display = dragPreview == null ? stack : dragPreview;
        if (display == null) return;
        context.pose.pushPose();
        context.pose.translate(getContentX() + 1, getContentY() + 1, 0);
        if (dragPreview != null) context.graphics.fill(0, 0, 16, 16, 0x80FFFFFF);
        AEKeyRendering.drawInGui(context.mc, context.graphics, 0, 0, display.what());
        if (display.amount() != 1 || craftable) {
            String amount = display.amount() > 1 || !craftable ? display.what().formatAmount(display.amount(), AmountFormat.SLOT) : "+";
            context.pose.translate(0, 10, 200);
            context.pose.scale(0.5F, 0.5F, 1);
            context.graphics.drawString(context.mc.font, amount, 32 - context.mc.font.width(amount), 0, 0xFFFFFFFF);
        }
        context.pose.popPose();
    }

    private void drawEncodedPattern(GUIContext context) {
        context.pose.pushPose();
        context.pose.translate(getContentX() + 1, getContentY() + 1, 0);
        if (stack == null) {
            Icon.BACKGROUND_ENCODED_PATTERN.getBlitter().dest(0, 0).blit(context.graphics);
        } else if (stack.what() instanceof AEItemKey itemKey) {
            ItemStack display = itemKey.toStack();
            if (display.getItem() instanceof EncodedPatternItem<?> pattern) {
                ItemStack output = pattern.getOutput(display);
                if (!output.isEmpty()) display = output;
            }
            context.graphics.renderItem(display, 0, 0);
            context.graphics.renderItemDecorations(context.mc.font, display, 0, 0);
        }
        context.pose.popPose();
    }
}
