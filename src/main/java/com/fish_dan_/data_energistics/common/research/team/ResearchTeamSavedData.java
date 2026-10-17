package com.fish_dan_.data_energistics.common.research.team;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.level.saveddata.SavedData;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Overworld membership authority. Invitations intentionally belong only to the running server session. */
final class ResearchTeamSavedData extends SavedData {

    private static final Factory<ResearchTeamSavedData> FACTORY = new Factory<>(ResearchTeamSavedData::new, ResearchTeamSavedData::load);
    private static final String DATA_NAME = Data_Energistics.MODID + "_research_teams";

    private final Object2ObjectMap<UUID, Team> teams = new Object2ObjectLinkedOpenHashMap<>();
    private final Object2ObjectMap<UUID, UUID> membership = new Object2ObjectLinkedOpenHashMap<>();
    private final Object2ObjectMap<UUID, ObjectSet<UUID>> invitations = new Object2ObjectLinkedOpenHashMap<>();
    private final Object2ObjectMap<String, UUID> scoreboardSubjects = new Object2ObjectLinkedOpenHashMap<>();
    private boolean scoreboardReconciled;

    static ResearchTeamSavedData get(MinecraftServer server) {
        ResearchTeamSavedData data = server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
        if (!data.scoreboardReconciled) {
            if (data.scoreboardSubjects.keySet().removeIf(name -> server.getScoreboard().getPlayerTeam(name) == null)) {
                data.setDirty();
            }
            data.scoreboardReconciled = true;
        }
        return data;
    }

    @Nullable
    Team teamFor(UUID playerId) {
        UUID teamId = membership.get(playerId);
        return teamId == null ? null : teams.get(teamId);
    }

    UUID scoreboardSubject(String name) {
        UUID id = scoreboardSubjects.get(name);
        if (id == null) {
            id = UUID.randomUUID();
            scoreboardSubjects.put(name, id);
            setDirty();
        }
        return id;
    }

    void forgetScoreboardTeam(String name) {
        if (scoreboardSubjects.remove(name) != null) {
            setDirty();
        }
    }

    void clearInvitations() {
        invitations.clear();
    }

    void clearInvitations(UUID target) {
        invitations.remove(target);
    }

    Result create(ServerPlayer player, String argument) {
        UUID playerId = player.getUUID();
        if (membership.containsKey(playerId)) {
            return failure("already_member");
        }
        String name = argument.strip();
        if (name.isBlank() || name.length() > 64 || name.codePoints().anyMatch(Character::isISOControl)) {
            return failure("invalid_name");
        }
        Team team = new Team(UUID.randomUUID(), name, playerId);
        team.members.put(playerId, player.getGameProfile().getName());
        teams.put(team.id, team);
        membership.put(playerId, team.id);
        invitations.remove(playerId);
        setDirty();
        return success("created", name);
    }

    Result invite(ServerPlayer player, @Nullable ServerPlayer target) {
        Team team = teamFor(player.getUUID());
        if (team == null) {
            return failure("no_team");
        }
        if (!team.owner.equals(player.getUUID())) {
            return failure("owner_only");
        }
        if (target == null) {
            return failure("target_offline");
        }
        if (membership.containsKey(target.getUUID())) {
            return failure("target_already_member");
        }
        ObjectSet<UUID> pending = invitations.computeIfAbsent(target.getUUID(), id -> new ObjectLinkedOpenHashSet<>());
        if (!pending.add(team.id)) {
            return failure("already_invited");
        }
        target.sendSystemMessage(Component.translatable("research.data_energistics.team.invitation_received",
                team.name, player.getGameProfile().getName(), team.id.toString()));
        return success("invited", target.getGameProfile().getName());
    }

    Result respond(ServerPlayer player, @Nullable UUID teamId, boolean accept) {
        if (teamId == null) {
            return failure("invalid_uuid");
        }
        ObjectSet<UUID> pending = invitations.get(player.getUUID());
        Team team = teams.get(teamId);
        if (pending == null || !pending.contains(teamId) || team == null) {
            return failure("invitation_missing");
        }
        if (!accept) {
            pending.remove(teamId);
            if (pending.isEmpty()) {
                invitations.remove(player.getUUID());
            }
            return success("rejected", team.name);
        }
        if (membership.containsKey(player.getUUID())) {
            return failure("already_member");
        }
        team.members.put(player.getUUID(), player.getGameProfile().getName());
        membership.put(player.getUUID(), team.id);
        invitations.remove(player.getUUID());
        setDirty();
        return success("joined", team.name);
    }

    Result leave(ServerPlayer player) {
        Team team = teamFor(player.getUUID());
        if (team == null) {
            return failure("no_team");
        }
        if (team.owner.equals(player.getUUID())) {
            if (team.members.size() > 1) {
                return failure("transfer_before_leaving");
            }
            removeTeam(team);
            return success("disbanded", team.name);
        }
        team.members.remove(player.getUUID());
        membership.remove(player.getUUID());
        setDirty();
        return success("left", team.name);
    }

    Result ownerAction(ServerPlayer player, String action, @Nullable UUID targetId) {
        Team team = teamFor(player.getUUID());
        if (team == null) {
            return failure("no_team");
        }
        if (!team.owner.equals(player.getUUID())) {
            return failure("owner_only");
        }
        if (action.equals("disband")) {
            removeTeam(team);
            return success("disbanded", team.name);
        }
        if (targetId == null) {
            return failure("invalid_uuid");
        }
        String targetName = team.members.get(targetId);
        if (targetName == null) {
            return failure("target_not_member");
        }
        if (targetId.equals(player.getUUID())) {
            return failure("target_is_owner");
        }
        if (action.equals("kick")) {
            team.members.remove(targetId);
            membership.remove(targetId);
            setDirty();
            return success("kicked", targetName);
        }
        team.owner = targetId;
        setDirty();
        return success("transferred", targetName);
    }

    private void removeTeam(Team team) {
        teams.remove(team.id);
        for (UUID member : team.members.keySet()) {
            membership.remove(member);
        }
        invitations.values().removeIf(pending -> {
            pending.remove(team.id);
            return pending.isEmpty();
        });
        setDirty();
    }

    CompoundTag snapshot(ServerPlayer player) {
        CompoundTag result = new CompoundTag();
        Team team = teamFor(player.getUUID());
        if (team != null) {
            CompoundTag custom = new CompoundTag();
            custom.putString("id", team.id.toString());
            custom.putString("name", team.name);
            custom.putString("owner", team.owner.toString());
            ListTag members = new ListTag();
            for (var member : team.members.object2ObjectEntrySet()) {
                String name = memberName(player.serverLevel().getServer(), member.getKey(), member.getValue());
                if (!name.equals(member.getValue())) {
                    member.setValue(name);
                    setDirty();
                }
                members.add(playerTag(member.getKey(), name));
            }
            custom.put("members", members);
            result.put("custom_team", custom);
        }
        ListTag pendingTags = new ListTag();
        ObjectSet<UUID> pending = invitations.get(player.getUUID());
        if (pending != null) {
            for (UUID id : pending) {
                Team invitedTeam = teams.get(id);
                // Only this class mutates invitations; removeTeam keeps these references live.
                if (invitedTeam == null) {
                    throw new IllegalStateException("Invitation references missing research team " + id);
                }
                CompoundTag entry = new CompoundTag();
                entry.putString("id", id.toString());
                entry.putString("name", invitedTeam.name);
                pendingTags.add(entry);
            }
        }
        result.put("invitations", pendingTags);
        ListTag onlineTags = new ListTag();
        for (ServerPlayer online : player.serverLevel().getServer().getPlayerList().getPlayers()) {
            CompoundTag entry = playerTag(online.getUUID(), online.getGameProfile().getName());
            entry.putBoolean("has_custom_team", membership.containsKey(online.getUUID()));
            onlineTags.add(entry);
        }
        result.put("online_players", onlineTags);
        return result;
    }

    private static CompoundTag playerTag(UUID id, String name) {
        CompoundTag entry = new CompoundTag();
        entry.putString("uuid", id.toString());
        entry.putString("name", name);
        return entry;
    }

    private static String memberName(MinecraftServer server, UUID id, String persistedName) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        GameProfileCache profiles = server.getProfileCache();
        // Dedicated server profile caches are an optional external API; persisted names cover offline owners.
        return profiles == null ? persistedName : profiles.get(id).map(profile -> profile.getName()).orElse(persistedName);
    }

    private static ResearchTeamSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ResearchTeamSavedData data = new ResearchTeamSavedData();
        for (Tag raw : tag.getList("teams", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            if (!entry.hasUUID("id") || !entry.hasUUID("owner")) {
                Data_Energistics.LOGGER.warn("Skipping malformed saved research team without identity or owner");
                continue;
            }
            UUID id = entry.getUUID("id");
            UUID owner = entry.getUUID("owner");
            String name = entry.getString("name").strip();
            if (name.isBlank() || name.length() > 64 || name.codePoints().anyMatch(Character::isISOControl) || data.teams.containsKey(id) || data.membership.containsKey(owner)) {
                Data_Energistics.LOGGER.warn("Skipping invalid or duplicate saved research team {}", id);
                continue;
            }
            Team team = new Team(id, name, owner);
            for (Tag rawMember : entry.getList("members", Tag.TAG_COMPOUND)) {
                CompoundTag member = (CompoundTag) rawMember;
                if (member.hasUUID("uuid")) {
                    UUID memberId = member.getUUID("uuid");
                    if (!data.membership.containsKey(memberId)) {
                        String memberName = member.getString("name");
                        team.members.putIfAbsent(memberId, memberName.isBlank() ? memberId.toString() : memberName);
                    }
                }
            }
            team.members.putIfAbsent(owner, owner.toString());
            data.teams.put(id, team);
            for (UUID member : team.members.keySet()) {
                data.membership.put(member, id);
            }
        }
        for (Tag raw : tag.getList("scoreboard_subjects", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            String name = entry.getString("name");
            if (!name.isBlank() && entry.hasUUID("id")) {
                data.scoreboardSubjects.putIfAbsent(name, entry.getUUID("id"));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag teamTags = new ListTag();
        for (Team team : teams.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", team.id);
            entry.putString("name", team.name);
            entry.putUUID("owner", team.owner);
            ListTag memberTags = new ListTag();
            for (var member : team.members.object2ObjectEntrySet()) {
                CompoundTag memberTag = new CompoundTag();
                memberTag.putUUID("uuid", member.getKey());
                memberTag.putString("name", member.getValue());
                memberTags.add(memberTag);
            }
            entry.put("members", memberTags);
            teamTags.add(entry);
        }
        tag.put("teams", teamTags);
        ListTag scoreboardTags = new ListTag();
        for (var subject : scoreboardSubjects.object2ObjectEntrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("name", subject.getKey());
            entry.putUUID("id", subject.getValue());
            scoreboardTags.add(entry);
        }
        tag.put("scoreboard_subjects", scoreboardTags);
        return tag;
    }

    private static Result success(String key, Object... arguments) {
        return new Result(true, Component.translatable("research.data_energistics.team." + key, arguments));
    }

    static Result failure(String key) {
        return new Result(false, Component.translatable("research.data_energistics.team." + key));
    }

    record Result(boolean success, Component message) {}

    static final class Team {

        final UUID id;
        final String name;
        UUID owner;
        final Object2ObjectMap<UUID, String> members = new Object2ObjectLinkedOpenHashMap<>();

        Team(UUID id, String name, UUID owner) {
            this.id = id;
            this.name = name;
            this.owner = owner;
        }
    }
}
