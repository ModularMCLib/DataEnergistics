package com.fish_dan_.data_energistics.core.mixins.normal.net.minecraft.server.players;

import com.fish_dan_.data_energistics.integration.ae.extendedaeplus.patternencoding.EaepPatternUploadScope;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Restriction(require = @Condition("extendedae_plus"))
@Mixin(PlayerList.class)
@NullMarked
public abstract class PlayerListMixin {

    @Inject(method = "remove", at = @At("HEAD"), require = 1)
    private void dataEnergistics$clearEaepPatternUploadScope(ServerPlayer player, CallbackInfo ci) {
        EaepPatternUploadScope.clearForPlayer(player);
    }
}
