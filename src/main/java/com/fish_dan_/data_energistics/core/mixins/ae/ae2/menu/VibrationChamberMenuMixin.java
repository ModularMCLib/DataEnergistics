package com.fish_dan_.data_energistics.core.mixins.ae.ae2.menu;

import com.fish_dan_.data_energistics.common.upgrade.production.MultiplicationCardOutput;

import appeng.blockentity.misc.VibrationChamberBlockEntity;
import appeng.menu.implementations.VibrationChamberMenu;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(VibrationChamberMenu.class)
public abstract class VibrationChamberMenuMixin {

    @Shadow
    @Final
    private VibrationChamberBlockEntity vibrationChamber;

    @ModifyReturnValue(method = { "getPowerPerTick", "getFuelEfficiency" }, at = @At("RETURN"))
    private double dataEnergistics$displayMultipliedGeneration(double original) {
        return MultiplicationCardOutput.boostExternalPower(original, this.vibrationChamber.getUpgrades());
    }
}
