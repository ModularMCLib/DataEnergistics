package com.fish_dan_.data_energistics.mixin.botania;

import com.fish_dan_.data_energistics.integration.crafting.packaged.botania.BotaniaItemOwnership;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vazkii.botania.common.block.block_entity.RunicAltarBlockEntity;

import java.util.List;

@Mixin(value = RunicAltarBlockEntity.class, remap = false)
abstract class RunicAltarOwnershipMixin {

    @ModifyExpressionValue(method = "serverTick",
                           at = @At(value = "INVOKE",
                                    target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private static List<ItemEntity> ownedInputs(List<ItemEntity> original, Level level, BlockPos position,
                                                BlockState state, RunicAltarBlockEntity altar) {
        return BotaniaItemOwnership.filter(altar, original);
    }

    @ModifyExpressionValue(method = "onUsedByWand",
                           at = @At(value = "INVOKE",
                                    target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private List<ItemEntity> ownedReagent(List<ItemEntity> original) {
        return BotaniaItemOwnership.filter((BlockEntity) (Object) this, original);
    }
}
