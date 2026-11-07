package com.fish_dan_.data_energistics.core.mixins.net.minecraft.world.entity.player;

import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.weapon.platform.ability.PlatformFlightMining;
import com.fish_dan_.data_energistics.common.weapon.platform.ability.PlatformMotion;
import com.fish_dan_.data_energistics.common.weapon.platform.retention.PlatformInventoryRetention;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameRules.BooleanValue;
import net.minecraft.world.level.GameRules.Key;
import net.minecraft.world.phys.Vec3;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native keep-inventory branches preserve cursed equipment and experience without changing world gamerules. */
@Mixin(Player.class)
abstract class PlayerMixin {

    @WrapOperation(method = { "dropEquipment", "getBaseExperienceReward" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"))
    private boolean dataEnergistics$retainInventory(GameRules rules, Key<BooleanValue> key, Operation<Boolean> original) {
        return (Player) (Object) this instanceof ServerPlayer player && player.getData(DEAttachments.PLATFORM_RETAINED_INVENTORY).retained() || original.call(rules, key);
    }

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$retainMenuItems(ItemStack stack, boolean scatter, boolean includeThrower, CallbackInfoReturnable<@Nullable ItemEntity> cir) {
        if (PlatformInventoryRetention.retainMenuItem((Player) (Object) this, stack)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "travel(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$inputOnly(Vec3 input, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (!player.hasData(DEAttachments.PLATFORM_MOTION)) return;
        var state = player.getData(DEAttachments.PLATFORM_MOTION);
        if (player.isPassenger()) {
            state.flightVelocity = Vec3.ZERO;
            return;
        }
        if (!state.locked && !(state.flying && player.getAbilities().flying)) {
            state.flightVelocity = Vec3.ZERO;
            return;
        }
        state.permits++;
        try {
            player.setDeltaMovement(PlatformMotion.travelMotion(player, state.input));
            if (player.isLocalPlayer()) {
                player.move(MoverType.SELF, player.getDeltaMovement());
                state.flightVelocity = player.getDeltaMovement();
            }
            // Noclip movement skips vanilla's collision branch and otherwise retains its old grounded flag.
            if (player.noPhysics) player.setOnGround(false);
            player.resetFallDistance();
            player.calculateEntityAnimation(false);
        } finally {
            state.permits--;
        }
        ci.cancel();
    }

    @WrapOperation(method = "getDigSpeed", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;hasEffect(Lnet/minecraft/core/Holder;)Z"))
    private boolean dataEnergistics$miningFatigue(Player player, Holder<MobEffect> effect, Operation<Boolean> original) {
        return !(effect == MobEffects.DIG_SLOWDOWN && PlatformFlightMining.immune(player)) && original.call(player, effect);
    }

    @WrapOperation(method = "getDigSpeed", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
    private double dataEnergistics$miningAttributes(Player player, Holder<Attribute> attribute, Operation<Double> original) {
        double value = original.call(player, attribute);
        return attribute == Attributes.BLOCK_BREAK_SPEED && PlatformFlightMining.immune(player) ? PlatformFlightMining.withoutNegativeModifiers(player.getAttribute(attribute), value) : value;
    }

    @WrapOperation(method = "getDigSpeed", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;getValue()D"))
    private double dataEnergistics$submergedMining(AttributeInstance attribute, Operation<Double> original) {
        double value = original.call(attribute);
        return PlatformFlightMining.immune((Player) (Object) this) ? Math.max(1, value) : value;
    }

    @WrapOperation(method = "getDigSpeed", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;onGround()Z"))
    private boolean dataEnergistics$airborneMining(Player player, Operation<Boolean> original) {
        return PlatformFlightMining.immune(player) || original.call(player);
    }

    @WrapOperation(method = "actuallyHurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setHealth(F)V"))
    private void dataEnergistics$settledHealth(Player target, float health, Operation<Void> original) {
        if (!(target instanceof ServerPlayer player)) {
            original.call(target, health);
            return;
        }
        var writes = player.getData(DEAttachments.SHIELD_HEALTH_WRITES);
        writes.settled++;
        try {
            original.call(target, health);
        } finally {
            writes.settled--;
        }
    }
}
