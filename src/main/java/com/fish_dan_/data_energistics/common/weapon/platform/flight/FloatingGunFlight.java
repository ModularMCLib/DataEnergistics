package com.fish_dan_.data_energistics.common.weapon.platform.flight;

import com.fish_dan_.data_energistics.common.data.DEEntities;
import com.fish_dan_.data_energistics.common.weapon.platform.combat.GunFormation;
import com.fish_dan_.data_energistics.common.weapon.platform.equipment.EquippedWeaponPlatform;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * A deployed gun's server-owned position. Its item and heat stay in the equipped platform; unloading or removing
 * this visual entity never creates an item. A fresh runtime redeploys it from that authoritative inventory.
 */
public final class FloatingGunFlight extends Entity {

    private static final EntityDataAccessor<Integer> FORM = SynchedEntityData.defineId(FloatingGunFlight.class, EntityDataSerializers.INT);
    private @Nullable ServerPlayer owner;
    private ItemStack platform = ItemStack.EMPTY;
    private int slot;
    private Vec3 destination = Vec3.ZERO;
    private Vec3 facing = Vec3.ZERO;
    private Vec3 previousOwnerPosition = Vec3.ZERO;
    private Vec3 detour = Vec3.ZERO;
    private int detourTicks;
    private long commandedAt;
    private int interpolationTicks;
    private Vec3 interpolationTarget = Vec3.ZERO;
    private float interpolationYaw;

    public FloatingGunFlight(EntityType<? extends FloatingGunFlight> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public FloatingGunFlight(ServerPlayer owner, ItemStack platform, int slot, GunForm form) {
        this(DEEntities.FLOATING_GUN_FLIGHT.get(), owner.level());
        this.owner = owner;
        this.platform = platform;
        this.slot = slot;
        previousOwnerPosition = owner.position();
        entityData.set(FORM, form.ordinal());
        destination = owner.position().add(GunFormation.offset(owner, slot));
        setPos(destination.subtract(0, getBbHeight() / 2, 0));
        command(form, null);
    }

    public GunForm form() {
        return GunForm.values()[entityData.get(FORM)];
    }

    /** Called once per platform tick, after target selection and before firing from this entity's current center. */
    public void command(GunForm form, @Nullable LivingEntity target) {
        ServerPlayer player = owner;
        if (player == null) throw new IllegalStateException("Only a server-owned floating gun can receive a flight command");
        entityData.set(FORM, form.ordinal());
        Vec3 home = player.position().add(GunFormation.offset(player, slot));
        destination = home;
        facing = home.add(player.getViewVector(1).scale(10));
        // Commands also run while the gun's old chunk is no longer ticking.
        if (distanceToSqr(player) > 128 * 128 || previousOwnerPosition.distanceToSqr(player.position()) > 96 * 96) {
            setPos(home.subtract(0, getBbHeight() / 2, 0));
            setDeltaMovement(Vec3.ZERO);
            detour = Vec3.ZERO;
            detourTicks = 0;
            target = null;
        }
        previousOwnerPosition = player.position();
        if (target != null) {
            facing = target.getBoundingBox().getCenter();
            if (target != player) {
                Vec3 retreat = player.position().subtract(facing).multiply(1, 0, 1).normalize();
                if (retreat.lengthSqr() < 0.5) retreat = new Vec3(0, 0, 1);
                double distance = switch (form) {
                    case NORMAL -> 10;
                    case SNIPER -> 24;
                    case PLASMA -> 8;
                    case GRENADE -> 12;
                    case MEDICAL -> 3;
                };
                destination = facing.add(retreat.yRot((slot - 3.5f) * 0.16f).scale(distance)).add(0, 1.5 + slot % 2 * 0.4, 0);
            }
        }
        BlockPos destinationBlock = BlockPos.containing(destination);
        if (!level().getChunkSource().hasChunk(destinationBlock.getX() >> 4, destinationBlock.getZ() >> 4)) destination = home;
        commandedAt = level().getGameTime();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(FORM, GunForm.NORMAL.ordinal());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (interpolationTicks > 0) {
                double amount = 1.0 / interpolationTicks--;
                setPos(position().lerp(interpolationTarget, amount));
                setYRot((float) (getYRot() + Mth.wrapDegrees(interpolationYaw - getYRot()) * amount));
            }
            return;
        }
        ServerPlayer player = owner;
        if (player == null || !player.isAlive() || player.isSpectator() || player.level() != level() || EquippedWeaponPlatform.find(player) != platform) {
            discard();
            return;
        }
        if (level().getGameTime() - commandedAt > 10) command(form(), null);
        Vec3 center = getBoundingBox().getCenter();
        Vec3 delta = destination.subtract(center);
        double speed = Math.min(0.55, delta.length() * 0.16);
        Vec3 direction = delta.normalize();
        if (!clear(direction.scale(Math.max(1, speed * 3)))) {
            if (--detourTicks <= 0 || !clear(detour.scale(1.2))) {
                double best = -Double.MAX_VALUE;
                detour = Vec3.ZERO;
                for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    Vec3 candidate = new Vec3(x, y, z).normalize();
                    if (!clear(candidate.scale(1.2))) continue;
                    double score = candidate.dot(direction) + candidate.dot(getDeltaMovement().normalize()) * 0.2 + y * 0.08;
                    if (score > best) {
                        best = score;
                        detour = candidate;
                    }
                }
                detourTicks = 12;
            }
            direction = detour;
        } else detourTicks = 0;
        Vec3 motion = getDeltaMovement().scale(0.6).add(direction.scale(speed * 0.4));
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        Vec3 aim = facing.subtract(center);
        if (aim.horizontalDistanceSqr() > 1e-6) setYRot((float) (Mth.atan2(-aim.x, aim.z) * Mth.RAD_TO_DEG));
    }

    private boolean clear(Vec3 movement) {
        BlockPos next = BlockPos.containing(position().add(movement));
        return level().getChunkSource().hasChunk(next.getX() >> 4, next.getZ() >> 4) && level().noCollision(this, getBoundingBox().expandTowards(movement));
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        interpolationTarget = new Vec3(x, y, z);
        interpolationYaw = yaw;
        interpolationTicks = Math.max(1, steps);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Slot", slot);
        if (owner != null) tag.putUUID("Owner", owner.getUUID());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // noSave entities are derived from the equipped inventory; commands cannot restore a second owner.
        discard();
    }
}
