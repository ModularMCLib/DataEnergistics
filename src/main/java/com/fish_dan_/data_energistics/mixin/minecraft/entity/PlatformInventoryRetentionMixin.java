package com.fish_dan_.data_energistics.mixin.minecraft.entity;

import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.weapon.platform.retention.PlatformInventoryRetention;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameRules.BooleanValue;
import net.minecraft.world.level.GameRules.Key;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native keep-inventory branches preserve cursed equipment and experience without changing world gamerules. */
@Mixin(Player.class)
abstract class PlatformInventoryRetentionMixin {

    @WrapOperation(method = { "dropEquipment", "getBaseExperienceReward" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"))
    private boolean dataEnergistics$retainInventory(GameRules rules, Key<BooleanValue> key, Operation<Boolean> original) {
        return (Object) this instanceof ServerPlayer player && player.getData(DEAttachments.PLATFORM_RETAINED_INVENTORY).retained() || original.call(rules, key);
    }

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$retainMenuItems(ItemStack stack, boolean scatter, boolean includeThrower, CallbackInfoReturnable<@Nullable ItemEntity> cir) {
        if (!stack.isEmpty() && (Object) this instanceof ServerPlayer player && !player.isAlive() && player.getData(DEAttachments.PLATFORM_RETAINED_INVENTORY).retained()) {
            PlatformInventoryRetention.retain(player, stack);
            cir.setReturnValue(null);
        }
    }
}
