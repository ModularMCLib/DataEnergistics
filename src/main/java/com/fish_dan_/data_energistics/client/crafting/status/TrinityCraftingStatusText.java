package com.fish_dan_.data_energistics.client.crafting.status;

import com.fish_dan_.data_energistics.common.crafting.trinity.status.TrinityCraftingStatusEntry;
import com.fish_dan_.data_energistics.common.crafting.trinity.status.TrinityStatusAmount;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import appeng.core.localization.GuiText;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

/** CPU table and tooltip text use the same bounded quantities formatted by the server. */
public final class TrinityCraftingStatusText {

    private TrinityCraftingStatusText() {}

    /** Returns the original key tooltip plus formatted amounts, or just compact table lines. */
    public static ObjectList<Component> lines(TrinityCraftingStatusEntry entry, boolean tooltip) {
        AEKey key = entry.getWhat();
        ObjectList<Component> lines = tooltip ? new ObjectArrayList<>(AEKeyRendering.getTooltip(key)) : new ObjectArrayList<>(3);
        if (!tooltip && entry.resident().amount() > 0) {
            lines.add(Component.translatable("gui.data_energistics.reusable_status.stored_and_held",
                    entry.stored().text(), entry.resident().text()));
        } else {
            add(lines, GuiText.FromStorage, key, entry.stored(), tooltip);
            if (entry.resident().amount() > 0) {
                lines.add(Component.translatable("gui.data_energistics.reusable_status.held_material", format(key, entry.resident(), true)));
            }
        }
        add(lines, GuiText.Crafting, key, entry.active(), tooltip);
        add(lines, GuiText.Scheduled, key, entry.pending(), tooltip);
        return lines;
    }

    private static void add(ObjectList<Component> lines, GuiText label, AEKey key, TrinityStatusAmount amount, boolean full) {
        if (amount.amount() > 0) {
            lines.add(label.text(format(key, amount, full)));
        }
    }

    private static String format(AEKey key, TrinityStatusAmount amount, boolean full) {
        if (!full) {
            return amount.text();
        }
        String unit = key.getUnitSymbol();
        return unit == null ? amount.text() : amount.text() + " " + unit;
    }
}
