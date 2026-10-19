package com.fish_dan_.data_energistics.mixin.viewer.emi;

import com.fish_dan_.data_energistics.client.terminal.TerminalOverlayClient;
import com.fish_dan_.data_energistics.integration.viewer.emi.transfer.PlayerTerminalEmiRecipeTransfer;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.registry.EmiRecipeFiller;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EmiRecipeFiller.class, remap = false)
@NullMarked
public abstract class PlayerTerminalRecipeFillerMixin {

    @Inject(method = "getFirstValidHandler", at = @At("RETURN"), cancellable = true)
    private static <T extends AbstractContainerMenu> void dataEnergistics$useMiniEncoder(EmiRecipe recipe, @Nullable AbstractContainerScreen<T> screen,
                                                                                         CallbackInfoReturnable<EmiRecipeHandler<T>> cir) {
        if (screen != null && (TerminalOverlayClient.encodingWorkspace(screen.getMenu()) != null || TerminalOverlayClient.canSupplyRecipe(screen.getMenu())))
            cir.setReturnValue(new PlayerTerminalEmiRecipeTransfer<>(cir.getReturnValue()));
    }
}
