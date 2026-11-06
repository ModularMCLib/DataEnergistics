package com.fish_dan_.data_energistics.core.mixins.guideme.internal.screen;

import com.fish_dan_.data_energistics.core.mixin.condition.RequireMods;
import com.fish_dan_.data_energistics.integration.guide.guideme.client.GuideNavBarHierarchySupport;

import guideme.document.LytRect;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@RequireMods("guideme")
@Mixin(targets = "guideme.internal.screen.GuideNavBar$Row", remap = false)
@NullMarked
public abstract class RowMixin {

    @Inject(method = "isVisible()Z", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$checkAllParents(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(GuideNavBarHierarchySupport.isVisible(this));
    }

    @Inject(method = "getBounds", at = @At("RETURN"), cancellable = true)
    private void dataEnergistics$indentNestedBounds(CallbackInfoReturnable<LytRect> cir) {
        cir.setReturnValue(GuideNavBarHierarchySupport.indentBounds(this, cir.getReturnValue()));
    }
}
