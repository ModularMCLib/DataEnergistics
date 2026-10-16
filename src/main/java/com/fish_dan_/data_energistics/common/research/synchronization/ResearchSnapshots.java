package com.fish_dan_.data_energistics.common.research.synchronization;

import com.fish_dan_.data_energistics.api.research.ResearchAccess;
import com.fish_dan_.data_energistics.api.research.ResearchNode;
import com.fish_dan_.data_energistics.api.research.ResearchScope;
import com.fish_dan_.data_energistics.blockentity.research.ResearchStationBlockEntity;
import com.fish_dan_.data_energistics.common.research.condition.ResearchConditionEvaluation;
import com.fish_dan_.data_energistics.common.research.condition.ResearchConditionEvaluator;
import com.fish_dan_.data_energistics.data.research.ResearchDefinitions;
import com.fish_dan_.data_energistics.common.research.persistence.ResearchNbt;
import com.fish_dan_.data_energistics.common.research.persistence.ResearchSavedData;
import com.fish_dan_.data_energistics.common.research.progression.ResearchExecution;
import com.fish_dan_.data_energistics.common.research.team.ResearchTeams;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.UUID;

/** Envelopes are built on the server thread and scoped to the viewing player. */
public final class ResearchSnapshots {

    private ResearchSnapshots() {}

    public static CompoundTag catalog(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        ListTag nodes = new ListTag();
        ResearchDefinitions.nodes().values().forEach(node -> nodes.add(ResearchNbt.node(node, player.registryAccess())));
        tag.put("nodes", nodes);
        tag.putLong("definitions_revision", ResearchDefinitions.revision());
        return tag;
    }

    public static CompoundTag station(ServerPlayer player, ResearchStationBlockEntity host, boolean definitions) {
        CompoundTag tag = definitions ? catalog(player) : new CompoundTag();
        var state = host.state();
        tag.putLong("definitions_revision", ResearchDefinitions.revision());
        tag.putLong("revision", state.revision);
        tag.putUUID("station_id", host.stationId());
        tag.putBoolean("locked", state.locked());
        tag.putBoolean("online", host.getMainNode().isOnline());
        tag.putBoolean("faulted", host.faulted());
        tag.putBoolean("refund_pending", !state.refunds.isEmpty());
        ResearchSavedData data = ResearchSavedData.get(player.server);
        ListTag projects = new ListTag();
        // Player menus may broadcast before block entities tick; the previous tick is still current then.
        boolean powered = host.getMainNode().isOnline() && !host.faulted() && state.lastTick >= (long) player.server.getTickCount() - 1;
        state.projects.values().forEach(project -> {
            CompoundTag snapshot = project.save(player.registryAccess());
            snapshot.putBoolean("powered", powered && project.powered);
            ResearchConditionEvaluation evaluation = project.evaluateConditions(player.server);
            snapshot.put("satisfied_conditions", ResearchNbt.conditions(evaluation.satisfied()));
            snapshot.put("unavailable_conditions", ResearchNbt.conditions(evaluation.unavailable()));
            snapshot.putBoolean("can_control", project.initiator.equals(player.getUUID()) || player.hasPermissions(2));
            boolean instant = ResearchExecution.instantResearchEnabled(project.node, data, project.subjects,
                    project.initiator, state.target);
            snapshot.putBoolean("instant", instant);
            ResourceLocation winner = ResearchExecution.exclusionWinner(project.node, data, project.subjects,
                    project.initiator, state.target);
            if (winner != null) snapshot.putString("exclusion_winner", winner.toString());
            ResourceLocation conflict = data.runningMutualProject(project.node, project.subjects, project.initiator, host.stationId());
            if (conflict != null) snapshot.putString("mutual_conflict", conflict.toString());
            snapshot.putBoolean("can_resume", !project.faulted && ResearchDefinitions.find(project.node.id()) != null &&
                    evaluation.satisfies(project.node) && winner == null && conflict == null &&
                    project.node.prerequisitesSatisfied(ResearchExecution.unlockedIds(state, data, project.subjects, project.initiator)));
            projects.add(snapshot);
        });
        tag.put("projects", projects);
        ObjectSet<ResourceLocation> unlocked = new ObjectOpenHashSet<>();
        ObjectSet<UUID> subjects = ResearchAccess.subjects(player, host.stationId());
        tag.putBoolean("instant_technology", data.hasInstantTechnology(subjects));
        tag.putBoolean("instant_player", data.hasInstantPlayerResearch(player.getUUID()));
        subjects.forEach(subject -> unlocked.addAll(data.unlocked(subject)));
        tag.put("unlocked", ResearchNbt.ids(unlocked));
        tag.put("player_upgrades", ResearchNbt.ids(data.playerUpgrades(player.getUUID())));
        Object2ObjectMap<ResourceLocation, ResourceLocation> technologyLocks = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectMap<ResourceLocation, ResourceLocation> playerLocks = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectMap<ResourceLocation, ResourceLocation> mutualConflicts = new Object2ObjectLinkedOpenHashMap<>();
        ListTag conditionResults = new ListTag();
        for (ResearchNode node : ResearchDefinitions.nodes().values()) {
            if (node.scope() == ResearchScope.TECHNOLOGY) {
                ResourceLocation winner = data.technologyExclusionWinner(subjects, node.id());
                if (winner != null) technologyLocks.put(node.id(), winner);
            } else if (node.scope() == ResearchScope.PLAYER_UPGRADE) {
                ResourceLocation winner = data.playerExclusionWinner(player.getUUID(), node.id());
                if (winner != null) playerLocks.put(node.id(), winner);
            }
            ResearchConditionEvaluation evaluation = ResearchConditionEvaluator.evaluate(player, node);
            CompoundTag condition = new CompoundTag();
            condition.putString("id", node.id().toString());
            condition.put("satisfied", ResearchNbt.conditions(evaluation.satisfied()));
            condition.put("unavailable", ResearchNbt.conditions(evaluation.unavailable()));
            conditionResults.add(condition);
            ResourceLocation conflict = data.runningMutualProject(node, subjects, player.getUUID(), host.stationId());
            if (conflict != null) mutualConflicts.put(node.id(), conflict);
        }
        tag.put("technology_exclusions", ResearchNbt.exclusions(technologyLocks));
        tag.put("player_exclusions", ResearchNbt.exclusions(playerLocks));
        tag.put("mutual_conflicts", ResearchNbt.exclusions(mutualConflicts));
        tag.put("condition_results", conditionResults);
        tag.put("teams", ResearchTeams.snapshot(player));
        return tag;
    }
}
