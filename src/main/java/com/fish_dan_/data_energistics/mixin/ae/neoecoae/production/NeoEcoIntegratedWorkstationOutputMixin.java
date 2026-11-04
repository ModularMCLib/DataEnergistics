package com.fish_dan_.data_energistics.mixin.ae.neoecoae.production;

import com.fish_dan_.data_energistics.common.upgrade.production.MultiplicationCardOutput;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.MachineUpgradesChanged;
import appeng.util.inv.AppEngInternalInventory;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import cn.dancingsnow.neoecoae.blocks.entity.ECOIntegratedWorkingStationBlockEntity;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(ECOIntegratedWorkingStationBlockEntity.class)
public abstract class NeoEcoIntegratedWorkstationOutputMixin {

    @Shadow
    @Final
    protected AppEngInternalInventory outputInv;

    @Shadow
    public abstract IUpgradeInventory getUpgrades();

    @ModifyArg(
               method = "<init>",
               at = @At(
                        value = "INVOKE",
                        target = "Lappeng/api/upgrades/UpgradeInventories;forMachine(Lnet/minecraft/world/level/ItemLike;ILappeng/api/upgrades/MachineUpgradesChanged;)Lappeng/api/upgrades/IUpgradeInventory;"),
               index = 2)
    private MachineUpgradesChanged dataEnergistics$wakeOnUpgradeChange(MachineUpgradesChanged original) {
        return () -> {
            original.onUpgradesChanged();
            ((ECOIntegratedWorkingStationBlockEntity) (Object) this).getMainNode()
                    .ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
        };
    }

    @ModifyExpressionValue(
                           method = { "hasCraftWork", "tickingRequest" },
                           at = @At(
                                    value = "INVOKE",
                                    target = "Lcn/dancingsnow/neoecoae/recipe/IntegratedWorkingStationRecipe;itemOutput()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack dataEnergistics$multiplyItemOutput(ItemStack output) {
        return MultiplicationCardOutput.boostExternalOutput(output, this.getUpgrades());
    }

    @ModifyExpressionValue(
                           method = { "hasCraftWork", "tickingRequest" },
                           at = @At(
                                    value = "INVOKE",
                                    target = "Lcn/dancingsnow/neoecoae/recipe/IntegratedWorkingStationRecipe;fluidOutput()Lnet/neoforged/neoforge/fluids/FluidStack;"))
    private FluidStack dataEnergistics$multiplyFluidOutput(FluidStack output) {
        return MultiplicationCardOutput.boostExternalOutput(output, this.getUpgrades());
    }

    @WrapOperation(
                   method = { "hasCraftWork", "tickingRequest" },
                   at = @At(
                            value = "INVOKE",
                            target = "Lappeng/util/inv/AppEngInternalInventory;insertItem(ILnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack dataEnergistics$insertCompleteOutput(AppEngInternalInventory inventory, int slot, ItemStack output,
                                                           boolean simulate, Operation<ItemStack> original) {
        if (inventory == this.outputInv && MultiplicationCardOutput.isInstalled(this.getUpgrades())) {
            return MultiplicationCardOutput.insertExternalOutput(inventory, slot, output, simulate, this.getUpgrades());
        }
        return original.call(inventory, slot, output, simulate);
    }

    @WrapOperation(
                   method = "pushOutResult",
                   at = @At(
                            value = "INVOKE",
                            target = "Lappeng/util/inv/AppEngInternalInventory;insertItem(ILnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack dataEnergistics$restoreExportRemainder(AppEngInternalInventory inventory, int slot, ItemStack remainder,
                                                             boolean simulate, Operation<ItemStack> original) {
        return inventory == this.outputInv ? MultiplicationCardOutput.restoreExternalOutput(inventory, slot, remainder, simulate) :
                original.call(inventory, slot, remainder, simulate);
    }
}
