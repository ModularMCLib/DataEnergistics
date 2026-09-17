package com.fish_dan_.data_energistics.mixin.core.crafting;

import appeng.api.stacks.AEItemKey;
import appeng.crafting.pattern.AECraftingPattern;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps component-sensitive ingredient validation out of AE2's cache indexed only by the registered item. */
@Mixin(AECraftingPattern.class)
public abstract class AECraftingPatternComponentCacheMixin {

    @Inject(method = "getTestResult", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$validateComponentVariant(int slot, @Nullable AEItemKey key,
                                                          CallbackInfoReturnable<Boolean> cir) {
        // AE2 19.2.17's hasComponents() does not correctly identify a stack's component patch.
        if (key != null && !key.getReadOnlyStack().getComponentsPatch().isEmpty()) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "setTestResult", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$avoidCachingComponentVariant(int slot, @Nullable AEItemKey key, boolean result,
                                                              CallbackInfo ci) {
        if (key != null && !key.getReadOnlyStack().getComponentsPatch().isEmpty()) {
            ci.cancel();
        }
    }
}
