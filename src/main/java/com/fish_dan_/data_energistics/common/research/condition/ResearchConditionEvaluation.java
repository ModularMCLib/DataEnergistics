package com.fish_dan_.data_energistics.common.research.condition;

import com.fish_dan_.data_energistics.api.research.ResearchCondition;
import com.fish_dan_.data_energistics.api.research.ResearchNode;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

/** Immutable per-player result used by project persistence and client snapshots. */
public record ResearchConditionEvaluation(ObjectSet<ResearchCondition> satisfied,
                                          ObjectSet<ResearchCondition> unavailable) {

    public static final ResearchConditionEvaluation EMPTY = new ResearchConditionEvaluation(ObjectSets.emptySet(), ObjectSets.emptySet());

    public ResearchConditionEvaluation {
        ObjectSet<ResearchCondition> satisfiedCopy = new ObjectOpenHashSet<>(satisfied);
        ObjectSet<ResearchCondition> unavailableCopy = new ObjectOpenHashSet<>(unavailable);
        for (ResearchCondition condition : satisfiedCopy) if (unavailableCopy.contains(condition))
            throw new IllegalArgumentException("A research condition cannot be both satisfied and unavailable");
        satisfied = ObjectSets.unmodifiable(satisfiedCopy);
        unavailable = ObjectSets.unmodifiable(unavailableCopy);
    }

    public boolean satisfies(ResearchNode node) {
        return node.conditionsSatisfied(satisfied);
    }
}
