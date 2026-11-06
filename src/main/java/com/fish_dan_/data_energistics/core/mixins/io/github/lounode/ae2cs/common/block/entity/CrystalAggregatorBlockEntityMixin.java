package com.fish_dan_.data_energistics.core.mixins.io.github.lounode.ae2cs.common.block.entity;

import com.fish_dan_.data_energistics.common.upgrade.production.MultiplicationCardOutput;
import com.fish_dan_.data_energistics.core.mixin.condition.RequireMods;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.util.inv.AppEngInternalInventory;

import net.minecraft.world.item.ItemStack;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.lounode.ae2cs.common.block.entity.CrystalAggregatorBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@RequireMods("ae2cs")
@Pseudo
@Mixin(CrystalAggregatorBlockEntity.class)
public abstract class CrystalAggregatorBlockEntityMixin {

    @Shadow
    public abstract IUpgradeInventory getUpgrades();

    @ModifyExpressionValue(
                           method = "serverTick",
                           at = @At(
                                    value = "INVOKE",
                                    target = "Lio/github/lounode/ae2cs/common/recipe/crystal_aggregator/CrystalAggregatorRecipe;assemble(Lio/github/lounode/ae2cs/common/recipe/input/ThreeItemStackRecipeInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack dataEnergistics$multiplyGeneratedOutput(ItemStack output) {
        return MultiplicationCardOutput.boostExternalOutput(output, this.getUpgrades());
    }

    @WrapOperation(
                   method = "serverTick",
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
}
