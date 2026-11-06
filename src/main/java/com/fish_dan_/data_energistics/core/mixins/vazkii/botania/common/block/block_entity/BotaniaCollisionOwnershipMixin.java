package com.fish_dan_.data_energistics.core.mixins.vazkii.botania.common.block.block_entity;

import com.fish_dan_.data_energistics.core.mixin.condition.RequireMods;
import com.fish_dan_.data_energistics.integration.magic.botania.packaged.BotaniaItemOwnership;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vazkii.botania.common.block.block_entity.PetalApothecaryBlockEntity;
import vazkii.botania.common.block.block_entity.mana.ManaPoolBlockEntity;

@RequireMods("botania")
@Mixin(value = { PetalApothecaryBlockEntity.class, ManaPoolBlockEntity.class }, remap = false)
abstract class BotaniaCollisionOwnershipMixin {

    @Inject(method = "collideEntityItem", at = @At("HEAD"), cancellable = true)
    private void ownedInput(ItemEntity item, CallbackInfoReturnable<Boolean> callback) {
        if (!BotaniaItemOwnership.accepts((BlockEntity) (Object) this, item)) callback.setReturnValue(false);
    }
}
