package com.fish_dan_.data_energistics.core.mixins.ae.ae2cs.production;

import com.fish_dan_.data_energistics.common.upgrade.production.MultiplicationCardOutput;

import appeng.api.inventories.BaseInternalInventory;

import net.minecraft.world.item.ItemStack;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.lounode.ae2cs.common.block.entity.CircuitEtcherBlockEntity;
import io.github.lounode.ae2cs.common.block.entity.CrystalAggregatorBlockEntity;
import io.github.lounode.ae2cs.common.block.entity.CrystalGrowthChamberBlockEntity;
import io.github.lounode.ae2cs.common.block.entity.CrystalPulverizerBlockEntity;
import io.github.lounode.ae2cs.common.machine.MachineComponentContainer;
import io.github.lounode.ae2cs.common.machine.component.SideConfigComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(SideConfigComponent.class)
public abstract class Ae2CsOutputExportMixin {

    @Shadow
    private MachineComponentContainer container;

    @WrapOperation(
                   method = "tickAppEngInv",
                   at = @At(
                            value = "INVOKE",
                            target = "Lappeng/api/inventories/BaseInternalInventory;addItems(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;",
                            ordinal = 0))
    private ItemStack dataEnergistics$restoreRejectedExport(BaseInternalInventory inventory, ItemStack remainder, boolean simulate,
                                                            Operation<ItemStack> original, @Local(index = 16) int sourceSlot) {
        var host = this.container.host();
        if (host instanceof CrystalAggregatorBlockEntity || host instanceof CircuitEtcherBlockEntity || host instanceof CrystalPulverizerBlockEntity || host instanceof CrystalGrowthChamberBlockEntity) {
            // This is only the rollback immediately after extracting sourceSlot for automatic export.
            return MultiplicationCardOutput.restoreExternalOutput(inventory, sourceSlot, remainder, simulate);
        }
        return original.call(inventory, remainder, simulate);
    }
}
