package com.fish_dan_.data_energistics.mixin.ae.extendedae;

import com.fish_dan_.data_energistics.common.upgrade.production.MultiplicationCardOutput;

import appeng.api.inventories.InternalInventory;
import appeng.util.inv.AppEngInternalInventory;

import net.minecraft.world.item.ItemStack;

import com.glodblock.github.extendedae.common.tileentities.TileCircuitCutter;
import com.glodblock.github.extendedae.common.tileentities.TileCrystalAssembler;
import com.glodblock.github.extendedae.util.FCUtil;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(FCUtil.class)
public abstract class ExtendedAeOutputExportMixin {

    @WrapOperation(
                   method = "ejectInv",
                   at = @At(
                            value = "INVOKE",
                            target = "Lappeng/api/inventories/InternalInventory;insertItem(ILnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack dataEnergistics$restoreExportRemainder(InternalInventory inventory, int slot, ItemStack remainder,
                                                                    boolean simulate, Operation<ItemStack> original) {
        if (slot == 0 && inventory instanceof AppEngInternalInventory itemInventory) {
            var host = itemInventory.getHost();
            if ((host instanceof TileCircuitCutter cutter && cutter.getOutput() == inventory) || (host instanceof TileCrystalAssembler assembler && assembler.getOutput() == inventory)) {
                return MultiplicationCardOutput.restoreExternalOutput(inventory, slot, remainder, simulate);
            }
        }
        return original.call(inventory, slot, remainder, simulate);
    }
}
