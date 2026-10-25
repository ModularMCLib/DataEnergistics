package com.fish_dan_.data_energistics.mixin.minecraft.entity;

import com.fish_dan_.data_energistics.common.weapon.platform.domain.DeferredImpact;
import com.fish_dan_.data_energistics.common.weapon.platform.domain.DomainStasis;
import com.fish_dan_.data_energistics.common.weapon.platform.domain.StasisDomainSavedData;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class DomainEntityPushMixin {

    @Inject(method = "push(DDD)V", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$defer(double x, double y, double z, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (!DomainStasis.frozen(entity)) return;
        if (entity.level() instanceof ServerLevel level && (x != 0 || y != 0 || z != 0))
            StasisDomainSavedData.get(level.getServer()).defer(entity, new DeferredImpact.Push(new Vec3(x, y, z)));
        ci.cancel();
    }
}
