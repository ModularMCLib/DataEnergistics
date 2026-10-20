package com.fish_dan_.data_energistics.mixin.viewer.emi;

import com.fish_dan_.data_energistics.integration.viewer.emi.ingredient.PatternEncodingGenericStackEmiProvider;

import net.minecraft.client.gui.screens.Screen;

import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.registry.EmiStackProviders;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Resolve the floating terminal before EMI's per-screen and generic ingredient providers. */
@NullMarked
@Mixin(value = EmiStackProviders.class, remap = false)
public abstract class PlayerTerminalIngredientMixin {

    @Inject(method = "getStackAt", at = @At("HEAD"), cancellable = true)
    private static void dataEnergistics$resolveTerminalIngredient(Screen screen, int x, int y, boolean notClick,
                                                                  CallbackInfoReturnable<EmiStackInteraction> cir) {
        var interaction = PatternEncodingGenericStackEmiProvider.terminalInteraction(screen, x, y, notClick);
        if (interaction != null) cir.setReturnValue(interaction);
    }
}
