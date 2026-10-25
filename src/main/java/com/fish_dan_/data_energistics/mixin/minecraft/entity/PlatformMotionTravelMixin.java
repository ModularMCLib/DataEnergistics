package com.fish_dan_.data_energistics.mixin.minecraft.entity;

import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.weapon.platform.ability.PlatformMotion;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class PlatformMotionTravelMixin {

    @Inject(method = "travel(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$inputOnly(Vec3 input, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!(entity instanceof Player player) || !player.hasData(DEAttachments.PLATFORM_MOTION)) return;
        var state = player.getData(DEAttachments.PLATFORM_MOTION);
        if (!state.locked && !(state.flying && player.getAbilities().flying)) return;
        state.permits++;
        try {
            player.setDeltaMovement(PlatformMotion.inputMotion(player, state.input));
            if (player.level().isClientSide) player.move(MoverType.SELF, player.getDeltaMovement());
            player.resetFallDistance();
        } finally {
            state.permits--;
        }
        ci.cancel();
    }
}
