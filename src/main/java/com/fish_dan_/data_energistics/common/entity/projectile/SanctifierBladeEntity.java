package com.fish_dan_.data_energistics.common.entity.projectile;

import com.fish_dan_.data_energistics.common.data.DEEntities;
import com.fish_dan_.data_energistics.common.item.powered.LightSaberColorData;
import com.fish_dan_.data_energistics.common.item.powered.PoweredSwordItem;
import com.fish_dan_.data_energistics.common.item.powered.sanctifier.SanctifierBladeRecovery;
import com.fish_dan_.data_energistics.common.item.powered.sanctifier.SanctifierCombat;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/** A secondary blade stays at its target or landing point until its caster approaches to recover it. */
public final class SanctifierBladeEntity extends Entity {

    private static final EntityDataAccessor<Integer> CASTER = SynchedEntityData.defineId(SanctifierBladeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(SanctifierBladeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(SanctifierBladeEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> EXECUTING = SynchedEntityData.defineId(SanctifierBladeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> STRIKING = SynchedEntityData.defineId(SanctifierBladeEntity.class, EntityDataSerializers.BOOLEAN);
    private @Nullable UUID ownerId;
    private @Nullable UUID targetId;
    private Vec3 direction = new Vec3(0, 0, 1);
    private float damage;
    private float attachedYawOffset;
    private boolean embedded;
    private boolean strikeResolved;

    public SanctifierBladeEntity(EntityType<? extends SanctifierBladeEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public SanctifierBladeEntity(ServerPlayer caster, ItemStack weapon) {
        this(DEEntities.SANCTIFIER_BLADE.get(), caster.level());
        ownerId = caster.getUUID();
        damage = (float) (PoweredSwordItem.getPanelAttackDamage(weapon) * 0.5);
        entityData.set(CASTER, caster.getId());
        entityData.set(COLOR, LightSaberColorData.getSanctifierAnimatedColor(level().getGameTime()));
        entityData.set(START, level().getGameTime());
        entityData.set(EXECUTING, true);
        Vec3 left = new Vec3(caster.getLookAngle().z, 0, -caster.getLookAngle().x).normalize().scale(0.35);
        direction = caster.getLookAngle();
        setPos(caster.getEyePosition().add(left).add(0, -0.35, 0));
        updateRotation();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(CASTER, -1);
        builder.define(COLOR, 0xFFE359);
        builder.define(START, 0L);
        builder.define(EXECUTING, false);
        builder.define(STRIKING, false);
    }

    public int getCasterId() {
        return entityData.get(CASTER);
    }

    public int getBladeColor() {
        return entityData.get(COLOR);
    }

    public int getPhaseTicks() {
        int elapsed = Math.clamp(level().getGameTime() - entityData.get(START), 0, Integer.MAX_VALUE);
        return isCharging() ? Math.min(18, elapsed) : elapsed;
    }

    public boolean isCharging() {
        return entityData.get(EXECUTING) && !entityData.get(STRIKING);
    }

    public void cancelCharge() {
        entityData.set(EXECUTING, false);
    }

    public boolean isExecutionActive() {
        return entityData.get(EXECUTING) && getPhaseTicks() < 30;
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
        if (caster != null && (caster.level() != level || !caster.isAlive() || caster.isSpectator())) {
            returnToOwner(caster);
            return;
        }
        Entity found = targetId == null ? null : level.getEntity(targetId);
        LivingEntity target = caster == null || found == null ? null : hitTarget(found, caster);
        if (target == null && embedded && targetId != null) {
            targetId = null;
            if (!strikeResolved) cancelCharge();
        }
        int phase = getPhaseTicks();
        if (!embedded) {
            if (caster == null) {
                returnToOwner(null);
                return;
            }
            fly(level, caster);
            if (isRemoved()) return;
            Entity impacted = targetId == null ? null : level.getEntity(targetId);
            target = impacted == null ? null : hitTarget(impacted, caster);
        } else if (target != null) {
            setYRot(target.yBodyRot + attachedYawOffset);
            direction = Vec3.directionFromRotation(getXRot(), -getYRot());
            setPos(chest(target).subtract(direction.scale(Math.min(0.3, target.getBbWidth() * 0.4))));
        }
        if (caster != null && (target == null ? distanceToSqr(caster) > 64 : target.distanceToSqr(caster) > 64)) {
            returnToOwner(caster);
            return;
        }
        if (entityData.get(STRIKING) && entityData.get(EXECUTING) && caster != null && target != null) execute(caster, target, phase);
        if (entityData.get(STRIKING) && phase >= 30) entityData.set(EXECUTING, false);
        if (embedded && !isExecutionActive() && caster != null && caster.getBoundingBox().inflate(1.5).contains(position()) && caster.hasLineOfSight(this)) returnToOwner(caster);
    }

    private static @Nullable LivingEntity hitTarget(Entity hit, ServerPlayer caster) {
        while (hit instanceof PartEntity<?> part) hit = part.getParent();
        if (!(hit instanceof LivingEntity living) || hit == caster || !living.isAlive() || living.isSpectator()) return null;
        if (living instanceof Player player && (player.isCreative() || !caster.canHarmPlayer(player))) return null;
        if (living instanceof ServerPlayer player && player.hasPermissions(2)) return null;
        return living;
    }

    private void fly(ServerLevel level, ServerPlayer caster) {
        Vec3 next = position().add(direction.scale(1.2));
        if (!loadedPath(level, position(), next)) {
            returnToOwner(caster);
            return;
        }
        HitResult block = level.clip(new ClipContext(position(), next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = block.getType() == HitResult.Type.MISS ? next : block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, this, position(), end,
                getBoundingBox().expandTowards(end.subtract(position())).inflate(0.4),
                candidate -> candidate.isPickable() && hitTarget(candidate, caster) != null, 0.25F);
        if (hit != null) {
            LivingEntity target = hitTarget(hit.getEntity(), caster);
            if (target != null) {
                targetId = target.getUUID();
                attachedYawOffset = getYRot() - target.yBodyRot;
                setPos(chest(target).subtract(direction.scale(Math.min(0.3, target.getBbWidth() * 0.4))));
                embedded = true;
                if (target.distanceToSqr(caster) > 64) returnToOwner(caster);
                return;
            }
        }
        if (block.getType() != HitResult.Type.MISS) {
            setPos(block.getLocation().subtract(direction.scale(0.05)));
            embedded = true;
            return;
        }
        setPos(next);
    }

    private static boolean loadedPath(ServerLevel level, Vec3 start, Vec3 end) {
        for (int x = Mth.floor(Math.min(start.x, end.x)) >> 4; x <= Mth.floor(Math.max(start.x, end.x)) >> 4; x++) {
            for (int z = Mth.floor(Math.min(start.z, end.z)) >> 4; z <= Mth.floor(Math.max(start.z, end.z)) >> 4; z++) {
                if (!level.hasChunk(x, z)) return false;
            }
        }
        return true;
    }

    /** Full charge resolves the blade's physical hit; no cone search can replace it with another target. */
    public void beginStrike(ServerPlayer caster) {
        if (targetId == null || !embedded || strikeResolved || !caster.getUUID().equals(ownerId) || caster.level() != level()) return;
        ServerLevel level = caster.serverLevel();
        Entity found = level.getEntity(targetId);
        LivingEntity target = found == null ? null : hitTarget(found, caster);
        if (target == null) return;
        if (target.distanceToSqr(caster) > 64) {
            returnToOwner(caster);
            return;
        }
        if (!caster.isAlive() || caster.isSpectator() || !caster.hasLineOfSight(target)) return;
        Vec3 approach = target.position().subtract(caster.position()).multiply(1, 0, 1).normalize();
        Vec3 destination = target.position().subtract(approach.scale(1.4));
        if (!loadedPath(level, caster.position(), destination) || !level.noCollision(caster, caster.getBoundingBox().move(destination.subtract(caster.position())))) return;
        Vec3 aim = chest(target).subtract(destination.add(0, caster.getEyeHeight(), 0));
        float yaw = (float) Math.toDegrees(Math.atan2(aim.z, aim.x)) - 90;
        float pitch = (float) -Math.toDegrees(Math.atan2(aim.y, aim.horizontalDistance()));
        caster.connection.teleport(destination.x, destination.y, destination.z, yaw, pitch);
        caster.swing(caster.getMainArm() == HumanoidArm.RIGHT ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, true);
        entityData.set(START, level.getGameTime() - 18);
        entityData.set(STRIKING, true);
        entityData.set(EXECUTING, true);
    }

    private void execute(ServerPlayer caster, LivingEntity target, int phase) {
        if (!strikeResolved && phase >= 26) {
            strikeResolved = true;
            if (phase < 30 && target.distanceToSqr(caster) <= 16 && caster.hasLineOfSight(target)) {
                if (target instanceof ServerPlayer player) SanctifierCombat.execute(caster, player);
                else target.hurt(caster.damageSources().playerAttack(caster), damage);
            }
        }
    }

    private void returnToOwner(@Nullable ServerPlayer caster) {
        SanctifierBladeRecovery recovery = SanctifierBladeRecovery.get(((ServerLevel) level()).getServer());
        recovery.recover(getUUID());
        if (caster != null) {
            for (ItemStack stack : caster.getInventory().items) recovery.synchronize(stack);
            for (ItemStack stack : caster.getInventory().offhand) recovery.synchronize(stack);
            caster.displayClientMessage(Component.translatable("message.data_energistics.sanctifier_recovered"), true);
        }
        discard();
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
        BladeState state = BladeState.CODEC.parse(registryAccess().createSerializationContext(NbtOps.INSTANCE), tag).getOrThrow();
        ownerId = state.owner.orElse(null);
        targetId = state.target.orElse(null);
        attachedYawOffset = state.attachedYaw;
        entityData.set(COLOR, state.color);
        entityData.set(START, state.start);
        entityData.set(EXECUTING, false);
        embedded = true;
        strikeResolved = true;
        direction = Vec3.directionFromRotation(getXRot(), -getYRot());
        // Reload restores recoverable blades, never a pending execution against a newly loaded entity.
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        BladeState state = new BladeState(Optional.ofNullable(ownerId), Optional.ofNullable(targetId), attachedYawOffset,
                getBladeColor(), entityData.get(START));
        tag.merge((CompoundTag) BladeState.CODEC.encodeStart(registryAccess().createSerializationContext(NbtOps.INSTANCE), state).getOrThrow());
    }

    private record BladeState(Optional<UUID> owner, Optional<UUID> target, float attachedYaw, int color, long start) {

        private static final Codec<BladeState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(BladeState::owner),
                UUIDUtil.CODEC.optionalFieldOf("target").forGetter(BladeState::target),
                Codec.FLOAT.validate(value -> Float.isFinite(value) ? DataResult.success(value) : DataResult.error(() -> "Invalid attached blade yaw"))
                        .fieldOf("attached_yaw").forGetter(BladeState::attachedYaw),
                Codec.INT.fieldOf("color").forGetter(BladeState::color),
                Codec.LONG.validate(value -> value >= 0 ? DataResult.success(value) : DataResult.error(() -> "Negative blade start"))
                        .fieldOf("start").forGetter(BladeState::start))
                .apply(instance, BladeState::new));
    }
}
