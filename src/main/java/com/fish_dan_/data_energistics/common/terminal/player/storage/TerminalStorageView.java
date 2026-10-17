package com.fish_dan_.data_energistics.common.terminal.player.storage;

import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.function.Predicate;

/**
 * Projects a sorted page of stored and craftable resources; requests identify resources by key, never row number.
 */
public final class TerminalStorageView {

    private Predicate<AEKey> search = key -> true;
    private String sort = "name";
    private String filter = "all";
    private String type = "all";
    private boolean descending;
    private int offset;
    private int limit = 96;

    public void configure(CompoundTag tag) {
        String nextQuery = tag.getString("query");
        String nextSort = tag.getString("sort");
        String nextFilter = tag.getString("filter");
        int nextOffset = tag.getInt("offset");
        int nextLimit = tag.getInt("limit");
        if (nextQuery.length() > 256 || !(nextSort.equals("name") || nextSort.equals("amount") || nextSort.equals("mod")) ||
                !(nextFilter.equals("all") || nextFilter.equals("stored") || nextFilter.equals("craftable")) ||
                nextOffset < 0 || nextLimit < 1 || nextLimit > 512)
            throw new IllegalArgumentException("Invalid terminal storage view");
        search = TerminalResourceSearch.compile(nextQuery);
        sort = nextSort;
        filter = nextFilter;
        type = tag.getString("type");
        descending = tag.getBoolean("descending");
        offset = nextOffset;
        limit = nextLimit;
    }

    public void snapshot(CompoundTag snapshot, @Nullable TerminalConnection connection, HolderLookup.Provider registries) {
        ObjectList<Entry> entries = new ObjectArrayList<>();
        if (connection != null) {
            var amounts = new Object2LongOpenHashMap<AEKey>();
            for (var entry : connection.grid().getStorageService().getCachedInventory()) {
                if (entry.getLongValue() > 0) amounts.put(entry.getKey(), entry.getLongValue());
            }
            var craftables = connection.grid().getCraftingService().getCraftables(key -> true);
            for (AEKey key : craftables) amounts.putIfAbsent(key, 0);
            for (var entry : amounts.object2LongEntrySet()) {
                AEKey key = entry.getKey();
                boolean craftable = craftables.contains(key);
                if ((!type.equals("all") && !key.getType().getId().toString().equals(type)) ||
                        (filter.equals("stored") && entry.getLongValue() == 0) || (filter.equals("craftable") && !craftable) || !search.test(key))
                    continue;
                entries.add(new Entry(key, entry.getLongValue(), craftable));
            }
        }
        Comparator<Entry> comparator = switch (sort) {
            case "amount" -> Comparator.comparingLong(Entry::amount);
            case "mod" -> Comparator.comparing(value -> value.key().getModId());
            default ->
                    Comparator.comparing(value -> value.key().getDisplayName().getString(), String.CASE_INSENSITIVE_ORDER);
        };
        if (descending) comparator = comparator.reversed();
        entries.sort(comparator.thenComparing(value -> value.key().getId().toString()).thenComparingInt(value -> value.key().hashCode()));
        offset = Math.min(offset, Math.max(0, entries.size() - 1));
        ListTag page = new ListTag();
        for (int index = offset; index < Math.min(entries.size(), offset + limit); index++) {
            Entry entry = entries.get(index);
            CompoundTag tag = new CompoundTag();
            tag.put("stack", GenericStack.writeTag(registries, new GenericStack(entry.key(), entry.amount())));
            tag.putBoolean("craftable", entry.craftable());
            page.add(tag);
        }
        snapshot.put("storage", page);
        snapshot.putInt("storage_total", entries.size());
        snapshot.putInt("storage_offset", offset);
    }

    private record Entry(AEKey key, long amount, boolean craftable) {
    }
}
