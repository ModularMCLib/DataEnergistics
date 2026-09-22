package com.fish_dan_.data_energistics.mixin.magic.forbiddenarcanus;

import com.fish_dan_.data_energistics.world.packaged.PackagedMachineClaims;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.stal111.forbidden_arcanus.common.block.entity.forge.HephaestusForgeBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freezes a dismantled claimed structure until its physical inventory has been recovered. */
@Mixin(value = HephaestusForgeBlockEntity.class, remap = false)
public abstract class HephaestusForgeRecoveryMixin {

    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private static void dataEnergistics$pauseRemoved(Level level, BlockPos position, BlockState state,
                                                     HephaestusForgeBlockEntity forge, CallbackInfo callback) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        var claims = PackagedMachineClaims.get(serverLevel);
        var owner = claims.owner(position);
        if (owner != null && claims.structureRemoved(owner)) callback.cancel();
    }
}
