package com.fish_dan_.data_energistics.client.terminal.storage;

import com.fish_dan_.data_energistics.client.terminal.search.TerminalSearchQuery;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.HolderLookup;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;

import java.util.Comparator;
import java.util.Objects;

/**
 * Session-local full resource index. Delta revisions and local search projections are independent of UI paging.
 */
@NullMarked
public final class TerminalStorageIndex {

    private final Object2ObjectLinkedOpenHashMap<AEKey, Entry> entries = new Object2ObjectLinkedOpenHashMap<>();
    private ObjectList<Entry> projected = ObjectImmutableList.of();
    private long revision = -1;
    private boolean dirty = true;
    private boolean pending;
    private int total;
    private String query = "";
    private String sort = "name";
    private String filter = "all";
    private String type = "all";
    private boolean descending;
    private Language language = Language.getInstance();
    private TerminalSearchQuery search = new TerminalSearchQuery("");

    /**
     * Applies each server revision once. Progress metadata may also change after the final empty batch.
     */
    public void apply(CompoundTag snapshot, HolderLookup.Provider registries) {
        if (!snapshot.contains("storage_revision")) return;
        long incoming = snapshot.getLong("storage_revision");
        if (incoming < revision) return;
        pending = snapshot.getBoolean("storage_pending");
        total = snapshot.getInt("storage_total");
        if (incoming == revision) return;
        revision = incoming;
        if (snapshot.getBoolean("storage_reset")) {
            dirty |= !entries.isEmpty();
            entries.clear();
            search = new TerminalSearchQuery(query);
        }
        for (Tag value : snapshot.getList("storage_removed", Tag.TAG_COMPOUND)) {
            AEKey key = Objects.requireNonNull(AEKey.fromTagGeneric(registries, (CompoundTag) value), "Invalid removed terminal resource");
            dirty |= entries.containsKey(key);
            entries.remove(key);
        }
        for (Tag value : snapshot.getList("storage", Tag.TAG_COMPOUND)) {
            CompoundTag raw = (CompoundTag) value;
            GenericStack stack = Objects.requireNonNull(GenericStack.readTag(registries, raw.getCompound("stack")), "Invalid indexed terminal resource");
            Entry entry = new Entry(stack.what(), stack.amount(), raw.getBoolean("craftable"));
            dirty |= !entry.equals(entries.put(entry.key(), entry));
        }
    }

    /**
     * Rebuilds only after actual index, language or query-option changes; equal projections keep their identity.
     */
    public void project(String query, String sort, boolean descending, String filter, String type) {
        Language currentLanguage = Language.getInstance();
        if (!this.query.equals(query) || language != currentLanguage) {
            this.query = query;
            language = currentLanguage;
            search = new TerminalSearchQuery(query);
            dirty = true;
        }
        if (!this.sort.equals(sort) || this.descending != descending || !this.filter.equals(filter) || !this.type.equals(type))
            dirty = true;
        this.sort = sort;
        this.descending = descending;
        this.filter = filter;
        this.type = type;
        if (!dirty) return;
        dirty = false;
        ObjectList<Entry> matches = new ObjectArrayList<>();
        for (Entry entry : entries.values()) {
            if ((!type.equals("all") && !entry.key().getType().getId().toString().equals(type)) ||
                    (filter.equals("stored") && entry.amount() == 0) || (filter.equals("craftable") && !entry.craftable()) || !search.test(entry.key()))
                continue;
            matches.add(entry);
        }
        Comparator<Entry> comparator = switch (sort) {
            case "amount" -> Comparator.comparingLong(Entry::amount);
            case "mod" -> Comparator.comparing(entry -> entry.key().getModId(), String.CASE_INSENSITIVE_ORDER);
            default -> Comparator.comparing(entry -> search.displayName(entry.key()), String.CASE_INSENSITIVE_ORDER);
        };
        if (descending) comparator = comparator.reversed();
        matches.sort(comparator.thenComparing(entry -> entry.key().getId().toString()).thenComparingInt(entry -> entry.key().hashCode()));
        if (!matches.equals(projected)) projected = new ObjectImmutableList<>(matches);
    }

    public ObjectList<Entry> projected() {
        return projected;
    }

    public int loaded() {
        return entries.size();
    }

    public int total() {
        return total;
    }

    public boolean pending() {
        return pending;
    }

    public record Entry(AEKey key, long amount, boolean craftable) {
        public GenericStack stack() {
            return new GenericStack(key, amount);
        }
    }
}
