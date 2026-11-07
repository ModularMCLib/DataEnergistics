package com.fish_dan_.data_energistics.integration.library.curios.equipment;

import com.fish_dan_.data_energistics.common.weapon.platform.equipment.protection.EquipmentProtection;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.event.CurioCanUnequipEvent;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/** Resolves only bindings created by the loaded Curios integration; client cosmetic slots remain separate. */
public final class CuriosEquipmentProtection {

    @SubscribeEvent
    public void unequip(CurioCanUnequipEvent event) {
        if (EquipmentProtection.denyExtraction(event.getStack())) event.setUnequipResult(TriState.FALSE);
    }

    public static boolean matches(ServerPlayer player, ItemStack stack) {
        return CuriosApi.getCuriosInventory(player).map(inventory -> inventory.getCurios().values().stream().anyMatch(handler -> matches(stack, handler.getStacks()) || matches(stack, handler.getCosmeticStacks()))).orElse(false);
    }

    private static boolean matches(ItemStack stack, IDynamicStackHandler contents) {
        for (int slot = 0; slot < contents.getSlots(); slot++) if (ItemStack.matches(stack, contents.getStackInSlot(slot))) return true;
        return false;
    }

    public static ItemStack stack(ServerPlayer player, String identifier, int slot, boolean cosmetic) {
        var inventory = CuriosApi.getCuriosInventory(player);
        if (inventory.isEmpty()) return ItemStack.EMPTY;
        var handler = inventory.get().getCurios().get(identifier);
        if (handler == null) return ItemStack.EMPTY;
        var stacks = cosmetic ? handler.getCosmeticStacks() : handler.getStacks();
        return slot < stacks.getSlots() ? stacks.getStackInSlot(slot) : ItemStack.EMPTY;
    }
}
