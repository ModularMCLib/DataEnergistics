package com.fish_dan_.data_energistics.mixin.minecraft.client.menu;

import com.fish_dan_.data_energistics.client.terminal.rendering.TerminalCarriedItemRenderer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Carries vanilla's cursor draw past the terminal windows without replacing the host's inventory gestures. */
@NullMarked
@Mixin(AbstractContainerScreen.class)
public abstract class PlayerTerminalCarriedItemMixin {

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
                                             target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderFloatingItem(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
                                             ordinal = 0))
    private void dataEnergistics$deferCarriedItem(AbstractContainerScreen<?> screen, GuiGraphics graphics, ItemStack stack, int x, int y,
                                               @Nullable String text, Operation<Void> original) {
        if (!TerminalCarriedItemRenderer.defer(screen, graphics, stack, cursor -> original.call(screen, graphics, cursor, x, y, text)))
            original.call(screen, graphics, stack, x, y, text);
    }
}
