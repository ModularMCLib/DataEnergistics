package com.fish_dan_.data_energistics.common.plane.configuration;

import appeng.api.stacks.AEItemKey;

import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;

/** Only configured cells are stored. Callers validate network indices before changing a cell. */
public final class PlaneConfiguration {

    @Getter
    private final int slots;
    private final Runnable changed;
    private final Int2ObjectMap<AEItemKey> filters = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<AEItemKey> filterView = Int2ObjectMaps.unmodifiable(filters);
    private final IntSet itemModes = new IntOpenHashSet();

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

    public boolean matches(ItemStack stack) {
        return filters.containsValue(AEItemKey.of(stack));
    }

    public void setFilter(int slot, ItemStack stack) {
        AEItemKey key = AEItemKey.of(stack.copyWithCount(1));
        if (key == null) filters.remove(slot);
        else filters.put(slot, key);
        changed.run();
    }

    public PlanePlacementMode mode(int slot) {
        return itemModes.contains(slot) ? PlanePlacementMode.ITEM : PlanePlacementMode.BLOCK;
    }

    public void setMode(int slot, PlanePlacementMode mode) {
        if (mode == PlanePlacementMode.ITEM) itemModes.add(slot);
        else itemModes.remove(slot);
        changed.run();
    }

    public void restore(PlaneConfigurationData data) {
        filters.clear();
        itemModes.clear();
        for (int slot : data.itemModes()) {
            if (slot < slots) itemModes.add(slot);
        }
        for (PlaneConfigurationData.Cell cell : data.cells()) {
            if (cell.slot() < slots) filters.put(cell.slot(), cell.filter());
        }
    }

    public PlaneConfigurationData snapshot() {
        var cells = new ObjectArrayList<PlaneConfigurationData.Cell>(filters.size());
        for (var entry : filters.int2ObjectEntrySet()) {
            cells.add(new PlaneConfigurationData.Cell(entry.getIntKey(), entry.getValue()));
        }
        return new PlaneConfigurationData(cells, new IntArrayList(itemModes));
    }
}
