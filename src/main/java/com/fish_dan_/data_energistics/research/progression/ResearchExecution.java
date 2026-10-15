package com.fish_dan_.data_energistics.research.progression;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.research.ResearchAccess;
import com.fish_dan_.data_energistics.api.research.ResearchNode;
import com.fish_dan_.data_energistics.api.research.ResearchScope;
import com.fish_dan_.data_energistics.blockentity.research.ResearchStationBlockEntity;
import com.fish_dan_.data_energistics.registry.DEDataComponents;
import com.fish_dan_.data_energistics.research.definition.ResearchDefinitions;
import com.fish_dan_.data_energistics.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.research.persistence.ResearchSavedData;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

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
            state.revision++;
            data.setDirty();
            return message("cancelled");
        }
        boolean requestMaterials = action.equals("request_materials");
        if (!requestMaterials && !action.equals("start") && !action.equals("resume")) return message("unknown_action");
        if (!requestMaterials && state.runningCount() >= 2) return message("full");
        ResearchNode node = ResearchDefinitions.find(id);
        if (node == null) return message("unavailable");
        if (existing != null) {
            if (requestMaterials) return message("materials_paid");
            if (existing.running) return message("already_running");
            if (!existing.initiator.equals(player.getUUID()) && !player.hasPermissions(2)) return message("project_owner");
            if (existing.faulted) return message("faulted");
            existing.running = true;
            state.revision++;
            data.setDirty();
            return message("resumed");
        }
        if (action.equals("resume")) return message("unavailable");
        ObjectSet<UUID> subjects = ResearchAccess.subjects(player, host.stationId());
        if (node.scope() == ResearchScope.ITEM_UPGRADE && !node.target().matches(state.target)) return message("wrong_item");
        ObjectSet<ResourceLocation> unlocked = unlockedIds(state, data, subjects);
        if (unlocked.contains(node.id())) return message("already_unlocked");
        if (!node.prerequisitesSatisfied(unlocked)) return message("prerequisite");
        IGrid grid = host.getMainNode().getGrid();
        if (grid == null || !host.getMainNode().isOnline()) return message("offline");
        if (!state.refunds.isEmpty()) return message("refund_pending");
        if (requestMaterials) return requestMaterials(host, player, node, grid, state, data);
        try {
            if (!ResearchPayment.withdraw(node.inputs(), grid.getStorageService().getInventory(),
                    IActionSource.ofPlayer(player, host), state, data))
                return state.refunds.isEmpty() ? requestMaterials(host, player, node, grid, state, data) : message("refund_pending");
            ResearchProject project = new ResearchProject(node, subjects, player.getUUID(), state.nextOrder++);
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

    private static ObjectSet<ResourceLocation> unlockedIds(ResearchStationState state, ResearchSavedData data,
                                                           ObjectSet<UUID> subjects) {
        ObjectSet<ResourceLocation> result = new ObjectOpenHashSet<>();
        result.addAll(ItemResearchUpgrades.of(state.target).ids());
        for (UUID subject : subjects) result.addAll(data.unlocked(subject));
        return result;
    }

    public static void tick(ResearchStationBlockEntity host) {
        var server = host.serverLevel().getServer();
        ResearchSavedData data = ResearchSavedData.get(server);
        ResearchStationState state = host.state();
        if (state.lastTick == server.getTickCount()) return;
        IGrid grid = host.getMainNode().getGrid();
        if (grid == null || !host.getMainNode().isOnline()) return;
        state.lastTick = server.getTickCount();
        for (ResearchProject project : state.projects.values()) project.powered = false;
        ResearchPayment.refund(grid.getStorageService().getInventory(), IActionSource.ofMachine(host), state, data);
        var projects = new ObjectArrayList<>(state.projects.values());
        projects.sort(Comparator.comparingLong(project -> project.order));
        for (ResearchProject project : projects) {
            if (!project.running || project.faulted || ResearchDefinitions.find(project.node.id()) == null) continue;
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
                    if (project.node.scope() == ResearchScope.ITEM_UPGRADE) {
                        if (state.target.isEmpty()) throw new IllegalStateException("Locked research target is missing");
                        state.target.set(DEDataComponents.RESEARCH_UPGRADES.get(), ItemResearchUpgrades.of(state.target).with(project.node.id()));
                    } else {
                        data.unlock(project.subjects, project.node.id());
                    }
                    state.projects.remove(project.node.id());
                    state.revision++;
                    host.saveChanges();
                } else {
                    project.progress++;
                }
            } catch (RuntimeException error) {
                project.faulted = true;
                project.running = false;
                project.powered = false;
                state.revision++;
                data.setDirty();
                Data_Energistics.LOGGER.error("Stopped research project {} in station {} at {}/{}", project.node.id(),
                        host.stationId(), project.progress, project.node.researchTicks(), error);
            }
        }
    }

    private static Component message(String key) {
        return Component.translatable("research.data_energistics." + key);
    }
}
