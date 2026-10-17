package com.fish_dan_.data_energistics.common.research.progression;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.research.ResearchAccess;
import com.fish_dan_.data_energistics.api.research.ResearchNode;
import com.fish_dan_.data_energistics.api.research.ResearchScope;
import com.fish_dan_.data_energistics.blockentity.research.ResearchStationBlockEntity;
import com.fish_dan_.data_energistics.common.research.condition.ResearchConditionEvaluation;
import com.fish_dan_.data_energistics.common.research.condition.ResearchConditionEvaluator;
import com.fish_dan_.data_energistics.common.research.item.ItemResearchExclusions;
import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.research.persistence.ResearchSavedData;
import com.fish_dan_.data_energistics.data.research.ResearchDefinitions;
import com.fish_dan_.data_energistics.registry.DEDataComponents;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.UUID;

/** Server-only command and tick boundary for paid projects; ordinary progress never touches the current rules. */
public final class ResearchExecution {

    private ResearchExecution() {}

    public static Component action(ResearchStationBlockEntity host, ServerPlayer player, String action, ResourceLocation id) {
        ResearchSavedData data = ResearchSavedData.get(player.server);
        ResearchStationState state = host.state();
        ResearchProject existing = state.projects.get(id);
        if (action.equals("cancel")) {
            if (existing == null || !existing.running) return message("not_running");
            if (!existing.initiator.equals(player.getUUID()) && !player.hasPermissions(2)) return message("project_owner");
            existing.running = false;
            existing.powered = false;
            existing.conditionBlocked = false;
            state.revision++;
            data.setDirty();
            return message("cancelled");
        }
        boolean requestMaterials = action.equals("request_materials");
        if (!requestMaterials && !action.equals("start") && !action.equals("resume")) return message("unknown_action");
        ResearchNode node = ResearchDefinitions.find(id);
        if (node == null) return message("unavailable");
        if (existing != null) {
            if (requestMaterials) return message("materials_paid");
            if (!existing.initiator.equals(player.getUUID()) && !player.hasPermissions(2)) return message("project_owner");
            if (existing.faulted) return message("faulted");
            updateConditions(state, existing, data, existing.evaluateConditions(player.server));
            if (existing.conditionBlocked) return message("condition_missing");
            if (existing.running) return message("already_running");
            if (!existing.node.prerequisitesSatisfied(unlockedIds(state, data, existing.subjects, existing.initiator))) {
                return message("prerequisite");
            }
            if (exclusionWinner(existing.node, data, existing.subjects, existing.initiator, state.target) != null) {
                return message("mutually_excluded");
            }
            ResourceLocation conflict = data.runningMutualProject(existing.node, existing.subjects,
                    existing.initiator, host.stationId());
            if (conflict != null) return message("mutual_project_running");
            if (instantResearchEnabled(existing.node, data, existing.subjects, existing.initiator, state.target)) {
                complete(host, state, data, existing.node, existing.subjects, existing.initiator);
                return message("instant_completed");
            }
            if (state.runningCount() >= 2) return message("full");
            existing.running = true;
            existing.conditionBlocked = false;
            state.revision++;
            data.setDirty();
            return message("resumed");
        }
        if (action.equals("resume")) return message("unavailable");
        ObjectSet<UUID> subjects = ResearchAccess.subjects(player, host.stationId());
        if (node.scope() == ResearchScope.ITEM_UPGRADE && !node.target().matches(state.target)) return message("wrong_item");
        ObjectSet<ResourceLocation> unlocked = unlockedIds(state, data, subjects, player.getUUID());
        if (unlocked.contains(node.id())) return message("already_unlocked");
        if (!node.prerequisitesSatisfied(unlocked)) return message("prerequisite");
        ResourceLocation winner = exclusionWinner(node, data, subjects, player.getUUID(), state.target);
        if (winner != null) return message("mutually_excluded");
        ResearchConditionEvaluation evaluation = ResearchConditionEvaluator.evaluate(player, node);
        if (!evaluation.satisfies(node)) return message("condition_missing");
        if (!requestMaterials && data.runningMutualProject(node, subjects, player.getUUID(), host.stationId()) != null) {
            return message("mutual_project_running");
        }
        boolean instant = instantResearchEnabled(node, data, subjects, player.getUUID(), state.target);
        if (requestMaterials && instant) return message("instant_enabled");
        if (!requestMaterials && !instant && state.runningCount() >= 2) return message("full");
        IGrid grid = host.getMainNode().getGrid();
        if (requestMaterials) {
            if (grid == null || !host.getMainNode().isOnline()) return message("offline");
            if (!state.refunds.isEmpty()) return message("refund_pending");
            return requestMaterials(host, player, node, grid, state, data);
        }
        if (instant) {
            try {
                complete(host, state, data, node, subjects, player.getUUID());
                return message("instant_completed");
            } catch (RuntimeException error) {
                Data_Energistics.LOGGER.error("Instant research completion failed for node {} in station {}",
                        node.id(), host.stationId(), error);
                data.setDirty();
                return message("faulted");
            }
        }
        if (grid == null || !host.getMainNode().isOnline()) return message("offline");
        if (!state.refunds.isEmpty()) return message("refund_pending");
        try {
            if (!ResearchPayment.withdraw(node.inputs(), grid.getStorageService().getInventory(),
                    IActionSource.ofPlayer(player, host), state, data))
                return state.refunds.isEmpty() ? requestMaterials(host, player, node, grid, state, data) : message("refund_pending");
            ResearchProject project = new ResearchProject(node, subjects, player.getUUID(), state.nextOrder++, evaluation);
            state.projects.put(id, project);
            state.refunds.clear();
            state.revision++;
            data.setDirty();
            return message("started");
        } catch (RuntimeException error) {
            Data_Energistics.LOGGER.error("Research payment failed for station {}, node {}, player {}. Extracted materials remain in the refund journal",
                    host.stationId(), id, player.getUUID(), error);
            data.setDirty();
            return message("faulted");
        }
    }

    private static Component requestMaterials(ResearchStationBlockEntity host, ServerPlayer player, ResearchNode node,
                                              IGrid grid, ResearchStationState state, ResearchSavedData data) {
        data.trackMaterialCalculations(state.materialRequests);
        return state.materialRequests.request(host, player, node, grid);
    }

    /** Resolves prerequisite IDs from their owning ledger, including cross-scope prerequisites. */
    public static ObjectSet<ResourceLocation> unlockedIds(ResearchStationState state, ResearchSavedData data,
                                                          ObjectSet<UUID> subjects, UUID player) {
        ObjectSet<ResourceLocation> result = new ObjectOpenHashSet<>();
        subjects.forEach(subject -> result.addAll(data.unlocked(subject)));
        // A saved technology must not grant an item upgrade after its definition changes scope.
        result.removeIf(id -> {
            ResearchNode node = ResearchDefinitions.find(id);
            return node != null && node.scope() != ResearchScope.TECHNOLOGY;
        });
        result.addAll(ItemResearchUpgrades.of(state.target).ids());
        result.addAll(data.playerUpgrades(player));
        return result;
    }

    /** The server snapshot and action path share entitlement resolution for the same subject snapshot. */
    public static boolean instantResearchEnabled(ResearchNode node, ResearchSavedData data,
                                                 ObjectSet<UUID> subjects, UUID player, ItemStack target) {
        return switch (node.scope()) {
            case TECHNOLOGY -> data.hasInstantTechnology(subjects);
            case ITEM_UPGRADE -> target.getOrDefault(DEDataComponents.RESEARCH_INSTANT.get(), false);
            case PLAYER_UPGRADE -> data.hasInstantPlayerResearch(player);
        };
    }

    /** Returns the persisted route winner for this scope owner, or null when the route is unlocked. */
    public static @Nullable ResourceLocation exclusionWinner(ResearchNode node, ResearchSavedData data,
                                                             ObjectSet<UUID> subjects, UUID player,
                                                             ItemStack target) {
        return switch (node.scope()) {
            case TECHNOLOGY -> data.technologyExclusionWinner(subjects, node.id());
            case ITEM_UPGRADE -> ItemResearchExclusions.of(target).winner(node.id());
            case PLAYER_UPGRADE -> data.playerExclusionWinner(player, node.id());
        };
    }

    public static void tick(ResearchStationBlockEntity host) {
        var server = host.serverLevel().getServer();
        ResearchSavedData data = ResearchSavedData.get(server);
        ResearchStationState state = host.state();
        if (state.lastTick == server.getTickCount()) return;
        state.lastTick = server.getTickCount();
        for (ResearchProject project : state.projects.values()) project.powered = false;
        ObjectList<ResearchProject> trackedProjects = new ObjectArrayList<>(state.projects.values());
        for (ResearchProject project : trackedProjects) {
            if (!project.faulted) updateConditions(state, project, data, project.evaluateConditions(server));
        }
        IGrid grid = host.getMainNode().getGrid();
        if (grid == null || !host.getMainNode().isOnline()) return;
        ResearchPayment.refund(grid.getStorageService().getInventory(), IActionSource.ofMachine(host), state, data);
        var projects = new ObjectArrayList<>(state.projects.values());
        projects.sort(Comparator.comparingLong(project -> project.order));
        for (ResearchProject project : projects) {
            if (state.projects.get(project.node.id()) != project || !project.running || project.faulted ||
                    ResearchDefinitions.find(project.node.id()) == null)
                continue;
            try {
                double needed = project.node.aePerTick() - project.energyCredit;
                double available = grid.getEnergyService().extractAEPower(needed, Actionable.SIMULATE, PowerMultiplier.ONE);
                if (available + 1.0e-7 < needed) continue;
                double consumed = grid.getEnergyService().extractAEPower(needed, Actionable.MODULATE, PowerMultiplier.ONE);
                project.energyCredit += consumed;
                data.setDirty();
                if (project.energyCredit + 1.0e-7 < project.node.aePerTick()) continue;
                project.energyCredit = 0;
                project.powered = true;
                if (project.progress + 1 == project.node.researchTicks()) {
                    complete(host, state, data, project.node, project.subjects, project.initiator);
                } else {
                    project.progress++;
                }
            } catch (RuntimeException error) {
                project.faulted = true;
                project.running = false;
                project.powered = false;
                project.conditionBlocked = false;
                state.revision++;
                data.setDirty();
                Data_Energistics.LOGGER.error("Stopped research project {} in station {} at {}/{}", project.node.id(),
                        host.stationId(), project.progress, project.node.researchTicks(), error);
            }
        }
    }

    private static void updateConditions(ResearchStationState state, ResearchProject project, ResearchSavedData data,
                                         ResearchConditionEvaluation evaluation) {
        boolean blocked = !evaluation.satisfies(project.node);
        if (evaluation.equals(project.conditionEvaluation) && project.conditionBlocked == blocked) return;
        project.conditionEvaluation = evaluation;
        project.conditionBlocked = blocked;
        if (blocked) {
            project.running = false;
            project.powered = false;
        }
        state.revision++;
        data.setDirty();
    }

    private static void complete(ResearchStationBlockEntity host, ResearchStationState state,
                                 ResearchSavedData data, ResearchNode node, ObjectSet<UUID> subjects,
                                 UUID initiator) {
        switch (node.scope()) {
            case TECHNOLOGY -> data.unlockTechnology(subjects, node);
            case ITEM_UPGRADE -> {
                if (state.target.isEmpty()) throw new IllegalStateException("Locked research target is missing");
                state.target.set(DEDataComponents.RESEARCH_UPGRADES.get(), ItemResearchUpgrades.of(state.target).with(node.id()));
                ItemResearchExclusions exclusions = ItemResearchExclusions.of(state.target);
                for (ResourceLocation blocked : node.mutuallyExclusive()) {
                    exclusions = exclusions.with(blocked, node.id());
                }
                if (!node.mutuallyExclusive().isEmpty()) {
                    state.target.set(DEDataComponents.RESEARCH_EXCLUSIONS.get(), exclusions);
                }
            }
            case PLAYER_UPGRADE -> data.unlockPlayerUpgrade(initiator, node);
        }
        state.projects.remove(node.id());
        data.cancelMutuallyExclusiveProjects(node, subjects, initiator, host.stationId());
        state.revision++;
        data.setDirty();
        host.saveChanges();
    }

    private static Component message(String key) {
        return Component.translatable("research.data_energistics." + key);
    }
}
