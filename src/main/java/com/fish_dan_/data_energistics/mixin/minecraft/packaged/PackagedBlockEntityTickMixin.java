package com.fish_dan_.data_energistics.mixin.minecraft.packaged;

import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedEntityCapture;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Brackets the real machine tick so delayed recipe drops retain their operation owner. */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
abstract class PackagedBlockEntityTickMixin {

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
