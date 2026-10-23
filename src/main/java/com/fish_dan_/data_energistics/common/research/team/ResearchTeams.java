package com.fish_dan_.data_energistics.common.research.team;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.MOD;
import com.fish_dan_.data_energistics.integration.library.ftbteams.research.FtbResearchTeamMembership;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/** GUI and command access to server-thread research team operations. */
public final class ResearchTeams {

    private ResearchTeams() {}

    /**
     * Returns current shared subjects without personal player identities.
     * Reevaluate when computing effective research access; membership is not an entitlement cache.
     */
    public static ObjectSet<UUID> subjectIds(ServerPlayer player) {
        ResearchTeamSavedData data = ResearchTeamSavedData.get(player.serverLevel().getServer());
        ObjectSet<UUID> subjects = new ObjectLinkedOpenHashSet<>();
        ResearchTeamSavedData.Team custom = data.teamFor(player.getUUID());
        if (custom != null) {
            subjects.add(custom.id);
        }
        PlayerTeam vanilla = player.getTeam();
        if (vanilla != null) {
            subjects.add(data.scoreboardSubject(vanilla.getName()));
        }
        ResearchTeamSubject ftb = ftbSubject(player);
        if (ftb != null) {
            subjects.add(ftb.id());
        }
        return subjects;
    }

    /**
     * Resolves current formal membership even when the other player is offline; invitations and allies do not count.
     */
    public static boolean shareTeam(ServerPlayer player, UUID otherId, String otherName) {
        if (player.getUUID().equals(otherId)) return true;
        var server = player.serverLevel().getServer();
        PlayerTeam vanilla = server.getScoreboard().getPlayersTeam(otherName);
        if (vanilla != null && vanilla.isAlliedTo(player.getTeam())) return true;
        ResearchTeamSavedData data = ResearchTeamSavedData.get(server);
        var own = data.teamFor(player.getUUID());
        if (own != null && own == data.teamFor(otherId)) return true;
        if (!MOD.FTB_TEAMS.isLoaded()) return false;
        ResearchTeamSubject first = FtbResearchTeamMembership.subject(player.getUUID());
        ResearchTeamSubject second = FtbResearchTeamMembership.subject(otherId);
        return first != null && second != null && first.id().equals(second.id());
    }

    /**
     * GUI schema (all IDs are UUID strings):
     * custom_team, when present: {id, name, owner, members:[{uuid,name}]}.
     * invitations:[{id,name}], online_players:[{uuid,name,has_custom_team}],
     * external_subjects:[{id,name,source}], with source scoreboard or ftbteams.
     * Snapshots must be refreshed after team operations and while the team page is open.
     */
    public static CompoundTag snapshot(ServerPlayer player) {
        ResearchTeamSavedData data = ResearchTeamSavedData.get(player.serverLevel().getServer());
        CompoundTag snapshot = data.snapshot(player);
        ListTag external = new ListTag();
        PlayerTeam vanilla = player.getTeam();
        if (vanilla != null) {
            external.add(subjectTag(new ResearchTeamSubject(data.scoreboardSubject(vanilla.getName()),
                    vanilla.getDisplayName().getString(), "scoreboard")));
        }
        ResearchTeamSubject ftb = ftbSubject(player);
        if (ftb != null) {
            external.add(subjectTag(ftb));
        }
        snapshot.put("external_subjects", external);
        return snapshot;
    }

    private static CompoundTag subjectTag(ResearchTeamSubject subject) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", subject.id().toString());
        tag.putString("name", subject.name());
        tag.putString("source", subject.source());
        return tag;
    }

    private static @Nullable ResearchTeamSubject ftbSubject(ServerPlayer player) {
        // This branch keeps the optional typed adapter unloaded when FTB Teams is absent.
        return MOD.FTB_TEAMS.isLoaded() ? FtbResearchTeamMembership.subject(player.getUUID()) : null;
    }

    /**
     * Executes create(name), invite(online player UUID/name), accept/reject(team UUID),
     * leave, kick/transfer(member UUID), or disband.
     * User errors return translated feedback; this entry point is also the GUI permission boundary.
     */
    public static Component execute(ServerPlayer player, String action, String argument) {
        return perform(player, action, argument).message();
    }

    private static ResearchTeamSavedData.Result perform(ServerPlayer player, String action, String argument) {
        if (argument.length() > 256) {
            return ResearchTeamSavedData.failure("invalid_argument");
        }
        try {
            ResearchTeamSavedData data = ResearchTeamSavedData.get(player.serverLevel().getServer());
            return switch (action) {
                case "create" -> data.create(player, argument);
                case "invite" -> data.invite(player, onlineTarget(player.serverLevel().getServer(), argument));
                case "accept" -> data.respond(player, parseId(argument), true);
                case "reject" -> data.respond(player, parseId(argument), false);
                case "leave" -> data.leave(player);
                case "kick", "transfer" -> data.ownerAction(player, action, parseId(argument));
                case "disband" -> data.ownerAction(player, action, null);
                default -> ResearchTeamSavedData.failure("unknown_action");
            };
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error("Research team action {} failed for player {}", action, player.getUUID(), exception);
            return ResearchTeamSavedData.failure("action_failed");
        }
    }

    private static @Nullable UUID parseId(String argument) {
        // UUID.fromString also accepts abbreviated groups; the protocol uses canonical UUID strings only.
        if (argument.length() != 36) {
            return null;
        }
        try {
            UUID id = UUID.fromString(argument);
            return id.toString().equalsIgnoreCase(argument) ? id : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static @Nullable ServerPlayer onlineTarget(MinecraftServer server, String argument) {
        UUID id = parseId(argument);
        return id == null ? server.getPlayerList().getPlayerByName(argument) : server.getPlayerList().getPlayer(id);
    }

    /** Supplies current team targets to the unified command registrar, on the server thread. */
    public static void suggestTargets(ServerPlayer player, String action, SuggestionsBuilder builder) {
        CompoundTag snapshot = snapshot(player);
        String listKey = action.equals("invite") ? "online_players" : action.equals("accept") || action.equals("reject") ? "invitations" : "members";
        ListTag targets = listKey.equals("members") ? snapshot.getCompound("custom_team").getList(listKey, Tag.TAG_COMPOUND) : snapshot.getList(listKey, Tag.TAG_COMPOUND);
        for (int index = 0; index < targets.size(); index++) {
            CompoundTag target = targets.getCompound(index);
            String value = listKey.equals("invitations") ? target.getString("id") : action.equals("invite") ? target.getString("name") : target.getString("uuid");
            if (value.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase())) {
                builder.suggest(value, Component.literal(target.getString("name")));
            }
        }
    }

    /** Executes a unified player command, reporting translated success or failure to its source. */
    public static int executeCommand(CommandContext<CommandSourceStack> context, String action, String argument)
                                                                                                                 throws CommandSyntaxException {
        ResearchTeamSavedData.Result result = perform(context.getSource().getPlayerOrException(), action, argument);
        if (result.success()) {
            context.getSource().sendSuccess(result::message, false);
            return 1;
        }
        context.getSource().sendFailure(result.message());
        return 0;
    }

    /** Target logout invalidates only that target's pending invitations; inviter logout has no effect. */
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ResearchTeamSavedData.get(player.serverLevel().getServer()).clearInvitations(player.getUUID());
        }
    }

    /** Invitations never survive shutdown or a new server session, even in an integrated server. */
    public static void clearSessions(ServerStoppedEvent event) {
        ResearchTeamSavedData.get(event.getServer()).clearInvitations();
    }

    /** Called after scoreboard removal so a recreated team with the same name receives a new identity. */
    public static void forgetScoreboardTeam(MinecraftServer server, String name) {
        ResearchTeamSavedData.get(server).forgetScoreboardTeam(name);
    }
}
