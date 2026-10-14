package com.fish_dan_.data_energistics.integration.library.ftbteams.research;

import com.fish_dan_.data_energistics.research.team.ResearchTeamSubject;

import net.minecraft.server.level.ServerPlayer;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import org.jspecify.annotations.Nullable;

/** Optional API boundary: parties share research; player teams, allies and invitations do not. */
public final class FtbResearchTeamMembership {

    private FtbResearchTeamMembership() {}

    /** Requires the ftbteams mod to be loaded. Returns null when no real party membership exists. */
    public static @Nullable ResearchTeamSubject subject(ServerPlayer player) {
        var api = FTBTeamsAPI.api();
        if (!api.isManagerLoaded()) {
            return null;
        }
        Team team = api.getManager().getTeamForPlayer(player).orElse(null);
        if (team == null || !team.isValid() || !team.isPartyTeam()
                || !team.getRankForPlayer(player.getUUID()).isMemberOrBetter()) {
            return null;
        }
        return new ResearchTeamSubject(team.getId(), team.getName().getString(), "ftbteams");
    }
}
