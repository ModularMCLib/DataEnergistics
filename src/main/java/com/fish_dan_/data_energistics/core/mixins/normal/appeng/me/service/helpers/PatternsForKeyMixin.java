package com.fish_dan_.data_energistics.core.mixins.normal.appeng.me.service.helpers;

import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clears AE2's pattern-sort dirty flag only after the rebuilt immutable list has been published successfully.
 */
@Mixin(targets = "appeng.me.service.helpers.NetworkCraftingProviders$PatternsForKey", remap = false)
@NullMarked
public abstract class PatternsForKeyMixin {

    @Shadow(remap = false)
    private boolean needsSorting;

    @Inject(method = "sortPatterns", at = @At("RETURN"), remap = false, require = 1)
    private void dataEnergistics$markPatternsSorted(CallbackInfo ci) {
        this.needsSorting = false;
    }
}
