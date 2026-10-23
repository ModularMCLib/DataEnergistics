package com.fish_dan_.data_energistics.common.weapon.platform.projectile;

import com.fish_dan_.data_energistics.common.combat.damage.DirectHealthDamage;
import com.fish_dan_.data_energistics.common.data.DEEntities;
import com.fish_dan_.data_energistics.common.weapon.platform.combat.GunImpactEffects;
import com.fish_dan_.data_energistics.common.weapon.platform.combat.GunTargeting;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunAmmunition;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Server-authoritative plasma, ballistic grenade and medical flight; beam modes resolve at firing time. */
public final class FloatingGunProjectile extends Projectile {

    private static final EntityDataAccessor<Integer> FORM = SynchedEntityData.defineId(FloatingGunProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> AMMUNITION = SynchedEntityData.defineId(FloatingGunProjectile.class, EntityDataSerializers.INT);
    private final ObjectOpenHashSet<UUID> pierced = new ObjectOpenHashSet<>();
    private @Nullable UUID targetId;
    private boolean protectFriendlies;
    private boolean anchored;
    private int remaining = 100;

    public FloatingGunProjectile(EntityType<? extends FloatingGunProjectile> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public FloatingGunProjectile(ServerLevel level, GunTargeting targeting, Vec3 origin, LivingEntity target, GunForm form, GunAmmunition ammunition) {
        this(DEEntities.FLOATING_GUN_PROJECTILE.get(), level);
        setOwner(targeting.owner());
        setPos(origin);
        targetId = target.getUUID();
        protectFriendlies = targeting.protectFriendlies();
        entityData.set(FORM, form.ordinal());
        entityData.set(AMMUNITION, ammunition.ordinal());
        Vec3 delta = target.getBoundingBox().getCenter().subtract(origin);
        if (form == GunForm.GRENADE) {
            double ticks = Math.max(8, delta.horizontalDistance() / 0.8);
            setDeltaMovement(delta.x / ticks, delta.y / ticks + 0.025 * (ticks - 1), delta.z / ticks);
        } else setDeltaMovement(delta.normalize().scale(form == GunForm.MEDICAL ? 1.4 : 0.7));
    }

    public GunForm form() {
        return GunForm.values()[entityData.get(FORM)];
    }

    public GunAmmunition ammunition() {
        return GunAmmunition.values()[entityData.get(AMMUNITION)];
    }

    public boolean protectsFriendlies() {
        return protectFriendlies;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(FORM, GunForm.PLASMA.ordinal());
        builder.define(AMMUNITION, GunAmmunition.AE.ordinal());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            setPos(position().add(getDeltaMovement()));
            return;
        }
        ServerLevel level = (ServerLevel) level();
        if (--remaining <= 0 || !(getOwner() instanceof ServerPlayer owner) || !owner.isAlive() || owner.level() != level) {
            discard();
            return;
        }
        GunForm form = form();
        GunTargeting targeting = new GunTargeting(owner, protectFriendlies);
        if (anchored) return;
        Vec3 from = position();
        if (form == GunForm.MEDICAL) {
            Entity target = targetId == null ? null : level.getEntity(targetId);
            if (!(target instanceof Player player) || !player.isAlive() || !targeting.allied(player)) {
                discard();
                return;
            }
            setDeltaMovement(target.getBoundingBox().getCenter().subtract(from).normalize().scale(1.4));
            if (target.getBoundingBox().inflate(0.15).clip(from, from.add(getDeltaMovement())).isPresent() || target.getBoundingBox().inflate(0.15).contains(from)) {
                if (targeting.visible(from, target.getBoundingBox().getCenter())) GunImpactEffects.heal(targeting, player);
                discard();
                return;
            }
        }
        Vec3 to = from.add(getDeltaMovement());
        var block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (block.getType() != HitResult.Type.MISS) to = block.getLocation();
        var hit = form == GunForm.MEDICAL ? null : ProjectileUtil.getEntityHitResult(level, this, from, to,
                getBoundingBox().expandTowards(getDeltaMovement()).inflate(1), entity -> {
                    LivingEntity target = DirectHealthDamage.target(entity);
                    return target != null && !pierced.contains(target.getUUID()) && targeting.canAttack(target);
                });
        if (hit != null) {
            setPos(hit.getLocation());
            if (form == GunForm.GRENADE) explode(level, targeting);
            else {
                LivingEntity target = DirectHealthDamage.target(hit.getEntity());
                if (target != null) {
                    pierced.add(target.getUUID());
                    GunImpactEffects.hit(targeting, target, form, ammunition(), form.damage);
                }
                if (ammunition() == GunAmmunition.BINARY) setPos(to);
                else {
                    anchored = true;
                    setDeltaMovement(Vec3.ZERO);
                }
            }
        } else if (block.getType() != HitResult.Type.MISS) {
            setPos(to);
            if (form == GunForm.GRENADE) explode(level, targeting);
            else if (form == GunForm.PLASMA) {
                anchored = true;
                setDeltaMovement(Vec3.ZERO);
            } else discard();
        } else {
            setPos(to);
            if (form == GunForm.GRENADE) setDeltaMovement(getDeltaMovement().add(0, -0.05, 0));
        }
        level.sendParticles(form == GunForm.MEDICAL ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.ELECTRIC_SPARK,
                getX(), getY(), getZ(), 1, 0, 0, 0, 0);
    }

    private void explode(ServerLevel level, GunTargeting targeting) {
        Vec3 center = position();
        level.explode(this, damageSources().explosion(this, targeting.owner()), new ExplosionDamageCalculator() {

            private final ObjectOpenHashSet<UUID> affected = new ObjectOpenHashSet<>();

            @Override
            public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
                LivingEntity target = DirectHealthDamage.target(entity);
                if (target != null && targeting.canAttack(target) && affected.add(target.getUUID())) {
                    double distance = target.getBoundingBox().getCenter().distanceTo(center);
                    if (distance < 3 && Explosion.getSeenPercent(center, target) > 0)
                        GunImpactEffects.hit(targeting, target, GunForm.GRENADE, ammunition(), (float) (14 * (1 - distance / 3)));
                }
                return false;
            }

            @Override
            public float getKnockbackMultiplier(Entity entity) {
                LivingEntity target = DirectHealthDamage.target(entity);
                return target != null && targeting.canAttack(target) && target.distanceToSqr(center) < 9 ? 1 : 0;
            }
        }, getX(), getY(), getZ(), 3, false, Level.ExplosionInteraction.TNT);
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Form", form().ordinal());
        tag.putInt("Ammunition", ammunition().ordinal());
        tag.putBoolean("Protect", protectFriendlies);
        tag.putBoolean("Anchored", anchored);
        tag.putInt("Remaining", remaining);
        if (targetId != null) tag.putUUID("Target", targetId);
        var hits = new ListTag();
        for (UUID id : pierced) hits.add(NbtUtils.createUUID(id));
        tag.put("Pierced", hits);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        int form = tag.getInt("Form"), ammo = tag.getInt("Ammunition");
        remaining = tag.getInt("Remaining");
        if (form < 0 || form >= GunForm.values().length || ammo < 0 || ammo >= GunAmmunition.values().length || remaining < 0 || remaining > 100)
            throw new IllegalArgumentException("Invalid floating gun projectile");
        entityData.set(FORM, form);
        entityData.set(AMMUNITION, ammo);
        protectFriendlies = tag.getBoolean("Protect");
        anchored = tag.getBoolean("Anchored");
        targetId = tag.hasUUID("Target") ? tag.getUUID("Target") : null;
        pierced.clear();
        for (var hit : tag.getList("Pierced", Tag.TAG_INT_ARRAY)) pierced.add(NbtUtils.loadUUID(hit));
    }
}
