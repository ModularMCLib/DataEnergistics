package com.fish_dan_.data_energistics.integration.library.ftbteams.research;

import com.fish_dan_.data_energistics.common.research.team.ResearchTeamSubject;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Optional API boundary: parties share research; player teams, allies and invitations do not. */
public final class FtbResearchTeamMembership {

    private FtbResearchTeamMembership() {}

    /** Requires the ftbteams mod to be loaded. Returns null when no real party membership exists. */
    public static @Nullable ResearchTeamSubject subject(UUID playerId) {
        var api = FTBTeamsAPI.api();
        if (!api.isManagerLoaded()) {
            return null;
        }
        Team team = api.getManager().getTeamForPlayerID(playerId).orElse(null);
        if (team == null || !team.isValid() || !team.isPartyTeam() || !team.getRankForPlayer(playerId).isMemberOrBetter()) {
            return null;
        }
        return new ResearchTeamSubject(team.getId(), team.getName().getString(), "ftbteams");
    }
}
