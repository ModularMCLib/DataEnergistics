package com.fish_dan_.data_energistics.mixin.extendedcrafting;

import com.fish_dan_.data_energistics.world.packaged.PackagedMachineClaims;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;

import com.blakebr0.extendedcrafting.tileentity.AutoEnderCrafterTileEntity;
import com.blakebr0.extendedcrafting.tileentity.AutoFluxCrafterTileEntity;
import com.blakebr0.extendedcrafting.tileentity.AutoTableTileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Temporarily separates an owned job from the machine's automatic adjacent-inventory pull.
 */
@Mixin(value = { AutoTableTileEntity.class, AutoEnderCrafterTileEntity.class, AutoFluxCrafterTileEntity.class }, remap = false)
abstract class PackagedAutomaticInputMixin {

    @Inject(method = "getAboveInventory", at = @At("HEAD"), cancellable = true)
    private void retainOwnedInputs(CallbackInfoReturnable<Optional<IItemHandler>> cir) {
        var tile = (BlockEntity) (Object) this;
        if (tile.getLevel() instanceof ServerLevel level && !PackagedMachineClaims.get(level).available(tile.getBlockPos())) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
