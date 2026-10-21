package com.fish_dan_.data_energistics.mixin.minecraft.client.render.terminal;

import com.fish_dan_.data_energistics.client.terminal.magnet.TerminalRestockClient;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Replaces only the HUD hotbar count; container slot decorations keep their actual local amounts. */
@Mixin(Gui.class)
public abstract class TerminalHotbarCountMixin {

    @WrapOperation(method = "renderSlot", at = @At(value = "INVOKE",
                                                 target = "Lnet/minecraft/client/gui/GuiGraphics;renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V"))
    private void dataEnergistics$networkCount(GuiGraphics graphics, Font font, ItemStack stack, int x, int y, Operation<Void> original) {
        String count = TerminalRestockClient.count(stack);
        if (count == null) original.call(graphics, font, stack, x, y);
        else graphics.renderItemDecorations(font, stack, x, y, count);
    }
}
