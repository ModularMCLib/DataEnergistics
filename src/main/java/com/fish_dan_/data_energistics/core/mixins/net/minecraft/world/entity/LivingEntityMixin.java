package com.fish_dan_.data_energistics.core.mixins.net.minecraft.world.entity;

import com.fish_dan_.data_energistics.Data_Energistics.MOD;
import com.fish_dan_.data_energistics.common.combat.erasure.lifecycle.ErasureAttachments;
import com.fish_dan_.data_energistics.common.combat.erasure.lifecycle.ErasureContext;
import com.fish_dan_.data_energistics.common.combat.erasure.lifecycle.ErasureState;
import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.terminal.player.session.TerminalSessions;
import com.fish_dan_.data_energistics.common.weapon.platform.ability.PlatformMotion;
import com.fish_dan_.data_energistics.common.weapon.platform.retention.PlatformInventoryRetention;
import com.fish_dan_.data_energistics.common.weapon.platform.shield.PlatformShield;
import com.fish_dan_.data_energistics.integration.technology.draconicevolution.orbital.DraconicGuardianErasureAdapter;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.common.CommonHooks;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Owns only terminal-life health writes and its drop routine; normal healing and death hooks are unchanged. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {

    @Shadow
    @Final
    private static EntityDataAccessor<Float> DATA_HEALTH_ID;
    @Shadow
    protected int lastHurtByPlayerTime;
    @Shadow
    protected boolean dead;
    @Shadow
    protected int deathScore;

    protected LivingEntityMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Shadow
    public abstract float getMaxHealth();

    @Shadow
    protected abstract boolean shouldDropLoot();

    @Shadow
    protected abstract void dropFromLootTable(DamageSource source, boolean recentlyHit);

    @Shadow
    protected abstract void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit);

    @Shadow
    protected abstract void dropEquipment();

    @Shadow
    protected abstract void dropExperience(@Nullable Entity attacker);

    @Shadow
    protected abstract void dropAllDeathLoot(ServerLevel level, DamageSource source);

    @Shadow
    protected abstract void createWitherRose(@Nullable LivingEntity killer);

    /** The owned termination retains vanilla loot hooks while equipment cannot cancel the life transition. */
    @WrapMethod(method = "die")
    private void dataEnergistics$settleLivingDeath(DamageSource source, Operation<Void> original) {
        LivingEntity target = (LivingEntity) (Object) this;
        ErasureContext context = ErasureContext.current(target, source);
        if (context == null || target instanceof ServerPlayer) {
            original.call(source);
            return;
        }
        ErasureState state = context.state();
        if (!state.enterDeathCall()) return;
        try {
            state.beginDeathSideEffects();
            CommonHooks.onLivingDeath(target, source);
            state.deathHookReturned();
            state.enterDeathBody();
            LivingEntity killer = target.getKillCredit();
            if (this.deathScore >= 0 && killer != null) killer.awardKillScore(target, this.deathScore, source);
            if (target.isSleeping()) target.stopSleeping();
            this.dead = true;
            target.getCombatTracker().recordDamage(source, context.initialHealth());
            target.getCombatTracker().recheckStatus();
            ServerLevel level = (ServerLevel) this.level();
            Entity owner = source.getEntity();
            if (owner == null || owner.killedEntity(level, target)) {
                level.gameEvent(target, GameEvent.ENTITY_DIE, this.position());
                this.dropAllDeathLoot(level, source);
                this.createWitherRose(killer);
            }
            level.broadcastEntityEvent(target, (byte) 3);
            this.setPose(Pose.DYING);
            state.completeDeathBody();
        } finally {
            state.exitDeathCall();
        }
    }

    @WrapMethod(method = "setHealth")
    private void dataEnergistics$routeTerminalHealth(float health, Operation<Void> original) {
        if (!ErasureAttachments.blocksRecovery(this)) {
            original.call(health);
        } else if (health <= 0) {
            // Bypass cancellable HEAD injections for the owning termination, without entering the damage pipeline.
            this.entityData.set(DATA_HEALTH_ID, Mth.clamp(health, 0, getMaxHealth()));
        }
    }

    @WrapMethod(method = "heal")
    private void dataEnergistics$blockOldLifeHealing(float amount, Operation<Void> original) {
        if (!ErasureAttachments.blocksRecovery(this)) {
            original.call(amount);
        }
    }

    @WrapMethod(method = "dropAllDeathLoot")
    private void dataEnergistics$routeErasureDrops(ServerLevel level, DamageSource source, Operation<Void> original) {
        LivingEntity target = (LivingEntity) (Object) this;
        if (target instanceof ServerPlayer player) PlatformInventoryRetention.capture(player);
        if (ErasureContext.current(target, source) == null) {
            original.call(level, source);
            return;
        }
        boolean guardian = MOD.DRACONIC_EVOLUTION.isLoaded() && DraconicGuardianErasureAdapter.supports(target);
        if (!(target instanceof ServerPlayer) && !guardian) {
            original.call(level, source);
            return;
        }
        // Preserve the NeoForge 1.21.1 drop pipeline, including its final cancellable drops event and inventory rules.
        this.captureDrops(new ObjectArrayList<>());
        boolean recentlyHit = this.lastHurtByPlayerTime > 0;
        if (this.shouldDropLoot() && level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            this.dropFromLootTable(source, recentlyHit);
            this.dropCustomDeathLoot(level, source, recentlyHit);
        }
        this.dropEquipment();
        if (guardian) DraconicGuardianErasureAdapter.completeEncounter(target, source);
        else this.dropExperience(source.getEntity());
        var drops = this.captureDrops(null);
        if (!CommonHooks.onLivingDrops(target, source, drops, recentlyHit)) {
            drops.forEach(level::addFreshEntity);
        }
    }

    @Inject(method = "calculateEntityAnimation", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$airbornePose(boolean includeHeight, CallbackInfo ci) {
        if ((Object) this instanceof Player player && !player.onGround() && (player.getAbilities().flying || PlatformMotion.locked(player))) {
            player.walkAnimation.setSpeed(0);
            player.walkAnimation.update(0, 1);
            ci.cancel();
        }
    }

    @WrapMethod(method = "setHealth")
    private void dataEnergistics$directHealth(float requested, Operation<Void> original) {
        if (!((Object) this instanceof ServerPlayer player) || ErasureAttachments.blocksRecovery(player)) {
            original.call(requested);
            return;
        }
        var writes = player.getData(DEAttachments.SHIELD_HEALTH_WRITES);
        float current = player.getHealth();
        if (writes.settled != 0 || requested >= current || current <= 0) {
            original.call(requested);
            return;
        }
        double remaining = PlatformShield.absorb(player, (double) current - requested, writes.sourceAt == player.level().getGameTime() ? writes.source : null);
        float adjusted = (float) (current - remaining);
        if (requested <= 0 && adjusted > 0) writes.rejectedDeathAt = player.level().getGameTime();
        writes.settled++;
        try {
            original.call(adjusted);
        } finally {
            writes.settled--;
        }
    }

    @WrapMethod(method = "releaseUsingItem")
    private void dataEnergistics$payReleasedUse(Operation<Void> original) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!(entity instanceof ServerPlayer player)) {
            original.call();
            return;
        }
        var use = TerminalSessions.beginItemUse(player);
        original.call();
        TerminalSessions.finishItemUse(player, use);
    }
}
