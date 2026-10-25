package com.fish_dan_.data_energistics.common.weapon.platform.shield;

import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;
import com.fish_dan_.data_energistics.common.weapon.platform.equipment.EquippedWeaponPlatform;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunAmmunition;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;
import com.fish_dan_.data_energistics.common.weapon.platform.network.NetworkShotPayment;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.runtime.PlatformRuntime;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.PlatformAbilitySettings;
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
        boolean enabled = researched && runtime.platform.getOrDefault(DEDataComponents.PLATFORM_ABILITIES, PlatformAbilitySettings.DEFAULT).enabled(PlatformAbility.SHIELD);
        if (researched && connection != null && runtime.player.tickCount % 20 == 0) {
            double available = connection.grid().getEnergyService().extractAEPower((double) upgrades.recovery() * upgrades.energyPerPoint(), Actionable.SIMULATE, PowerMultiplier.ONE);
            int points = Math.min(Math.min(upgrades.recovery(), upgrades.capacity() - charge), (int) (available / upgrades.energyPerPoint()));
            int replenished = charge + points;
            if (points > 0 && NetworkShotPayment.pay(runtime.platform, connection, points * upgrades.energyPerPoint(), GunAmmunition.AE, false,
                    ammo -> runtime.platform.set(DEDataComponents.PLATFORM_SHIELD, new ShieldState(replenished, previous.overloadedUntil(), enabled))) != null)
                return;
        }
        ShieldState updated = new ShieldState(charge, previous.overloadedUntil(), enabled);
        if (!previous.equals(updated)) runtime.platform.set(DEDataComponents.PLATFORM_SHIELD, updated);
    }

    public static double absorb(LivingEntity entity, double damage, @Nullable DamageSource source) {
        if (!(entity instanceof ServerPlayer player) || !(damage > 0)) return damage;
        ItemStack platform = EquippedWeaponPlatform.find(player);
        if (platform.isEmpty()) return damage;
        var state = platform.getOrDefault(DEDataComponents.PLATFORM_SHIELD, ShieldState.EMPTY);
        if (!state.active() || state.charge() == 0 || !available(platform)) return damage;
        long cost = (long) Math.floor(damage / 100);
        if (cost <= state.charge()) {
            platform.set(DEDataComponents.PLATFORM_SHIELD, new ShieldState(state.charge() - (int) cost, state.overloadedUntil(), true));
            impact(player, source);
            return 0;
        }
        if (resist(player, platform, state)) {
            impact(player, source);
            return 0;
        }
        platform.set(DEDataComponents.PLATFORM_SHIELD, new ShieldState(0, state.overloadedUntil(), true));
        impact(player, source);
        return damage - state.charge() * 100.0;
    }

    public static boolean resistDeath(ServerPlayer player) {
        ItemStack platform = EquippedWeaponPlatform.find(player);
        if (platform.isEmpty()) return false;
        ShieldState state = platform.getOrDefault(DEDataComponents.PLATFORM_SHIELD, ShieldState.EMPTY);
        if (!state.active() || !available(platform) || !resist(player, platform, state)) return false;
        var writes = player.getData(DEAttachments.SHIELD_HEALTH_WRITES);
        impact(player, writes.sourceAt == player.level().getGameTime() ? writes.source : null);
        return true;
    }

    private static boolean available(ItemStack platform) {
        return platform.getOrDefault(DEDataComponents.PLATFORM_ABILITIES, PlatformAbilitySettings.DEFAULT).enabled(PlatformAbility.SHIELD) && ItemResearchUpgrades.of(new WeaponPlatformInventory(platform).getStackInSlot(WeaponPlatformInventory.CHIP_SLOT)).ids().contains(PlatformAbility.SHIELD.research);
    }

    private static boolean resist(ServerPlayer player, ItemStack platform, ShieldState state) {
        ShieldUpgrades upgrades = ShieldUpgrades.of(new WeaponPlatformInventory(platform).getStackInSlot(WeaponPlatformInventory.CHIP_SLOT));
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
