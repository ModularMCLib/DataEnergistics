package com.fish_dan_.data_energistics.api.crafting.dynamic;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.util.List;
import java.util.Objects;

/**
 * Immutable dynamic-output declarations for one logical invocation of the supplied outer pattern details.
 *
 * @param outputs non-empty dynamic physical outputs in deterministic declaration order
 */
public record DynamicCraftingOutputSemantics(List<DynamicCraftingOutput> outputs) {

    /**
     * @deprecated scheduled for removal in plan 340; use {@link #outputsFast()}
     */
    @Deprecated(forRemoval = true)
    @Override
    public List<DynamicCraftingOutput> outputs() {
        return outputs;
    }

    /** Returns an immutable FastUtil view of the declared outputs. */
    public ObjectList<DynamicCraftingOutput> outputsFast() {
        return ObjectLists.unmodifiable(new ObjectArrayList<>(outputs));
    }

    /**
     * Copies and validates declarations before an adapter can expose them to crafting execution.
     */
    public DynamicCraftingOutputSemantics {
        Objects.requireNonNull(outputs, "Dynamic crafting output semantics must not be null");
        if (outputs.isEmpty()) {
            throw new IllegalArgumentException("Dynamic crafting output semantics require at least one output");
        }
        outputs = List.copyOf(outputs);
    }
}
