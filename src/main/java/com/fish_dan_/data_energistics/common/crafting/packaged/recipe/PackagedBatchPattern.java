package com.fish_dan_.data_energistics.common.crafting.packaged.recipe;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;

/** Admission-only batch output view. The original definition and input domains keep their slot identities. */
public record PackagedBatchPattern(IPatternDetails original, long count, ObjectList<GenericStack> outputs) implements IPatternDetails {

    public PackagedBatchPattern(IPatternDetails original, long count) {
        this(original, count, original.getOutputs().stream()
                .map(stack -> new GenericStack(stack.what(), Math.multiplyExact(stack.amount(), count)))
                .collect(ObjectImmutableList.toList()));
    }

    public PackagedBatchPattern {
        if (count <= 0 || original instanceof PackagedBatchPattern) throw new IllegalArgumentException("Invalid packaged batch pattern");
        outputs = new ObjectImmutableList<>(outputs);
    }

    @Override
    public AEItemKey getDefinition() {
        return original.getDefinition();
    }

    @Override
    public IInput[] getInputs() {
        return original.getInputs();
    }

    @Override
    public ObjectList<GenericStack> getOutputs() {
        return outputs;
    }

    @Override
    public boolean supportsPushInputsToExternalInventory() {
        return original.supportsPushInputsToExternalInventory();
    }
}
