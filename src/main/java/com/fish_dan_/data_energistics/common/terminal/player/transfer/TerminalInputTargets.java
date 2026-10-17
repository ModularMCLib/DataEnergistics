package com.fish_dan_.data_energistics.common.terminal.player.transfer;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.FakeSlot;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

/**
 * Explicit input adapters avoid guessing whether a third-party slot is a result or a configuration ghost.
 */
public final class TerminalInputTargets {

    private TerminalInputTargets() {
    }

    public static ObjectList<Slot> resolve(Player player, AbstractContainerMenu menu) {
        ObjectList<Slot> result = new ObjectArrayList<>();
        if (menu instanceof AEBaseMenu aeMenu) result.addAll(aeMenu.getSlots(SlotSemantics.MACHINE_INPUT));
        else if (menu instanceof ChestMenu || menu instanceof HopperMenu || menu instanceof ShulkerBoxMenu || menu instanceof DispenserMenu) {
            for (Slot slot : menu.slots) if (slot.container != player.getInventory()) result.add(slot);
        } else if (menu instanceof AbstractFurnaceMenu) {
            result.add(menu.getSlot(0));
            result.add(menu.getSlot(1));
        } else if (menu instanceof BrewingStandMenu) {
            for (int index = 0; index < 5; index++) result.add(menu.getSlot(index));
        }
        result.removeIf(slot -> slot.container == player.getInventory() || slot instanceof FakeSlot);
        return result;
    }
}
