package com.fish_dan_.data_energistics.mixin.viewer.jei;

import com.fish_dan_.data_energistics.client.terminal.TerminalOverlayClient;
import com.fish_dan_.data_energistics.integration.viewer.jei.ingredient.PatternEncodingGenericStackJeiHandler;

import net.minecraft.client.gui.screens.Screen;

import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.library.gui.helpers.ScreenHelper;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.stream.Stream;

/** The floating terminal takes precedence over host ingredients regardless of plugin registration order. */
@NullMarked
@Mixin(value = ScreenHelper.class, remap = false)
public abstract class PlayerTerminalIngredientMixin {

    @Shadow
    @Final
    private IClickableIngredientFactory clickableIngredientFactory;

    @Inject(method = "getClickableIngredientUnderMouse", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$resolveTerminalIngredient(Screen screen, double mouseX, double mouseY,
                                                           CallbackInfoReturnable<Stream<IClickableIngredient<?>>> cir) {
        if (!TerminalOverlayClient.contains(screen, mouseX, mouseY)) return;
        var stack = TerminalOverlayClient.stackUnderMouse(screen, mouseX, mouseY);
        cir.setReturnValue(stack == null ? Stream.empty() : PatternEncodingGenericStackJeiHandler.createClickableIngredient(clickableIngredientFactory, stack).stream());
    }
}
