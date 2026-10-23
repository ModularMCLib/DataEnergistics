package com.fish_dan_.data_energistics.common.item.powered.sanctifier;

import com.fish_dan_.data_energistics.common.combat.erasure.EntityErasure;
import com.fish_dan_.data_energistics.common.combat.erasure.strike.ErasureOutcome;
import com.fish_dan_.data_energistics.common.combat.erasure.strike.ErasureStrike;
import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.data.DEItems;
import com.fish_dan_.data_energistics.common.data.DEMobEffects;
import com.fish_dan_.data_energistics.common.entity.projectile.SanctifierBladeEntity;
import com.fish_dan_.data_energistics.common.item.powered.PoweredSwordItem;

import appeng.api.config.Actionable;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import it.unimi.dsi.fastutil.objects.ObjectSets;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Server-side charge, kill rewards and the post-respawn spectator interval. */
public final class SanctifierCombat {

    /** Launches once at right-button press; subsequent use ticks belong to this exact deployed blade. */
    public static boolean startCharge(ServerPlayer player, ItemStack stack) {
        if (!SanctifierBladeRecovery.get(player.server).canCharge(player, stack)) return false;
        finishCharge(player);
        SanctifierBladeEntity blade = new SanctifierBladeEntity(player, stack);
        if (!player.level().addFreshEntity(blade)) return false;
        SanctifierBladeRecovery.get(player.server).launch(player, stack, blade.getUUID());
        player.setData(DEAttachments.SANCTIFIER_CHARGE, new SanctifierAttachments.Charge(blade.getUUID(), 0));
        player.swing(player.getMainArm() == HumanoidArm.RIGHT ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, true);
        return true;
    }

    private static @Nullable SanctifierBladeEntity chargingBlade(ServerPlayer player) {
        if (!player.hasData(DEAttachments.SANCTIFIER_CHARGE)) return null;
        Entity entity = player.serverLevel().getEntity(player.getData(DEAttachments.SANCTIFIER_CHARGE).blade());
        return entity instanceof SanctifierBladeEntity blade && SanctifierBladeRecovery.get(player.server).belongsTo(blade.getUUID(), player.getUUID()) ? blade : null;
    }

    public static void chargeTick(ServerPlayer player, ItemStack stack) {
        SanctifierBladeEntity blade = chargingBlade(player);
        if (blade == null || !blade.isCharging() || !blade.getUUID().equals(stack.get(DEDataComponents.SANCTIFIER_BLADE.get()))) {
            finishCharge(player);
            player.stopUsingItem();
            return;
        }
        int previous = player.getData(DEAttachments.SANCTIFIER_CHARGE).ticks();
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
        player.setData(DEAttachments.SANCTIFIER_CHARGE, new SanctifierAttachments.Charge(blade.getUUID(), ticks));
        // Start at I immediately and reach V exactly when the ten-second charge completes.
        int amplifier = Math.min(4, ticks / 50);
        MobEffectInstance current = player.getEffect(DEMobEffects.LIFE_REDUCTION);
        int level = current == null ? amplifier : Math.clamp(current.getAmplifier(), amplifier, 4);
        if (current == null || current.getAmplifier() != level || !current.isInfiniteDuration()) {
            player.removeEffect(DEMobEffects.LIFE_REDUCTION);
            player.addEffect(new MobEffectInstance(DEMobEffects.LIFE_REDUCTION, MobEffectInstance.INFINITE_DURATION, level, false, false, true));
        }
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    public static boolean finishCharge(ServerPlayer player) {
        SanctifierBladeEntity blade = chargingBlade(player);
        if (blade != null) blade.cancelCharge();
        int ticks = player.hasData(DEAttachments.SANCTIFIER_CHARGE) ? player.getData(DEAttachments.SANCTIFIER_CHARGE).ticks() : 0;
        player.removeData(DEAttachments.SANCTIFIER_CHARGE);
        MobEffectInstance effect = player.getEffect(DEMobEffects.LIFE_REDUCTION);
        if (ticks > 0 && effect != null) {
            int amplifier = effect.getAmplifier();
            player.removeEffect(DEMobEffects.LIFE_REDUCTION);
            player.addEffect(new MobEffectInstance(DEMobEffects.LIFE_REDUCTION, 800, amplifier, false, false, true));
        }
        return ticks >= 200;
    }

    public static void release(ServerPlayer player, ItemStack stack, int elapsed) {
        SanctifierBladeEntity blade = chargingBlade(player);
        boolean completed = finishCharge(player);
        if (completed && elapsed >= 200 && blade != null && blade.getUUID().equals(stack.get(DEDataComponents.SANCTIFIER_BLADE.get()))) blade.beginStrike(player);
    }

    public static void execute(ServerPlayer caster, ServerPlayer target) {
        if (!target.isAlive() || target.isSpectator() || target.isCreative() || !caster.canHarmPlayer(target)) return;
        target.setData(DEAttachments.SANCTIFIER_RESPAWN, new SanctifierAttachments.Respawn(400, target.gameMode.getGameModeForPlayer(), true));
        ErasureOutcome outcome = ErasureOutcome.FAILED_BEFORE_COMMIT;
        try {
            outcome = EntityErasure.eraseHit(target, new ErasureStrike(UUID.randomUUID(), caster.getUUID(), ObjectSets.singleton(caster.getUUID())));
        } finally {
            if (outcome != ErasureOutcome.PLAYER_DEATH_COMPLETED) {
                target.removeData(DEAttachments.SANCTIFIER_RESPAWN);
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
        SanctifierAttachments.Rewards previous = killer.getData(DEAttachments.SANCTIFIER_REWARDS);
        long oldEnd = previous.end();
        int streak = oldEnd > now ? Math.incrementExact(previous.streak()) : 0;
        int duration = oldEnd > now ? (int) Math.min(Integer.MAX_VALUE, oldEnd - now + 60) : 400;
        killer.setData(DEAttachments.SANCTIFIER_REWARDS, new SanctifierAttachments.Rewards(now + duration, streak));
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
        if (player.hasData(DEAttachments.SANCTIFIER_CHARGE) && (!player.isUsingItem() || !player.getUseItem().is(DEItems.DATA_SANCTIFIER.get()))) finishCharge(player);
        if (!player.hasData(DEAttachments.SANCTIFIER_RESPAWN)) return;
        SanctifierAttachments.Respawn respawn = player.getData(DEAttachments.SANCTIFIER_RESPAWN);
        if (respawn.pending()) return;
        int remaining = respawn.ticks() - 1;
        if (remaining == 0) {
            player.removeData(DEAttachments.SANCTIFIER_RESPAWN);
            player.setGameMode(respawn.originalMode());
        } else {
            player.setData(DEAttachments.SANCTIFIER_RESPAWN, new SanctifierAttachments.Respawn(remaining, respawn.originalMode(), false));
            if (!player.isSpectator()) player.setGameMode(GameType.SPECTATOR);
            if (remaining % 20 == 0) player.displayClientMessage(
                    Component.translatable("message.data_energistics.sanctifier_respawn", remaining / 20), true);
        }
    }

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) finishCharge(player);
    }

    @SubscribeEvent
    public void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.hasData(DEAttachments.SANCTIFIER_RESPAWN)) return;
        SanctifierAttachments.Respawn respawn = player.getData(DEAttachments.SANCTIFIER_RESPAWN);
        if (!respawn.pending()) return;
        if (player.server.isHardcore()) {
            player.removeData(DEAttachments.SANCTIFIER_RESPAWN);
            return;
        }
        player.setData(DEAttachments.SANCTIFIER_RESPAWN, new SanctifierAttachments.Respawn(respawn.ticks(), respawn.originalMode(), false));
        player.setGameMode(GameType.SPECTATOR);
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) finishCharge(player);
    }

    @SubscribeEvent
    public void gameModeChange(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (event.getEntity().hasData(DEAttachments.SANCTIFIER_RESPAWN) && !event.getEntity().getData(DEAttachments.SANCTIFIER_RESPAWN).pending() && event.getNewGameMode() != GameType.SPECTATOR) event.setCanceled(true);
    }
}
