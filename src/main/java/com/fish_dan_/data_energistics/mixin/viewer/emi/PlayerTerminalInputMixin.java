package com.fish_dan_.data_energistics.mixin.viewer.emi;

import com.fish_dan_.data_energistics.client.terminal.TerminalOverlayClient;

import net.minecraft.client.Minecraft;

import dev.emi.emi.screen.EmiScreenManager;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Retains terminal mouse gestures even when EMI's event listener runs first. Sidebar drags keep EMI's lifecycle. */
@NullMarked
@Mixin(value = EmiScreenManager.class, remap = false)
public abstract class PlayerTerminalInputMixin {

    @Unique
    private static int dataEnergistics$terminalButton = -1;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private static void dataEnergistics$keepTerminalPress(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        var screen = Minecraft.getInstance().screen;
        dataEnergistics$terminalButton = screen != null && TerminalOverlayClient.ownsMouseButton(screen, mouseX, mouseY, button) ? button : -1;
        if (dataEnergistics$terminalButton != -1) cir.setReturnValue(false);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private static void dataEnergistics$keepTerminalRelease(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (dataEnergistics$terminalButton != button) return;
        dataEnergistics$terminalButton = -1;
        cir.setReturnValue(false);
    }
}
