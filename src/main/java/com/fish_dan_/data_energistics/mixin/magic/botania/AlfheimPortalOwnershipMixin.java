package com.fish_dan_.data_energistics.mixin.magic.botania;

import com.fish_dan_.data_energistics.integration.magic.botania.packaged.BotaniaItemOwnership;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vazkii.botania.common.block.block_entity.AlfheimPortalBlockEntity;

import java.util.List;

@Mixin(value = AlfheimPortalBlockEntity.class, remap = false)
abstract class AlfheimPortalOwnershipMixin {

    @ModifyExpressionValue(method = "commonTick",
                           at = @At(value = "INVOKE",
                                    target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private static List<ItemEntity> ownedInput(List<ItemEntity> original, Level level, BlockPos position,
                                               BlockState state, AlfheimPortalBlockEntity portal) {
        return BotaniaItemOwnership.filter(portal, original);
    }
}
