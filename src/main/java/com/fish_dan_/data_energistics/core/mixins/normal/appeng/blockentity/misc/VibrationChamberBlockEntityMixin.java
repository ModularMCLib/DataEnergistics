package com.fish_dan_.data_energistics.core.mixins.normal.appeng.blockentity.misc;

import com.fish_dan_.data_energistics.common.upgrade.production.MultiplicationCardOutput;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.blockentity.misc.VibrationChamberBlockEntity;

import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(VibrationChamberBlockEntity.class)
@NullMarked
public abstract class VibrationChamberBlockEntityMixin {

    @Shadow
    public abstract IUpgradeInventory getUpgrades();

    @ModifyArg(
               method = "tickingRequest",
               at = @At(
                        value = "INVOKE",
                        target = "Lappeng/api/networking/energy/IEnergyService;injectPower(DLappeng/api/config/Actionable;)D",
                        ordinal = 1),
               index = 0)
    private double dataEnergistics$multiplyGeneratedPower(double power) {
        // Only change committed generation, keeping the original fuel-rate calculations intact.
        return MultiplicationCardOutput.boostExternalPower(power, this.getUpgrades());
    }
}
