package com.fish_dan_.data_energistics.core.mixins.com.glodblock.github.extendedae.util;

import com.fish_dan_.data_energistics.common.upgrade.production.MultiplicationCardOutput;
import com.fish_dan_.data_energistics.core.mixin.condition.RequireMods;

import appeng.api.inventories.InternalInventory;
import appeng.api.upgrades.IUpgradeableObject;

import net.minecraft.world.item.ItemStack;

import com.glodblock.github.extendedae.api.IRecipeMachine;
import com.glodblock.github.extendedae.common.tileentities.TileCircuitCutter;
import com.glodblock.github.extendedae.common.tileentities.TileCrystalAssembler;
import com.glodblock.github.extendedae.util.RecipeExecutor;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@RequireMods("extendedae")
@Pseudo
@Mixin(RecipeExecutor.class)
@NullMarked
public abstract class RecipeExecutorMixin {

    @Final
    @Shadow
    private IRecipeMachine<?, ?> machine;

    @ModifyExpressionValue(
                           method = "execute",
                           at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;copy()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack dataEnergistics$multiplyOutput(ItemStack output) {
        if ((this.machine instanceof TileCircuitCutter || this.machine instanceof TileCrystalAssembler) && this.machine instanceof IUpgradeableObject upgradeable) {
            // The executor uses this same copy for both simulation and committed insertion.
            return MultiplicationCardOutput.boostExternalOutput(output, upgradeable.getUpgrades());
        }
        return output;
    }

    @WrapOperation(
                   method = "execute",
                   at = @At(
                            value = "INVOKE",
                            target = "Lappeng/api/inventories/InternalInventory;insertItem(ILnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack dataEnergistics$insertCompleteOutput(InternalInventory inventory, int slot, ItemStack output,
                                                           boolean simulate, Operation<ItemStack> original) {
        if ((this.machine instanceof TileCircuitCutter || this.machine instanceof TileCrystalAssembler) && this.machine instanceof IUpgradeableObject upgradeable && MultiplicationCardOutput.isInstalled(upgradeable.getUpgrades())) {
            return MultiplicationCardOutput.insertExternalOutput(inventory, slot, output, simulate, upgradeable.getUpgrades());
        }
        return original.call(inventory, slot, output, simulate);
    }
}
