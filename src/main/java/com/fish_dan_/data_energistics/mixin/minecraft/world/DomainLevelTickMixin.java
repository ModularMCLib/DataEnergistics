package com.fish_dan_.data_energistics.mixin.minecraft.world;

import com.fish_dan_.data_energistics.common.weapon.platform.domain.DomainStasis;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
abstract class DomainLevelTickMixin {

    @Inject(method = "tickChunk", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$freezeRandom(LevelChunk chunk, int speed, CallbackInfo ci) {
        if (DomainStasis.frozen((ServerLevel) (Object) this, chunk.getPos().getWorldPosition())) ci.cancel();
    }
}
