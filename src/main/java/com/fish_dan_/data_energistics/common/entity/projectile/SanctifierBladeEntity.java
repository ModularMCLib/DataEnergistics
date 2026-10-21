package com.fish_dan_.data_energistics.common.entity.projectile;

import com.fish_dan_.data_energistics.common.data.DEEntities;
import com.fish_dan_.data_energistics.common.item.powered.LightSaberColorData;
import com.fish_dan_.data_energistics.common.item.powered.PoweredSwordItem;
import com.fish_dan_.data_energistics.common.item.powered.sanctifier.SanctifierBladeRecovery;
import com.fish_dan_.data_energistics.common.item.powered.sanctifier.SanctifierCombat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** A secondary blade stays at its target or landing point until its caster approaches to recover it. */
public final class SanctifierBladeEntity extends Entity {

    private static final EntityDataAccessor<Integer> CASTER = SynchedEntityData.defineId(SanctifierBladeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(SanctifierBladeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(SanctifierBladeEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> EXECUTING = SynchedEntityData.defineId(SanctifierBladeEntity.class, EntityDataSerializers.BOOLEAN);
    private @Nullable UUID ownerId;
    private @Nullable UUID targetId;
    private Vec3 launchPosition = Vec3.ZERO;
    private Vec3 direction = new Vec3(0, 0, 1);
    private float damage;
    private float attachedYawOffset;
    private boolean embedded;
    private boolean strikeResolved;
    private int missingTargetTicks;

    public SanctifierBladeEntity(EntityType<? extends SanctifierBladeEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public SanctifierBladeEntity(ServerPlayer caster, @Nullable LivingEntity target, ItemStack weapon) {
        this(DEEntities.SANCTIFIER_BLADE.get(), caster.level());
        ownerId = caster.getUUID();
        targetId = target == null ? null : target.getUUID();
        damage = (float) (PoweredSwordItem.getPanelAttackDamage(weapon) * 0.5);
        entityData.set(CASTER, caster.getId());
        entityData.set(COLOR, LightSaberColorData.getSanctifierAnimatedColor(level().getGameTime()));
        entityData.set(START, level().getGameTime());
        entityData.set(EXECUTING, target instanceof ServerPlayer);
        Vec3 left = new Vec3(caster.getLookAngle().z, 0, -caster.getLookAngle().x).normalize().scale(0.35);
        launchPosition = caster.getEyePosition().add(left).add(0, -0.35, 0);
        direction = (target == null ? caster.getLookAngle() : chest(target).subtract(launchPosition)).normalize();
        setPos(launchPosition);
        updateRotation();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(CASTER, -1);
        builder.define(COLOR, 0xFFE359);
        builder.define(START, 0L);
        builder.define(EXECUTING, false);
    }

    public int getCasterId() {
        return entityData.get(CASTER);
    }

    public int getBladeColor() {
        return entityData.get(COLOR);
    }

    public int getPhaseTicks() {
        return (int) Math.clamp(level().getGameTime() - entityData.get(START), 0, Integer.MAX_VALUE);
    }

    public boolean isExecutionActive() {
        return entityData.get(EXECUTING) && getPhaseTicks() >= 18 && getPhaseTicks() < 30;
    }

    private static Vec3 chest(LivingEntity target) {
        return target.position().add(0, target.getBbHeight() * 0.62, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (ownerId == null || !SanctifierBladeRecovery.get(level.getServer()).belongsTo(getUUID(), ownerId)) {
            discard();
            return;
        }
        ServerPlayer caster = level.getServer().getPlayerList().getPlayer(ownerId);
        entityData.set(CASTER, caster != null && caster.level() == level ? caster.getId() : -1);
        Entity found = targetId == null ? null : level.getEntity(targetId);
        LivingEntity target = found instanceof LivingEntity living && living.isAlive() && !living.isSpectator() ? living : null;
        if (target == null && embedded) {
            if (found != null || ++missingTargetTicks > 40) targetId = null;
            entityData.set(EXECUTING, false);
        } else missingTargetTicks = 0;
        int phase = getPhaseTicks();
        if (!embedded) fly(level, caster, target, phase);
        else if (target != null) {
            setYRot(target.yBodyRot + attachedYawOffset);
            direction = Vec3.directionFromRotation(getXRot(), -getYRot());
            setPos(chest(target).subtract(direction.scale(Math.min(0.3, target.getBbWidth() * 0.4))));
        }
        if (entityData.get(EXECUTING) && caster != null && caster.level() == level && target instanceof ServerPlayer player) execute(caster, player, phase);
        if (phase >= 30) entityData.set(EXECUTING, false);
        if (embedded && phase >= 30 && caster != null && caster.level() == level && caster.isAlive() && !caster.isSpectator() && caster.getBoundingBox().inflate(1.5).contains(position()) && caster.hasLineOfSight(this)) {
            SanctifierBladeRecovery recovery = SanctifierBladeRecovery.get(level.getServer());
            recovery.recover(getUUID());
            for (ItemStack stack : caster.getInventory().items) recovery.synchronize(stack);
            for (ItemStack stack : caster.getInventory().offhand) recovery.synchronize(stack);
            caster.displayClientMessage(Component.translatable("message.data_energistics.sanctifier_recovered"), true);
            discard();
        }
    }

    private void fly(ServerLevel level, @Nullable ServerPlayer caster, @Nullable LivingEntity target, int phase) {
        Vec3 destination = target == null ? launchPosition.add(direction.scale(8)) : chest(target).subtract(direction.scale(Math.min(0.3, target.getBbWidth() * 0.4)));
        Vec3 next = launchPosition.lerp(destination, Math.min(1, phase / 8.0));
        if (!loadedPath(level, position(), next)) {
            embedded = true;
            targetId = null;
            entityData.set(EXECUTING, false);
            return;
        }
        HitResult block = level.clip(new ClipContext(position(), next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (block.getType() != HitResult.Type.MISS) {
            setPos(block.getLocation().subtract(direction.scale(0.05)));
            embedded = true;
            targetId = null;
            entityData.set(EXECUTING, false);
            return;
        }
        direction = destination.subtract(launchPosition).normalize();
        updateRotation();
        setPos(next);
        if (phase < 8) return;
        embedded = true;
        if (target != null) attachedYawOffset = getYRot() - target.yBodyRot;
        if (!(target instanceof ServerPlayer)) {
            strikeResolved = true;
            entityData.set(EXECUTING, false);
            if (target != null && caster != null && caster.level() == level) target.hurt(caster.damageSources().playerAttack(caster), damage);
        }
    }

    private static boolean loadedPath(ServerLevel level, Vec3 start, Vec3 end) {
        for (int x = Mth.floor(Math.min(start.x, end.x)) >> 4; x <= Mth.floor(Math.max(start.x, end.x)) >> 4; x++) {
            for (int z = Mth.floor(Math.min(start.z, end.z)) >> 4; z <= Mth.floor(Math.max(start.z, end.z)) >> 4; z++) {
                if (!level.hasChunk(x, z)) return false;
            }
        }
        return true;
    }

    private void execute(ServerPlayer caster, ServerPlayer target, int phase) {
        if (!caster.isAlive() || caster.isSpectator()) {
            entityData.set(EXECUTING, false);
            return;
        }
        if (phase == 18 && target.distanceToSqr(caster) <= 144 && caster.hasLineOfSight(target)) {
            Vec3 approach = target.position().subtract(caster.position()).normalize();
            Vec3 position = target.position().subtract(approach.scale(1.4));
            if (level().hasChunk(Mth.floor(position.x) >> 4, Mth.floor(position.z) >> 4) && level().noCollision(caster, caster.getBoundingBox().move(position.subtract(caster.position())))) caster.teleportTo(position.x, position.y, position.z);
            caster.swing(caster.getMainArm() == HumanoidArm.RIGHT ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, true);
        }
        if (!strikeResolved && phase >= 26) {
            strikeResolved = true;
            if (phase < 30 && target.distanceToSqr(caster) <= 16 && caster.hasLineOfSight(target)) SanctifierCombat.execute(caster, target);
        }
    }

    private void updateRotation() {
        setYRot((float) Math.toDegrees(Math.atan2(direction.x, direction.z)));
        setXRot((float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance())));
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && level() instanceof ServerLevel level) SanctifierBladeRecovery.get(level.getServer()).recover(getUUID());
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        targetId = tag.hasUUID("Target") ? tag.getUUID("Target") : null;
        attachedYawOffset = Float.isFinite(tag.getFloat("AttachedYaw")) ? tag.getFloat("AttachedYaw") : 0;
        entityData.set(COLOR, tag.getInt("Color"));
        entityData.set(START, Math.max(0, tag.getLong("Start")));
        entityData.set(EXECUTING, false);
        embedded = true;
        strikeResolved = true;
        direction = Vec3.directionFromRotation(getXRot(), -getYRot());
        // Reload restores recoverable blades, never a pending execution against a newly loaded entity.
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        if (targetId != null) tag.putUUID("Target", targetId);
        tag.putFloat("AttachedYaw", attachedYawOffset);
        tag.putInt("Color", getBladeColor());
        tag.putLong("Start", entityData.get(START));
    }
}
