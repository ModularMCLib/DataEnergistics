package com.fish_dan_.data_energistics.common.crafting.dynamic;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.dynamic.DynamicCraftingOutput;
import com.fish_dan_.data_energistics.api.crafting.matching.ProcessingMatchMode;
import com.fish_dan_.data_energistics.common.crafting.pattern.matching.EncodedPatternMatching;

import appeng.api.crafting.IPatternDetails;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;

/** Resolves explicit slot rules without weakening the complete encoded-pattern identity. */
public final class EncodedPatternDynamicOutput {

    public static final ResourceLocation SOURCE_ID = Data_Energistics.id("encoded_pattern_output");

    private EncodedPatternDynamicOutput() {}

    public static boolean isMarked(AEItemKey definition) {
        return EncodedPatternMatching.flexible(definition);
    }

    public static boolean isMarked(AEItemKey definition, int inputIndex, int outputIndex) {
        return (inputIndex >= 0 ? EncodedPatternMatching.mode(definition, inputIndex) :
                EncodedPatternMatching.outputMode(definition, outputIndex)) != ProcessingMatchMode.EXACT;
    }

    /** Every explicitly flexible sparse output slot contributes its own frozen count and rule. */
    public static ObjectList<DynamicCraftingOutput> resolveAll(IPatternDetails details) {
        var definition = details.getDefinition();
        var encoded = definition.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        var outputs = encoded == null ? details.getOutputs() : encoded.sparseOutputs();
        var result = new ObjectArrayList<DynamicCraftingOutput>();
        for (int slot = 0; slot < outputs.size(); slot++) {
            var output = outputs.get(slot);
            var mode = EncodedPatternMatching.outputMode(definition, slot);
            if (output == null || mode == ProcessingMatchMode.EXACT) continue;
            if (output.amount() <= 0 || !(output.what() instanceof AEItemKey)) {
                throw new DynamicCraftingOutputResolutionException("Flexible pattern output requires a positive item output");
            }
            result.add(new DynamicCraftingOutput(output, mode, mode == ProcessingMatchMode.TAG ?
                    EncodedPatternMatching.outputTags(definition, slot) : ObjectList.of()));
        }
        return new ObjectImmutableList<>(result);
    }
}
