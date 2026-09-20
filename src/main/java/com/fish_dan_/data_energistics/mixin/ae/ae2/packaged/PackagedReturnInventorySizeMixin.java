package com.fish_dan_.data_energistics.mixin.ae.ae2.packaged;

import com.fish_dan_.data_energistics.ae2.patternprovider.packaged.PackagedReturnInventory;

import appeng.api.stacks.GenericStack;
import appeng.helpers.externalstorage.GenericStackInv;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sizes only this provider's fresh inventory, without changing AE2's shared slot-count field. */
@Mixin(GenericStackInv.class)
public abstract class PackagedReturnInventorySizeMixin {

    @Shadow
    @Final
    @Mutable
    protected GenericStack[] stacks;

    @Inject(method = "<init>(Ljava/util/Set;Ljava/lang/Runnable;Lappeng/helpers/externalstorage/GenericStackInv$Mode;I)V",
            at = @At("RETURN"))
    private void dataEnergistics$packagedReturnSlots(CallbackInfo ci) {
        if ((Object) this instanceof PackagedReturnInventory) {
            this.stacks = new GenericStack[PackagedReturnInventory.SLOT_COUNT];
        }
    }
}
