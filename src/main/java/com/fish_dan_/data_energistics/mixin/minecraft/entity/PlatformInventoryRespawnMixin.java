package com.fish_dan_.data_energistics.mixin.minecraft.entity;

import com.fish_dan_.data_energistics.common.data.DEAttachments;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameRules.BooleanValue;
import net.minecraft.world.level.GameRules.Key;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Only inventory and experience follow the previous life; health, effects and respawn remain native. */
@Mixin(ServerPlayer.class)
abstract class PlatformInventoryRespawnMixin {

    @WrapOperation(method = "restoreFrom", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"))
    private boolean dataEnergistics$restoreInventory(GameRules rules, Key<BooleanValue> key, Operation<Boolean> original, @Local(argsOnly = true) ServerPlayer previous) {
        return previous.getData(DEAttachments.PLATFORM_RETAINED_INVENTORY).retained() || original.call(rules, key);
    }
}
