package com.fish_dan_.data_energistics.mixin.core.packaged;

import com.fish_dan_.data_energistics.world.packaged.PackagedMachineClaims;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Releases every packaged machine family when a reserved physical block is replaced. */
@Mixin(LevelChunk.class)
abstract class PackagedMachineRemovalMixin {

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void releaseReplacedMachine(BlockPos position, BlockState state, boolean moving,
                                        CallbackInfoReturnable<BlockState> cir) {
        BlockState previous = cir.getReturnValue();
        if (previous == null || previous.is(state.getBlock())) return;
        if (((LevelChunk) (Object) this).getLevel() instanceof ServerLevel level) {
            PackagedMachineClaims.get(level).blockReplaced(position);
        }
    }
}
