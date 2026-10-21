package com.fish_dan_.data_energistics.common.terminal.player.magnet;

import com.fish_dan_.data_energistics.common.menu.terminal.TerminalPickBlockMenuLocator;
import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;
import com.fish_dan_.data_energistics.common.terminal.player.storage.TerminalNetworkStorage;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageHelper;
import appeng.menu.me.crafting.CraftAmountMenu;

import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.NullMarked;

/** Pick-block uses ME stock, or AE2's existing request menu when the selected block is craftable. */
@NullMarked
public final class TerminalPickBlock {

    private TerminalPickBlock() {}

    /**
     * Runs on the server thread after research, reach and connection checks; only paid ME extraction creates the held
     * stack.
     */
    public static void pick(ServerPlayer player, TerminalConnection connection, ItemStack stack, boolean craftIfMissing) {
        var inventory = player.getInventory();
        int existing = inventory.findSlotMatchingItem(stack);
        if (existing >= 0) {
            if (Inventory.isHotbarSlot(existing)) inventory.selected = existing;
            else inventory.pickSlot(existing);
            select(player, inventory.selected);
            return;
        }
        AEItemKey key = AEItemKey.of(stack);
        if (key == null) return;
        int slot = inventory.getSuitableHotbarSlot();
        ItemStack previous = inventory.getItem(slot);
        AEItemKey previousKey = AEItemKey.of(previous);
        if (previousKey != null && connection.storage().insert(previousKey, previous.getCount(), Actionable.SIMULATE, connection.source()) < previous.getCount())
            return;
        long available = connection.storage().extract(key, stack.getMaxStackSize(), Actionable.SIMULATE, connection.source());
        if (available == 0) {
            if (craftIfMissing && !connection.grid().getCraftingService().getCraftingFor(key).isEmpty())
                CraftAmountMenu.open(player, new TerminalPickBlockMenuLocator(connection.frequency()), key, 1);
            return;
        }
        if (previousKey != null) {
            int original = previous.getCount();
            long inserted = StorageHelper.poweredInsert(connection.grid().getEnergyService(), connection.storage(), previousKey, original, connection.source());
            TerminalNetworkStorage.checkAmount(inserted, original);
            previous.shrink((int) inserted);
            inventory.setChanged();
            if (!previous.isEmpty()) return;
        }
        int wanted = stack.getMaxStackSize();
        long extracted = StorageHelper.poweredExtraction(connection.grid().getEnergyService(), connection.storage(), key, wanted, connection.source());
        TerminalNetworkStorage.checkAmount(extracted, wanted);
        inventory.setItem(slot, extracted == 0 ? ItemStack.EMPTY : key.toStack((int) extracted));
        if (extracted > 0) select(player, slot);
    }

    private static void select(ServerPlayer player, int slot) {
        player.getInventory().selected = slot;
        player.getInventory().setChanged();
        player.connection.send(new ClientboundSetCarriedItemPacket(slot));
        player.inventoryMenu.broadcastChanges();
    }
}
