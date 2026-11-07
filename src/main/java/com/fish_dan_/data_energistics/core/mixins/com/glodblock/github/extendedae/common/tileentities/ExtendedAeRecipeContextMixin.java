package com.fish_dan_.data_energistics.core.mixins.com.glodblock.github.extendedae.common.tileentities;

import com.fish_dan_.data_energistics.common.upgrade.production.MultiplicationCardOutput;

import appeng.api.upgrades.IUpgradeableObject;
import appeng.util.inv.AppEngInternalInventory;

import net.minecraft.world.item.ItemStack;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Restriction(require = @Condition("extendedae"))
@Pseudo
@Mixin(targets = {
        "com.glodblock.github.extendedae.common.tileentities.TileCircuitCutter$CutterRecipeContext",
        "com.glodblock.github.extendedae.common.tileentities.TileCrystalAssembler$CrystalRecipeContext"
})
@NullMarked
public abstract class ExtendedAeRecipeContextMixin {

    @WrapOperation(
                   method = "testRecipe",
                   at = @At(
                            value = "INVOKE",
                            target = "Lappeng/util/inv/AppEngInternalInventory;insertItem(ILnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack dataEnergistics$simulateCompleteOutput(AppEngInternalInventory inventory, int slot, ItemStack output,
                                                             boolean simulate, Operation<ItemStack> original) {
        if (inventory.getHost() instanceof IUpgradeableObject upgradeable && MultiplicationCardOutput.isInstalled(upgradeable.getUpgrades())) {
            ItemStack boosted = MultiplicationCardOutput.boostExternalOutput(output, upgradeable.getUpgrades());
            return MultiplicationCardOutput.insertExternalOutput(inventory, slot, boosted, simulate, upgradeable.getUpgrades());
        }
        return original.call(inventory, slot, output, simulate);
    }
}
