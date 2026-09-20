package com.fish_dan_.data_energistics.mixin.technology.mekanismmore.appmek;

import com.fish_dan_.data_energistics.world.packaged.PackagedMachineClaims;

import net.minecraft.server.level.ServerLevel;

import com.jerry.meklm.common.tile.machine.TileEntityLargeElectrolyticSeparator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player dumping-mode changes cannot delete an in-flight operation's chemical products. */
@Mixin(value = TileEntityLargeElectrolyticSeparator.class, remap = false)
abstract class LargeSeparatorDumpingMixin {

    @Inject(method = "handleTank", at = @At("HEAD"), cancellable = true)
    private void retainClaimedChemicals(CallbackInfo ci) {
        var tile = (TileEntityLargeElectrolyticSeparator) (Object) this;
        if (tile.getLevel() instanceof ServerLevel level && !PackagedMachineClaims.get(level).available(tile.getBlockPos())) ci.cancel();
    }
}
