package com.fish_dan_.data_energistics.mixin.neoforge.terminal;

import com.fish_dan_.data_energistics.common.terminal.player.session.TerminalSessions;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Standard player item capabilities may draw from ME; simulations and unrelated machine inventories stay untouched. */
@Mixin(value = InvWrapper.class, remap = false)
public abstract class TerminalInventoryExtractionMixin {

    @Shadow
    public abstract Container getInv();

    @WrapMethod(method = "extractItem", remap = false)
    private ItemStack dataEnergistics$payInventoryExtraction(int slot, int amount, boolean simulate, Operation<ItemStack> original) {
        if (simulate || amount <= 0 || !(getInv() instanceof Inventory inventory) || !(inventory.player instanceof ServerPlayer player))
            return original.call(slot, amount, simulate);
        ItemStack previous = inventory.getItem(slot).copy();
        ItemStack extracted = original.call(slot, amount, false);
        if (extracted.isEmpty()) return extracted;
        ItemStack current = inventory.getItem(slot);
        ItemStack result = TerminalSessions.payConsumedItems(player, previous, current);
        if (result != current) {
            inventory.setItem(slot, result);
            inventory.setChanged();
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
        }
        return extracted;
    }
}
