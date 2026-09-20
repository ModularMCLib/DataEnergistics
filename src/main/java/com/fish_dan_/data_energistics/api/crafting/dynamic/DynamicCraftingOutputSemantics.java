package com.fish_dan_.data_energistics.api.crafting.dynamic;

import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.List;
import java.util.Objects;

/**
 * Immutable dynamic-output declarations for one logical invocation of the supplied outer pattern details.
 *
 * @param outputs non-empty dynamic physical outputs in deterministic declaration order
 */
public record DynamicCraftingOutputSemantics(ObjectList<DynamicCraftingOutput> outputs) {

    public DynamicCraftingOutputSemantics(List<DynamicCraftingOutput> outputs) {
        this(new ObjectImmutableList<>(outputs));
    }

    /** Returns an immutable FastUtil view of the declared outputs. */
    public ObjectList<DynamicCraftingOutput> outputsFast() {
        return outputs;
    }

    /**
     * Copies and validates declarations before an adapter can expose them to crafting execution.
     */
    public DynamicCraftingOutputSemantics {
        Objects.requireNonNull(outputs, "Dynamic crafting output semantics must not be null");
        if (outputs.isEmpty()) {
            throw new IllegalArgumentException("Dynamic crafting output semantics require at least one output");
        }
        outputs = new ObjectImmutableList<>(outputs);
    }
}
