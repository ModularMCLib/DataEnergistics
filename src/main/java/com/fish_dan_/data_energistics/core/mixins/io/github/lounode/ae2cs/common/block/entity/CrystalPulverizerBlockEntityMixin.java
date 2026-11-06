package com.fish_dan_.data_energistics.core.mixins.io.github.lounode.ae2cs.common.block.entity;

import com.fish_dan_.data_energistics.common.upgrade.production.MultiplicationCardOutput;
import com.fish_dan_.data_energistics.core.mixin.condition.RequireMods;

import appeng.api.upgrades.IUpgradeInventory;

import net.minecraft.world.item.ItemStack;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.lounode.ae2cs.common.block.entity.CrystalPulverizerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@RequireMods("ae2cs")
@Pseudo
@Mixin(CrystalPulverizerBlockEntity.class)
public abstract class CrystalPulverizerBlockEntityMixin {

    @Shadow
    public abstract IUpgradeInventory getUpgrades();

    @ModifyExpressionValue(
                           method = "serverTick",
                           at = @At(
                                    value = "INVOKE",
                                    target = "Lio/github/lounode/ae2cs/common/recipe/crystal_pulverizer/CrystalPulverizerRecipe;assemble(Lnet/minecraft/world/item/crafting/SingleRecipeInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack dataEnergistics$multiplyGeneratedOutput(ItemStack output) {
        // The native addItems path simulates and then distributes this same result among four output slots.
        return MultiplicationCardOutput.boostExternalOutput(output, this.getUpgrades());
    }
}
