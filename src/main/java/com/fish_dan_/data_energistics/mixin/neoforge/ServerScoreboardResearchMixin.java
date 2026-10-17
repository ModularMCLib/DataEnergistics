package com.fish_dan_.data_energistics.mixin.neoforge;

import com.fish_dan_.data_energistics.common.research.team.ResearchTeams;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.PlayerTeam;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Removal, rather than the mutable team name, determines the end of a scoreboard subject's lifetime. */
@Mixin(ServerScoreboard.class)
public abstract class ServerScoreboardResearchMixin {

    @Shadow
    @Final
    private MinecraftServer server;

    @Inject(method = "onTeamRemoved", at = @At("RETURN"))
    private void dataEnergistics$forgetResearchSubject(PlayerTeam team, CallbackInfo callback) {
        ResearchTeams.forgetScoreboardTeam(server, team.getName());
    }
}
