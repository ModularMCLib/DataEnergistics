package com.fish_dan_.data_energistics.mixin.minecraft.client.render.terminal;

import com.fish_dan_.data_energistics.client.terminal.magnet.TerminalRestockClient;

import appeng.client.gui.me.common.StackSizeRenderer;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces only the HUD hotbar count; container slot decorations keep their actual local amounts. */
@Mixin(value = Gui.class, priority = 1100)
public abstract class TerminalHotbarCountMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    // Run before AE2WT's cancellable decoration hook so the player terminal owns its enabled count.
    @Inject(method = "renderSlot",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/gui/GuiGraphics;renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V"),
            cancellable = true)
    private void dataEnergistics$networkCount(GuiGraphics graphics, int x, int y, DeltaTracker deltaTracker, Player player, ItemStack stack, int seed, CallbackInfo ci) {
        String count = TerminalRestockClient.count(stack);
        if (count == null) return;
        graphics.renderItemDecorations(minecraft.font, stack, x, y, "");
        StackSizeRenderer.renderSizeLabel(graphics, minecraft.font, x, y, count, false);
        ci.cancel();
    }
}
