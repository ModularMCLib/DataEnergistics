package com.fish_dan_.data_energistics.gui.ldlib2.terminal.stonecutting;

import com.fish_dan_.data_energistics.gui.ldlib2.terminal.text.TerminalTooltipLines;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.NullMarked;

/** A recipe button using AE's native stonecutting states, rather than an inventory slot. */
@NullMarked
public final class TerminalStonecuttingRecipeCell extends Button {

    public static final int WIDTH = 20;
    public static final int HEIGHT = 22;

    private final UIEventListener click;
    private ItemStack output = ItemStack.EMPTY;
    private boolean selected;

    public TerminalStonecuttingRecipeCell(UIEventListener click) {
        this.click = click;
        addClass("terminal-stonecutting-recipe");
        noText();
        layout(layout -> layout.width(WIDTH).height(HEIGHT).paddingAll(0).flexShrink(0));
        setVisible(false);
        addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            var lines = new ObjectArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), output));
            lines.add(Component.translatable("gui.data_energistics.player_terminal.stonecutting_gestures"));
            event.hoverTooltips = new HoverTooltips(TerminalTooltipLines.split(lines), null, null, null);
        });
    }

    public void entry(ItemStack value, boolean selected) {
        output = value;
        if (this.selected != selected) {
            this.selected = selected;
            if (selected) addClass("selected-recipe");
            else removeClass("selected-recipe");
        }
        setVisible(!output.isEmpty());
        setActive(!output.isEmpty());
    }

    @Override
    protected void onMouseDown(UIEvent event) {
        if (!isActive() || event.button < 0 || event.button > 1) return;
        setButtonState(State.PRESSED);
        click.handleEvent(event);
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        super.drawBackgroundAdditional(context);
        int x = Math.round(getContentX()) + 2;
        int y = Math.round(getContentY()) + (selected || getState() != State.DEFAULT ? 2 : 1);
        context.graphics.renderItem(output, x, y);
        context.graphics.renderItemDecorations(context.mc.font, output, x, y);
    }
}
