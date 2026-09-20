package com.fish_dan_.data_energistics.mixin.mekanismmore;

import com.fish_dan_.data_energistics.world.packaged.PackagedMachineClaims;

import net.minecraft.server.level.ServerLevel;

import com.jerry.meklm.common.tile.machine.TileEntityLargeAntiprotonicNucleosynthesizer;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.component.TileComponentEjector;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TileComponentEjector.class, remap = false)
abstract class LargeMachineItemEjectorMixin {

    @Shadow
    @Final
    private TileEntityMekanism tile;

    @Inject(method = "tickServer", at = @At("HEAD"), cancellable = true)
    private void retainNuclearProduct(CallbackInfo ci) {
        if (tile instanceof TileEntityLargeAntiprotonicNucleosynthesizer && tile.getLevel() instanceof ServerLevel level && !PackagedMachineClaims.get(level).available(tile.getBlockPos())) ci.cancel();
    }
}
