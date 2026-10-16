package com.fish_dan_.data_energistics.api.research;

import net.minecraft.resources.ResourceLocation;

import org.jspecify.annotations.Nullable;

/** An FTB Quests task; the optional quest key labels the datapack source while taskId is authoritative. */
public record FtbQuestTaskCondition(long taskId, @Nullable ResourceLocation quest) implements ResearchCondition {

    public FtbQuestTaskCondition {
        if (taskId <= 0) throw new IllegalArgumentException("FTB Quests task IDs must be positive");
    }
}
