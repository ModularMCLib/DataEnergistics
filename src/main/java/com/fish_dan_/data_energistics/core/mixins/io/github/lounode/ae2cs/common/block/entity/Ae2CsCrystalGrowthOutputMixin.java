package com.fish_dan_.data_energistics.core.mixins.io.github.lounode.ae2cs.common.block.entity;

import com.fish_dan_.data_energistics.common.upgrade.production.MultiplicationCardOutput;
import com.fish_dan_.data_energistics.core.mixin.condition.RequireMods;

import appeng.api.upgrades.IUpgradeInventory;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.lounode.ae2cs.common.block.entity.CrystalGrowthChamberBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@RequireMods("ae2cs")
@Pseudo
@Mixin(CrystalGrowthChamberBlockEntity.class)
public abstract class Ae2CsCrystalGrowthOutputMixin {

    @Shadow
    public abstract IUpgradeInventory getUpgrades();

    @WrapOperation(
                   method = "serverTick",
                   at = @At(
                            value = "INVOKE",
                            target = "Lio/github/lounode/ae2cs/common/item/CrystalSeedItem;grow(Lnet/minecraft/world/item/ItemStack;I)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack dataEnergistics$multiplyMatureCrystals(ItemStack seed, int growth, Operation<ItemStack> original) {
        Item seedItem = seed.getItem();
        ItemStack result = original.call(seed, growth);
        // grow mutates seed progress in place; only the mature crystal changes the item identity.
        return result.getItem() == seedItem ? result : MultiplicationCardOutput.boostExternalOutput(result, this.getUpgrades());
    }
}
