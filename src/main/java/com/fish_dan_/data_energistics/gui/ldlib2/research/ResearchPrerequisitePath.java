package com.fish_dan_.data_energistics.gui.ldlib2.research;

import com.fish_dan_.data_energistics.api.research.ResearchNode;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

/** Client UI projection of the selected node's full prerequisite path, including definitions hidden by filters. */
final class ResearchPrerequisitePath {

    private final ObjectSet<ResourceLocation> nodeIds = new ObjectOpenHashSet<>();
    private final ObjectSet<ResourceLocation> blockedIds = new ObjectOpenHashSet<>();
    private @Nullable ResourceLocation selected;

    void update(@Nullable ResourceLocation selected, Object2ObjectMap<ResourceLocation, ResearchNode> definitions,
                Object2ObjectMap<ResourceLocation, ResearchNode> projects, ObjectSet<ResourceLocation> unlocked) {
        this.selected = selected;
        nodeIds.clear();
        blockedIds.clear();
        if (selected == null) return;

        Object2ObjectMap<ResourceLocation, ObjectSet<ResourceLocation>> dependents = new Object2ObjectOpenHashMap<>();
        ObjectList<ResourceLocation> pending = new ObjectArrayList<>();
        nodeIds.add(selected);
        pending.add(selected);
        for (int index = 0; index < pending.size(); index++) {
            ResourceLocation id = pending.get(index);
            ResearchNode node = projects.containsKey(id) ? projects.get(id) :
                    definitions.containsKey(id) ? definitions.get(id) : null;
            // The synthetic item root and removed snapshot definitions have no further known prerequisites.
            if (node == null) continue;
            for (ResourceLocation prerequisite : node.prerequisites()) {
                dependents.computeIfAbsent(prerequisite, ignored -> new ObjectOpenHashSet<>()).add(id);
                if (nodeIds.add(prerequisite)) pending.add(prerequisite);
            }
        }

        pending.clear();
        for (ResourceLocation id : nodeIds) {
            // Selection itself is the research target; only its prerequisite chain seeds missing requirements.
            if (!id.equals(selected) && !unlocked.contains(id)) {
                blockedIds.add(id);
                pending.add(id);
            }
        }
        for (int index = 0; index < pending.size(); index++) {
            ResourceLocation id = pending.get(index);
            if (!dependents.containsKey(id)) continue;
            for (ResourceLocation dependent : dependents.get(id)) {
                if (blockedIds.add(dependent)) pending.add(dependent);
            }
        }
    }

    boolean contains(ResourceLocation id) {
        return nodeIds.contains(id);
    }

    boolean blocked(ResourceLocation id) {
        return blockedIds.contains(id);
    }

    boolean missingPrerequisite(ResourceLocation id) {
        return !id.equals(selected) && blocked(id);
    }
}
