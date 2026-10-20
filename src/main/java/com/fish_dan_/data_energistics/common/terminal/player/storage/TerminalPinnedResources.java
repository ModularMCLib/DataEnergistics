package com.fish_dan_.data_energistics.common.terminal.player.storage;

import appeng.api.stacks.AEKey;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.jspecify.annotations.Nullable;

/** Player-owned resource templates; hiding rows never removes their fixed slot assignments. */
public final class TerminalPinnedResources {

    public static final int MAX_ROWS = 19;
    public static final int MAX_SLOTS = MAX_ROWS * 20;
    private final Int2ObjectMap<AEKey> templates = new Int2ObjectLinkedOpenHashMap<>();
    private final Runnable changed;
    private int rows;

    public TerminalPinnedResources(Runnable changed) {
        this.changed = changed;
    }

    public void rows(int value) {
        if (value < 0 || value > MAX_ROWS) throw new IllegalArgumentException("Invalid terminal pin row count");
        rows = value;
        changed.run();
    }

    public void set(int index, @Nullable AEKey key) {
        if (index < 0 || index >= MAX_SLOTS) throw new IllegalArgumentException("Invalid terminal pin slot");
        if (key == null) templates.remove(index);
        else templates.put(index, key);
        changed.run();
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("rows", rows);
        ListTag slots = new ListTag();
        for (var entry : templates.int2ObjectEntrySet()) {
            CompoundTag slot = new CompoundTag();
            slot.putInt("slot", entry.getIntKey());
            slot.put("key", entry.getValue().toTagGeneric(registries));
            slots.add(slot);
        }
        tag.put("slots", slots);
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        rows = tag.getInt("rows");
        templates.clear();
        for (Tag value : tag.getList("slots", Tag.TAG_COMPOUND)) {
            CompoundTag slot = (CompoundTag) value;
            AEKey key = AEKey.fromTagGeneric(registries, slot.getCompound("key"));
            if (key != null) templates.put(slot.getInt("slot"), key);
        }
    }
}
