package com.fish_dan_.data_energistics.common.effect.reboot;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.combat.erasure.lifecycle.ErasureAttachments;
import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.data.DEMobEffects;
import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.equipment.EquippedWeaponPlatform;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformChipUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.WeaponPlatformInventory;
import com.fish_dan_.data_energistics.common.world.reboot.RebootCooldownSavedData;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Optional;

/** Server event boundary for snapshots and death interception; the world owns the single cooldown clock. */
public final class RadixReboot {

    private static boolean researched(ServerPlayer player) {
        ItemStack platform = EquippedWeaponPlatform.find(player);
        return !platform.isEmpty() && ItemResearchUpgrades.of(new WeaponPlatformInventory(platform).getStackInSlot(WeaponPlatformInventory.CHIP_SLOT))
                .ids().contains(PlatformChipUpgrades.REBOOT);
    }

    /** Starts a researched twenty-second anchor without consuming the shared cooldown before it resolves. */
    public static void activate(ServerPlayer player) {
        var server = player.serverLevel().getServer();
        if (researched(player) && !player.hasEffect(DEMobEffects.RADIX_REBOOT) && RebootCooldownSavedData.get(server).remaining(server, player.getUUID()) == 0) {
            player.addEffect(new MobEffectInstance(DEMobEffects.RADIX_REBOOT, 400, 0));
        }
    }

    /** Runs before lethal loss, including the cannon path that bypasses native damage events. */
    public static boolean tryPreventDeath(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player) || ErasureAttachments.blocksRecovery(player)) return false;
        RadixRebootHistory history = player.getData(DEAttachments.RADIX_REBOOT_HISTORY);
        if (history.restoring) return false;
        boolean anchored = player.hasEffect(DEMobEffects.RADIX_REBOOT) || history.expired;
        if (!anchored && !researched(player)) return false;
        var server = player.serverLevel().getServer();
        var cooldown = RebootCooldownSavedData.get(server);
        if (cooldown.remaining(server, player.getUUID()) > 0) return false;
        RadixRebootSnapshot snapshot = anchored ? player.getData(DEAttachments.RADIX_REBOOT_ANCHOR).orElse(null) : history.rewind();
        if (snapshot == null) return false;
        ServerLevel destination = server.getLevel(snapshot.dimension());
        if (destination == null) {
            Data_Energistics.LOGGER.warn("Cannot reboot player {} into unavailable dimension {}", player.getUUID(), snapshot.dimension().location());
            return false;
        }
        // Claim the cooldown before effect callbacks or a teleport can re-enter damage processing.
        cooldown.begin(server, player.getUUID());
        history.restoring = true;
        player.removeData(DEAttachments.RADIX_REBOOT_ANCHOR);
        try {
            snapshot.restore(player, destination);
            player.deathTime = 0;
            player.hurtTime = 0;
            player.hurtDuration = 0;
            history.clear();
            syncCooldown(player);
        } finally {
            history.restoring = false;
        }
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void lethalDamage(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        if (event.getNewDamage() - target.getAbsorptionAmount() >= target.getHealth() && tryPreventDeath(target)) event.setNewDamage(0);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void death(LivingDeathEvent event) {
        if (tryPreventDeath(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void applicable(MobEffectEvent.Applicable event) {
        MobEffectInstance effect = event.getEffectInstance();
        if (!effect.is(DEMobEffects.RADIX_REBOOT) && !effect.is(DEMobEffects.RADIX_REBOOT_COOLDOWN)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            if (!event.getEntity().level().isClientSide) event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            return;
        }
        var server = player.serverLevel().getServer();
        int remaining = RebootCooldownSavedData.get(server).remaining(server, player.getUUID());
        if (effect.is(DEMobEffects.RADIX_REBOOT) ? !player.isAlive() || remaining > 0 : remaining == 0) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    @SubscribeEvent
    public void added(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !event.getEffectInstance().is(DEMobEffects.RADIX_REBOOT) || event.getOldEffectInstance() != null) return;
        player.setData(DEAttachments.RADIX_REBOOT_ANCHOR, Optional.of(RadixRebootSnapshot.capture(player)));
        player.getData(DEAttachments.RADIX_REBOOT_HISTORY).expired = false;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void expired(MobEffectEvent.Expired event) {
        MobEffectInstance effect = event.getEffectInstance();
        if (event.getEntity() instanceof ServerPlayer player && effect != null && effect.is(DEMobEffects.RADIX_REBOOT)) {
            // tickEffects owns an iterator; restore only after it has finished removing the expired effect.
            player.getData(DEAttachments.RADIX_REBOOT_HISTORY).expired = true;
        }
    }

    @SubscribeEvent
    public void removed(MobEffectEvent.Remove event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getEffect().is(DEMobEffects.RADIX_REBOOT_COOLDOWN.getKey())) {
            var server = player.serverLevel().getServer();
            if (RebootCooldownSavedData.get(server).remaining(server, player.getUUID()) > 0) event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void record(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator()) return;
        if (researched(player)) {
            var server = player.serverLevel().getServer();
            player.getData(DEAttachments.RADIX_REBOOT_HISTORY).record(player, server.overworld().getGameTime());
        } else if (!player.hasEffect(DEMobEffects.RADIX_REBOOT)) {
            player.removeData(DEAttachments.RADIX_REBOOT_HISTORY);
        }
    }

    @SubscribeEvent
    public void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.hasData(DEAttachments.RADIX_REBOOT_HISTORY) && player.getData(DEAttachments.RADIX_REBOOT_HISTORY).expired) {
            if (!player.hasEffect(DEMobEffects.RADIX_REBOOT) && player.isAlive()) tryPreventDeath(player);
            player.getData(DEAttachments.RADIX_REBOOT_HISTORY).expired = false;
        }
        if (!player.hasEffect(DEMobEffects.RADIX_REBOOT)) player.removeData(DEAttachments.RADIX_REBOOT_ANCHOR);
        if (player.tickCount % 20 == 0) syncCooldown(player);
    }

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncCooldown(player);
            var server = player.serverLevel().getServer();
            if (researched(player) && player.isAlive() && RebootCooldownSavedData.get(server).remaining(server, player.getUUID()) == 0) {
                player.getData(DEAttachments.RADIX_REBOOT_HISTORY).record(player, server.overworld().getGameTime());
            }
        }
    }

    @SubscribeEvent
    public void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) syncCooldown(player);
    }

    private static void syncCooldown(ServerPlayer player) {
        var server = player.serverLevel().getServer();
        int remaining = RebootCooldownSavedData.get(server).remaining(server, player.getUUID());
        MobEffectInstance visible = player.getEffect(DEMobEffects.RADIX_REBOOT_COOLDOWN);
        if (remaining > 0) {
            if (visible == null || Math.abs(visible.getDuration() - remaining) > 1 || visible.getAmplifier() != 0) {
                player.forceAddEffect(new MobEffectInstance(DEMobEffects.RADIX_REBOOT_COOLDOWN, remaining, 0, false, false, true), null);
            }
        } else if (visible != null) player.removeEffect(DEMobEffects.RADIX_REBOOT_COOLDOWN);
    }
}
