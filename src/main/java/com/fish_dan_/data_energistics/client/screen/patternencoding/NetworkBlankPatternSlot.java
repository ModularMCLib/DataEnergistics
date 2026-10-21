package com.fish_dan_.data_energistics.client.screen.patternencoding;

import com.fish_dan_.data_energistics.common.menu.patternencoding.BlankPatternProxyMenu;

import appeng.api.stacks.AEItemKey;
import appeng.client.gui.Icon;
import appeng.client.gui.me.common.StackSizeRenderer;
import appeng.core.definitions.AEItems;
import appeng.helpers.InventoryAction;
import appeng.menu.SlotSemantics;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.util.ReadableNumberConverter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

/** Network-backed blank-pattern display and gestures shared by every native encoding-screen subclass. */
public final class NetworkBlankPatternSlot {

    private NetworkBlankPatternSlot() {}

    public static boolean render(PatternEncodingTermMenu menu, Slot slot, Iterable<GridInventoryEntry> entries, Font font, GuiGraphics graphics) {
        if (proxy(menu, slot) == null) return false;
        GridInventoryEntry entry = find(entries);
        long networkStored = entry == null ? 0 : entry.getStoredAmount();
        long displayed = networkStored > 0 ? networkStored : AEItems.BLANK_PATTERN.is(slot.getItem()) ? slot.getItem().getCount() : 0;
        if (slot.getItem().isEmpty() && displayed == 0) {
            Icon.BACKGROUND_ENCODED_PATTERN.getBlitter().dest(slot.x, slot.y).blit(graphics);
        } else {
            ItemStack stack = slot.getItem().isEmpty() ? AEItems.BLANK_PATTERN.stack() : slot.getItem().copyWithCount(1);
            graphics.renderItem(stack, slot.x, slot.y);
            graphics.renderItemDecorations(font, stack, slot.x, slot.y, "");
        }
        if (displayed > 0) StackSizeRenderer.renderSizeLabel(graphics, font, slot.x, slot.y, ReadableNumberConverter.format(displayed, 4));
        if (entry != null && (entry.isCraftable() || entry.getRequestableAmount() > 0)) {
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(0, 0, 100);
                StackSizeRenderer.renderSizeLabel(graphics, font, slot.x - 11, slot.y - 11, "+", false);
            } finally {
                graphics.pose().popPose();
            }
        }
        return true;
    }

    public static boolean click(PatternEncodingTermMenu menu, @Nullable Slot slot, Iterable<GridInventoryEntry> entries, int button) {
        BlankPatternProxyMenu proxy = proxy(menu, slot);
        if (proxy == null) return false;
        if (button == 0 || button == 1) {
            if (menu.getCarried().isEmpty()) proxy.data_energistics$pickupBlankPatterns(button == 1);
            else if (AEItems.BLANK_PATTERN.is(menu.getCarried())) proxy.data_energistics$depositCarriedBlankPatterns(button == 1);
            else return false;
            return true;
        }
        if (Minecraft.getInstance().options.keyPickItem.matchesMouse(button)) {
            GridInventoryEntry entry = find(entries);
            if (entry != null && entry.isCraftable()) {
                menu.handleInteraction(entry.getSerial(), InventoryAction.AUTO_CRAFT);
                return true;
            }
        }
        return false;
    }

    private static @Nullable BlankPatternProxyMenu proxy(PatternEncodingTermMenu menu, @Nullable Slot slot) {
        return slot != null && menu.getSlotSemantic(slot) == SlotSemantics.BLANK_PATTERN && menu instanceof BlankPatternProxyMenu proxy &&
                proxy.data_energistics$usesNetworkBackedBlankPatternSlot() ? proxy : null;
    }

    private static @Nullable GridInventoryEntry find(Iterable<GridInventoryEntry> entries) {
        AEItemKey key = AEItemKey.of(AEItems.BLANK_PATTERN);
        GridInventoryEntry fallback = null;
        for (GridInventoryEntry entry : entries) {
            if (!key.equals(entry.getWhat())) continue;
            if (entry.isMeaningful()) return entry;
            if (fallback == null) fallback = entry;
        }
        return fallback;
    }
}
