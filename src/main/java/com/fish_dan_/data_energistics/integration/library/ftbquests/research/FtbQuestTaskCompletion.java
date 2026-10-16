package com.fish_dan_.data_energistics.integration.library.ftbquests.research;

import net.minecraft.server.level.ServerPlayer;

import dev.ftb.mods.ftbquests.api.FTBQuestsAPI;

import java.util.Optional;

/** Typed optional FTB Quests boundary; call only after checking the ftbquests mod ID. */
public final class FtbQuestTaskCompletion {

    private FtbQuestTaskCompletion() {}

    /** Empty means the configured task ID is not present in the active quest file. */
    public static Optional<Boolean> completed(ServerPlayer player, long taskId) {
        var file = FTBQuestsAPI.api().getQuestFile(false);
        var task = file.getTask(taskId);
        if (task == null || !task.isValid()) return Optional.empty();
        return Optional.of(file.getTeamData(player).map(team -> team.isCompleted(task)).orElse(false));
    }
}
