package com.fish_dan_.data_energistics.api.research;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** A Minecraft advancement that must be completed by the research initiator. */
public record AdvancementResearchCondition(ResourceLocation id) implements ResearchCondition {

    public AdvancementResearchCondition {
        Objects.requireNonNull(id, "id");
    }
}
