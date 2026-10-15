package com.fish_dan_.data_energistics.api.research;

import appeng.api.stacks.AEKey;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

import java.util.Objects;

/** Immutable Java defaults or a validated datapack override. Register during the mod registration event. */
public record ResearchNode(ResourceLocation id, ResearchScope scope, String name, String description,
                           ResourceLocation icon, ResearchTarget target, ObjectList<ResearchInput> inputs,
                           ObjectSet<ResourceLocation> requiredPrerequisites,
                           ObjectList<ObjectSet<ResourceLocation>> optionalPrerequisiteGroups,
                           int researchTicks, double aePerTick) {

    public ResearchNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(requiredPrerequisites, "requiredPrerequisites");
        Objects.requireNonNull(optionalPrerequisiteGroups, "optionalPrerequisiteGroups");
        if (!name.matches("[a-zA-Z0-9_.:/-]+") || !description.matches("[a-zA-Z0-9_.:/-]+")) {
            throw new IllegalArgumentException("Research name and description must be translation keys");
        }
        if (researchTicks <= 0 || !Double.isFinite(aePerTick) || aePerTick < 0) {
            throw new IllegalArgumentException("Research ticks must be positive and AE/t finite and nonnegative");
        }
        if (scope == ResearchScope.ITEM_UPGRADE && target.items().isEmpty() && target.tags().isEmpty()) {
            throw new IllegalArgumentException("Item upgrades require a target item or tag");
        }
        ObjectSet<ResourceLocation> prerequisiteIds = new ObjectOpenHashSet<>();
        for (ResourceLocation prerequisite : requiredPrerequisites) {
            Objects.requireNonNull(prerequisite, "requiredPrerequisites contains null");
            if (!prerequisiteIds.add(prerequisite)) {
                throw new IllegalArgumentException("Duplicate research prerequisite " + prerequisite + " for node " + id);
            }
        }
        ObjectList<ObjectSet<ResourceLocation>> groups = new ObjectArrayList<>(optionalPrerequisiteGroups.size());
        for (ObjectSet<ResourceLocation> group : optionalPrerequisiteGroups) {
            Objects.requireNonNull(group, "optionalPrerequisiteGroups contains null");
            if (group.isEmpty()) throw new IllegalArgumentException("Optional prerequisite groups cannot be empty");
            ObjectSet<ResourceLocation> copy = new ObjectOpenHashSet<>();
            for (ResourceLocation prerequisite : group) {
                Objects.requireNonNull(prerequisite, "optional prerequisite group contains null");
                if (!copy.add(prerequisite) || !prerequisiteIds.add(prerequisite)) {
                    throw new IllegalArgumentException("Duplicate research prerequisite " + prerequisite + " for node " + id);
                }
            }
            groups.add(ObjectSets.unmodifiable(copy));
        }
        Object2LongMap<AEKey> totals = new Object2LongLinkedOpenHashMap<>();
        for (ResearchInput input : inputs) {
            try {
                totals.mergeLong(input.key(), input.amount(), Math::addExact);
            } catch (ArithmeticException error) {
                throw new IllegalArgumentException("Research input total overflows for node " + id + ", key " + input.key(), error);
            }
        }
        inputs = new ObjectImmutableList<>(inputs);
        requiredPrerequisites = ObjectSets.unmodifiable(new ObjectOpenHashSet<>(requiredPrerequisites));
        optionalPrerequisiteGroups = ObjectLists.unmodifiable(groups);
    }

    /** Returns every prerequisite edge, including every alternative in an optional group. */
    public ObjectSet<ResourceLocation> prerequisiteIds() {
        ObjectSet<ResourceLocation> result = new ObjectOpenHashSet<>(requiredPrerequisites);
        for (ObjectSet<ResourceLocation> group : optionalPrerequisiteGroups) result.addAll(group);
        return result;
    }

    /** Returns whether all required edges and one member of every optional group are unlocked. */
    public boolean prerequisitesSatisfied(ObjectSet<ResourceLocation> unlocked) {
        if (!unlocked.containsAll(requiredPrerequisites)) return false;
        for (ObjectSet<ResourceLocation> group : optionalPrerequisiteGroups) {
            boolean satisfied = false;
            for (ResourceLocation prerequisite : group) {
                if (unlocked.contains(prerequisite)) {
                    satisfied = true;
                    break;
                }
            }
            if (!satisfied) return false;
        }
        return true;
    }

    /** Returns whether an edge is missing; optional edges are missing only when their whole group is missing. */
    public boolean prerequisiteMissing(ResourceLocation prerequisite, ObjectSet<ResourceLocation> unlocked) {
        if (requiredPrerequisites.contains(prerequisite)) return !unlocked.contains(prerequisite);
        for (ObjectSet<ResourceLocation> group : optionalPrerequisiteGroups) {
            if (group.contains(prerequisite)) return !unlocked.contains(prerequisite);
        }
        return false;
    }

    /** Required parameters make every node explicitly choose its time and energy cost. */
    public static Builder builder(ResourceLocation id, ResearchScope scope, String name, String description,
                                  int researchTicks, double aePerTick) {
        return new Builder(id, scope, name, description, researchTicks, aePerTick);
    }

    public static final class Builder {

        private final ResourceLocation id;
        private final ResearchScope scope;
        private final String name;
        private final String description;
        private final int ticks;
        private final double energy;
        private ResourceLocation icon = ResourceLocation.withDefaultNamespace("knowledge_book");
        private ResearchTarget target = ResearchTarget.NONE;
        private final ObjectList<ResearchInput> inputs = new ObjectArrayList<>();
        private final ObjectSet<ResourceLocation> requiredPrerequisites = new ObjectOpenHashSet<>();
        private final ObjectList<ObjectSet<ResourceLocation>> optionalPrerequisiteGroups = new ObjectArrayList<>();

        private Builder(ResourceLocation id, ResearchScope scope, String name, String description, int ticks, double energy) {
            this.id = id;
            this.scope = scope;
            this.name = name;
            this.description = description;
            this.ticks = ticks;
            this.energy = energy;
        }

        public Builder icon(ResourceLocation value) {
            this.icon = value;
            return this;
        }

        public Builder target(ResearchTarget value) {
            this.target = value;
            return this;
        }

        public Builder input(ResearchInput value) {
            this.inputs.add(value);
            return this;
        }

        public Builder requiredPrerequisite(ResourceLocation value) {
            this.requiredPrerequisites.add(value);
            return this;
        }

        public Builder optionalPrerequisites(Iterable<ResourceLocation> values) {
            ObjectSet<ResourceLocation> group = new ObjectOpenHashSet<>();
            for (ResourceLocation value : values) group.add(value);
            if (group.isEmpty()) throw new IllegalArgumentException("Optional prerequisite groups cannot be empty");
            this.optionalPrerequisiteGroups.add(group);
            return this;
        }

        public ResearchNode build() {
            return new ResearchNode(id, scope, name, description, icon, target, inputs,
                    requiredPrerequisites, optionalPrerequisiteGroups, ticks, energy);
        }
    }
}
