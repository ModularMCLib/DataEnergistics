package com.fish_dan_.data_energistics.common.gui.research;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectHeapPriorityQueue;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.Comparator;

/** Measures complete descendant bands, then centers every parent within its band at every depth. */
final class ResearchGraphLayout {

    private static final int COLUMN_GAP = 44;
    private static final int ROW_GAP = 26;
    private final Object2IntOpenHashMap<ResourceLocation> ranks = new Object2IntOpenHashMap<>();
    private final Object2ObjectMap<ResourceLocation, ObjectList<ResourceLocation>> branches = new Object2ObjectOpenHashMap<>();
    private final ObjectList<ResourceLocation> ordered = new ObjectArrayList<>();
    private final ObjectList<ResourceLocation> roots = new ObjectArrayList<>();

    static ResearchGraphLayout forDependencies(Object2ObjectMap<ResourceLocation, ObjectSet<ResourceLocation>> dependencies) {
        ResearchGraphLayout layout = new ResearchGraphLayout();
        Object2ObjectMap<ResourceLocation, ObjectList<ResourceLocation>> successors = new Object2ObjectOpenHashMap<>();
        Object2IntOpenHashMap<ResourceLocation> pending = new Object2IntOpenHashMap<>();
        Comparator<ResourceLocation> byId = Comparator.comparing(ResourceLocation::toString);
        ObjectHeapPriorityQueue<ResourceLocation> ready = new ObjectHeapPriorityQueue<>(byId);
        for (ResourceLocation id : dependencies.keySet()) successors.put(id, new ObjectArrayList<>());
        for (var entry : dependencies.object2ObjectEntrySet()) {
            int count = 0;
            for (ResourceLocation prerequisite : entry.getValue()) {
                if (!dependencies.containsKey(prerequisite)) continue;
                successors.get(prerequisite).add(entry.getKey());
                count++;
            }
            pending.put(entry.getKey(), count);
            if (count == 0) ready.enqueue(entry.getKey());
        }
        ObjectList<ResourceLocation> ordered = layout.ordered;
        while (!ready.isEmpty()) {
            ResourceLocation id = ready.dequeue();
            ordered.add(id);
            for (ResourceLocation child : successors.get(id)) {
                layout.ranks.put(child, Math.max(layout.ranks.getInt(child), layout.ranks.getInt(id) + 1));
                int remaining = pending.getInt(child) - 1;
                pending.put(child, remaining);
                if (remaining == 0) ready.enqueue(child);
            }
        }
        if (ordered.size() != dependencies.size()) throw new IllegalArgumentException("Research layout requires an acyclic graph");

        Object2ObjectMap<ResourceLocation, ObjectList<ResourceLocation>> branches = layout.branches;
        for (ResourceLocation id : ordered) branches.put(id, new ObjectArrayList<>());
        ObjectList<ResourceLocation> roots = layout.roots;
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
        return layout;
    }

    Object2ObjectMap<ResourceLocation, Position> positions(Object2ObjectMap<ResourceLocation, CardSize> sizes) {
        Int2IntOpenHashMap widths = new Int2IntOpenHashMap();
        int maxRank = 0;
        for (var entry : sizes.object2ObjectEntrySet()) {
            ResourceLocation id = entry.getKey();
            if (!branches.containsKey(id)) throw new IllegalArgumentException("Unknown research layout node: " + id);
            int rank = ranks.getInt(id);
            widths.put(rank, Math.max(widths.get(rank), entry.getValue().width()));
            maxRank = Math.max(maxRank, rank);
        }
        Int2IntOpenHashMap columns = new Int2IntOpenHashMap();
        int left = 0;
        for (int rank = 0; rank <= maxRank; rank++) {
            columns.put(rank, left);
            left += widths.get(rank) + COLUMN_GAP;
        }
        Object2IntOpenHashMap<ResourceLocation> branchHeights = new Object2IntOpenHashMap<>();
        Object2IntOpenHashMap<ResourceLocation> childHeights = new Object2IntOpenHashMap<>();
        for (int index = ordered.size() - 1; index >= 0; index--) {
            ResourceLocation id = ordered.get(index);
            ObjectList<ResourceLocation> children = branches.get(id);
            int height = 0;
            for (ResourceLocation child : children) height += branchHeights.getInt(child);
            if (!children.isEmpty()) height += (children.size() - 1) * ROW_GAP;
            childHeights.put(id, height);
            branchHeights.put(id, Math.max(sizes.get(id).height(), height));
        }
        Object2IntOpenHashMap<ResourceLocation> tops = new Object2IntOpenHashMap<>();
        int nextTop = 0;
        for (ResourceLocation root : roots) {
            tops.put(root, nextTop);
            nextTop += branchHeights.getInt(root) + ROW_GAP;
        }
        Object2ObjectMap<ResourceLocation, Position> positions = new Object2ObjectOpenHashMap<>();
        for (ResourceLocation id : ordered) {
            int top = tops.getInt(id);
            int height = branchHeights.getInt(id);
            positions.put(id, new Position(columns.get(ranks.getInt(id)), top + (height - sizes.get(id).height()) / 2));
            // Keep the band's top fixed and center its parent over the descendants' actual height.
            int childTop = top + (height - childHeights.getInt(id)) / 2;
            for (ResourceLocation child : branches.get(id)) {
                tops.put(child, childTop);
                childTop += branchHeights.getInt(child) + ROW_GAP;
            }
        }
        return positions;
    }

    record CardSize(int width, int height) {}

    record Position(int x, int y) {}
}
