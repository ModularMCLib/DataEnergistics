package com.fish_dan_.data_energistics.common.weapon.platform.combat;

import com.fish_dan_.data_energistics.common.combat.damage.DirectHealthDamage;
import com.fish_dan_.data_energistics.common.data.DEMobEffects;
import com.fish_dan_.data_energistics.common.effect.RadixLossEffectLogic;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunAmmunition;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.joml.Vector3f;

import java.util.UUID;

/** Persisted on-hit effects; only entities with an effect marker enter the tick work. */
public final class GunImpactEffects {

    public static final String BINARY = "data_energistics:binary_layers";
    private static final String STELLAR = "data_energistics:stellar_affliction";
    private static final String ECHO = "data_energistics:echo_shots";

    public static void hit(GunTargeting targeting, LivingEntity target, GunForm form, GunAmmunition ammunition, float damage) {
        if (!targeting.canAttack(target)) return;
        float amount = ammunition == GunAmmunition.BINARY ? 1 : damage;
        if (!DirectHealthDamage.hurt(target, DirectHealthDamage.source(target, targeting.owner()), amount) || !target.isAlive()) return;
        switch (ammunition) {
            case BINARY -> accumulate(targeting, target);
            case DATA_FLOW -> RadixLossEffectLogic.applyCannonHit(target, 200, targeting.owner(), amount, false, 1);
            case ECHO -> {
                var data = target.getPersistentData();
                ListTag shots = data.getList(ECHO, Tag.TAG_COMPOUND);
                CompoundTag shot = new CompoundTag();
                shot.putUUID("Owner", targeting.owner().getUUID());
                shot.putInt("Remaining", 60);
                shot.putInt("Form", form.ordinal());
                shot.putBoolean("Protect", targeting.protectFriendlies());
                shots.add(shot);
                data.put(ECHO, shots);
            }
            case STELLAR -> {
                afflict(targeting, target);
                var visited = new ObjectOpenHashSet<UUID>();
                visited.add(target.getUUID());
                LivingEntity previous = target;
                for (int hop = 0; hop < 3; hop++) {
                    LivingEntity nearest = null;
                    double distance = 144;
                    Vec3 origin = previous.getBoundingBox().getCenter();
                    for (LivingEntity candidate : previous.level().getEntitiesOfClass(LivingEntity.class, previous.getBoundingBox().inflate(12))) {
                        double candidateDistance = candidate.distanceToSqr(previous);
                        if (candidateDistance >= distance || visited.contains(candidate.getUUID()) || !targeting.canAttack(candidate) || !targeting.visible(origin, candidate.getBoundingBox().getCenter())) continue;
                        nearest = candidate;
                        distance = candidateDistance;
                    }
                    if (nearest == null) break;
                    visited.add(nearest.getUUID());
                    trail((ServerLevel) target.level(), origin, nearest.getBoundingBox().getCenter(), GunAmmunition.STELLAR.color);
                    if (DirectHealthDamage.hurt(nearest, DirectHealthDamage.source(nearest, targeting.owner()), damage) && nearest.isAlive()) afflict(targeting, nearest);
                    previous = nearest;
                }
            }
            default -> {}
        }
    }

    private static void afflict(GunTargeting targeting, LivingEntity target) {
        CompoundTag marker = new CompoundTag();
        marker.putUUID("Owner", targeting.owner().getUUID());
        marker.putBoolean("Protect", targeting.protectFriendlies());
        target.getPersistentData().put(STELLAR, marker);
        target.setRemainingFireTicks(40);
        target.setTicksFrozen(target.getTicksRequiredToFreeze() + 2);
        target.addEffect(new MobEffectInstance(MobEffects.POISON, -1));
        target.addEffect(new MobEffectInstance(MobEffects.WITHER, -1));
    }

    private static void accumulate(GunTargeting targeting, LivingEntity initial) {
        int layers = initial.getPersistentData().getInt(BINARY) + 1;
        if (layers < 1 || layers > 32) throw new IllegalArgumentException("Invalid persisted binary layers");
        initial.getPersistentData().putInt(BINARY, layers);
        if (layers != 32) return;
        var subjects = new ObjectArrayList<LivingEntity>();
        var impacts = new ObjectArrayList<BinaryImpact>();
        var visited = new ObjectOpenHashSet<UUID>();
        subjects.add(initial);
        visited.add(initial.getUUID());
        for (int index = 0; index < subjects.size(); index++) {
            LivingEntity subject = subjects.get(index);
            int count = subject.getPersistentData().getInt(BINARY);
            if (count < 1 || count > 32) throw new IllegalArgumentException("Invalid binary chain layers");
            impacts.add(new BinaryImpact(subject, subject.getHealth() * (count / 32.0) * 0.75));
            Vec3 center = subject.getBoundingBox().getCenter();
            AABB area = new AABB(center.subtract(1.5, 1.5, 1.5), center.add(1.5, 1.5, 1.5));
            for (LivingEntity nearby : subject.level().getEntitiesOfClass(LivingEntity.class, area)) {
                if (nearby.getPersistentData().getInt(BINARY) == 0 || visited.contains(nearby.getUUID()) || !targeting.canAttack(nearby)) continue;
                visited.add(nearby.getUUID());
                subjects.add(nearby);
            }
        }
        for (BinaryImpact impact : impacts) impact.target.getPersistentData().remove(BINARY);
        for (BinaryImpact impact : impacts) {
            ((ServerLevel) impact.target.level()).sendParticles(ParticleTypes.EXPLOSION, impact.target.getX(), impact.target.getY(0.5), impact.target.getZ(), 1, 0, 0, 0, 0);
            DirectHealthDamage.hurt(impact.target, DirectHealthDamage.source(impact.target, targeting.owner()), impact.damage);
        }
    }

    public static void heal(GunTargeting targeting, Player player) {
        if (!player.isAlive() || !targeting.allied(player)) return;
        player.heal(1);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20, 1));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20, 0));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20, 0));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20, 1));
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 20, 0));
        player.addEffect(new MobEffectInstance(DEMobEffects.DATA_OVERLOAD, 20, 0));
    }

    public static void trail(ServerLevel level, Vec3 from, Vec3 to, int color) {
        Vec3 delta = to.subtract(from);
        int steps = Math.min(128, Math.max(1, (int) (delta.length() * 4)));
        DustParticleOptions particle = new DustParticleOptions(new Vector3f((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f), 0.6f);
        for (int step = 0; step <= steps; step++) {
            Vec3 point = from.add(delta.scale((double) step / steps));
            level.sendParticles(particle, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
    }

    @SubscribeEvent
    public void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity target) || !(target.level() instanceof ServerLevel level)) return;
        var data = target.getPersistentData();
        if (!target.isAlive()) {
            data.remove(BINARY);
            data.remove(STELLAR);
            data.remove(ECHO);
            return;
        }
        if (data.contains(ECHO, Tag.TAG_LIST)) {
            ListTag shots = data.getList(ECHO, Tag.TAG_COMPOUND);
            for (int index = shots.size() - 1; index >= 0; index--) {
                CompoundTag shot = shots.getCompound(index);
                int remaining = shot.getInt("Remaining") - 1;
                if (remaining > 0) {
                    shot.putInt("Remaining", remaining);
                    continue;
                }
                shots.remove(index);
                int form = shot.getInt("Form");
                if (form < 0 || form >= GunForm.values().length || !shot.hasUUID("Owner")) throw new IllegalArgumentException("Invalid persisted echo shot");
                ServerPlayer owner = level.getServer().getPlayerList().getPlayer(shot.getUUID("Owner"));
                if (owner == null || !owner.isAlive() || owner.level() != level) continue;
                GunTargeting targeting = new GunTargeting(owner, shot.getBoolean("Protect"));
                if (!targeting.canAttack(target)) continue;
                Vec3 center = target.getBoundingBox().getCenter();
                Vec3 direction = owner.position().subtract(center).multiply(1, 0, 1).normalize().scale(4);
                if (direction.lengthSqr() < 1) direction = new Vec3(4, 0, 0);
                GunShot.fire(targeting, center.add(direction).add(0, 4, 0), target, GunForm.values()[form], GunAmmunition.AE);
            }
            if (shots.isEmpty()) data.remove(ECHO);
        }
        if (data.contains(STELLAR, Tag.TAG_COMPOUND)) {
            CompoundTag marker = data.getCompound(STELLAR);
            target.setRemainingFireTicks(40);
            target.setTicksFrozen(target.getTicksRequiredToFreeze() + 2);
            if (!target.hasEffect(MobEffects.POISON)) target.addEffect(new MobEffectInstance(MobEffects.POISON, -1));
            if (!target.hasEffect(MobEffects.WITHER)) target.addEffect(new MobEffectInstance(MobEffects.WITHER, -1));
            int tick = marker.getInt("Tick") + 1;
            marker.putInt("Tick", tick % 200);
            Entity owner = marker.hasUUID("Owner") ? level.getServer().getPlayerList().getPlayer(marker.getUUID("Owner")) : null;
            if (!(owner instanceof ServerPlayer player) || !new GunTargeting(player, marker.getBoolean("Protect")).canAttack(target)) return;
            int damage = (tick % 20 == 0 ? 1 : 0) + (tick % 40 == 0 ? 2 : 0) + (tick % 25 == 0 && target.getHealth() > 1 ? 1 : 0);
            if (damage > 0) DirectHealthDamage.hurt(target, DirectHealthDamage.source(target, owner), damage);
        }
    }

    @SubscribeEvent
    public void suppressNativeStellarDamage(LivingIncomingDamageEvent event) {
        if (!event.getEntity().getPersistentData().contains(STELLAR)) return;
        var type = event.getSource();
        // Only the cached environmental sources used by these vanilla effect ticks are replaced.
        // Indirect magic from potions and other attackers keeps its own damage path.
        var sources = event.getEntity().damageSources();
        if (type == sources.onFire() || type == sources.freeze() || type == sources.magic() || type == sources.wither()) event.setCanceled(true);
    }

    private record BinaryImpact(LivingEntity target, double damage) {}
}
