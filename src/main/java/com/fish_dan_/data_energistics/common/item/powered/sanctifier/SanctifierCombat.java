package com.fish_dan_.data_energistics.common.item.powered.sanctifier;

import com.fish_dan_.data_energistics.common.combat.erasure.EntityErasure;
import com.fish_dan_.data_energistics.common.combat.erasure.strike.ErasureOutcome;
import com.fish_dan_.data_energistics.common.combat.erasure.strike.ErasureStrike;
import com.fish_dan_.data_energistics.common.data.DEItems;
import com.fish_dan_.data_energistics.common.data.DEMobEffects;
import com.fish_dan_.data_energistics.common.entity.projectile.SanctifierBladeEntity;
import com.fish_dan_.data_energistics.common.item.powered.PoweredSwordItem;

import appeng.api.config.Actionable;

import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import it.unimi.dsi.fastutil.objects.ObjectSets;

import java.util.Comparator;
import java.util.UUID;

/** Server-side charge, kill rewards and the post-respawn spectator interval. */
public final class SanctifierCombat {

    private static final String CHARGE = "data_energistics:sanctifier_charge";
    private static final String BUFF_END = "data_energistics:sanctifier_buff_end";
    private static final String STREAK = "data_energistics:sanctifier_streak";
    private static final String RESPAWN_PENDING = "data_energistics:sanctifier_respawn_pending";
    private static final String SPECTATOR_TICKS = "data_energistics:sanctifier_spectator_ticks";
    private static final String ORIGINAL_MODE = "data_energistics:sanctifier_original_mode";

    public static void chargeTick(ServerPlayer player, ItemStack stack) {
        if (!SanctifierBladeRecovery.get(player.server).canCharge(player, stack)) {
            finishCharge(player);
            player.stopUsingItem();
            return;
        }
        CompoundTag data = player.getPersistentData();
        int previous = data.getInt(CHARGE);
        if (previous < 200) {
            PoweredSwordItem item = (PoweredSwordItem) stack.getItem();
            if (item.extractAEPower(stack, 12, Actionable.SIMULATE) < 12) {
                finishCharge(player);
                player.stopUsingItem();
                return;
            }
            item.extractAEPower(stack, 12, Actionable.MODULATE);
        }
        int ticks = Math.min(200, previous + 1);
        data.putInt(CHARGE, ticks);
        // Start at I immediately and reach V exactly when the ten-second charge completes.
        int amplifier = Math.min(4, ticks / 50);
        MobEffectInstance current = player.getEffect(DEMobEffects.LIFE_REDUCTION);
        int level = current == null ? amplifier : Math.max(amplifier, Math.min(4, current.getAmplifier()));
        if (current == null || current.getAmplifier() != level || !current.isInfiniteDuration()) {
            player.removeEffect(DEMobEffects.LIFE_REDUCTION);
            player.addEffect(new MobEffectInstance(DEMobEffects.LIFE_REDUCTION, MobEffectInstance.INFINITE_DURATION, level, false, false, true));
        }
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    public static boolean finishCharge(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        int ticks = data.getInt(CHARGE);
        data.remove(CHARGE);
        MobEffectInstance effect = player.getEffect(DEMobEffects.LIFE_REDUCTION);
        if (ticks > 0 && effect != null) {
            int amplifier = effect.getAmplifier();
            player.removeEffect(DEMobEffects.LIFE_REDUCTION);
            player.addEffect(new MobEffectInstance(DEMobEffects.LIFE_REDUCTION, 800, amplifier, false, false, true));
        }
        return ticks >= 200;
    }

    public static void release(ServerPlayer player, ItemStack stack, int elapsed) {
        if (!finishCharge(player) || elapsed < 200 || !SanctifierBladeRecovery.get(player.server).canCharge(player, stack)) return;
        Vec3 origin = player.getEyePosition();
        Vec3 facing = player.getLookAngle();
        LivingEntity target = player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(8), candidate -> {
                    if (candidate == player || !candidate.isAlive() || candidate.isSpectator()) return false;
                    if (!(candidate instanceof Player) && !(candidate instanceof Enemy)) return false;
                    if (candidate instanceof Player other && (other.isCreative() || !player.canHarmPlayer(other))) return false;
                    Vec3 offset = candidate.getBoundingBox().getCenter().subtract(origin);
                    return offset.lengthSqr() <= 64 && offset.normalize().dot(facing) >= 0.5 && player.hasLineOfSight(candidate);
                }).stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
        SanctifierBladeEntity blade = new SanctifierBladeEntity(player, target, stack);
        if (player.level().addFreshEntity(blade)) {
            SanctifierBladeRecovery.get(player.server).launch(player, stack, blade.getUUID());
            player.swing(player.getMainArm() == HumanoidArm.RIGHT ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, true);
        }
    }

    public static void execute(ServerPlayer caster, ServerPlayer target) {
        if (!target.isAlive() || target.isSpectator() || target.isCreative() || !caster.canHarmPlayer(target)) return;
        CompoundTag targetData = target.getPersistentData();
        targetData.putInt(RESPAWN_PENDING, 400);
        targetData.putInt(ORIGINAL_MODE, target.gameMode.getGameModeForPlayer().getId());
        ErasureOutcome outcome = ErasureOutcome.FAILED_BEFORE_COMMIT;
        try {
            outcome = EntityErasure.eraseHit(target, new ErasureStrike(UUID.randomUUID(), caster.getUUID(), ObjectSets.singleton(caster.getUUID())));
        } finally {
            if (outcome != ErasureOutcome.PLAYER_DEATH_COMPLETED) {
                targetData.remove(RESPAWN_PENDING);
                targetData.remove(ORIGINAL_MODE);
            }
        }
        if (outcome == ErasureOutcome.PLAYER_DEATH_COMPLETED) rewardKill(caster);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer) || !(event.getSource().getEntity() instanceof ServerPlayer killer)) return;
        boolean melee = event.getSource().is(DamageTypes.PLAYER_ATTACK) && event.getSource().getDirectEntity() == killer && killer.getMainHandItem().is(DEItems.DATA_SANCTIFIER.get());
        if (melee) rewardKill(killer);
    }

    private static void rewardKill(ServerPlayer killer) {
        long now = killer.server.overworld().getGameTime();
        CompoundTag data = killer.getPersistentData();
        long oldEnd = data.getLong(BUFF_END);
        int streak = oldEnd > now ? data.getInt(STREAK) + 1 : 0;
        int duration = oldEnd > now ? (int) Math.min(Integer.MAX_VALUE, oldEnd - now + 60) : 400;
        data.putInt(STREAK, streak);
        data.putLong(BUFF_END, now + duration);
        reward(killer, MobEffects.DAMAGE_BOOST, duration, 2 + streak);
        reward(killer, MobEffects.MOVEMENT_SPEED, duration, 2 + streak);
        reward(killer, MobEffects.DAMAGE_RESISTANCE, duration, 1 + streak);
        reward(killer, DEMobEffects.DATA_OVERLOAD, duration, 3 + streak);
    }

    private static void reward(ServerPlayer player, Holder<MobEffect> effect, int duration, int amplifier) {
        player.addEffect(new MobEffectInstance(effect, duration, amplifier, false, true, true));
    }

    @SubscribeEvent
    public void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag data = player.getPersistentData();
        if (data.contains(CHARGE) && (!player.isUsingItem() || !player.getUseItem().is(DEItems.DATA_SANCTIFIER.get()))) finishCharge(player);
        int remaining = data.getInt(SPECTATOR_TICKS);
        if (remaining <= 0) return;
        remaining--;
        data.putInt(SPECTATOR_TICKS, remaining);
        if (remaining == 0) {
            player.setGameMode(GameType.byId(data.getInt(ORIGINAL_MODE)));
            data.remove(SPECTATOR_TICKS);
            data.remove(ORIGINAL_MODE);
        } else {
            if (!player.isSpectator()) player.setGameMode(GameType.SPECTATOR);
            if (remaining % 20 == 0) player.displayClientMessage(
                    Component.translatable("message.data_energistics.sanctifier_respawn", remaining / 20), true);
        }
    }

    @SubscribeEvent
    public void clonePlayer(PlayerEvent.Clone event) {
        CompoundTag oldData = event.getOriginal().getPersistentData();
        CompoundTag newData = event.getEntity().getPersistentData();
        if (oldData.contains(RESPAWN_PENDING)) newData.putInt(RESPAWN_PENDING, oldData.getInt(RESPAWN_PENDING));
        if (oldData.contains(ORIGINAL_MODE)) newData.putInt(ORIGINAL_MODE, oldData.getInt(ORIGINAL_MODE));
        if (oldData.contains(SPECTATOR_TICKS)) newData.putInt(SPECTATOR_TICKS, oldData.getInt(SPECTATOR_TICKS));
    }

    @SubscribeEvent
    public void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag data = player.getPersistentData();
        if (!data.contains(RESPAWN_PENDING)) return;
        if (player.server.isHardcore()) {
            data.remove(RESPAWN_PENDING);
            data.remove(ORIGINAL_MODE);
            return;
        }
        data.putInt(SPECTATOR_TICKS, data.getInt(RESPAWN_PENDING));
        data.remove(RESPAWN_PENDING);
        player.setGameMode(GameType.SPECTATOR);
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) finishCharge(player);
    }

    @SubscribeEvent
    public void gameModeChange(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (event.getEntity().getPersistentData().getInt(SPECTATOR_TICKS) > 0 && event.getNewGameMode() != GameType.SPECTATOR) event.setCanceled(true);
    }
}
