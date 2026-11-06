package com.fish_dan_.data_energistics.core.mixins.net.minecraft.world.level.chunk;

import com.fish_dan_.data_energistics.client.weapon.platform.vision.VisionSectionCache;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
@NullMarked
abstract class PlatformVisionChunkMixin {

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void dataEnergistics$invalidateVision(BlockPos position, BlockState state, boolean moving, CallbackInfoReturnable<BlockState> cir) {
        if (((LevelChunk) (Object) this).getLevel() instanceof ClientLevel level) VisionSectionCache.invalidate(level, position);
    }
}
