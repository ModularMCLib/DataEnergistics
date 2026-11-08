package com.fish_dan_.data_energistics.core.mixins.normal.appeng.menu.me.networktool;

import com.fish_dan_.data_energistics.common.blockentity.machine.DataExtractorBlockEntity;

import appeng.api.networking.IGridNode;
import appeng.menu.me.networktool.NetworkStatus;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(NetworkStatus.class)
@NullMarked
public abstract class NetworkStatusMixin {

    @WrapOperation(
                   method = "fromGrid",
                   at = @At(
                            value = "INVOKE",
                            target = "Lappeng/api/networking/IGridNode;getIdlePowerUsage()D"))
    private static double dataEnergistics$getDisplayedIdlePowerUsage(IGridNode machine, Operation<Double> original) {
        Object owner = machine.getOwner();
        if (owner instanceof DataExtractorBlockEntity extractor) {
            return extractor.getTargetCount() > 0 ? DataExtractorBlockEntity.AE_POWER_PER_TICK : 0.0;
        }

        return original.call(machine);
    }
}
