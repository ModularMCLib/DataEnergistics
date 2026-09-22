package com.fish_dan_.data_energistics.mixin.magic.neovitae;

import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedEntityCapture;
import com.fish_dan_.data_energistics.integration.magic.neovitae.packaged.HellfireForgeAdapter;
import com.fish_dan_.data_energistics.world.packaged.PackagedMachineClaims;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;

import com.breakinblocks.neovitae.common.block.HellfireForgeBlock;
import com.breakinblocks.neovitae.common.blockentity.HellfireForgeBlockEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keeps a player's installed gem outside the task-owned native removal drops. */
@Mixin(HellfireForgeBlock.class)
abstract class HellfireForgeDropMixin {

    @WrapOperation(method = "onRemove", at = @At(value = "INVOKE", target = "Lcom/breakinblocks/neovitae/util/helper/BlockEntityHelper;dropContents(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/neoforged/neoforge/items/IItemHandler;)V"))
    private void preserveInstalledGem(Level level, BlockPos position, IItemHandler inventory, Operation<Void> original) {
        if (!(level instanceof ServerLevel server) || PackagedMachineClaims.get(server).owner(position) == null ||
                !(level.getBlockEntity(position) instanceof HellfireForgeBlockEntity forge) ||
                HellfireForgeAdapter.hasSuppliedGem(forge)) {
            original.call(level, position, inventory);
            return;
        }
        ItemStack installed = forge.inv.getStackInSlot(HellfireForgeBlockEntity.GEM_SLOT);
        forge.inv.setStackInSlot(HellfireForgeBlockEntity.GEM_SLOT, ItemStack.EMPTY);
        original.call(level, position, inventory);
        if (!installed.isEmpty()) {
            PackagedEntityCapture.unowned(() -> Containers.dropItemStack(level, position.getX(), position.getY(), position.getZ(), installed));
        }
    }
}
