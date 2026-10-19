package com.fish_dan_.data_energistics.common.terminal.player.transfer;

import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;
import com.fish_dan_.data_energistics.common.terminal.player.persistence.TerminalPlayerState;
import com.fish_dan_.data_energistics.common.terminal.player.storage.TerminalNetworkStorage;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageHelper;
import appeng.helpers.ICraftingGridMenu;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Server-thread ingredient transfer shared by physical workspaces and validated host input slots.
 * The caller owns host validation and must exclude output, player and configuration slots.
 * Preflight does not change physical items; committed network items remain journaled until a slot owns them.
 */
@NullMarked
public final class TerminalIngredientSupply {

    private final ServerPlayer player;
    private final TerminalPlayerState state;
    private final @Nullable TerminalConnection connection;
    private final ObjectList<Target> targets;
    private final boolean usePlayerInventory;
    private final ObjectList<ItemStack> targetBefore = new ObjectArrayList<>();
    private final ObjectList<Slot> inventorySlots = new ObjectArrayList<>();
    private final ObjectList<ItemStack> inventoryBefore = new ObjectArrayList<>();
    private final IntArrayList inventoryTaken = new IntArrayList();
    private final ObjectList<Removal> removals = new ObjectArrayList<>();
    private final Object2LongMap<AEItemKey> demand = new Object2LongLinkedOpenHashMap<>();
    private final Object2LongMap<AEItemKey> displaced = new Object2LongLinkedOpenHashMap<>();
    private final Object2LongMap<AEItemKey> network = new Object2LongLinkedOpenHashMap<>();
    private final Object2LongMap<AEItemKey> held = new Object2LongLinkedOpenHashMap<>();

    private TerminalIngredientSupply(ServerPlayer player, TerminalPlayerState state,
                                     @Nullable TerminalConnection connection, ObjectList<Target> targets, boolean usePlayerInventory) {
        this.player = player;
        this.state = state;
        this.connection = connection;
        this.targets = new ObjectArrayList<>(targets);
        this.usePlayerInventory = usePlayerInventory;
    }

    /**
     * One desired physical ingredient position. A null key clears the position and requires amount zero;
     * a non-null key requires a positive amount, retaining any matching excess already in the slot.
     */
    public record Target(Slot slot, @Nullable AEItemKey key, int amount) {}

    /**
     * Empty message means success. Missing entries aggregate counts by resource, represented by their
     * slot-list lengths for AE's crafting request menu; their individual slot indices are unused.
     */
    public record Result(String message, ObjectList<ICraftingGridMenu.AutoCraftEntry> missing) {}

    /**
     * Runs on the server thread with a freshly validated optional network connection.
     * Only target contents, the main 36 player slots and the connected network can supply ingredients.
     * No crafting request is submitted. Invalid target descriptions throw IllegalArgumentException.
     * Third-party slot exceptions propagate after retained resources have been returned or journaled.
     */
    public static Result supply(ServerPlayer player, TerminalPlayerState state,
                                @Nullable TerminalConnection connection, ObjectList<Target> targets) {
        return new TerminalIngredientSupply(player, state, connection, targets, true).run();
    }

    /** Small recipe areas consume their physical inputs first and draw any missing ingredients directly from ME. */
    public static Result supplyFromNetwork(ServerPlayer player, TerminalPlayerState state,
                                          @Nullable TerminalConnection connection, ObjectList<Target> targets) {
        return new TerminalIngredientSupply(player, state, connection, targets, false).run();
    }

    private Result run() {
        String preflight = prepareTargets();
        if (!preflight.isEmpty()) return result(preflight);
        prepareInventory();
        if (!returnCapacity()) return result("inventory_full");
        Result available = preflightNetwork();
        if (!available.message().isEmpty()) return available;

        Result outcome;
        boolean returned;
        try {
            outcome = commit();
        } finally {
            // Local items keep physical ownership; only AE extraction belongs in the frequency ledger.
            try {
                returned = returnHeld();
            } finally {
                try {
                    if (connection != null) state.returns().refund(connection);
                } finally {
                    player.getInventory().setChanged();
                    player.containerMenu.broadcastChanges();
                }
            }
        }
        return returned ? outcome : result("inventory_full");
    }

    private String prepareTargets() {
        for (int index = 0; index < targets.size(); index++) {
            Target target = targets.get(index);
            Slot slot = target.slot();
            AEItemKey key = target.key();
            if (key == null ? target.amount() != 0 : target.amount() <= 0)
                throw new IllegalArgumentException("Invalid terminal ingredient amount");
            for (int previous = 0; previous < index; previous++) {
                Slot other = targets.get(previous).slot();
                if (slot.container == other.container && slot.getContainerSlot() == other.getContainerSlot())
                    throw new IllegalArgumentException("Duplicate terminal ingredient slot");
            }
            if (!slot.isActive()) return "container_full";
            ItemStack existing = slot.getItem().copy();
            targetBefore.add(existing);
            boolean matches = key != null && key.matches(existing);
            AEItemKey existingKey = AEItemKey.of(existing);
            if (existingKey != null && !matches) {
                if (!slot.mayPickup(player)) return "container_full";
                displaced.mergeLong(existingKey, existing.getCount(), Math::addExact);
                removals.add(new Removal(slot, existingKey, existing.getCount(), false));
            }
            int needed = target.amount() - (matches ? existing.getCount() : 0);
            if (key != null && needed > 0) {
                ItemStack sample = key.toStack(1);
                if (!slot.mayPlace(sample) ||
                        target.amount() > Math.min(slot.getMaxStackSize(sample), sample.getMaxStackSize()))
                    return "container_full";
                demand.mergeLong(key, needed, Math::addExact);
            }
        }
        return "";
    }

    private void prepareInventory() {
        network.putAll(demand);
        for (var entry : displaced.object2LongEntrySet()) {
            long consumed = Math.min(entry.getLongValue(), network.getLong(entry.getKey()));
            subtract(network, entry.getKey(), consumed);
        }
        for (int index = 0; index < player.getInventory().items.size(); index++) {
            Slot slot = inventorySlot(index);
            ItemStack existing = slot.getItem().copy();
            inventorySlots.add(slot);
            inventoryBefore.add(existing);
            int taken = 0;
            AEItemKey key = AEItemKey.of(existing);
            if (usePlayerInventory && key != null && slot.isActive() && slot.mayPickup(player) && !inventoryLocked(index)) {
                taken = (int) Math.min(existing.getCount(), network.getLong(key));
                subtract(network, key, taken);
                if (taken > 0) removals.add(new Removal(slot, key, taken, true));
            }
            inventoryTaken.add(taken);
        }
    }

    private Slot inventorySlot(int index) {
        for (Slot slot : player.containerMenu.slots) {
            if (slot.container == player.getInventory() && slot.getContainerSlot() == index) return slot;
        }
        return new Slot(player.getInventory(), index, 0, 0);
    }

    private boolean inventoryLocked(int index) {
        return player.containerMenu instanceof ICraftingGridMenu menu && menu.isPlayerInventorySlotLocked(index);
    }

    private boolean returnCapacity() {
        ObjectList<ItemStack> simulated = new ObjectArrayList<>();
        for (int index = 0; index < inventoryBefore.size(); index++) {
            ItemStack existing = inventoryBefore.get(index);
            simulated.add(existing.copyWithCount(existing.getCount() - inventoryTaken.getInt(index)));
        }
        for (var entry : displaced.object2LongEntrySet()) {
            long remaining = Math.max(0, entry.getLongValue() - demand.getLong(entry.getKey()));
            ItemStack sample = entry.getKey().toStack(1);
            for (int pass = 0; pass < 2 && remaining > 0; pass++) {
                for (int index = 0; index < inventorySlots.size() && remaining > 0; index++) {
                    Slot slot = inventorySlots.get(index);
                    ItemStack existing = simulated.get(index);
                    if (inventoryLocked(index) || !slot.isActive() || !slot.mayPlace(sample) ||
                            (pass == 0) == existing.isEmpty() ||
                            !existing.isEmpty() && !entry.getKey().matches(existing))
                        continue;
                    int room = Math.max(0, Math.min(slot.getMaxStackSize(sample), sample.getMaxStackSize()) - existing.getCount());
                    int inserted = (int) Math.min(room, remaining);
                    simulated.set(index, sample.copyWithCount(existing.getCount() + inserted));
                    remaining -= inserted;
                }
            }
            if (remaining != 0) return false;
        }
        return true;
    }

    private Result preflightNetwork() {
        if (network.isEmpty()) return result("");
        if (connection == null) return result("offline");
        ObjectList<ICraftingGridMenu.AutoCraftEntry> missing = new ObjectArrayList<>();
        double requiredEnergy = 0;
        boolean powered = true;
        for (var entry : network.object2LongEntrySet()) {
            AEItemKey key = entry.getKey();
            long amount = entry.getLongValue();
            long available = connection.storage().extract(key, amount, Actionable.SIMULATE, connection.source());
            TerminalNetworkStorage.checkAmount(available, amount);
            if (available < amount) missing.add(missing(key, amount - available));
            powered &= StorageHelper.poweredExtraction(connection.grid().getEnergyService(), connection.storage(),
                    key, amount, connection.source(), Actionable.SIMULATE) == amount;
            requiredEnergy += amount / Math.max(1.0, key.getAmountPerOperation());
        }
        if (!missing.isEmpty()) return new Result("missing_inputs", missing);
        if (!powered || connection.grid().getEnergyService()
                .extractAEPower(requiredEnergy, Actionable.SIMULATE, PowerMultiplier.CONFIG) + 0.000001 < requiredEnergy)
            return result("offline");
        return result("");
    }

    private Result commit() {
        if (connection != null) {
            for (var entry : network.object2LongEntrySet()) {
                long amount = StorageHelper.poweredExtraction(connection.grid().getEnergyService(), connection.storage(),
                        entry.getKey(), entry.getLongValue(), connection.source());
                TerminalNetworkStorage.checkAmount(amount, entry.getLongValue());
                if (amount > 0) state.returns().extracted(connection.frequency(), entry.getKey(), amount);
                if (amount != entry.getLongValue()) {
                    ObjectList<ICraftingGridMenu.AutoCraftEntry> missing = new ObjectArrayList<>();
                    missing.add(missing(entry.getKey(), entry.getLongValue() - amount));
                    return new Result("missing_inputs", missing);
                }
            }
        }
        // Storage callbacks may have changed inventories after preflight.
        for (int index = 0; index < targets.size(); index++) {
            if (!ItemStack.matches(targetBefore.get(index), targets.get(index).slot().getItem()))
                return result("container_full");
        }
        for (int index = 0; index < inventorySlots.size(); index++) {
            if (!ItemStack.matches(inventoryBefore.get(index), inventorySlots.get(index).getItem()))
                return result("inventory_full");
        }
        for (Removal removal : removals) {
            if (take(removal.slot(), removal.key(), removal.amount()) != removal.amount())
                return result(removal.playerInventory() ? "inventory_full" : "container_full");
        }
        for (Target target : targets) {
            AEItemKey key = target.key();
            Slot slot = target.slot();
            if (key == null) {
                if (!slot.getItem().isEmpty()) return result("container_full");
                continue;
            }
            int before = count(slot, key);
            int needed = Math.max(0, target.amount() - before);
            if (needed == 0) continue;
            if (!slot.isActive() || !slot.mayPlace(key.toStack(1)) ||
                    !slot.getItem().isEmpty() && !key.matches(slot.getItem()))
                return result("container_full");
            if (needed > held.getLong(key) + network.getLong(key)) return result("container_full");
            int inserted = -1;
            try {
                inserted = needed - slot.safeInsert(key.toStack(needed)).getCount();
            } finally {
                if (inserted < 0) inserted = count(slot, key) - before;
                TerminalNetworkStorage.checkAmount(inserted, needed);
                long local = Math.min(inserted, held.getLong(key));
                subtract(held, key, local);
                if (inserted > local && connection != null) {
                    state.returns().delivered(connection.frequency(), key, inserted - local);
                    subtract(network, key, inserted - local);
                }
            }
            if (inserted != needed) return result("container_full");
        }
        return result("");
    }

    private int take(Slot slot, AEItemKey key, int amount) {
        if (!slot.isActive() || !slot.mayPickup(player) || !key.matches(slot.getItem())) return 0;
        int before = count(slot, key);
        int removed = -1;
        try {
            removed = slot.safeTake(amount, before, player).getCount();
        } finally {
            // onTake can throw after the source inventory has already relinquished the item.
            if (removed < 0) removed = before - count(slot, key);
            TerminalNetworkStorage.checkAmount(removed, amount);
            if (removed > 0) held.mergeLong(key, removed, Math::addExact);
        }
        return removed;
    }

    private boolean returnHeld() {
        boolean complete;
        try {
            for (var entry : held.object2LongEntrySet()) {
                AEItemKey key = entry.getKey();
                ItemStack sample = key.toStack(1);
                long remaining = entry.getLongValue();
                for (int pass = 0; pass < 2 && remaining > 0; pass++) {
                    for (int index = 0; index < inventorySlots.size() && remaining > 0; index++) {
                        Slot slot = inventorySlots.get(index);
                        ItemStack existing = slot.getItem();
                        if (inventoryLocked(index) || !slot.isActive() || !slot.mayPlace(sample) ||
                                (pass == 0) == existing.isEmpty() ||
                                !existing.isEmpty() && !key.matches(existing))
                            continue;
                        int room = Math.max(0, Math.min(slot.getMaxStackSize(sample), sample.getMaxStackSize()) - existing.getCount());
                        int amount = (int) Math.min(remaining, room);
                        if (amount == 0) continue;
                        int before = count(slot, key);
                        int inserted = -1;
                        try {
                            inserted = amount - slot.safeInsert(key.toStack(amount)).getCount();
                        } finally {
                            if (inserted < 0) inserted = count(slot, key) - before;
                            TerminalNetworkStorage.checkAmount(inserted, amount);
                            remaining -= inserted;
                            entry.setValue(remaining);
                        }
                    }
                }
            }
        } finally {
            // A failing inventory callback must not strand the remainder of this local physical pool.
            complete = dropHeld();
        }
        return complete;
    }

    private boolean dropHeld() {
        boolean complete = true;
        for (var entry : held.object2LongEntrySet()) {
            AEItemKey key = entry.getKey();
            long remaining = entry.getLongValue();
            // Only unexpected third-party changes can invalidate the reserved return capacity.
            // Keep the local remainder physical rather than crediting it to an unrelated AE frequency.
            while (remaining > 0) {
                int amount = (int) Math.min(remaining, key.getMaxStackSize());
                player.drop(key.toStack(amount), false);
                remaining -= amount;
                entry.setValue(remaining);
                complete = false;
            }
        }
        return complete;
    }

    private static int count(Slot slot, AEItemKey key) {
        ItemStack stack = slot.getItem();
        return key.matches(stack) ? stack.getCount() : 0;
    }

    private static void subtract(Object2LongMap<AEItemKey> amounts, AEItemKey key, long amount) {
        if (amount == 0) return;
        long remaining = amounts.getLong(key) - amount;
        if (remaining < 0) throw new IllegalStateException("Ingredient reservation exceeds available resources");
        if (remaining == 0) amounts.removeLong(key);
        else amounts.put(key, remaining);
    }

    private static ICraftingGridMenu.AutoCraftEntry missing(AEItemKey key, long amount) {
        IntArrayList slots = new IntArrayList(Math.toIntExact(amount));
        for (long index = 0; index < amount; index++) slots.add(-1);
        return new ICraftingGridMenu.AutoCraftEntry(key, slots);
    }

    private static Result result(String message) {
        return new Result(message, new ObjectArrayList<>());
    }

    private record Removal(Slot slot, AEItemKey key, int amount, boolean playerInventory) {}
}
