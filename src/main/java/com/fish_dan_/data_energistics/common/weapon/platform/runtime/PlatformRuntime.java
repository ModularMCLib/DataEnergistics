package com.fish_dan_.data_energistics.common.weapon.platform.runtime;

import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.data.DEMobEffects;
import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalQuantumAccess;
import com.fish_dan_.data_energistics.common.weapon.platform.combat.GunShot;
import com.fish_dan_.data_energistics.common.weapon.platform.combat.GunTargeting;
import com.fish_dan_.data_energistics.common.weapon.platform.flight.FloatingGunFlight;
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
import net.minecraft.world.phys.Vec3;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** One equipped stack's server-thread firing state. Item contents, settings and heat remain component-owned. */
public final class PlatformRuntime {

    public final ServerPlayer player;
    public final ItemStack platform;
    public final WeaponPlatformInventory inventory;
    public final ObjectArrayList<GunStatus> guns = new ObjectArrayList<>();
    public final ObjectLinkedOpenHashSet<UUID> locks = new ObjectLinkedOpenHashSet<>();
    private final @Nullable FloatingGunFlight[] flights = new FloatingGunFlight[8];
    private final @Nullable UUID[] assignedLocks = new UUID[8];
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
        int targetMask = platform.getOrDefault(DEDataComponents.PLATFORM_TARGETS, GunSettings.HOSTILE);
        var connection = TerminalQuantumAccess.resolve(player);
        connected = connection != null;
        if (connection != null) NetworkShotPayment.refund(platform, connection);
        var locked = new Object2ObjectOpenHashMap<UUID, LivingEntity>();
        for (var iterator = locks.iterator(); iterator.hasNext();) {
            UUID id = iterator.next();
            if (player.serverLevel().getEntity(id) instanceof LivingEntity living && targeting.canAttack(living) && player.distanceToSqr(living) <= GunTargeting.FIRE_CONTROL_RADIUS_SQUARED) locked.put(id, living);
            else iterator.remove();
        }
        long now = player.level().getGameTime();
        var candidates = player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(GunTargeting.FIRE_CONTROL_RADIUS),
                entity -> player.distanceToSqr(entity) <= GunTargeting.FIRE_CONTROL_RADIUS_SQUARED);
        for (int slot = 0; slot < flights.length; slot++) {
            if (slot >= capacity || inventory.getStackInSlot(slot).isEmpty()) {
                if (flights[slot] != null) flights[slot].discard();
                flights[slot] = null;
                assignedLocks[slot] = null;
            }
        }
        for (int slot = 0; slot < capacity; slot++) {
            ItemStack gun = inventory.getStackInSlot(slot);
            if (gun.isEmpty()) continue;
            GunForm form = GunForm.of(gun);
            FloatingGunFlight flight = flights[slot];
            if (flight == null || flight.isRemoved() || flight.level() != player.level()) {
                flight = new FloatingGunFlight(player, platform, slot, form);
                if (!player.serverLevel().addFreshEntity(flight)) throw new IllegalStateException("Floating gun could not deploy from slot " + slot);
                flights[slot] = flight;
            }
            GunSettings settings = gun.getOrDefault(DEDataComponents.GUN_SETTINGS, GunSettings.DEFAULT);
            GunThermalState thermal = gun.getOrDefault(DEDataComponents.GUN_THERMAL, GunThermalState.COLD);
            double heat = thermal.currentHeat(now, cooling);
            boolean overheated = thermal.overheated() && heat > 40;
            GunAmmunition selected = form == GunForm.MEDICAL ? GunAmmunition.BINARY : settings.ammunition();
            Vec3 origin = flight.getBoundingBox().getCenter();
            LivingEntity target = null;
            if (form == GunForm.MEDICAL) {
                assignedLocks[slot] = null;
                double lowest = Double.POSITIVE_INFINITY;
                int mostMissing = -1;
                for (LivingEntity candidate : candidates) {
                    if (!(candidate instanceof Player ally) || !ally.isAlive() || ally.isSpectator() || !targeting.allied(ally) || (ally == player ? !settings.supportSelf() : !settings.supportTeam())) continue;
                    double ratio = ally.getHealth() / ally.getMaxHealth();
                    int missing = (ally.hasEffect(MobEffects.REGENERATION) ? 0 : 1) + (ally.hasEffect(MobEffects.MOVEMENT_SPEED) ? 0 : 1) + (ally.hasEffect(MobEffects.DAMAGE_RESISTANCE) ? 0 : 1) + (ally.hasEffect(MobEffects.DAMAGE_BOOST) ? 0 : 1) + (ally.hasEffect(MobEffects.DIG_SPEED) ? 0 : 1) + (ally.hasEffect(DEMobEffects.DATA_OVERLOAD) ? 0 : 1);
                    if (ratio < 1 && ratio < lowest || ratio == 1 && lowest >= 1 && missing > mostMissing && missing > 0) {
                        target = ally;
                        lowest = ratio;
                        mostMissing = missing;
                    }
                }
            } else if (!locked.isEmpty()) {
                target = locked.get(assignedLocks[slot]);
                if (target == null) {
                    UUID next = locks.removeFirst();
                    locks.add(next);
                    assignedLocks[slot] = next;
                    target = locked.get(next);
                }
            } else {
                assignedLocks[slot] = null;
                double nearest = Double.POSITIVE_INFINITY;
                for (LivingEntity candidate : candidates) {
                    double distance = origin.distanceToSqr(candidate.getBoundingBox().getCenter());
                    if (distance >= nearest || !targeting.canAttack(candidate) || !targeting.matches(candidate, targetMask)) continue;
                    target = candidate;
                    nearest = distance;
                }
            }
            boolean approaching = target != null && !targeting.visible(origin, target.getBoundingBox().getCenter());
            flight.command(form, connected && settings.enabled() && !overheated ? target : null);
            String state = failed ? "error" : !settings.enabled() ? "disabled" : overheated ? "overheated" : !connected ? "network" : target == null ? "searching" : approaching ? "approaching" : thermal.readyAt() > now ? "cooldown" : "ready";
            GunAmmunition actual = selected;
            long ready = thermal.readyAt();
            if (state.equals("ready")) {
                // Each shot re-resolves the same public quantum access as the terminal; it does not charge terminal
                // upkeep.
                connection = TerminalQuantumAccess.resolve(player);
                LivingEntity firingTarget = target;
                GunAmmunition paid = connection == null ? null : NetworkShotPayment.pay(platform, connection, form.energy, selected, form == GunForm.MEDICAL,
                        ammunition -> GunShot.fire(targeting, origin, firingTarget, form, ammunition));
                if (paid == null) state = "resources";
                else {
                    if (form != GunForm.MEDICAL) assignedLocks[slot] = null;
                    actual = paid;
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
            guns.add(new GunStatus(slot, form, heat, overheated, selected, actual, target == null ? -1 : target.getId(), state,
                    flight.getId(), origin, target == null ? origin : target.getBoundingBox().getCenter()));
        }
    }

    /** Releases only derived flight entities; the equipped item remains the source of assembly and heat. */
    public void close() {
        for (FloatingGunFlight flight : flights) if (flight != null) flight.discard();
    }

    public void toggleLock() {
        Vec3 from = player.getEyePosition();
        Vec3 direction = player.getViewVector(1);
        GunTargeting targeting = new GunTargeting(player, platform.getOrDefault(DEDataComponents.PLATFORM_FRIENDLY_PROTECTION, true));
        double closest = Double.POSITIVE_INFINITY;
        LivingEntity aimed = null;
        for (var candidate : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(GunTargeting.FIRE_CONTROL_RADIUS))) {
            if (player.distanceToSqr(candidate) > GunTargeting.FIRE_CONTROL_RADIUS_SQUARED) continue;
            if (!targeting.canAttack(candidate)) continue;
            double projection = candidate.getBoundingBox().getCenter().subtract(from).dot(direction);
            if (projection < 0) continue;
            Vec3 to = from.add(direction.scale(projection + candidate.getBoundingBox().getSize()));
            var hit = candidate.getBoundingBox().inflate(0.2).clip(from, to);
            if (hit.isPresent() && hit.get().distanceToSqr(from) < closest) {
                closest = hit.get().distanceToSqr(from);
                aimed = candidate;
            }
        }
        if (aimed != null && !locks.remove(aimed.getUUID())) locks.add(aimed.getUUID());
    }

    public record GunStatus(int slot, GunForm form, double heat, boolean overheated, GunAmmunition selected, GunAmmunition actual, int target, String state,
                            int flight, Vec3 origin, Vec3 destination) {}
}
