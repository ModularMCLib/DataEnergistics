package com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph;

import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature;
import com.fish_dan_.data_energistics.utils.FastUtilCollections;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.math.BigInteger;

/**
 * Immutable planner-facing transition captured from one AE crafting pattern.
 *
 * @param identity         stable component-aware semantic identity used for ordering and later invalidation
 * @param publication      complete immutable input/output surface
 * @param reusableBindings request-local complete assignments proved on the server, or empty for legacy Cartesian
 *                         binding
 */
public record TrinityCraftingGraphPattern(TrinityPatternIdentity identity,
                                          TrinityPatternPublicationSignature publication,
                                          ObjectList<ObjectList<TrinityBoundPatternInput>> reusableBindings,
                                          Object2ObjectMap<AEKey, BigInteger> lifetimeTools) {

    public TrinityCraftingGraphPattern {
        lifetimeTools = lifetimeTools.isEmpty() ? FastUtilCollections.mapOf() : FastUtilCollections.immutableMap(lifetimeTools);
        if (reusableBindings.isEmpty()) {
            reusableBindings = ObjectList.of();
        } else {
            ObjectArrayList<ObjectList<TrinityBoundPatternInput>> captured = new ObjectArrayList<>(reusableBindings.size());
            ObjectOpenHashSet<ObjectList<TrinityBoundPatternInput>> unique = new ObjectOpenHashSet<>(reusableBindings.size());
            for (ObjectList<TrinityBoundPatternInput> source : reusableBindings) {
                ObjectList<TrinityBoundPatternInput> assignment = source instanceof ObjectImmutableList<?> ? source : new ObjectImmutableList<>(source);
                if (assignment.size() != publication.inputs().size() || !unique.add(assignment)) {
                    throw new IllegalArgumentException("Reusable graph bindings must be complete and unique");
                }
                for (int slot = 0; slot < assignment.size(); slot++) {
                    if (assignment.get(slot).slotIndex() != slot ||
                            assignment.get(slot).multiplier() != publication.inputs().get(slot).multiplier()) {
                        throw new IllegalArgumentException("Reusable graph binding does not match the original input slots");
                    }
                }
                captured.add(assignment);
            }
            reusableBindings = ObjectLists.unmodifiable(captured);
        }
    }

    public TrinityCraftingGraphPattern(TrinityPatternIdentity identity, TrinityPatternPublicationSignature publication,
                                       ObjectList<ObjectList<TrinityBoundPatternInput>> reusableBindings) {
        this(identity, publication, reusableBindings, FastUtilCollections.mapOf());
    }

    /** Retains the original provider-only graph representation when no request-local rule was captured. */
    public TrinityCraftingGraphPattern(TrinityPatternIdentity identity, TrinityPatternPublicationSignature publication) {
        this(identity, publication, ObjectList.of());
    }

    /**
     * @return encoded pattern key with all data components
     */
    public AEItemKey definition() {
        return this.publication.definition();
    }

    /**
     * @return immutable ordered input slots and their legal alternatives
     */
    public ObjectList<TrinityPatternPublicationSignature.Input> inputs() {
        return this.publication.inputs();
    }

    /**
     * @return immutable ordered outputs, with the primary output first
     */
    public ObjectList<GenericStack> outputs() {
        return this.publication.outputs();
    }
}
