package com.fish_dan_.data_energistics.common.weapon.platform.shield;

import com.fish_dan_.data_energistics.common.combat.survival.PlayerProtection;
import com.fish_dan_.data_energistics.common.combat.survival.PlayerSurvival;
import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunAmmunition;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;
import com.fish_dan_.data_energistics.common.weapon.platform.network.NetworkShotPayment;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.runtime.PlatformRuntime;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.PlatformAbilities;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.WeaponPlatformInventory;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import org.jspecify.annotations.Nullable;

/** Shield transactions run before health loss and never enter the erasure lifecycle. Server thread only. */
public final class PlatformShield {

    public static void tick(PlatformRuntime runtime, @Nullable TerminalConnection connection) {
        ItemStack chip = runtime.inventory.getStackInSlot(WeaponPlatformInventory.CHIP_SLOT);
        boolean researched = ItemResearchUpgrades.of(chip).ids().contains(PlatformAbility.SHIELD.research);
        ShieldState previous = runtime.platform.getOrDefault(DEDataComponents.PLATFORM_SHIELD, ShieldState.EMPTY);
        ShieldUpgrades upgrades = ShieldUpgrades.of(chip);
        int charge = Math.min(previous.charge(), upgrades.capacity());
        long overloadedUntil = upgrades.overloadTicks() == 0 ? 0 : previous.overloadedUntil();
        boolean enabled = researched && runtime.platform.getOrDefault(DEDataComponents.PLATFORM_ABILITIES, PlatformAbilities.DEFAULT).enabled(PlatformAbility.SHIELD);
        if (researched && connection != null && runtime.player.tickCount % 20 == 0) {
            double available = connection.grid().getEnergyService().extractAEPower((double) upgrades.recovery() * upgrades.energyPerPoint(), Actionable.SIMULATE, PowerMultiplier.ONE);
            int points = Math.min(Math.min(upgrades.recovery(), upgrades.capacity() - charge), (int) (available / upgrades.energyPerPoint()));
            int replenished = charge + points;
            if (points > 0 && NetworkShotPayment.pay(runtime.platform, connection, points * upgrades.energyPerPoint(), GunAmmunition.AE, false,
                    ammo -> runtime.platform.set(DEDataComponents.PLATFORM_SHIELD, new ShieldState(replenished, overloadedUntil, enabled))) != null)
                return;
        }
        ShieldState updated = new ShieldState(charge, overloadedUntil, enabled);
        if (!previous.equals(updated)) runtime.platform.set(DEDataComponents.PLATFORM_SHIELD, updated);
    }

    public static double absorb(LivingEntity entity, double damage, @Nullable DamageSource source) {
        if (!(entity instanceof ServerPlayer player) || !(damage > 0)) return damage;
        return PlayerSurvival.absorb(player, damage, source);
    }

    /** The operation has already resolved research, settings and capacity changes. */
    public static double absorb(ServerPlayer player, PlayerProtection protection, double damage, @Nullable DamageSource source) {
        ShieldUpgrades upgrades = protection.shield();
        if (upgrades == null) return damage;
        ItemStack platform = protection.platform();
        var state = platform.getOrDefault(DEDataComponents.PLATFORM_SHIELD, ShieldState.EMPTY);
        long cost = (long) Math.floor(damage / 100);
        if (state.charge() > 0 && cost <= state.charge()) {
            platform.set(DEDataComponents.PLATFORM_SHIELD, new ShieldState(state.charge() - (int) cost, state.overloadedUntil(), true));
            impact(player, source);
            return 0;
        }
        if (resist(player, platform, state, upgrades)) {
            impact(player, source);
            return 0;
        }
        if (state.charge() == 0) return damage;
        platform.set(DEDataComponents.PLATFORM_SHIELD, new ShieldState(0, state.overloadedUntil(), true));
        impact(player, source);
        return damage - state.charge() * 100.0;
    }

    public static boolean resistDeath(ServerPlayer player) {
        var operation = player.getData(DEAttachments.SHIELD_HEALTH_WRITES).operation;
        return resistDeath(player, operation == null ? PlayerProtection.capture(player) : operation.protection, PlayerSurvival.recentSource(player));
    }

    public static boolean resistDeath(ServerPlayer player, PlayerProtection protection, @Nullable DamageSource source) {
        ShieldUpgrades upgrades = protection.shield();
        if (upgrades == null) return false;
        ItemStack platform = protection.platform();
        ShieldState state = platform.getOrDefault(DEDataComponents.PLATFORM_SHIELD, ShieldState.EMPTY);
        if (!resist(player, platform, state, upgrades)) return false;
        impact(player, source);
        return true;
    }

    private static boolean resist(ServerPlayer player, ItemStack platform, ShieldState state, ShieldUpgrades upgrades) {
        if (upgrades.overloadTicks() == 0) return true;
        int reserve = upgrades.capacity() * 15 / 100;
        long now = player.serverLevel().getServer().overworld().getGameTime();
        if (state.charge() <= reserve || state.overloadedUntil() > now) return false;
        platform.set(DEDataComponents.PLATFORM_SHIELD, new ShieldState(reserve, now + upgrades.overloadTicks(), true));
        return true;
    }

    private static void impact(ServerPlayer player, @Nullable DamageSource source) {
        Vec3 position = source == null ? null : source.getSourcePosition();
        Vec3 direction = position == null ? Vec3.ZERO : position.subtract(player.getBoundingBox().getCenter()).normalize();
        var impacts = player.getData(DEAttachments.SHIELD_HEALTH_WRITES).impacts;
        long now = player.level().getGameTime();
        impacts.removeIf(hit -> now - hit.at() >= 30);
        if (!impacts.isEmpty() && impacts.getLast().at() == now) impacts.removeLast();
        if (impacts.size() == 8) impacts.removeFirst();
        impacts.add(new ShieldImpact(direction, now));
        PlatformRpc.shield(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void incoming(LivingIncomingDamageEvent event) {
        double remaining = absorb(event.getEntity(), event.getAmount(), event.getSource());
        if (remaining == 0) event.setCanceled(true);
        else event.setAmount((float) remaining);
    }
}
