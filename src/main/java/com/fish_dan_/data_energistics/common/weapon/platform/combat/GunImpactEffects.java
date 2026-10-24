package com.fish_dan_.data_energistics.common.weapon.platform.combat;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.combat.damage.AttributedPlayerDamageSource;
import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.data.DEMobEffects;
import com.fish_dan_.data_energistics.common.effect.RadixRedundancyEffectLogic;
import com.fish_dan_.data_energistics.common.entity.projectile.cannon.WeaponDamage;
import com.fish_dan_.data_energistics.common.research.team.ResearchTeams;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunAmmunition;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.joml.Vector3f;

import java.util.Optional;
import java.util.UUID;

/** Persisted on-hit effects; only entities with an effect marker enter the tick work. */
public final class GunImpactEffects {

    private static final ObjectList<SupportEffect> SUPPORT = ObjectList.of(
            new SupportEffect(MobEffects.REGENERATION, 1), new SupportEffect(MobEffects.MOVEMENT_SPEED, 0),
            new SupportEffect(MobEffects.DAMAGE_RESISTANCE, 0), new SupportEffect(MobEffects.DAMAGE_BOOST, 1),
            new SupportEffect(MobEffects.DIG_SPEED, 0), new SupportEffect(DEMobEffects.DATA_OVERLOAD, 0));

    public static int missingSupport(Player player) {
        int missing = 0;
        for (SupportEffect support : SUPPORT) {
            MobEffectInstance effect = player.getEffect(support.effect());
            if (effect == null || !effect.isInfiniteDuration() && effect.getDuration() < 40) missing++;
        }
        return missing;
    }

    public static void hit(GunTargeting targeting, LivingEntity target, GunForm form, GunAmmunition ammunition, float damage) {
        if (!targeting.canAttack(target)) return;
        float amount = ammunition == GunAmmunition.BINARY ? 1 : damage;
        if (!WeaponDamage.hurt(target, WeaponDamage.source(target, targeting.owner()), amount) || !target.isAlive()) return;
        switch (ammunition) {
            case BINARY -> accumulate(targeting, target);
            case DATA_FLOW -> RadixRedundancyEffectLogic.applyCannonHit(target, 200, targeting.owner(), amount, false, 1);
            case ECHO -> {
                GunEffectState state = target.getData(DEAttachments.GUN_EFFECTS);
                var shots = new ObjectArrayList<>(state.echoes());
                shots.add(new GunEffectState.Echo(targeting.owner().getUUID(), 60, form, targeting.protectFriendlies()));
                target.setData(DEAttachments.GUN_EFFECTS, new GunEffectState(state.stellar(), shots));
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
                    trail((ServerLevel) target.level(), origin, nearest.getBoundingBox().getCenter(), GunAmmunition.STELLAR.color, 0.6f);
                    if (WeaponDamage.hurt(nearest, WeaponDamage.source(nearest, targeting.owner()), damage) && nearest.isAlive()) afflict(targeting, nearest);
                    previous = nearest;
                }
            }
            default -> {}
        }
    }

    private static void afflict(GunTargeting targeting, LivingEntity target) {
        GunEffectState state = target.getData(DEAttachments.GUN_EFFECTS);
        var stellar = new GunEffectState.Stellar(targeting.owner().getUUID(), targeting.owner().getGameProfile().getName(), targeting.protectFriendlies(), 0);
        target.setData(DEAttachments.GUN_EFFECTS, new GunEffectState(Optional.of(stellar), state.echoes()));
        target.setRemainingFireTicks(40);
        target.setTicksFrozen(target.getTicksRequiredToFreeze() + 2);
        target.addEffect(new MobEffectInstance(MobEffects.POISON, -1));
        target.addEffect(new MobEffectInstance(MobEffects.WITHER, -1));
    }

    private static void accumulate(GunTargeting targeting, LivingEntity initial) {
        int layers = Math.min(32, RadixRedundancyEffectLogic.layers(initial) + 1);
        if (!initial.addEffect(new MobEffectInstance(DEMobEffects.RADIX_REDUNDANCY, MobEffectInstance.INFINITE_DURATION, layers - 1, false, true, true))) return;
        if (layers < 32) return;
        var subjects = new ObjectArrayList<LivingEntity>();
        var impacts = new ObjectArrayList<BinaryImpact>();
        var visited = new ObjectOpenHashSet<UUID>();
        subjects.add(initial);
        visited.add(initial.getUUID());
        for (int index = 0; index < subjects.size(); index++) {
            LivingEntity subject = subjects.get(index);
            int count = RadixRedundancyEffectLogic.layers(subject);
            impacts.add(new BinaryImpact(subject, subject.getHealth() * (count / 32.0) * 0.75));
            Vec3 center = subject.getBoundingBox().getCenter();
            AABB area = new AABB(center.subtract(1.5, 1.5, 1.5), center.add(1.5, 1.5, 1.5));
            for (Entity candidate : subject.level().getEntities(subject, area)) {
                LivingEntity nearby = WeaponDamage.target(candidate);
                if (nearby == null || RadixRedundancyEffectLogic.layers(nearby) == 0 || visited.contains(nearby.getUUID()) || !targeting.canAttack(nearby)) continue;
                visited.add(nearby.getUUID());
                subjects.add(nearby);
            }
        }
        for (BinaryImpact impact : impacts) impact.target.removeEffect(DEMobEffects.RADIX_REDUNDANCY);
        for (BinaryImpact impact : impacts) {
            ((ServerLevel) impact.target.level()).sendParticles(ParticleTypes.EXPLOSION, impact.target.getX(), impact.target.getY(0.5), impact.target.getZ(), 1, 0, 0, 0, 0);
            WeaponDamage.hurt(impact.target, WeaponDamage.source(impact.target, targeting.owner()), impact.damage);
        }
    }

    public static void heal(GunTargeting targeting, Player player) {
        if (!player.isAlive() || !targeting.allied(player)) return;
        player.heal(1);
        for (SupportEffect support : SUPPORT) player.addEffect(new MobEffectInstance(support.effect(), 60, support.amplifier()));
    }

    public static void trail(ServerLevel level, Vec3 from, Vec3 to, int color, float size) {
        Vec3 delta = to.subtract(from);
        int steps = Math.clamp((int) (delta.length() * 4), 1, 128);
        DustParticleOptions particle = new DustParticleOptions(new Vector3f((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f), size);
        for (int step = 0; step <= steps; step++) {
            Vec3 point = from.add(delta.scale((double) step / steps));
            level.sendParticles(particle, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
    }

    @SubscribeEvent
    public void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity target) || !(target.level() instanceof ServerLevel level)) return;
        if (!target.hasData(DEAttachments.GUN_EFFECTS)) return;
        GunEffectState state = target.getData(DEAttachments.GUN_EFFECTS);
        try {
            tickEffects(target, level, state);
        } catch (RuntimeException failure) {
            target.removeData(DEAttachments.GUN_EFFECTS);
            if (state.stellar().isPresent()) {
                target.clearFire();
                target.setTicksFrozen(0);
                MobEffectInstance poison = target.getEffect(MobEffects.POISON), wither = target.getEffect(MobEffects.WITHER);
                if (poison != null && poison.isInfiniteDuration()) target.removeEffect(MobEffects.POISON);
                if (wither != null && wither.isInfiniteDuration()) target.removeEffect(MobEffects.WITHER);
            }
            Data_Energistics.LOGGER.error("Persistent floating gun effects disabled for entity {} at {} in {}",
                    target.getUUID(), target.blockPosition(), level.dimension().location(), failure);
        }
    }

    private static void tickEffects(LivingEntity target, ServerLevel level, GunEffectState state) {
        if (!target.isAlive()) {
            target.removeData(DEAttachments.GUN_EFFECTS);
            return;
        }
        ObjectList<GunEffectState.Echo> pending = state.echoes().isEmpty() ? ObjectList.of() : new ObjectArrayList<>(state.echoes().size());
        ObjectList<GunEffectState.Echo> ready = state.echoes().isEmpty() ? ObjectList.of() : new ObjectArrayList<>();
        for (GunEffectState.Echo shot : state.echoes()) {
            if (shot.remaining() > 1) pending.add(new GunEffectState.Echo(shot.owner(), shot.remaining() - 1, shot.form(), shot.protect()));
            else ready.add(shot);
        }
        var stellar = state.stellar().map(value -> new GunEffectState.Stellar(value.owner(), value.ownerName(), value.protect(), (value.tick() + 1) % 200));
        GunEffectState updated = new GunEffectState(stellar, pending);
        // Publish before damage callbacks: a reboot replaces this value and stops the old effect pass.
        target.setData(DEAttachments.GUN_EFFECTS, updated);
        for (int index = ready.size() - 1; index >= 0; index--) {
            GunEffectState.Echo shot = ready.get(index);
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(shot.owner());
            if (owner == null || !owner.isAlive() || owner.level() != level) continue;
            GunTargeting targeting = new GunTargeting(owner, shot.protect());
            if (!targeting.canAttack(target)) continue;
            Vec3 center = target.getBoundingBox().getCenter();
            Vec3 direction = owner.position().subtract(center).multiply(1, 0, 1).normalize().scale(4);
            if (direction.lengthSqr() < 1) direction = new Vec3(4, 0, 0);
            GunShot.fire(targeting, center.add(direction).add(0, 4, 0), target, shot.form(), GunAmmunition.AE);
            if (!target.hasData(DEAttachments.GUN_EFFECTS) || target.getData(DEAttachments.GUN_EFFECTS) != updated) return;
        }
        if (stellar.isPresent()) {
            GunEffectState.Stellar marker = stellar.get();
            target.setRemainingFireTicks(40);
            target.setTicksFrozen(target.getTicksRequiredToFreeze() + 2);
            if (!target.hasEffect(MobEffects.POISON)) target.addEffect(new MobEffectInstance(MobEffects.POISON, -1));
            if (!target.hasEffect(MobEffects.WITHER)) target.addEffect(new MobEffectInstance(MobEffects.WITHER, -1));
            if (!afflictionAllowed(level, target, marker)) return;
            int tick = marker.tick();
            int damage = (tick % 20 == 0 ? 1 : 0) + (tick % 40 == 0 ? 3 : 0) + (tick % 25 == 0 && target.getHealth() > 1 ? 1 : 0);
            if (damage > 0) WeaponDamage.hurt(target, new AttributedPlayerDamageSource(level, marker.owner()), damage);
        } else if (pending.isEmpty()) target.removeData(DEAttachments.GUN_EFFECTS);
    }

    private static boolean afflictionAllowed(ServerLevel level, LivingEntity target, GunEffectState.Stellar marker) {
        UUID ownerId = marker.owner();
        if (target.getUUID().equals(ownerId)) return false;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
        if (owner != null && owner.level() == level) return new GunTargeting(owner, marker.protect()).canAttack(target);
        boolean protect = marker.protect();
        if (target instanceof ServerPlayer player) {
            if (player.isCreative() || player.isSpectator() || player.hasPermissions(2) || !level.getServer().isPvpAllowed()) return false;
            String name = owner == null ? marker.ownerName() : owner.getGameProfile().getName();
            // Offline damage still requires a known scoreboard identity.
            if (name.isEmpty()) return false;
            var team = level.getScoreboard().getPlayersTeam(name);
            if (team != null && team.isAlliedTo(player.getTeam()) && !team.isAllowFriendlyFire()) return false;
            if (protect && ResearchTeams.shareTeam(player, ownerId, name)) return false;
        }
        return !protect || !(target instanceof TamableAnimal animal && ownerId.equals(animal.getOwnerUUID()));
    }

    @SubscribeEvent
    public void suppressNativeStellarDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (!target.hasData(DEAttachments.GUN_EFFECTS) || target.getData(DEAttachments.GUN_EFFECTS).stellar().isEmpty()) return;
        var type = event.getSource();
        // Only the cached environmental sources used by these vanilla effect ticks are replaced.
        // Indirect magic from potions and other attackers keeps its own damage path.
        var sources = event.getEntity().damageSources();
        if (type == sources.onFire() || type == sources.freeze() || type == sources.magic() || type == sources.wither()) event.setCanceled(true);
    }

    private record BinaryImpact(LivingEntity target, double damage) {}

    private record SupportEffect(Holder<MobEffect> effect, int amplifier) {}
}
