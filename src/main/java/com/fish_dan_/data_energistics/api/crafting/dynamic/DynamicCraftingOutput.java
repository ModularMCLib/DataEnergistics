package com.fish_dan_.data_energistics.api.crafting.dynamic;

import com.fish_dan_.data_energistics.api.crafting.matching.ProcessingMatchMode;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.List;
import java.util.Objects;

/**
 * One pattern-declared physical output that may be completed by a related runtime key.
 *
 * @param plannedOutput complete declared key and positive amount for one logical pattern push
 * @param matchMode     policy used only when an exact output-key match is unavailable
 */
public record DynamicCraftingOutput(GenericStack plannedOutput,
                                    ProcessingMatchMode matchMode,
                                    ObjectList<ResourceLocation> tags) {

    public DynamicCraftingOutput(GenericStack plannedOutput, ProcessingMatchMode matchMode, List<ResourceLocation> tags) {
        this(plannedOutput, matchMode, new ObjectImmutableList<>(tags));
    }

    public DynamicCraftingOutput(GenericStack plannedOutput, ProcessingMatchMode matchMode) {
        this(plannedOutput, matchMode, ObjectList.of());
    }

    /**
     * Rejects declarations that cannot be represented safely by the supported matching policies.
     */
    public DynamicCraftingOutput {
        Objects.requireNonNull(plannedOutput, "Planned dynamic crafting output must not be null");
        Objects.requireNonNull(plannedOutput.what(), "Planned dynamic crafting output key must not be null");
        Objects.requireNonNull(matchMode, "Dynamic crafting output match mode must not be null");
        tags = new ObjectImmutableList<>(tags);
        if (plannedOutput.amount() <= 0L) {
            throw new IllegalArgumentException("Dynamic crafting output requires a positive planned amount");
        }
        if (matchMode != ProcessingMatchMode.EXACT && !(plannedOutput.what() instanceof AEItemKey)) {
            throw new IllegalArgumentException("Flexible dynamic crafting outputs require an AE item key");
        }
        if (matchMode == ProcessingMatchMode.TAG && tags.isEmpty()) throw new IllegalArgumentException("TAG output requires declared recipe tags");
        if (matchMode != ProcessingMatchMode.TAG && !tags.isEmpty()) throw new IllegalArgumentException("Only TAG output carries declared tags");
    }
}
