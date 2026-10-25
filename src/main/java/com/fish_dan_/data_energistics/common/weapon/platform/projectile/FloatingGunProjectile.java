package com.fish_dan_.data_energistics.common.weapon.platform.projectile;

import com.fish_dan_.data_energistics.common.data.DEEntities;
import com.fish_dan_.data_energistics.common.entity.projectile.cannon.WeaponDamage;
import com.fish_dan_.data_energistics.common.weapon.platform.combat.GrenadeTrajectory;
import com.fish_dan_.data_energistics.common.weapon.platform.combat.GunImpactEffects;
import com.fish_dan_.data_energistics.common.weapon.platform.combat.GunRayTrace;
import com.fish_dan_.data_energistics.common.weapon.platform.combat.GunTargeting;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunAmmunition;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
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
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/** Server-authoritative plasma, ballistic grenade and medical flight; beam modes resolve at firing time. */
public final class FloatingGunProjectile extends Projectile {

    private static final EntityDataAccessor<Integer> FORM = SynchedEntityData.defineId(FloatingGunProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> AMMUNITION = SynchedEntityData.defineId(FloatingGunProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> BEAM = SynchedEntityData.defineId(FloatingGunProjectile.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(FloatingGunProjectile.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final Codec<Optional<UUID>> OWNER_CODEC = UUIDUtil.CODEC.optionalFieldOf("Owner").codec();
    private final ObjectOpenHashSet<UUID> pierced = new ObjectOpenHashSet<>();
    private @Nullable UUID targetId;
    private boolean protectFriendlies;
    private boolean anchored;
    private int remaining = 200;

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
        remaining = form == GunForm.PLASMA ? 100 : form == GunForm.SNIPER ? 3 : 200;
        Vec3 delta = target.getBoundingBox().getCenter().subtract(origin);
        if (form == GunForm.GRENADE) setDeltaMovement(GrenadeTrajectory.aimed(origin, target.getBoundingBox().getCenter()).velocity());
        else setDeltaMovement(delta.normalize().scale(form == GunForm.MEDICAL ? 1.4 : form == GunForm.NORMAL ? 3 : 0.7));
    }

    public GunForm form() {
        return GunForm.values()[entityData.get(FORM)];
    }

    public GunAmmunition ammunition() {
        return GunAmmunition.values()[entityData.get(AMMUNITION)];
    }

    public Vec3 beam() {
        return new Vec3(entityData.get(BEAM));
    }

    public void beam(Vec3 direction) {
        entityData.set(BEAM, direction.toVector3f());
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return form() == GunForm.SNIPER ? getBoundingBox().expandTowards(beam()).inflate(0.2) : super.getBoundingBoxForCulling();
    }

    public boolean protectsFriendlies() {
        return protectFriendlies;
    }

    @Override
    public void setOwner(@Nullable Entity owner) {
        super.setOwner(owner);
        if (owner != null) entityData.set(OWNER, Optional.of(owner.getUUID()));
    }

    public @Nullable UUID ownerId() {
        return entityData.get(OWNER).orElse(null);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(FORM, GunForm.PLASMA.ordinal());
        builder.define(AMMUNITION, GunAmmunition.AE.ordinal());
        builder.define(BEAM, new Vector3f());
        builder.define(OWNER, Optional.empty());
    }

    @Override
    public void tick() {
        super.tick();
        if (form() == GunForm.SNIPER) {
            if (!level().isClientSide && --remaining <= 0) discard();
            return;
        }
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
        Player support = null;
        if (form == GunForm.MEDICAL) {
            Entity target = targetId == null ? null : level.getEntity(targetId);
            if (!(target instanceof Player player) || !player.isAlive() || !targeting.allied(player)) {
                discard();
                return;
            }
            support = player;
            setDeltaMovement(target.getBoundingBox().getCenter().subtract(from).normalize().scale(1.4));
        }
        Vec3 to = from.add(getDeltaMovement());
        var block = GunRayTrace.clip(level, this, from, to);
        if (block.getType() != HitResult.Type.MISS) to = block.getLocation();
        if (support != null) {
            var bounds = support.getBoundingBox().inflate(0.15);
            if (bounds.contains(from) || bounds.clip(from, to).isPresent()) {
                GunImpactEffects.heal(targeting, support);
                discard();
                return;
            }
        } else {
            for (GunRayTrace.Hit hit : GunRayTrace.collect(level, this, from, to, 0.15,
                    target -> !pierced.contains(target.getUUID()) && targeting.canAttack(target))) {
                setPos(hit.position());
                if (form == GunForm.GRENADE) {
                    explode(level, targeting);
                    return;
                }
                pierced.add(hit.target().getUUID());
                GunImpactEffects.hit(targeting, hit.target(), form, ammunition(), form.damage);
                if (ammunition() != GunAmmunition.BINARY) {
                    if (form == GunForm.PLASMA) {
                        anchored = true;
                        setDeltaMovement(Vec3.ZERO);
                    } else discard();
                    return;
                }
            }
        }
        setPos(to);
        if (block.getType() != HitResult.Type.MISS) {
            if (form == GunForm.GRENADE) {
                explode(level, targeting);
                return;
            } else if (form == GunForm.PLASMA) {
                anchored = true;
                setDeltaMovement(Vec3.ZERO);
            } else discard();
        } else if (form == GunForm.GRENADE) setDeltaMovement(getDeltaMovement().add(0, -GrenadeTrajectory.GRAVITY, 0));
        level.sendParticles(form == GunForm.MEDICAL ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.ELECTRIC_SPARK,
                getX(), getY(), getZ(), 1, 0, 0, 0, 0);
    }

    private void explode(ServerLevel level, GunTargeting targeting) {
        Vec3 center = position();
        level.explode(this, damageSources().explosion(this, targeting.owner()), new ExplosionDamageCalculator() {

            private final ObjectOpenHashSet<UUID> affected = new ObjectOpenHashSet<>();

            @Override
            public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
                LivingEntity target = WeaponDamage.target(entity);
                if (target != null && targeting.canAttack(target) && affected.add(target.getUUID())) {
                    double distance = target.getBoundingBox().getCenter().distanceTo(center);
                    if (distance < 3 && Explosion.getSeenPercent(center, target) > 0)
                        GunImpactEffects.hit(targeting, target, GunForm.GRENADE, ammunition(), (float) (14 * (1 - distance / 3)));
                }
                return false;
            }

            @Override
            public float getKnockbackMultiplier(Entity entity) {
                LivingEntity target = WeaponDamage.target(entity);
                return target != null && targeting.canAttack(target) && target.distanceToSqr(center) < 9 ? 1 : 0;
            }
        }, getX(), getY(), getZ(), 3, false, Level.ExplosionInteraction.NONE);
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Payload", GunProjectileState.CODEC.encodeStart(NbtOps.INSTANCE,
                new GunProjectileState(form().ordinal(), ammunition().ordinal(), protectFriendlies, anchored, remaining, Optional.ofNullable(targetId), pierced, beam())).getOrThrow());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(OWNER, OWNER_CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow());
        var state = GunProjectileState.CODEC.parse(NbtOps.INSTANCE, tag.get("Payload")).getOrThrow();
        remaining = state.remaining();
        entityData.set(FORM, state.form());
        entityData.set(AMMUNITION, state.ammunition());
        protectFriendlies = state.protect();
        anchored = state.anchored();
        targetId = state.target().orElse(null);
        pierced.clear();
        pierced.addAll(state.pierced());
        entityData.set(BEAM, state.beam().toVector3f());
    }
}
