package com.fish_dan_.data_energistics.client.terminal.storage;

import com.fish_dan_.data_energistics.client.screen.patternencoding.PatternEncodingUploadClient;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalOverlayUi;

import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.client.Hotkeys;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.Slot;

import com.mojang.blaze3d.platform.InputConstants;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.BiConsumer;

/** Resolves the shared AE stow binding to the foreground physical slot of a player-terminal session. */
@NullMarked
public final class TerminalStowInput {

    private TerminalStowInput() {}

    public static boolean matches(InputConstants.Key key) {
        var hotkey = Hotkeys.getHotkeyMapping("ae2wtlib_stow");
        return hotkey != null && hotkey.mapping().isActiveAndMatches(key);
    }

    /** Client-thread dispatch; text editors and foreground child windows retain their input and slot ownership. */
    public static boolean stow(AbstractContainerScreen<?> screen, TerminalOverlayUi overlay, ModularUI ui, double x, double y,
                               BiConsumer<String, CompoundTag> request) {
        if (textInputFocused(ui)) return false;
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        boolean foreground = overlay.contains(x, y);
        if (!foreground && (PatternEncodingUploadClient.textInputFocused(screen) || textInputFocused(screen))) return false;
        if (!screen.getMenu().getCarried().isEmpty()) {
            request.accept("stow", new CompoundTag());
            return true;
        }
        if (foreground) return overlay.stowSlot(x, y);
        if (PatternEncodingUploadClient.contains(screen, x, y)) return true;
        Slot slot = screen.getSlotUnderMouse();
        if (slot == null || !slot.isActive() || slot.isFake() || !slot.mayPickup(player) || GenericStack.isWrapped(slot.getItem()))
            return false;
        AEItemKey key = AEItemKey.of(slot.getItem());
        if (key == null) return false;
        int index = slot.index;
        if (screen instanceof CreativeModeInventoryScreen) {
            index = slot.container == player.getInventory() ? player.inventoryMenu.findSlot(slot.container, slot.getContainerSlot()).orElse(-1) : -1;
            if (index < 0) return false;
        }
        CompoundTag data = new CompoundTag();
        data.putInt("slot", index);
        data.put("key", key.toTagGeneric(player.registryAccess()));
        request.accept("stow", data);
        return true;
    }

    private static boolean textInputFocused(GuiEventListener listener) {
        if (listener instanceof EditBox field && field.isFocused()) return true;
        if (listener instanceof IModularUIHolder holder && textInputFocused(holder.getModularUI())) return true;
        return listener instanceof ContainerEventHandler container && container.children().stream().anyMatch(TerminalStowInput::textInputFocused);
    }

    private static boolean textInputFocused(@Nullable ModularUI ui) {
        for (UIElement focused = ui == null ? null : ui.getFocusedElement(); focused != null; focused = focused.getParent())
            if (focused instanceof TextField) return true;
        return false;
    }
}
