package com.fish_dan_.data_energistics.common.terminal.player.transfer;

import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;
import com.fish_dan_.data_energistics.common.terminal.player.persistence.TerminalPlayerState;
import com.fish_dan_.data_energistics.common.terminal.player.storage.TerminalNetworkStorage;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.StorageHelper;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

/**
 * Preflights the complete bill and target capacity, then journals extraction until insertion owns each item.
 */
public final class TerminalContainerTransfer {

    private TerminalContainerTransfer() {
    }

    public static String transfer(ServerPlayer player, TerminalConnection connection, TerminalPlayerState state) {
        ObjectList<Slot> targets = TerminalInputTargets.resolve(player, player.containerMenu);
        if (targets.isEmpty()) return "unsupported_container";
        var bill = new Object2LongLinkedOpenHashMap<AEKey>();
        var inventory = state.work().patternInputs;
        for (int index = 0; index < inventory.size(); index++) {
            var input = inventory.get(index);
            if (input == null) continue;
            if (!(input.what() instanceof AEItemKey)) return "unsupported_resource";
            long previous = bill.getLong(input.what());
            if (input.amount() > Long.MAX_VALUE - previous) return "invalid_amount";
            bill.put(input.what(), previous + input.amount());
        }
        if (bill.isEmpty()) return "missing_inputs";
        ObjectList<ItemStack> simulated = new ObjectArrayList<>();
        for (Slot target : targets) simulated.add(target.getItem().copy());
        ObjectList<Insertion> plan = new ObjectArrayList<>();
        for (var entry : bill.object2LongEntrySet()) {
            AEItemKey key = (AEItemKey) entry.getKey();
            long remaining = entry.getLongValue();
            if (StorageHelper.poweredExtraction(connection.grid().getEnergyService(), connection.storage(), key, remaining, connection.source(), Actionable.SIMULATE) != remaining)
                return "missing_inputs";
            ItemStack sample = key.toStack(1);
            for (int pass = 0; pass < 2; pass++) {
                for (int index = 0; index < targets.size() && remaining > 0; index++) {
                    Slot target = targets.get(index);
                    ItemStack existing = simulated.get(index);
                    if (!target.isActive() || !target.mayPlace(sample) || (pass == 0) == existing.isEmpty() ||
                            (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, sample)))
                        continue;
                    int room = Math.min(target.getMaxStackSize(sample), sample.getMaxStackSize()) - existing.getCount();
                    int count = (int) Math.min(remaining, Math.max(0, room));
                    if (count == 0) continue;
                    simulated.set(index, sample.copyWithCount(existing.getCount() + count));
                    plan.add(new Insertion(target, key, count));
                    remaining -= count;
                }
            }
            if (remaining != 0) return "container_full";
        }
        // Every extraction is journaled before calling another mod's slot code.
        for (var entry : bill.object2LongEntrySet()) {
            long extracted = StorageHelper.poweredExtraction(connection.grid().getEnergyService(), connection.storage(), entry.getKey(), entry.getLongValue(), connection.source());
            TerminalNetworkStorage.checkAmount(extracted, entry.getLongValue());
            if (extracted > 0) state.returns().extracted(connection.frequency(), entry.getKey(), extracted);
            if (extracted != entry.getLongValue()) {
                TerminalNetworkStorage.refund(connection, state);
                return "missing_inputs";
            }
        }
        for (Insertion insertion : plan) {
            int before = count(insertion.slot(), insertion.key());
            int inserted;
            try {
                insertion.slot().safeInsert(insertion.key().toStack(insertion.amount()));
            } finally {
                // A slot may mutate its inventory before notifying another mod and throwing.
                inserted = count(insertion.slot(), insertion.key()) - before;
                TerminalNetworkStorage.checkAmount(inserted, insertion.amount());
                state.returns().delivered(connection.frequency(), insertion.key(), inserted);
            }
            if (inserted != insertion.amount()) {
                TerminalNetworkStorage.refund(connection, state);
                return "container_full";
            }
        }
        player.containerMenu.broadcastChanges();
        return "transferred";
    }

    private static int count(Slot slot, AEItemKey key) {
        ItemStack stack = slot.getItem();
        return key.matches(stack) ? stack.getCount() : 0;
    }

    private record Insertion(Slot slot, AEItemKey key, int amount) {
    }
}
