package com.fish_dan_.data_energistics.ae2.worldenergy;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;
import org.jspecify.annotations.Nullable;

/**
 * Read-only AE mount that exposes one amount-one marker for every known resource type.
 *
 * <p>
 * The storage never accepts or extracts a quantity. Real resource transactions use the ordinary network storage
 * returned by AE2, so these markers cannot become inventory, capacity or consumption.
 * </p>
 */
public final class PresenceMarkerStorage implements MEStorage {

    private final ObjectSet<AEKey> keys = new ObjectLinkedOpenHashSet<>();
    private final Runnable changeListener;

    public PresenceMarkerStorage(Runnable changeListener) {
        this.changeListener = changeListener;
    }

    /** Returns an immutable snapshot of currently present resource keys. */
    public ObjectSet<AEKey> keys() {
        return ObjectSets.unmodifiable(new ObjectLinkedOpenHashSet<>(this.keys));
    }

    /** Normalizes every positive external amount to one presence marker. */
    public boolean setPresent(AEKey key, boolean present) {
        boolean changed = present ? this.keys.add(key) : this.keys.remove(key);
        if (changed) {
            this.changeListener.run();
        }
        return changed;
    }

    /** Clears markers while loading a replacement state. */
    public void clear() {
        if (!this.keys.isEmpty()) {
            this.keys.clear();
            this.changeListener.run();
        }
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        MEStorage.checkPreconditions(what, amount, mode, source);
        return 0L;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        MEStorage.checkPreconditions(what, amount, mode, source);
        return 0L;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (AEKey key : this.keys) {
            out.add(key, 1L);
        }
    }

    @Override
    public Component getDescription() {
        return Component.translatable("block.data_energistics.digital_supply_interface");
    }

    /** Returns whether a key is present, treating every positive amount as the same marker. */
    public boolean contains(AEKey key) {
        return this.keys.contains(key);
    }

    /** Normalizes a serialized marker amount at the storage boundary. */
    public void loadMarker(@Nullable AEKey key, long amount) {
        if (key != null && amount > 0L) {
            setPresent(key, true);
        }
    }
}
