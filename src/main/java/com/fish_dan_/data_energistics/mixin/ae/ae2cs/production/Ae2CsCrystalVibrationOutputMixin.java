package com.fish_dan_.data_energistics.mixin.ae.ae2cs.production;

import com.fish_dan_.data_energistics.common.upgrade.production.MultiplicationCardOutput;

import appeng.api.upgrades.IUpgradeInventory;

import io.github.lounode.ae2cs.common.block.entity.CrystalVibrationChamberBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(CrystalVibrationChamberBlockEntity.class)
public abstract class Ae2CsCrystalVibrationOutputMixin {

    @Shadow
    public abstract IUpgradeInventory getUpgrades();

    @ModifyArg(
            method = "serverTick",
            at = @At(
                     value = "INVOKE",
                     target = "Lio/github/lounode/ae2cs/common/block/entity/CrystalVibrationChamberBlockEntity;outputGeneratedPower(D)V"),
            index = 0)
    private double dataEnergistics$multiplyGeneratedPower(double amount) {
        return MultiplicationCardOutput.boostExternalPower(amount, this.getUpgrades());
    }
}
