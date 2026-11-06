package com.fish_dan_.data_energistics.core.mixins.ae.advancedae;

import com.fish_dan_.data_energistics.common.upgrade.production.MultiplicationCardOutput;
import com.fish_dan_.data_energistics.core.mixin.condition.RequireMods;

import appeng.api.stacks.AEFluidKey;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.MachineUpgradesChanged;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.util.inv.AppEngInternalInventory;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.pedroksl.advanced_ae.common.entities.ReactionChamberEntity;
import net.pedroksl.advanced_ae.recipes.ReactionChamberRecipe;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@RequireMods("advanced_ae")
@Pseudo
@Mixin(ReactionChamberEntity.class)
public abstract class AdvancedAeReactionChamberMixin {

    @Shadow
    public abstract IUpgradeInventory getUpgrades();

    @Shadow
    public abstract GenericStackInv getTank();

    @Shadow
    public abstract @Nullable ReactionChamberRecipe getTask();

    @ModifyArg(
               method = "<init>",
               at = @At(
                        value = "INVOKE",
                        target = "Lappeng/api/upgrades/UpgradeInventories;forMachine(Lnet/minecraft/world/level/ItemLike;ILappeng/api/upgrades/MachineUpgradesChanged;)Lappeng/api/upgrades/IUpgradeInventory;"),
               index = 2)
    private MachineUpgradesChanged dataEnergistics$wakeOnUpgradeChange(MachineUpgradesChanged original) {
        return () -> {
            original.onUpgradesChanged();
            ((ReactionChamberEntity) (Object) this).getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
        };
    }

    @Inject(method = "hasCraftWork", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$checkFluidOutputCapacity(CallbackInfoReturnable<Boolean> cir) {
        if (!MultiplicationCardOutput.isInstalled(this.getUpgrades())) {
            return;
        }
        ReactionChamberRecipe task = this.getTask();
        if (task != null && !task.isItemOutput()) {
            FluidStack output = MultiplicationCardOutput.boostExternalOutput(task.getResultFluid(), this.getUpgrades());
            // AAE's canAdd accepts an empty output tank without checking its maximum capacity.
            if (output.getAmount() > this.getTank().getMaxAmount(AEFluidKey.of(output))) {
                cir.setReturnValue(false);
            }
        }
    }

    @ModifyExpressionValue(
                           method = { "hasCraftWork", "tickingRequest" },
                           at = @At(value = "INVOKE", target = "Lnet/pedroksl/advanced_ae/recipes/ReactionChamberRecipe;getResultItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack dataEnergistics$multiplyItemOutput(ItemStack output) {
        return MultiplicationCardOutput.boostExternalOutput(output, this.getUpgrades());
    }

    @WrapOperation(
                   method = { "hasCraftWork", "tickingRequest" },
                   at = @At(
                            value = "INVOKE",
                            target = "Lappeng/util/inv/AppEngInternalInventory;insertItem(ILnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack dataEnergistics$insertCompleteOutput(AppEngInternalInventory inventory, int slot, ItemStack output,
                                                           boolean simulate, Operation<ItemStack> original) {
        if (MultiplicationCardOutput.isInstalled(this.getUpgrades())) {
            return MultiplicationCardOutput.insertExternalOutput(inventory, slot, output, simulate, this.getUpgrades());
        }
        return original.call(inventory, slot, output, simulate);
    }

    @WrapOperation(
                   method = "pushOutResult",
                   at = @At(
                            value = "INVOKE",
                            target = "Lappeng/util/inv/AppEngInternalInventory;insertItem(ILnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack dataEnergistics$restoreExportRemainder(AppEngInternalInventory inventory, int slot, ItemStack output,
                                                             boolean simulate, Operation<ItemStack> original) {
        return MultiplicationCardOutput.restoreExternalOutput(inventory, slot, output, simulate);
    }

    @ModifyExpressionValue(
                           method = { "hasCraftWork", "tickingRequest" },
                           at = @At(value = "INVOKE", target = "Lnet/pedroksl/advanced_ae/recipes/ReactionChamberRecipe;getResultFluid()Lnet/neoforged/neoforge/fluids/FluidStack;"))
    private FluidStack dataEnergistics$multiplyFluidOutput(FluidStack output) {
        return MultiplicationCardOutput.boostExternalOutput(output, this.getUpgrades());
    }
}
