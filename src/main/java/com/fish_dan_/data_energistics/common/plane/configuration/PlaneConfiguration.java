package com.fish_dan_.data_energistics.common.plane.configuration;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

/** Only configured cells are stored. Callers validate network indices before changing a cell. */
public final class PlaneConfiguration {

    @Getter
    private final int slots;
    private final Runnable changed;
    private final Int2ObjectMap<AEKey> filters = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<AEKey> filterView = Int2ObjectMaps.unmodifiable(filters);
    private final IntSet itemModes = new IntOpenHashSet();

    public PlaneConfiguration(int slots, Runnable changed) {
        this.slots = slots;
        this.changed = changed;
    }

    public Int2ObjectMap<AEKey> filters() {
        return filterView;
    }

    public ItemStack filterStack(int slot) {
        return filters.containsKey(slot) ? filters.get(slot).wrapForDisplayOrFilter() : ItemStack.EMPTY;
    }

    public boolean matches(ItemStack stack) {
        return matches(AEItemKey.of(stack));
    }

    public boolean matches(@Nullable AEKey key) {
        return filters.isEmpty() || filters.containsValue(key);
    }

    public void setFilter(int slot, ItemStack stack) {
        GenericStack resource = GenericStack.fromItemStack(stack);
        AEKey key = resource == null ? null : resource.what();
        if (key == null) filters.remove(slot);
        else filters.put(slot, key);
        if (key != null && !(key instanceof AEItemKey)) itemModes.remove(slot);
        changed.run();
    }

    public PlanePlacementMode mode(int slot) {
        return itemModes.contains(slot) ? PlanePlacementMode.ITEM : PlanePlacementMode.BLOCK;
    }

    public void setMode(int slot, PlanePlacementMode mode) {
        if (mode == PlanePlacementMode.ITEM && allowsItemMode(slot)) itemModes.add(slot);
        else itemModes.remove(slot);
        changed.run();
    }

    private boolean allowsItemMode(int slot) {
        return !filters.containsKey(slot) || filters.get(slot) instanceof AEItemKey;
    }

    public void restore(PlaneConfigurationData data) {
        filters.clear();
        itemModes.clear();
        for (PlaneConfigurationData.Cell cell : data.cells()) {
            if (cell.slot() < slots) filters.put(cell.slot(), cell.filter());
        }
        for (int slot : data.itemModes()) {
            if (slot >= slots) continue;
            if (allowsItemMode(slot)) itemModes.add(slot);
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
