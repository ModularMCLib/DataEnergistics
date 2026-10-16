package com.fish_dan_.data_energistics.common.research.condition;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.research.AdvancementResearchCondition;
import com.fish_dan_.data_energistics.api.research.FtbQuestTaskCondition;
import com.fish_dan_.data_energistics.api.research.ResearchCondition;
import com.fish_dan_.data_energistics.api.research.ResearchNode;
import com.fish_dan_.data_energistics.integration.library.ftbquests.research.FtbQuestTaskCompletion;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.Optional;

/** Evaluates advancement and optional-mod progress only at server-thread entry points. */
public final class ResearchConditionEvaluator {

    private static final ObjectSet<String> WARNED = new ObjectOpenHashSet<>();

    private ResearchConditionEvaluator() {}

    public static ResearchConditionEvaluation evaluate(ServerPlayer player, ResearchNode node) {
        ObjectSet<ResearchCondition> satisfied = new ObjectOpenHashSet<>();
        ObjectSet<ResearchCondition> unavailable = new ObjectOpenHashSet<>();
        for (ResearchCondition condition : node.conditionIds()) {
            if (condition instanceof AdvancementResearchCondition advancement) {
                AdvancementHolder holder = player.server.getAdvancements().get(advancement.id());
                if (holder == null) {
                    unavailable.add(condition);
                    warnOnce(node, condition, "Research node {} references missing advancement {}", node.id(), advancement.id());
                } else if (player.getAdvancements().getOrStartProgress(holder).isDone()) {
                    satisfied.add(condition);
                }
            } else if (condition instanceof FtbQuestTaskCondition task) {
                String questLabel = task.quest() == null ? "" : " (quest " + task.quest() + ")";
                if (!Data_Energistics.isModLoaded("ftbquests")) {
                    unavailable.add(condition);
                    warnOnce(node, condition, "Research node {} requires FTB Quests task {}{}, but FTB Quests is not installed",
                            node.id(), task.taskId(), questLabel);
                } else {
                    Optional<Boolean> completed = FtbQuestTaskCompletion.completed(player, task.taskId());
                    if (completed.isEmpty()) {
                        unavailable.add(condition);
                        warnOnce(node, condition, "Research node {} references missing FTB Quests task {}{}",
                                node.id(), task.taskId(), questLabel);
                    } else if (completed.get()) {
                        satisfied.add(condition);
                    }
                }
            } else {
                throw new IllegalStateException("Unsupported research condition " + condition.getClass().getSimpleName());
            }
        }
        return new ResearchConditionEvaluation(satisfied, unavailable);
    }

    private static void warnOnce(ResearchNode node, ResearchCondition condition, String pattern, Object... arguments) {
        if (WARNED.add(node.id() + "|" + condition)) Data_Energistics.LOGGER.warn(pattern, arguments);
    }
}
