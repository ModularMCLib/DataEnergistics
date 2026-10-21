package com.fish_dan_.data_energistics.common.entity.projectile;

import com.fish_dan_.data_energistics.common.data.DEEntities;
import com.fish_dan_.data_energistics.common.item.powered.LightSaberColorData;
import com.fish_dan_.data_energistics.common.item.powered.sanctifier.SanctifierCombat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class SanctifierBladeEntity extends Entity {

    private static final EntityDataAccessor<Integer> CASTER = SynchedEntityData.defineId(SanctifierBladeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(SanctifierBladeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(SanctifierBladeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(SanctifierBladeEntity.class, EntityDataSerializers.LONG);
    private Vec3 launchPosition = Vec3.ZERO;

    public SanctifierBladeEntity(EntityType<? extends SanctifierBladeEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public SanctifierBladeEntity(ServerPlayer caster, ServerPlayer target, ItemStack weapon) {
        this(DEEntities.SANCTIFIER_BLADE.get(), caster.level());
        entityData.set(CASTER, caster.getId());
        entityData.set(TARGET, target.getId());
        entityData.set(COLOR, LightSaberColorData.getSanctifierAnimatedColor(level().getGameTime()));
        entityData.set(START, level().getGameTime());
        launchPosition = caster.getEyePosition().add(caster.getLookAngle().scale(0.6));
        setPos(launchPosition);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(CASTER, -1);
        builder.define(TARGET, -1);
        builder.define(COLOR, 0xFFE359);
        builder.define(START, 0L);
    }

    public int getCasterId() {
        return entityData.get(CASTER);
    }

    public int getTargetId() {
        return entityData.get(TARGET);
    }

    public int getBladeColor() {
        return entityData.get(COLOR);
    }

    public int getPhaseTicks() {
        return (int) (level().getGameTime() - entityData.get(START));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        Entity casterEntity = level().getEntity(getCasterId());
        Entity targetEntity = level().getEntity(getTargetId());
        int phase = getPhaseTicks();
        if (!(casterEntity instanceof ServerPlayer caster) || !(targetEntity instanceof ServerPlayer target) || !caster.isAlive() || caster.isSpectator() || caster.level() != target.level() || phase >= 30 || phase < 26 && (!target.isAlive() || target.isSpectator() || target.distanceToSqr(caster) > 144 || !caster.hasLineOfSight(target))) {
            discard();
            return;
        }
        Vec3 destination = target.getBoundingBox().getCenter();
        setPos(launchPosition.lerp(destination, Math.min(1, phase / 8.0)));
        Vec3 direction = destination.subtract(launchPosition).normalize();
        setYRot((float) Math.toDegrees(Math.atan2(direction.x, direction.z)));
        setXRot((float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance())));
        if (phase == 18) {
            Vec3 approach = target.position().subtract(caster.position()).normalize();
            Vec3 position = target.position().subtract(approach.scale(1.4));
            if (level().hasChunk(target.chunkPosition().x, target.chunkPosition().z) && level().noCollision(caster, caster.getBoundingBox().move(position.subtract(caster.position())))) {
                caster.teleportTo(position.x, position.y, position.z);
            }
            caster.swing(caster.getUsedItemHand(), true);
        }
        if (phase == 26 && target.distanceToSqr(caster) <= 16 && caster.hasLineOfSight(target)) SanctifierCombat.execute(caster, target);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // A restarted server cancels an incomplete execution; it must never resume against a recycled entity id.
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
