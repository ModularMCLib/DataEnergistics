package com.fish_dan_.data_energistics.mixin.magic.naturesaura;

import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedEntityCapture;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

/** Player destruction calls this before the normal block replacement callback. */
@Mixin(targets = "de.ellpeck.naturesaura.blocks.tiles.BlockEntityImpl", remap = false)
abstract class NatureInventoryDropMixin {

    @WrapMethod(method = "dropInventory")
    private void captureInventory(Operation<Void> original) {
        var blockEntity = (BlockEntity) (Object) this;
        if (blockEntity.getLevel() instanceof ServerLevel level) {
            PackagedEntityCapture.machineTick(level, blockEntity.getBlockPos(), () -> original.call());
        } else {
            original.call();
        }
    }
}
