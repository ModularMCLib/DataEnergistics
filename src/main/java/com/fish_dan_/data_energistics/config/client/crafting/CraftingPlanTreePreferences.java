package com.fish_dan_.data_energistics.config.client.crafting;

import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanLayoutMode;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Client-local presentation preferences, never synchronized to or persisted by a server. */
public record CraftingPlanTreePreferences(int autoExpandBudget, boolean compact, boolean missingOnly,
                                          boolean screenshotAmounts, CraftingPlanLayoutMode layoutMode) {

    public static final CraftingPlanTreePreferences DEFAULT = new CraftingPlanTreePreferences(256, false, false,
            false, CraftingPlanLayoutMode.LAYERED);
    public static final Codec<CraftingPlanTreePreferences> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(64, 4096).optionalFieldOf("autoExpandBudget", DEFAULT.autoExpandBudget()).forGetter(CraftingPlanTreePreferences::autoExpandBudget),
            Codec.BOOL.optionalFieldOf("compact", false).forGetter(CraftingPlanTreePreferences::compact),
            Codec.BOOL.optionalFieldOf("missingOnly", false).forGetter(CraftingPlanTreePreferences::missingOnly),
            Codec.BOOL.optionalFieldOf("screenshotAmounts", false).forGetter(CraftingPlanTreePreferences::screenshotAmounts),
            Codec.STRING.xmap(CraftingPlanLayoutMode::valueOf, CraftingPlanLayoutMode::name)
                    .optionalFieldOf("layoutMode", CraftingPlanLayoutMode.LAYERED).forGetter(CraftingPlanTreePreferences::layoutMode))
            .apply(instance, CraftingPlanTreePreferences::new));
}
