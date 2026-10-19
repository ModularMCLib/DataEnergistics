package com.fish_dan_.data_energistics.gui.ldlib2.terminal.magnet;

import com.fish_dan_.data_energistics.common.terminal.player.magnet.TerminalMagnetSettings.Mode;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalActionButton;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalUiLayout;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.text.TerminalTooltipLines;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.NullMarked;

import java.util.function.BiConsumer;

/** Compact wireless-terminal settings and two independent 9-by-3 filter grids. */
@NullMarked
public final class TerminalMagnetUi {

    private final BiConsumer<String, CompoundTag> request;
    private final TerminalActionButton attract;
    private final TerminalActionButton insert;
    private final TerminalActionButton restock;
    private final TerminalActionButton pickBlock;
    private final TerminalActionButton craftIfMissing;
    private final Button pickupMode;
    private final Button insertMode;
    private final Button copyDown;
    private final Button swap;
    private final Button copyUp;
    private Mode mode = Mode.OFF;

    public TerminalMagnetUi(UIElement settings, UIElement filters, BiConsumer<String, CompoundTag> request, Runnable openFilters) {
        this.request = request;
        pickBlock = TerminalUiLayout.toggle("pick_block", text("magnet_pick_block"), 10, 25, 180);
        craftIfMissing = TerminalUiLayout.toggle("craft_if_missing", text("magnet_craft_if_missing"), 10, 41, 180);
        restock = TerminalUiLayout.toggle("restock", text("magnet_restock"), 10, 57, 180);
        attract = TerminalUiLayout.toggle("attract", text("magnet_attract"), 10, 83, 180);
        insert = TerminalUiLayout.toggle("pickup_to_me", text("magnet_insert"), 10, 98, 180);
        Label heading = TerminalUiLayout.at(new Label(), 8, 73, 160, 10);
        heading.addClass("terminal-popup-heading");
        heading.textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(false));
        heading.setText(text("magnet_settings"));
        heading.setAllowHitTest(false);
        Button gear = TerminalUiLayout.icon("filters", "terminal-small", "icon-settings", 176, 70, 14, 14);
        gear.style(style -> style.tooltips(TerminalTooltipLines.split(text("filters"))));
        gear.setOnClick(event -> {
            if (event.button == 0) openFilters.run();
        });
        settings.addChildren(pickBlock, craftIfMissing, restock, heading, attract, insert, gear);
        pickBlock.setOnClick(event -> {
            if (event.button == 0) request.accept("magnet_pick_block", new CompoundTag());
        });
        craftIfMissing.setOnClick(event -> {
            if (event.button == 0) request.accept("magnet_craft_if_missing", new CompoundTag());
        });
        restock.setOnClick(event -> {
            if (event.button == 0) request.accept("magnet_restock", new CompoundTag());
        });
        attract.setOnClick(event -> {
            if (event.button == 0) changeMode(!mode.attract(), mode.insert());
        });
        insert.setOnClick(event -> {
            if (event.button == 0) changeMode(mode.attract(), !mode.insert());
        });

        filters.addChildren(grid("pickup", 20), grid("insert", 101));
        pickupMode = action(filters, "pickup_whitelist", "icon-filter-list", 45);
        copyDown = action(filters, "filter_copy_down", "icon-filter-copy-down", 63);
        swap = action(filters, "filter_swap", "icon-filter-swap", 81);
        copyUp = action(filters, "filter_copy_up", "icon-filter-copy-up", 99);
        insertMode = action(filters, "insert_whitelist", "icon-filter-list", 117);
    }

    private static UIElement grid(String id, int top) {
        UIElement frame = TerminalUiLayout.at(new UIElement(), 5, top, 166, 58);
        frame.addClass("terminal-filter-frame");
        UIElement cells = TerminalUiLayout.at(new UIElement(), 2, 1, 162, 54);
        cells.setId(id);
        frame.addChild(cells);
        return frame;
    }

    private Button action(UIElement root, String id, String glyph, int left) {
        Button button = TerminalUiLayout.icon(id, "terminal-filter-action", glyph, left, 80, 16, 17);
        button.style(style -> style.tooltips(TerminalTooltipLines.split(text(id))));
        button.setOnClick(event -> {
            if (event.button == 0) request.accept(id, new CompoundTag());
        });
        root.addChild(button);
        return button;
    }

    private void changeMode(boolean attractItems, boolean insertItems) {
        Mode next = attractItems ? (insertItems ? Mode.NETWORK : Mode.INVENTORY) : (insertItems ? Mode.PICKUP_NETWORK : Mode.OFF);
        mode = next;
        selected(attract, attractItems);
        selected(insert, insertItems);
        CompoundTag data = new CompoundTag();
        data.putString("value", next.name());
        request.accept("magnet_mode", data);
    }

    public void refresh(CompoundTag settings, boolean unlocked) {
        mode = Mode.valueOf(settings.getString("mode"));
        selected(attract, mode.attract());
        selected(insert, mode.insert());
        selected(restock, settings.getBoolean("restock"));
        selected(pickBlock, settings.getBoolean("pick_block"));
        selected(craftIfMissing, settings.getBoolean("craft_if_missing"));
        for (Button button : new Button[] { attract, insert, pickupMode, insertMode, copyDown, swap, copyUp })
            button.setActive(unlocked);
        craftIfMissing.setActive(settings.getBoolean("pick_block"));
        filterMode(pickupMode, "pickup", settings.getBoolean("pickup_whitelist"));
        filterMode(insertMode, "insert", settings.getBoolean("insert_whitelist"));
    }

    private static void filterMode(Button button, String name, boolean whitelist) {
        selected(button, whitelist);
        button.style(style -> style.tooltips(TerminalTooltipLines.split(text(name + (whitelist ? "_whitelist" : "_blacklist")))));
    }

    private static void selected(UIElement element, boolean value) {
        if (value) element.addClass("selected");
        else element.removeClass("selected");
    }

    private static Component text(String key) {
        return Component.translatable("gui.data_energistics.player_terminal." + key);
    }
}
