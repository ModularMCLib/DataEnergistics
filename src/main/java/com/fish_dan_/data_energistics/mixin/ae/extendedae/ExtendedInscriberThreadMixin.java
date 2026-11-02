package com.fish_dan_.data_energistics.mixin.ae.extendedae;

import com.fish_dan_.data_energistics.common.data.DEItems;
import com.fish_dan_.data_energistics.common.upgrade.production.MultiplicationCardOutput;

import appeng.api.networking.ticking.TickRateModulation;
import appeng.recipes.handlers.InscriberRecipe;
import appeng.util.inv.AppEngInternalInventory;

import net.minecraft.world.item.ItemStack;

import com.glodblock.github.extendedae.common.me.InscriberThread;
import com.glodblock.github.extendedae.common.tileentities.TileExInscriber;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(InscriberThread.class)
public abstract class ExtendedInscriberThreadMixin {

    @Final
    @Shadow
    private TileExInscriber host;

    @Final
    @Shadow
    private AppEngInternalInventory sideItemHandler;

    @Shadow
    public abstract InscriberRecipe getTask();

    @ModifyExpressionValue(
            method = {"hasCraftWork", "tick"},
            at = @At(value = "INVOKE", target = "Lappeng/recipes/handlers/InscriberRecipe;getResultItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack dataEnergistics$multiplyOutput(ItemStack output) {
        return MultiplicationCardOutput.boostExternalOutput(output, this.host.getUpgrades());
    }

    @WrapOperation(
            method = {"hasCraftWork", "tick"},
            at = @At(
                     value = "INVOKE",
                     target = "Lappeng/util/inv/AppEngInternalInventory;insertItem(ILnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack dataEnergistics$insertCompleteOutput(AppEngInternalInventory inventory, int slot, ItemStack output,
                                                        boolean simulate, Operation<ItemStack> original) {
        if (MultiplicationCardOutput.isInstalled(this.host.getUpgrades())) {
            return MultiplicationCardOutput.insertExternalOutput(inventory, slot, output, simulate, this.host.getUpgrades());
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

    @Unique
    private ItemStack dataEnergistics$templateSnapshot = ItemStack.EMPTY;

    @Inject(method = "tick", at = @At("HEAD"))
    private void dataEnergistics$captureTemplate(CallbackInfoReturnable<TickRateModulation> cir) {
        this.dataEnergistics$templateSnapshot = ItemStack.EMPTY;

        InscriberRecipe task = this.getTask();
        if (task == null || !task.getResultItem().is(DEItems.DATA_CIRCUIT_BOARD.get())) {
            return;
        }

        ItemStack stack = this.sideItemHandler.getStackInSlot(0);
        if (stack.is(DEItems.DATA_INSCRIBER_TEMPLATE.get())) {
            this.dataEnergistics$templateSnapshot = stack.copy();
        }
    }

    @Inject(
            method = "tick",
            at = @At(
                     value = "INVOKE",
                     target = "Lappeng/util/inv/AppEngInternalInventory;extractItem(IIZ)Lnet/minecraft/world/item/ItemStack;",
                     ordinal = 2,
                     shift = At.Shift.AFTER))
    private void dataEnergistics$restoreTemplate(CallbackInfoReturnable<TickRateModulation> cir) {
        if (this.dataEnergistics$templateSnapshot.isEmpty()) {
            return;
        }

        if (!ItemStack.isSameItemSameComponents(this.sideItemHandler.getStackInSlot(0), this.dataEnergistics$templateSnapshot)) {
            this.sideItemHandler.setItemDirect(0, this.dataEnergistics$templateSnapshot.copy());
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void dataEnergistics$clearSnapshot(CallbackInfoReturnable<TickRateModulation> cir) {
        this.dataEnergistics$templateSnapshot = ItemStack.EMPTY;
    }
}
