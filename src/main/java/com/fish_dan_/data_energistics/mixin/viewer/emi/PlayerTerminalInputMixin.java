package com.fish_dan_.data_energistics.mixin.viewer.emi;

import com.fish_dan_.data_energistics.client.terminal.TerminalOverlayClient;
import com.fish_dan_.data_energistics.integration.viewer.emi.ingredient.PatternEncodingGenericStackEmiProvider;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.screens.Screen;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.stack.EmiStackInteraction;
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
        if (dataEnergistics$terminalButton != -1) {
            EmiPort.focus(EmiScreenManager.search, false);
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private static void dataEnergistics$keepTerminalRelease(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (dataEnergistics$terminalButton != button) return;
        dataEnergistics$terminalButton = -1;
        cir.setReturnValue(false);
    }

    @Inject(method = "getHoveredStack(IIZZ)Ldev/emi/emi/api/stack/EmiStackInteraction;", at = @At("HEAD"), cancellable = true)
    private static void dataEnergistics$useForegroundIngredient(int mouseX, int mouseY, boolean notClick, boolean ignoreLastHoveredCraftable,
                                                                CallbackInfoReturnable<EmiStackInteraction> cir) {
        var screen = Minecraft.getInstance().screen;
        if (screen == null) return;
        var interaction = PatternEncodingGenericStackEmiProvider.terminalInteraction(screen, mouseX, mouseY, notClick);
        if (interaction != null) cir.setReturnValue(interaction);
    }

    @Inject(method = "hasFocusedTextField", at = @At("HEAD"), cancellable = true)
    private static void dataEnergistics$useForegroundTextFocus(ContainerEventHandler parent, int depthBail, CallbackInfoReturnable<Boolean> cir) {
        if (!(parent instanceof Screen screen)) return;
        Boolean focused = TerminalOverlayClient.recipeViewerTextInputFocus(screen);
        if (focused != null) cir.setReturnValue(focused);
    }
}
