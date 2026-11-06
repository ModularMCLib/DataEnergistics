package com.fish_dan_.data_energistics.core.mixins.appeng.integration.modules.emi;

import com.fish_dan_.data_energistics.core.mixin.condition.RequireMods;
import com.fish_dan_.data_energistics.integration.viewer.emi.transfer.EmiEncodePatternHandlerMultiblockTransferGuard;

import dev.emi.emi.api.recipe.EmiRecipe;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@RequireMods("emi")
@Mixin(targets = "appeng.integration.modules.emi.AbstractRecipeHandler", remap = false)
@NullMarked
public abstract class AbstractRecipeHandlerMixin {

    /**
     * Defers typed multiblock recipes before AE2's inherited catch-all handler claims them.
     */
    @Inject(method = "supportsRecipe", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$deferTypedMultiblockRecipe(EmiRecipe recipe,
                                                            CallbackInfoReturnable<Boolean> cir) {
        if (EmiEncodePatternHandlerMultiblockTransferGuard.shouldDefer(this, recipe)) {
            cir.setReturnValue(false);
        }
    }
}
