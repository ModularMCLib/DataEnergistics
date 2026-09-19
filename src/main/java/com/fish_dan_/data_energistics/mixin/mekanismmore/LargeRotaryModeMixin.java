package com.fish_dan_.data_energistics.mixin.mekanismmore;

import com.fish_dan_.data_energistics.world.packaged.PackagedMachineClaims;

import net.minecraft.server.level.ServerLevel;

import com.jerry.meklm.common.tile.machine.TileEntityLargeRotaryCondensentrator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Holds a claimed rotary operation when its user-selected direction changes mid-cycle. */
@Mixin(value = TileEntityLargeRotaryCondensentrator.class, remap = false)
abstract class LargeRotaryModeMixin {

    @Inject(method = "onUpdateServer", at = @At("HEAD"), cancellable = true)
    private void waitForOriginalMode(CallbackInfoReturnable<Boolean> cir) {
        var tile = (TileEntityLargeRotaryCondensentrator) (Object) this;
        if (!(tile.getLevel() instanceof ServerLevel level)) return;
        var owner = PackagedMachineClaims.get(level).owner(tile.getBlockPos());
        var data = tile.getPersistentData();
        if (owner != null && data.hasUUID("de_packaged_rotary_owner") && owner.equals(data.getUUID("de_packaged_rotary_owner")) && data.getBoolean("de_packaged_rotary_mode") != tile.getMode()) cir.setReturnValue(false);
    }
}
