package com.fish_dan_.data_energistics.mixin.minecraft.world;

import com.fish_dan_.data_energistics.common.weapon.platform.domain.DomainStasis;

import net.minecraft.world.level.block.entity.BlockEntity;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
abstract class DomainBlockEntityTickMixin {

    @Shadow
    @Final
    private BlockEntity blockEntity;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$freeze(CallbackInfo ci) {
        if (blockEntity.getLevel() != null && DomainStasis.frozen(blockEntity.getLevel(), blockEntity.getBlockPos())) ci.cancel();
    }
}
