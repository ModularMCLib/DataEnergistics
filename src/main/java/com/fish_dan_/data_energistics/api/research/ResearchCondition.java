package com.fish_dan_.data_energistics.api.research;

/**
 * A typed external requirement shared by Java definitions, datapacks and immutable project snapshots.
 * The sealed variants keep optional-mod types outside the research model and client synchronization.
 * Values are immutable; evaluation belongs to the initiating player's server thread and world lifecycle.
 */
public sealed interface ResearchCondition permits AdvancementResearchCondition, FtbQuestTaskCondition {}
