package com.fish_dan_.data_energistics.core.mixins.normal.net.minecraft.server.level;

import com.fish_dan_.data_energistics.common.weapon.platform.domain.DomainStasis;
import com.fish_dan_.data_energistics.common.weapon.platform.domain.StasisDomainSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;

import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
@NullMarked
abstract class ServerLevelMixin {

    @Shadow
    protected abstract void tickPassenger(Entity mount, Entity rider);

    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$entity(Entity entity, CallbackInfo ci) {
        if (!DomainStasis.freezeTick(entity)) return;
        for (var passenger : entity.getPassengers()) tickPassenger(entity, passenger);
        ci.cancel();
    }

    @Inject(method = "tickPassenger", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$passenger(Entity mount, Entity rider, CallbackInfo ci) {
        if (!DomainStasis.freezeTick(rider)) return;
        for (var passenger : rider.getPassengers()) tickPassenger(rider, passenger);
        ci.cancel();
    }

    @Inject(method = "blockEvent", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$enqueue(BlockPos pos, Block block, int action, int parameter, CallbackInfo ci) {
        var level = (ServerLevel) (Object) this;
        if (StasisDomainSavedData.get(level.getServer()).pauseBlockEvent(level, pos, block, action, parameter)) ci.cancel();
    }

    @Inject(method = "doBlockEvent", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$pending(BlockEventData event, CallbackInfoReturnable<Boolean> cir) {
        var level = (ServerLevel) (Object) this;
        if (StasisDomainSavedData.get(level.getServer()).pauseBlockEvent(level, event.pos(), event.block(), event.paramA(), event.paramB())) cir.setReturnValue(false);
    }

    @Inject(method = "tickChunk", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$freezeRandom(LevelChunk chunk, int speed, CallbackInfo ci) {
        if (DomainStasis.frozen((ServerLevel) (Object) this, chunk.getPos().getWorldPosition())) ci.cancel();
    }
}
