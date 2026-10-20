package com.fish_dan_.data_energistics.integration.library.curios.equipment;

import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalQuantumAccess;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Optional Curios boundary; slot order is deterministic and cosmetic stacks do not grant connections.
 */
@NullMarked
public final class CuriosTerminalSingularities {

    private CuriosTerminalSingularities() {}

    public static void collect(Player player, LongSet frequencies) {
        CuriosApi.getCuriosInventory(player).ifPresent(inventory -> {
            var curios = inventory.getCurios();
            ObjectList<String> slots = new ObjectArrayList<>(curios.keySet());
            slots.sort(String::compareTo);
            for (String slot : slots) {
                var stacks = curios.get(slot).getStacks();
                for (int index = 0; index < stacks.getSlots(); index++)
                    TerminalQuantumAccess.add(stacks.getStackInSlot(index), frequencies);
            }
        });
    }

    public static ItemStack find(Player player, long frequency) {
        return CuriosApi.getCuriosInventory(player).map(inventory -> {
            var curios = inventory.getCurios();
            ObjectList<String> slots = new ObjectArrayList<>(curios.keySet());
            slots.sort(String::compareTo);
            for (String slot : slots) {
                var stacks = curios.get(slot).getStacks();
                for (int index = 0; index < stacks.getSlots(); index++) {
                    ItemStack stack = stacks.getStackInSlot(index);
                    if (TerminalQuantumAccess.matches(stack, frequency)) return stack;
                }
            }
            return ItemStack.EMPTY;
        }).orElse(ItemStack.EMPTY);
    }
}
