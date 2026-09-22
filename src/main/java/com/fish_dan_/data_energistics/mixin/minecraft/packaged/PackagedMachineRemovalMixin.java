package com.fish_dan_.data_energistics.mixin.minecraft.packaged;

import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedBlockChanges;
import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedEntityCapture;
import com.fish_dan_.data_energistics.world.packaged.PackagedMachineClaims;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Releases every packaged machine family when a reserved physical block is replaced. */
@Mixin(LevelChunk.class)
abstract class PackagedMachineRemovalMixin {

    @WrapOperation(method = "setBlockState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;onRemove(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)V"))
    private void captureRemovedInventory(BlockState previous, Level level, BlockPos position, BlockState replacement,
                                         boolean moving, Operation<Void> original) {
        if (level instanceof ServerLevel server) {
            PackagedEntityCapture.machineTick(server, position, () -> original.call(previous, level, position, replacement, moving));
        } else {
            original.call(previous, level, position, replacement, moving);
        }
    }

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void releaseReplacedMachine(BlockPos position, BlockState state, boolean moving,
                                        CallbackInfoReturnable<BlockState> cir) {
        BlockState previous = cir.getReturnValue();
        if (previous == null) return;
        if (((LevelChunk) (Object) this).getLevel() instanceof ServerLevel level) {
            PackagedBlockChanges.changed(level, position, previous, state);
            if (!previous.is(state.getBlock())) PackagedMachineClaims.get(level).blockReplaced(position);
        }
    }
}
