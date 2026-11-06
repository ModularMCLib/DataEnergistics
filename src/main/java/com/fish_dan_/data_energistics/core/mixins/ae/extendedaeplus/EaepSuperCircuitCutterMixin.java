package com.fish_dan_.data_energistics.core.mixins.ae.extendedaeplus;

import com.fish_dan_.data_energistics.common.upgrade.production.MultiplicationCardOutput;
import com.fish_dan_.data_energistics.core.mixin.condition.RequireMods;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.util.inv.AppEngInternalInventory;

import net.minecraft.world.item.ItemStack;

import com.extendedae_plus.content.cutter.SuperCircuitCutterBlockEntity;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@RequireMods("extendedae_plus")
@Pseudo
@Mixin(SuperCircuitCutterBlockEntity.class)
public abstract class EaepSuperCircuitCutterMixin {

    @Shadow
    public abstract IUpgradeInventory getUpgrades();

    @Shadow
    public abstract AppEngInternalInventory getOutput();

    @Inject(method = "getOutputCapacity", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$expandedOutputCapacity(ItemStack output, CallbackInfoReturnable<Integer> cir) {
        if (!MultiplicationCardOutput.isInstalled(this.getUpgrades())) {
            return;
        }
        ItemStack current = this.getOutput().getStackInSlot(0);
        if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, output)) {
            cir.setReturnValue(0);
            return;
        }
        int capacity = MultiplicationCardOutput.externalOutputCapacity(this.getOutput(), 0, output, this.getUpgrades());
        cir.setReturnValue(Math.max(0, capacity - current.getCount()) / output.getCount());
    }

    @WrapOperation(
                   method = "tickingRequest",
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

    @ModifyExpressionValue(
                           method = { "getParallelOperations", "createOutput" },
                           at = @At(value = "INVOKE", target = "Lcom/extendedae_plus/recipe/SuperCircuitCutterRecipe;output()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack dataEnergistics$multiplyOutput(ItemStack output) {
        // Round once per logical recipe, before the machine multiplies by its parallel count.
        return MultiplicationCardOutput.boostExternalOutput(output, this.getUpgrades());
    }
}
