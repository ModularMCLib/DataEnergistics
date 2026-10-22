package com.fish_dan_.data_energistics.integration.library.curios.equipment;

import com.fish_dan_.data_energistics.common.data.DEItems;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import top.theillusivec4.curios.api.CuriosApi;

/** Called only behind the optional Curios boundary; only the head slot can activate a platform. */
public final class CuriosWeaponPlatformAccess {

    private CuriosWeaponPlatformAccess() {}

    public static ItemStack find(Player player) {
        var inventory = CuriosApi.getCuriosInventory(player);
        if (inventory.isEmpty()) return ItemStack.EMPTY;
        var head = inventory.get().getCurios().get("head");
        if (head == null) return ItemStack.EMPTY;
        var stacks = head.getStacks();
        for (int slot = 0; slot < stacks.getSlots(); slot++) {
            ItemStack stack = stacks.getStackInSlot(slot);
            if (stack.is(DEItems.DIGITAL_WEAPON_PLATFORM)) return stack;
        }
        return ItemStack.EMPTY;
    }
}
