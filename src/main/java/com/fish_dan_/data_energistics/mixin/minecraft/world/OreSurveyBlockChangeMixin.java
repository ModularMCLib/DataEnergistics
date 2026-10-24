package com.fish_dan_.data_energistics.mixin.minecraft.world;

import com.fish_dan_.data_energistics.common.weapon.platform.prospecting.OreSectionCache;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class OreSurveyBlockChangeMixin {

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void dataEnergistics$invalidate(BlockPos pos, BlockState state, boolean moving, CallbackInfoReturnable<BlockState> cir) {
        if (cir.getReturnValue() != null) OreSectionCache.changed(((LevelChunk) (Object) this).getLevel(), pos);
    }
}
