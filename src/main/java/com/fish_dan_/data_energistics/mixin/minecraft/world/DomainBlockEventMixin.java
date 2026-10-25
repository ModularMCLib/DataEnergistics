package com.fish_dan_.data_energistics.mixin.minecraft.world;

import com.fish_dan_.data_energistics.common.weapon.platform.domain.StasisDomainSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.block.Block;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
abstract class DomainBlockEventMixin {

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
}
