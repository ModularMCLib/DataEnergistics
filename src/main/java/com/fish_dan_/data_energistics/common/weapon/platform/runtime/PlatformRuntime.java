package com.fish_dan_.data_energistics.common.weapon.platform.runtime;

import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.data.DEMobEffects;
import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalQuantumAccess;
import com.fish_dan_.data_energistics.common.weapon.platform.combat.GunFormation;
import com.fish_dan_.data_energistics.common.weapon.platform.combat.GunShot;
import com.fish_dan_.data_energistics.common.weapon.platform.combat.GunTargeting;
import com.fish_dan_.data_energistics.common.weapon.platform.model.BiochipResearch;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunAmmunition;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;
import com.fish_dan_.data_energistics.common.weapon.platform.network.NetworkShotPayment;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.GunSettings;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.GunThermalState;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.WeaponPlatformInventory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** One equipped stack's server-thread firing state. Item contents, settings and heat remain component-owned. */
public final class PlatformRuntime {

    public final ServerPlayer player;
    public final ItemStack platform;
    public final WeaponPlatformInventory inventory;
    public final ObjectArrayList<GunStatus> guns = new ObjectArrayList<>();
    public @Nullable UUID lock;
    public boolean connected;
    public boolean failed;

    public PlatformRuntime(ServerPlayer player, ItemStack platform) {
        this.player = player;
        this.platform = platform;
        this.inventory = new WeaponPlatformInventory(platform);
    }

    public void tick() {
        guns.clear();
        ItemStack chip = inventory.getStackInSlot(WeaponPlatformInventory.CHIP_SLOT);
        int capacity = BiochipResearch.capacity(chip);
        double cooling = BiochipResearch.cooling(chip);
        GunTargeting targeting = new GunTargeting(player, platform.getOrDefault(DEDataComponents.PLATFORM_FRIENDLY_PROTECTION, true));
        var connection = TerminalQuantumAccess.resolve(player);
        connected = connection != null;
        if (connection != null) NetworkShotPayment.refund(platform, connection);
        LivingEntity locked = lock == null ? null : player.serverLevel().getEntity(lock) instanceof LivingEntity living ? living : null;
        if (locked == null || !targeting.canAttack(locked) || player.distanceToSqr(locked) > 4096) lock = null;
        long now = player.level().getGameTime();
        var candidates = player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(64));
        for (int slot = 0; slot < capacity; slot++) {
            ItemStack gun = inventory.getStackInSlot(slot);
            if (gun.isEmpty()) continue;
            GunForm form = GunForm.of(gun);
            GunSettings settings = gun.getOrDefault(DEDataComponents.GUN_SETTINGS, GunSettings.DEFAULT);
            GunThermalState thermal = gun.getOrDefault(DEDataComponents.GUN_THERMAL, GunThermalState.COLD);
            double heat = thermal.currentHeat(now, cooling);
            boolean overheated = thermal.overheated() && heat > 40;
            GunAmmunition selected = form == GunForm.MEDICAL ? GunAmmunition.BINARY : settings.ammunition();
            if ((selected == GunAmmunition.ECHO || selected == GunAmmunition.STELLAR) && !ItemResearchUpgrades.of(gun).ids().contains(BiochipResearch.AMMUNITION)) selected = GunAmmunition.AE;
            Vec3 origin = player.position().add(GunFormation.offset(player, slot));
            LivingEntity target = null;
            if (form == GunForm.MEDICAL) {
                double lowest = Double.POSITIVE_INFINITY;
                int mostMissing = -1;
                for (LivingEntity candidate : candidates) {
                    if (!(candidate instanceof Player ally) || !ally.isAlive() || ally.isSpectator() || !targeting.allied(ally) || (ally == player ? !settings.supportSelf() : !settings.supportTeam()) || origin.distanceToSqr(ally.getBoundingBox().getCenter()) > form.range * form.range || !targeting.visible(origin, ally.getBoundingBox().getCenter())) continue;
                    double ratio = ally.getHealth() / ally.getMaxHealth();
                    int missing = (ally.hasEffect(MobEffects.REGENERATION) ? 0 : 1) + (ally.hasEffect(MobEffects.MOVEMENT_SPEED) ? 0 : 1) + (ally.hasEffect(MobEffects.DAMAGE_RESISTANCE) ? 0 : 1) + (ally.hasEffect(MobEffects.DAMAGE_BOOST) ? 0 : 1) + (ally.hasEffect(MobEffects.DIG_SPEED) ? 0 : 1) + (ally.hasEffect(DEMobEffects.DATA_OVERLOAD) ? 0 : 1);
                    if (ratio < 1 && ratio < lowest || ratio == 1 && lowest >= 1 && missing > mostMissing && missing > 0) {
                        target = ally;
                        lowest = ratio;
                        mostMissing = missing;
                    }
                }
            } else if (lock != null && locked != null && origin.distanceToSqr(locked.getBoundingBox().getCenter()) <= form.range * form.range && targeting.visible(origin, locked.getBoundingBox().getCenter())) target = locked;
            else if (lock == null) {
                double nearest = form.range * form.range;
                for (LivingEntity candidate : candidates) {
                    double distance = origin.distanceToSqr(candidate.getBoundingBox().getCenter());
                    if (distance >= nearest || !targeting.canAttack(candidate) || !targeting.matches(candidate, settings.targets()) || !targeting.visible(origin, candidate.getBoundingBox().getCenter())) continue;
                    target = candidate;
                    nearest = distance;
                }
            }
            String state = failed ? "error" : !settings.enabled() ? "disabled" : overheated ? "overheated" : !connected ? "network" : target == null ? "searching" : thermal.readyAt() > now ? "cooldown" : "ready";
            GunAmmunition actual = selected;
            long ready = thermal.readyAt();
            if (state.equals("ready")) {
                // Each shot re-resolves the same public quantum access as the terminal; it does not charge terminal upkeep.
                connection = TerminalQuantumAccess.resolve(player);
                GunAmmunition paid = connection == null ? null : NetworkShotPayment.pay(platform, connection, form.energy, selected, form == GunForm.MEDICAL);
                if (paid == null) state = "resources";
                else {
                    actual = paid;
                    GunShot.fire(targeting, origin, target, form, paid);
                    heat = Math.min(100, heat + form.heat);
                    overheated = heat >= 100;
                    ready = now + form.interval;
                    state = overheated ? "overheated" : "firing";
                }
            }
            GunThermalState updated = new GunThermalState(heat, now, ready, overheated);
            if (heat != thermal.heat() || ready != thermal.readyAt() || overheated != thermal.overheated()) {
                gun.set(DEDataComponents.GUN_THERMAL, updated);
                inventory.setStackInSlot(slot, gun);
            }
            guns.add(new GunStatus(slot, form, heat, overheated, selected, actual, target == null ? -1 : target.getId(), state));
        }
    }

    public void toggleLock() {
        if (lock != null) {
            lock = null;
            return;
        }
        Vec3 from = player.getEyePosition();
        Vec3 to = from.add(player.getViewVector(1).scale(64));
        GunTargeting targeting = new GunTargeting(player, platform.getOrDefault(DEDataComponents.PLATFORM_FRIENDLY_PROTECTION, true));
        double closest = 4096;
        for (LivingEntity candidate : player.level().getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(1))) {
            if (!targeting.canAttack(candidate)) continue;
            var hit = candidate.getBoundingBox().inflate(0.2).clip(from, to);
            if (hit.isPresent() && hit.get().distanceToSqr(from) < closest && targeting.visible(from, hit.get())) {
                closest = hit.get().distanceToSqr(from);
                lock = candidate.getUUID();
            }
        }
    }

    public record GunStatus(int slot, GunForm form, double heat, boolean overheated, GunAmmunition selected, GunAmmunition actual, int target, String state) {}
}
