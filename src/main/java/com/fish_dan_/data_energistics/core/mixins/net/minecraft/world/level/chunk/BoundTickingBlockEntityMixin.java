package com.fish_dan_.data_energistics.core.mixins.net.minecraft.world.level.chunk;

import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedEntityCapture;
import com.fish_dan_.data_energistics.common.weapon.platform.domain.DomainStasis;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
@NullMarked
abstract class BoundTickingBlockEntityMixin {

    @Shadow
    @Final
    private BlockEntity blockEntity;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$freeze(CallbackInfo ci) {
        if (blockEntity.getLevel() != null && DomainStasis.frozen(blockEntity.getLevel(), blockEntity.getBlockPos())) ci.cancel();
    }

    @WrapOperation(method = "tick",
                   at = @At(value = "INVOKE",
                            target = "Lnet/minecraft/world/level/block/entity/BlockEntityTicker;tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;)V"))
    private <T extends BlockEntity> void capture(BlockEntityTicker<T> ticker, Level level, BlockPos position,
                                                 BlockState state, T entity, Operation<Void> original) {
        if (level instanceof ServerLevel server) {
            PackagedEntityCapture.machineTick(server, position, () -> original.call(ticker, level, position, state, entity));
        } else {
            original.call(ticker, level, position, state, entity);
        }
    }
}
