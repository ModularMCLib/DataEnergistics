package com.fish_dan_.data_energistics.research.synchronization;

import com.fish_dan_.data_energistics.api.research.ResearchAccess;
import com.fish_dan_.data_energistics.blockentity.research.ResearchStationBlockEntity;
import com.fish_dan_.data_energistics.research.definition.ResearchDefinitions;
import com.fish_dan_.data_energistics.research.persistence.ResearchNbt;
import com.fish_dan_.data_energistics.research.persistence.ResearchSavedData;
import com.fish_dan_.data_energistics.research.team.ResearchTeams;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

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
        ListTag projects = new ListTag();
        // Player menus may broadcast before block entities tick; the previous tick is still current then.
        boolean powered = host.getMainNode().isOnline() && !host.faulted() && state.lastTick >= (long) player.server.getTickCount() - 1;
        state.projects.values().forEach(project -> {
            CompoundTag snapshot = project.save(player.registryAccess());
            snapshot.putBoolean("powered", powered && project.powered);
            projects.add(snapshot);
        });
        tag.put("projects", projects);
        ResearchSavedData data = ResearchSavedData.get(player.server);
        ObjectSet<ResourceLocation> unlocked = new ObjectOpenHashSet<>();
        ResearchAccess.subjects(player, host.stationId()).forEach(subject -> unlocked.addAll(data.unlocked(subject)));
        tag.put("unlocked", ResearchNbt.ids(unlocked));
        tag.put("teams", ResearchTeams.snapshot(player));
        return tag;
    }
}
