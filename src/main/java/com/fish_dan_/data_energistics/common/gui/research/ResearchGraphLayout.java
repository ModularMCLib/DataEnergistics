package com.fish_dan_.data_energistics.common.gui.research;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.Comparator;
import java.util.PriorityQueue;

/** Reserves a complete band for each branch before placing any of its descendants. */
final class ResearchGraphLayout {

    private static final int COLUMN_GAP = 44;
    private static final int ROW_GAP = 26;
    private final Object2IntOpenHashMap<ResourceLocation> ranks = new Object2IntOpenHashMap<>();
    private final Object2IntOpenHashMap<ResourceLocation> rows = new Object2IntOpenHashMap<>();

    static ResearchGraphLayout forDependencies(Object2ObjectMap<ResourceLocation, ObjectSet<ResourceLocation>> dependencies) {
        ResearchGraphLayout layout = new ResearchGraphLayout();
        Object2ObjectMap<ResourceLocation, ObjectList<ResourceLocation>> successors = new Object2ObjectOpenHashMap<>();
        Object2IntOpenHashMap<ResourceLocation> pending = new Object2IntOpenHashMap<>();
        Comparator<ResourceLocation> byId = Comparator.comparing(ResourceLocation::toString);
        PriorityQueue<ResourceLocation> ready = new PriorityQueue<>(byId);
        for (ResourceLocation id : dependencies.keySet()) successors.put(id, new ObjectArrayList<>());
        for (var entry : dependencies.object2ObjectEntrySet()) {
            int count = 0;
            for (ResourceLocation prerequisite : entry.getValue()) {
                if (!dependencies.containsKey(prerequisite)) continue;
                successors.get(prerequisite).add(entry.getKey());
                count++;
            }
            pending.put(entry.getKey(), count);
            if (count == 0) ready.add(entry.getKey());
        }
        ObjectList<ResourceLocation> ordered = new ObjectArrayList<>();
        while (!ready.isEmpty()) {
            ResourceLocation id = ready.remove();
            ordered.add(id);
            for (ResourceLocation child : successors.get(id)) {
                layout.ranks.put(child, Math.max(layout.ranks.getInt(child), layout.ranks.getInt(id) + 1));
                int remaining = pending.getInt(child) - 1;
                pending.put(child, remaining);
                if (remaining == 0) ready.add(child);
            }
        }
        if (ordered.size() != dependencies.size()) throw new IllegalArgumentException("Research layout requires an acyclic graph");

        Object2ObjectMap<ResourceLocation, ObjectList<ResourceLocation>> branches = new Object2ObjectOpenHashMap<>();
        for (ResourceLocation id : ordered) branches.put(id, new ObjectArrayList<>());
        ObjectList<ResourceLocation> roots = new ObjectArrayList<>();
        for (ResourceLocation id : ordered) {
            // Prefer a dedicated continuation over a prerequisite shared by several branches. Other
            // prerequisite edges still determine columns and are drawn as cross-branch connections.
            Comparator<ResourceLocation> parentOrder = Comparator
                    .comparingInt((ResourceLocation parent) -> successors.get(parent).size())
                    .thenComparing(Comparator.comparingInt((ResourceLocation parent) -> layout.ranks.getInt(parent)).reversed())
                    .thenComparing(byId);
            ResourceLocation parent = null;
            for (ResourceLocation prerequisite : dependencies.get(id)) {
                if (!dependencies.containsKey(prerequisite)) continue;
                if (parent == null || parentOrder.compare(prerequisite, parent) < 0) parent = prerequisite;
            }
            if (parent == null) roots.add(id);
            else branches.get(parent).add(id);
        }
        roots.sort(byId);
        branches.values().forEach(children -> children.sort(byId));
        Object2IntOpenHashMap<ResourceLocation> spans = new Object2IntOpenHashMap<>();
        for (int index = ordered.size() - 1; index >= 0; index--) {
            ResourceLocation id = ordered.get(index);
            int span = 0;
            for (ResourceLocation child : branches.get(id)) span += spans.getInt(child);
            spans.put(id, Math.max(1, span));
        }
        int nextRow = 0;
        for (ResourceLocation root : roots) {
            layout.rows.put(root, nextRow);
            nextRow += spans.getInt(root);
        }
        for (ResourceLocation id : ordered) {
            int row = layout.rows.getInt(id);
            for (ResourceLocation child : branches.get(id)) {
                layout.rows.put(child, row);
                row += spans.getInt(child);
            }
        }
        return layout;
    }

    Object2ObjectMap<ResourceLocation, Position> positions(Object2ObjectMap<ResourceLocation, CardSize> sizes) {
        Int2IntOpenHashMap widths = new Int2IntOpenHashMap();
        Int2IntOpenHashMap heights = new Int2IntOpenHashMap();
        int maxRank = 0;
        int maxRow = 0;
        for (var entry : sizes.object2ObjectEntrySet()) {
            ResourceLocation id = entry.getKey();
            if (!rows.containsKey(id)) throw new IllegalArgumentException("Unknown research layout node: " + id);
            int rank = ranks.getInt(id);
            int row = rows.getInt(id);
            widths.put(rank, Math.max(widths.get(rank), entry.getValue().width()));
            heights.put(row, Math.max(heights.get(row), entry.getValue().height()));
            maxRank = Math.max(maxRank, rank);
            maxRow = Math.max(maxRow, row);
        }
        Int2IntOpenHashMap columns = new Int2IntOpenHashMap();
        int left = 0;
        for (int rank = 0; rank <= maxRank; rank++) {
            columns.put(rank, left);
            left += widths.get(rank) + COLUMN_GAP;
        }
        Int2IntOpenHashMap rowTops = new Int2IntOpenHashMap();
        int top = 0;
        for (int row = 0; row <= maxRow; row++) {
            rowTops.put(row, top);
            top += heights.get(row) + ROW_GAP;
        }
        Object2ObjectMap<ResourceLocation, Position> positions = new Object2ObjectOpenHashMap<>();
        for (ResourceLocation id : sizes.keySet()) {
            positions.put(id, new Position(columns.get(ranks.getInt(id)), rowTops.get(rows.getInt(id))));
        }
        return positions;
    }

    record CardSize(int width, int height) {}

    record Position(int x, int y) {}
}
