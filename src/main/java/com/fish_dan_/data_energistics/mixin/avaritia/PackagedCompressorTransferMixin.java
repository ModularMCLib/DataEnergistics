package com.fish_dan_.data_energistics.mixin.avaritia;

import com.fish_dan_.data_energistics.world.packaged.PackagedMachineClaims;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;

import committee.nova.mods.avaritia.common.tile.NeutronCompressorTile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps claimed compressor materials and outputs out of its autonomous neighbor transfer cycle.
 */
@Mixin(value = NeutronCompressorTile.class, remap = false)
abstract class PackagedCompressorTransferMixin {

    @Inject(method = { "extractFromHandler", "insertToHandler" }, at = @At("HEAD"), cancellable = true)
    private void retainOwnedResources(IItemHandler handler, Direction side, CallbackInfo ci) {
        var tile = (BlockEntity) (Object) this;
        if (tile.getLevel() instanceof ServerLevel level && !PackagedMachineClaims.get(level).available(tile.getBlockPos())) {
            ci.cancel();
        }
    }
}
