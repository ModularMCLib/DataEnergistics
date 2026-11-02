package com.fish_dan_.data_energistics.common.plane.configuration;

import appeng.api.stacks.AEItemKey;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;

/** Only configured cells are stored. Callers validate network indices before changing a cell. */
public final class PlaneConfiguration {

    @Getter
    private final int slots;
    private final Runnable changed;
    private final Int2ObjectMap<AEItemKey> filters = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<AEItemKey> filterView = Int2ObjectMaps.unmodifiable(filters);
    private int entityModes;

    public PlaneConfiguration(int slots, Runnable changed) {
        this.slots = slots;
        this.changed = changed;
    }

    public Int2ObjectMap<AEItemKey> filters() {
        return filterView;
    }

    public ItemStack filterStack(int slot) {
        AEItemKey key = filters.get(slot);
        return key == null ? ItemStack.EMPTY : key.toStack();
    }

    public void setFilter(int slot, ItemStack stack) {
        AEItemKey key = AEItemKey.of(stack.copyWithCount(1));
        if (key == null) filters.remove(slot);
        else filters.put(slot, key);
        changed.run();
    }

    public int modeMask() {
        return entityModes;
    }

    public PlanePlacementMode mode(int slot) {
        return (entityModes & 1 << slot) == 0 ? PlanePlacementMode.BLOCK : PlanePlacementMode.ITEM;
    }

    public void setMode(int slot, PlanePlacementMode mode) {
        if (mode == PlanePlacementMode.ITEM) entityModes |= 1 << slot;
        else entityModes &= ~(1 << slot);
        changed.run();
    }

    public void read(CompoundTag data, HolderLookup.Provider registries) {
        filters.clear();
        entityModes = data.getInt("entity_modes") & ((1 << slots) - 1);
        ListTag cells = data.getList("cells", Tag.TAG_COMPOUND);
        for (int index = 0; index < cells.size(); index++) {
            CompoundTag cell = cells.getCompound(index);
            if (!cell.contains("slot", Tag.TAG_INT)) continue;
            int slot = cell.getInt("slot");
            if (slot < 0 || slot >= slots) continue;
            AEItemKey key = AEItemKey.fromTag(registries, cell);
            if (key != null) filters.put(slot, key);
        }
    }

    public void write(CompoundTag data, HolderLookup.Provider registries) {
        data.putInt("entity_modes", entityModes);
        ListTag cells = new ListTag();
        for (var entry : filters.int2ObjectEntrySet()) {
            CompoundTag cell = entry.getValue().toTag(registries);
            cell.putInt("slot", entry.getIntKey());
            cells.add(cell);
        }
        data.put("cells", cells);
    }
}
