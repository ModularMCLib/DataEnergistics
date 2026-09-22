package com.fish_dan_.data_energistics.mixin.magic.neovitae;

import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedEntityCapture;
import com.fish_dan_.data_energistics.world.packaged.PackagedMachineClaims;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.breakinblocks.neovitae.common.blockentity.TabulaVitaeBlockEntity;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures native destroy-path contents while preserving the pre-installed player's orb. */
@Mixin(TabulaVitaeBlockEntity.class)
abstract class TabulaVitaeDropMixin {

    @WrapMethod(method = "dropItems")
    private void captureNativeDrops(Operation<Void> original) {
        var table = (TabulaVitaeBlockEntity) (Object) this;
        if (table.getLevel() instanceof ServerLevel level) {
            PackagedEntityCapture.machineTick(level, table.getBlockPos(), () -> original.call());
        } else {
            original.call();
        }
    }

    @WrapOperation(method = "dropItems", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/Containers;dropItemStack(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V"))
    private void preserveInstalledOrb(Level level, double x, double y, double z, ItemStack stack, Operation<Void> original) {
        var table = (TabulaVitaeBlockEntity) (Object) this;
        if (stack == table.inv.getStackInSlot(TabulaVitaeBlockEntity.ORB_SLOT)) {
            PackagedEntityCapture.unowned(() -> original.call(level, x, y, z, stack));
        } else {
            original.call(level, x, y, z, stack);
        }
    }

    @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true)
    private void stopRemovedStructure(CallbackInfo callback) {
        var table = (TabulaVitaeBlockEntity) (Object) this;
        if (!(table.getLevel() instanceof ServerLevel level)) return;
        var claims = PackagedMachineClaims.get(level);
        var owner = claims.owner(table.getBlockPos());
        if (owner != null && claims.structureRemoved(owner)) callback.cancel();
    }
}
