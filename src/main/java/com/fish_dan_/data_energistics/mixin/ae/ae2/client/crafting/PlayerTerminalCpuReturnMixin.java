package com.fish_dan_.data_energistics.mixin.ae.ae2.client.crafting;

import com.fish_dan_.data_energistics.client.terminal.TerminalOverlayClient;

import appeng.client.gui.implementations.AESubScreen;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the original CPU Back button while ordering its return with this terminal's session protocol.
 */
@Mixin(value = AESubScreen.class, remap = false)
public abstract class PlayerTerminalCpuReturnMixin {

    @Inject(method = "goBack", at = @At("HEAD"), cancellable = true)
    private static void dataEnergistics$returnToPlayerTerminal(CallbackInfo callback) {
        if (TerminalOverlayClient.requestCpuReturn(Minecraft.getInstance().screen)) callback.cancel();
    }
}
