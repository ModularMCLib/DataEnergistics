package com.fish_dan_.data_energistics.api.research;

import appeng.api.stacks.AEKey;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

import java.util.Objects;

/** Immutable Java defaults or a validated datapack override. Register during the mod registration event. */
public record ResearchNode(ResourceLocation id, ResearchScope scope, String name, String description,
                           ResourceLocation icon, ResearchTarget target, ObjectList<ResearchInput> inputs,
                           ObjectSet<ResourceLocation> prerequisites, int researchTicks, double aePerTick) {

    public ResearchNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(target, "target");
        if (!name.matches("[a-zA-Z0-9_.:/-]+") || !description.matches("[a-zA-Z0-9_.:/-]+")) {
            throw new IllegalArgumentException("Research name and description must be translation keys");
        }
        if (researchTicks <= 0 || !Double.isFinite(aePerTick) || aePerTick < 0) {
            throw new IllegalArgumentException("Research ticks must be positive and AE/t finite and nonnegative");
        }
        if (scope == ResearchScope.ITEM_UPGRADE && target.items().isEmpty() && target.tags().isEmpty()) {
            throw new IllegalArgumentException("Item upgrades require a target item or tag");
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
        prerequisites = ObjectSets.unmodifiable(new ObjectOpenHashSet<>(prerequisites));
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
        private final ObjectSet<ResourceLocation> prerequisites = new ObjectOpenHashSet<>();

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

        public Builder prerequisite(ResourceLocation value) {
            this.prerequisites.add(value);
            return this;
        }

        public ResearchNode build() {
            return new ResearchNode(id, scope, name, description, icon, target, inputs, prerequisites, ticks, energy);
        }
    }
}
