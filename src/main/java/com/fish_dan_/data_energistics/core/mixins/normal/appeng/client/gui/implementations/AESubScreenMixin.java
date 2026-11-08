package com.fish_dan_.data_energistics.core.mixins.normal.appeng.client.gui.implementations;

import com.fish_dan_.data_energistics.client.terminal.TerminalOverlayClient;

import appeng.client.gui.implementations.AESubScreen;

import net.minecraft.client.Minecraft;

import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps AE2's original Back buttons while ordering the terminal-parent return with this session protocol.
 */
@Mixin(value = AESubScreen.class, remap = false)
@NullMarked
public abstract class AESubScreenMixin {

    @Inject(method = "goBack", at = @At("HEAD"), cancellable = true)
    private static void dataEnergistics$returnToPlayerTerminal(CallbackInfo callback) {
        if (TerminalOverlayClient.requestTerminalReturn(Minecraft.getInstance().screen)) callback.cancel();
    }
}
