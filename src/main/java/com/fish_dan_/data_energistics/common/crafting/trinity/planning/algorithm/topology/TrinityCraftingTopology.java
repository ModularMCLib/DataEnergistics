package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Deterministic Tarjan partition and condensation DAG for one immutable variant graph.
 *
 * @param components                stable components
 * @param componentByKey            exact key-to-component lookup
 * @param topologicalOrder          input-to-output condensation order
 * @param variantsByOutputComponent explicit transition ownership for every output-side component
 * @param variantsByOutputKey       exact pre-sorted producer index for reverse demand propagation
 * @param cyclicOwnerByVariant      unique cyclic component that owns a transition with internal feedback
 */
public record TrinityCraftingTopology(
                                      List<TrinityStronglyConnectedComponent> components,
                                      Object2IntMap<AEKey> componentByKey,
                                      IntList topologicalOrder,
                                      Int2ObjectMap<List<TrinityPatternVariant>> variantsByOutputComponent,
                                      Map<AEKey, List<TrinityPatternVariant>> variantsByOutputKey,
                                      Object2IntMap<TrinityPatternVariant> cyclicOwnerByVariant) {

    /**
     * Validates complete component coverage and a legal condensation ordering.
     */
    public TrinityCraftingTopology {
        if (components.isEmpty() || components.size() != topologicalOrder.size()) {
            throw new IllegalArgumentException("A Trinity crafting topology requires complete components and order");
        }
        components = List.copyOf(components);
        int componentCount = components.size();
        Object2IntLinkedOpenHashMap<AEKey> copiedMapping = new Object2IntLinkedOpenHashMap<>();
        Object2IntMaps.fastForEach(componentByKey, entry -> {
            int index = entry.getIntValue();
            if (index < 0 || index >= componentCount) {
                throw new IllegalArgumentException("A Trinity topology key must map to a valid component");
            }
            copiedMapping.put(entry.getKey(), index);
        });
        for (TrinityStronglyConnectedComponent component : components) {
            if (component.index() >= components.size()) {
                throw new IllegalArgumentException("A Trinity topology component index is invalid");
            }
            for (AEKey key : component.keys()) {
                if (!copiedMapping.containsKey(key) || copiedMapping.getInt(key) != component.index()) {
                    throw new IllegalArgumentException("A Trinity topology must map every component key exactly");
                }
            }
        }
        Int2ObjectLinkedOpenHashMap<List<TrinityPatternVariant>> copiedVariants = new Int2ObjectLinkedOpenHashMap<>();
        Int2ObjectMaps.fastForEach(variantsByOutputComponent, entry -> {
            int index = entry.getIntKey();
            if (index < 0 || index >= componentCount) {
                throw new IllegalArgumentException("A Trinity output transition index must reference a component");
            }
            copiedVariants.put(index, List.copyOf(entry.getValue()));
        });
        variantsByOutputComponent = Int2ObjectMaps.unmodifiable(copiedVariants);
        Object2ObjectLinkedOpenHashMap<AEKey, List<TrinityPatternVariant>> copiedProducers = new Object2ObjectLinkedOpenHashMap<>();
        variantsByOutputKey.forEach((key, variants) -> {
            if (!copiedMapping.containsKey(key)) {
                throw new IllegalArgumentException("A Trinity producer index must reference a topology key");
            }
            copiedProducers.put(key, List.copyOf(variants));
        });
        variantsByOutputKey = Collections.unmodifiableMap(copiedProducers);
        Object2IntLinkedOpenHashMap<TrinityPatternVariant> copiedOwners = new Object2IntLinkedOpenHashMap<>();
        for (Object2IntMap.Entry<TrinityPatternVariant> owner : cyclicOwnerByVariant.object2IntEntrySet()) {
            TrinityPatternVariant variant = owner.getKey();
            int index = owner.getIntValue();
            if (index < 0 || index >= componentCount || !components.get(index).cyclic() || !components.get(index).cycleVariants().contains(variant)) {
                throw new IllegalArgumentException("A Trinity cyclic transition owner must reference its feedback component");
            }
            copiedOwners.put(variant, index);
        }
        cyclicOwnerByVariant = Object2IntMaps.unmodifiable(copiedOwners);
        componentByKey = Object2IntMaps.unmodifiable(copiedMapping);
        topologicalOrder = IntLists.unmodifiable(new IntArrayList(topologicalOrder));
        boolean[] seen = new boolean[components.size()];
        int[] positions = new int[components.size()];
        for (int position = 0; position < topologicalOrder.size(); position++) {
            int index = topologicalOrder.getInt(position);
            if (index < 0 || index >= components.size() || seen[index]) {
                throw new IllegalArgumentException("A Trinity condensation order must contain every component once");
            }
            seen[index] = true;
            positions[index] = position;
        }
        for (TrinityStronglyConnectedComponent component : components) {
            for (int successor : component.successorIndexes()) {
                if (positions[component.index()] >= positions[successor]) {
                    throw new IllegalArgumentException("A Trinity condensation order must be topological");
                }
            }
        }
    }
}
