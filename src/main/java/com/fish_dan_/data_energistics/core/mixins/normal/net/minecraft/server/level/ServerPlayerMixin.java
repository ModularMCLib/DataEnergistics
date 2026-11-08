package com.fish_dan_.data_energistics.core.mixins.normal.net.minecraft.server.level;

import com.fish_dan_.data_energistics.common.combat.erasure.lifecycle.ErasureAttachments;
import com.fish_dan_.data_energistics.common.combat.erasure.lifecycle.ErasureContext;
import com.fish_dan_.data_energistics.common.combat.erasure.lifecycle.ErasureState;
import com.fish_dan_.data_energistics.common.combat.survival.PlayerSurvival;
import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.weapon.platform.ability.PlatformMotion;
import com.fish_dan_.data_energistics.common.weapon.platform.domain.DomainStasis;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameRules.BooleanValue;
import net.minecraft.world.level.GameRules.Key;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.Team;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.neoforged.neoforge.common.CommonHooks;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.authlib.GameProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.Set;

/** Routes only the claimed life through the verified 1.21.1 death settlement; ordinary deaths retain their chain. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin extends Player {

    protected ServerPlayerMixin(Level level, BlockPos position, float rotation, GameProfile profile) {
        super(level, position, rotation, profile);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void dataEnergistics$initializeProtection(CallbackInfo ci) {
        PlayerSurvival.initialize((ServerPlayer) (Object) this);
    }

    @WrapOperation(method = "die", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/CommonHooks;onLivingDeath(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;)Z"))
    private boolean dataEnergistics$commitDeath(LivingEntity entity, DamageSource source, Operation<Boolean> original) {
        boolean canceled = original.call(entity, source);
        if (!canceled) entity.getData(DEAttachments.SHIELD_HEALTH_WRITES).deathCommitted = true;
        return canceled;
    }

    @WrapMethod(method = "die")
    private void dataEnergistics$routeDeath(DamageSource cause, Operation<Void> original) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        ErasureState state = ErasureAttachments.find(player);
        if (state == null || !state.blocksRecovery()) {
            PlayerSurvival.die(player, cause, () -> original.call(cause));
            return;
        }
        ErasureContext context = ErasureContext.current(player, cause);
        if (context == null || !state.enterDeathCall()) {
            return;
        }
        try {
            dataEnergistics$settleErasureDeath(player, cause, context);
        } finally {
            state.exitDeathCall();
        }
    }

    /**
     * Keeps vanilla's notification, inventory/drop hooks, score, statistics and final death location. Calling the
     * wrapped original here would re-enter third-party cancellable HEAD injections before this body could run.
     */
    @Unique
    private void dataEnergistics$settleErasureDeath(ServerPlayer player, DamageSource cause, ErasureContext context) {
        ErasureState state = context.state();
        state.beginDeathSideEffects();
        this.getCombatTracker().recordDamage(cause, context.initialHealth());
        // Use the original Level event endpoint directly, rather than an equipment-controlled Entity shortcut.
        this.level().gameEvent(player, GameEvent.ENTITY_DIE, this.position());
        // Dispatch once for other death rules; equipment cancellation does not veto this owned termination.
        CommonHooks.onLivingDeath(player, cause);
        state.deathHookReturned();
        state.enterDeathBody();
        if (this.level().getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES)) {
            Component message = this.getCombatTracker().getDeathMessage();
            player.connection.send(new ClientboundPlayerCombatKillPacket(this.getId(), message),
                    PacketSendListener.exceptionallySend(() -> {
                        Component shortened = Component.translatable("death.attack.message_too_long",
                                Component.literal(message.getString(256)).withStyle(ChatFormatting.YELLOW));
                        Component fallback = Component.translatable("death.attack.even_more_magic", this.getDisplayName())
                                .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, shortened)));
                        return new ClientboundPlayerCombatKillPacket(this.getId(), fallback);
                    }));
            Team team = this.getTeam();
            if (team == null || team.getDeathMessageVisibility() == Team.Visibility.ALWAYS) {
                player.getServer().getPlayerList().broadcastSystemMessage(message, false);
            } else if (team.getDeathMessageVisibility() == Team.Visibility.HIDE_FOR_OTHER_TEAMS) {
                player.getServer().getPlayerList().broadcastSystemToTeam(this, message);
            } else if (team.getDeathMessageVisibility() == Team.Visibility.HIDE_FOR_OWN_TEAM) {
                player.getServer().getPlayerList().broadcastSystemToAllExceptTeam(this, message);
            }
        } else {
            player.connection.send(new ClientboundPlayerCombatKillPacket(this.getId(), CommonComponents.EMPTY));
        }
        this.removeEntitiesOnShoulder();
        if (this.level().getGameRules().getBoolean(GameRules.RULE_FORGIVE_DEAD_PLAYERS)) {
            player.tellNeutralMobsThatIDied();
        }
        if (!this.isSpectator()) {
            this.dropAllDeathLoot(player.serverLevel(), cause);
        }
        this.getScoreboard().forAllObjectives(ObjectiveCriteria.DEATH_COUNT, this, ScoreAccess::increment);
        LivingEntity killer = context.initiator();
        if (killer != null) {
            this.awardStat(Stats.ENTITY_KILLED_BY.get(killer.getType()));
            killer.awardKillScore(this, this.deathScore, cause);
            this.createWitherRose(killer);
        }
        this.level().broadcastEntityEvent(this, (byte) 3);
        this.awardStat(Stats.DEATHS);
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_DEATH));
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        this.clearFire();
        this.setTicksFrozen(0);
        this.setSharedFlagOnFire(false);
        this.getCombatTracker().recheckStatus();
        this.setLastDeathLocation(Optional.of(GlobalPos.of(this.level().dimension(), this.blockPosition())));
        state.completeDeathBody();
    }

    @WrapMethod(method = "restoreFrom")
    private void dataEnergistics$releaseNewLife(ServerPlayer previous, boolean keepEverything, Operation<Void> original) {
        original.call(previous, keepEverything);
        // Do not clear the old object's lock: delayed callbacks must not resurrect that discarded instance.
        ErasureAttachments.clear(this);
    }

    @Inject(method = { "tick", "doTick" }, at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$freeze(CallbackInfo ci) {
        if (DomainStasis.freezeTick((Entity) (Object) this)) ci.cancel();
    }

    @WrapOperation(method = "restoreFrom", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"))
    private boolean dataEnergistics$restoreInventory(GameRules rules, Key<BooleanValue> key, Operation<Boolean> original, @Local(argsOnly = true) ServerPlayer previous) {
        return previous.getData(DEAttachments.PLATFORM_RETAINED_INVENTORY).retained() || original.call(rules, key);
    }

    @Inject(method = "changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity;", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$dimension(DimensionTransition transition, CallbackInfoReturnable<Entity> cir) {
        if (PlatformMotion.blocked((Entity) (Object) this)) cir.setReturnValue(null);
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$teleport(ServerLevel level, double x, double y, double z, float yaw, float pitch, CallbackInfo ci) {
        if (PlatformMotion.blocked((Entity) (Object) this)) ci.cancel();
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$relativeTeleport(ServerLevel level, double x, double y, double z, Set<RelativeMovement> relative, float yaw, float pitch, CallbackInfoReturnable<Boolean> cir) {
        if (PlatformMotion.blocked((Entity) (Object) this)) cir.setReturnValue(false);
    }
}
