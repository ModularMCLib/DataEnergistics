package com.fish_dan_.data_energistics.common.menu.slot;

import appeng.api.inventories.InternalInventory;
import appeng.menu.slot.AppEngSlot;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import lombok.Getter;

/** Displays a real machine key balance; transfers go through the explicit server-side eject action. */
public final class MachineKeySlot extends AppEngSlot {

    @Getter
    private final int keySlotIndex;

    public MachineKeySlot(InternalInventory inventory, int keySlotIndex) {
        super(inventory, 0);
        this.keySlotIndex = keySlotIndex;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }
}
