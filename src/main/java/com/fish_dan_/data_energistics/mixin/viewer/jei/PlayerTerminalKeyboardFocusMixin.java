package com.fish_dan_.data_energistics.mixin.viewer.jei;

import com.fish_dan_.data_energistics.client.terminal.TerminalOverlayClient;

import net.minecraft.client.gui.screens.Screen;

import mezz.jei.gui.input.ClientInputHandler;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native bookmark, recipe, usage and chat-link bindings use the editor in the foreground. */
@NullMarked
@Mixin(value = ClientInputHandler.class, remap = false)
public abstract class PlayerTerminalKeyboardFocusMixin {

    @Inject(method = "isContainerTextFieldFocused", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$useForegroundTextFocus(Screen screen, CallbackInfoReturnable<Boolean> cir) {
        Boolean focused = TerminalOverlayClient.recipeViewerTextInputFocus(screen);
        if (focused != null) cir.setReturnValue(focused);
    }
}
