package com.fish_dan_.data_energistics.api.research;

/** Immutable external progress requirement evaluated for the project initiator on the server thread. */
public sealed interface ResearchCondition permits AdvancementResearchCondition, FtbQuestTaskCondition {}
