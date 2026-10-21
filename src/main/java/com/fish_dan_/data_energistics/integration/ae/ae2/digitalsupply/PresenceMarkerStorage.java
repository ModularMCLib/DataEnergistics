package com.fish_dan_.data_energistics.integration.ae.ae2.digitalsupply;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;
import org.jspecify.annotations.Nullable;

/**
 * Persistent AE storage owned by a Digital Supply Interface.
 *
 * <p>
 * Presence markers remain metadata for adapter discovery and never appear as inventory. Registered quantity keys
 * use the same mounted storage for real AE quantities, so extracting a resource immediately changes the amount shown
 * by the network and inserting a new resource does not need a second storage mount.
 * </p>
 */
public final class PresenceMarkerStorage implements MEStorage {

    private final ObjectSet<AEKey> presenceKeys = new ObjectLinkedOpenHashSet<>();
    private final ObjectSet<AEKey> quantityKeys = new ObjectLinkedOpenHashSet<>();
    private final Object2LongLinkedOpenHashMap<AEKey> quantities = new Object2LongLinkedOpenHashMap<>();
    private final Runnable changeListener;

    public PresenceMarkerStorage(Runnable changeListener) {
        this.changeListener = changeListener;
    }

    /** Returns an immutable snapshot of currently present resource keys. */
    public ObjectSet<AEKey> keys() {
        return ObjectSets.unmodifiable(new ObjectLinkedOpenHashSet<>(this.presenceKeys));
    }

    /** Returns an immutable snapshot of keys whose real quantities are stored by this interface. */
    public ObjectSet<AEKey> storedKeys() {
        return ObjectSets.unmodifiable(new ObjectLinkedOpenHashSet<>(this.quantities.keySet()));
    }

    /** Marks a resource type without creating a consumable quantity. */
    public boolean setPresent(AEKey key, boolean present) {
        boolean changed = present ? this.presenceKeys.add(key) : this.presenceKeys.remove(key);
        if (changed) {
            this.changeListener.run();
        }
        return changed;
    }

    /** Enables real quantity storage for a key without manufacturing an inventory entry. */
    public boolean registerQuantityKey(AEKey key) {
        boolean changed = this.quantityKeys.add(key);
        if (changed) {
            this.changeListener.run();
        }
        return changed;
    }

    /** Returns the currently stored real amount for a quantity key. */
    public long storedAmount(AEKey key) {
        return this.quantities.getLong(key);
    }

    /** Clears markers and quantities while loading a replacement state. */
    public void clear() {
        if (this.presenceKeys.isEmpty() && this.quantityKeys.isEmpty() && this.quantities.isEmpty()) {
            return;
        }
        this.presenceKeys.clear();
        this.quantityKeys.clear();
        this.quantities.clear();
        this.changeListener.run();
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        MEStorage.checkPreconditions(what, amount, mode, source);
        if (!this.quantityKeys.contains(what) || amount <= 0L) {
            return 0L;
        }
        long current = this.quantities.getLong(what);
        long accepted = Math.min(amount, Long.MAX_VALUE - current);
        if (accepted > 0L && mode == Actionable.MODULATE) {
            this.quantities.put(what, current + accepted);
            this.presenceKeys.add(what);
            this.changeListener.run();
        }
        return accepted;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        MEStorage.checkPreconditions(what, amount, mode, source);
        if (!this.quantityKeys.contains(what) || amount <= 0L) {
            return 0L;
        }
        long current = this.quantities.getLong(what);
        long extracted = Math.min(amount, current);
        if (extracted > 0L && mode == Actionable.MODULATE) {
            long remaining = current - extracted;
            if (remaining == 0L) {
                this.quantities.removeLong(what);
                this.presenceKeys.remove(what);
            } else {
                this.quantities.put(what, remaining);
            }
            this.changeListener.run();
        }
        return extracted;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (Object2LongMap.Entry<AEKey> entry : this.quantities.object2LongEntrySet()) {
            long amount = entry.getLongValue();
            if (amount > 0L) {
                out.add(entry.getKey(), amount);
            }
        }
    }

    @Override
    public Component getDescription() {
        return Component.translatable("block.data_energistics.digital_supply_interface");
    }

    /** Returns whether a key has a marker, regardless of whether its real quantity is currently nonzero. */
    public boolean contains(AEKey key) {
        return this.presenceKeys.contains(key);
    }

    /** Loads an older serialized marker without turning it into a quantity. */
    public void loadMarker(@Nullable AEKey key, long amount) {
        if (key != null && amount > 0L) {
            this.presenceKeys.add(key);
        }
    }

    /** Loads a persisted real quantity and enables future insertion for its key. */
    public void loadQuantity(@Nullable AEKey key, long amount) {
        if (key == null || amount <= 0L) {
            return;
        }
        this.quantityKeys.add(key);
        long current = this.quantities.getLong(key);
        long accepted = Math.min(amount, Long.MAX_VALUE - current);
        if (accepted > 0L) {
            this.quantities.put(key, current + accepted);
            this.presenceKeys.add(key);
        }
    }
}
