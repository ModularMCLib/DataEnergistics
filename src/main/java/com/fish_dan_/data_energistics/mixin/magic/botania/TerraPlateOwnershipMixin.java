package com.fish_dan_.data_energistics.mixin.magic.botania;

import com.fish_dan_.data_energistics.integration.crafting.packaged.magic.botania.BotaniaItemOwnership;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vazkii.botania.common.block.block_entity.TerrestrialAgglomerationPlateBlockEntity;

import java.util.List;

@Mixin(value = TerrestrialAgglomerationPlateBlockEntity.class, remap = false)
abstract class TerraPlateOwnershipMixin {

    @ModifyReturnValue(method = "getItemEntities", at = @At("RETURN"))
    private List<ItemEntity> ownedInput(List<ItemEntity> original) {
        return BotaniaItemOwnership.filter((BlockEntity) (Object) this, original);
    }
}
