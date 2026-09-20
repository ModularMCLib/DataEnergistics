package com.fish_dan_.data_energistics.mixin.mekanismmore;

import com.fish_dan_.data_energistics.world.packaged.PackagedMachineClaims;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.jerry.meklm.common.tile.machine.TileEntityLargeChemicalInfuser;
import com.jerry.meklm.common.tile.machine.TileEntityLargeElectrolyticSeparator;
import com.jerry.meklm.common.tile.machine.TileEntityLargePigmentMixer;
import com.jerry.meklm.common.tile.machine.TileEntityLargeRotaryCondensentrator;
import com.jerry.meklm.common.tile.machine.TileEntityLargeSolarNeutronActivator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Retains real products in the native tanks until their owning provider has extracted them. */
@Mixin(value = { TileEntityLargeChemicalInfuser.class, TileEntityLargePigmentMixer.class,
        TileEntityLargeSolarNeutronActivator.class, TileEntityLargeElectrolyticSeparator.class,
        TileEntityLargeRotaryCondensentrator.class },
       remap = false)
abstract class LargeMachineOutputOwnershipMixin {

    @Inject(method = "handleEject", at = @At("HEAD"), cancellable = true)
    private void retainClaimedOutputs(CallbackInfo ci) {
        var tile = (BlockEntity) (Object) this;
        if (tile.getLevel() instanceof ServerLevel level && !PackagedMachineClaims.get(level).available(tile.getBlockPos())) ci.cancel();
    }
}
