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

import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * A deployed gun's server-owned position. Its item and heat stay in the equipped platform; unloading or removing
 * this visual entity never creates an item. A fresh runtime redeploys it from that authoritative inventory.
 */
public final class FloatingGunFlight extends Entity {

    private static final EntityDataAccessor<Integer> FORM = SynchedEntityData.defineId(FloatingGunFlight.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(FloatingGunFlight.class, EntityDataSerializers.OPTIONAL_UUID);
    private @Nullable ServerPlayer owner;
    private ItemStack platform = ItemStack.EMPTY;
    private int slot;
    private Vec3 destination = Vec3.ZERO;
    private Vec3 facing = Vec3.ZERO;
    private Vec3 previousOwnerPosition = Vec3.ZERO;
    private @Nullable FlightPathSearch search;
    private ObjectList<Vec3> path = ObjectList.of();
    private int waypoint;
    private long retryAt;
    private Vec3 pathGoal = Vec3.ZERO;
    private boolean returning;
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
        entityData.set(OWNER, Optional.of(owner.getUUID()));
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

    public @Nullable UUID ownerId() {
        return entityData.get(OWNER).orElse(null);
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
        if (distanceToSqr(player) > 320 * 320 || previousOwnerPosition.distanceToSqr(player.position()) > 96 * 96) {
            setPos(home.subtract(0, getBbHeight() / 2, 0));
            setDeltaMovement(Vec3.ZERO);
            if (search != null) search.cancel();
            search = null;
            path = ObjectList.of();
            waypoint = 0;
            retryAt = 0;
            target = null;
        }
        previousOwnerPosition = player.position();
        if (returning && getBoundingBox().getCenter().distanceToSqr(home) < 4) returning = false;
        if (target != null && !returning) {
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
        builder.define(OWNER, Optional.empty());
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
        long now = level().getGameTime();
        if (search != null && search.goal().distanceToSqr(destination) > 9) {
            search.cancel();
            search = null;
            path = ObjectList.of();
        }
        if (!path.isEmpty() && pathGoal.distanceToSqr(destination) > 9) {
            path = ObjectList.of();
            waypoint = 0;
        }
        boolean direct = FlightPathSearch.clear(this, center, destination);
        if (direct) {
            path = ObjectList.of();
            waypoint = 0;
            if (search != null) search.cancel();
            search = null;
        } else {
            if (search != null && search.complete()) {
                var result = search.result();
                pathGoal = search.goal();
                path = result == null ? ObjectList.of() : result;
                waypoint = 0;
                search = null;
                retryAt = now + 20;
                if (path.isEmpty()) {
                    returning = true;
                    destination = player.position().add(GunFormation.offset(player, slot));
                }
            }
            while (waypoint < path.size() && center.distanceToSqr(path.get(waypoint)) < 0.25) waypoint++;
            if (waypoint == path.size()) {
                path = ObjectList.of();
                waypoint = 0;
            }
            if (waypoint < path.size() && !FlightPathSearch.clear(this, center, path.get(waypoint))) {
                path = ObjectList.of();
                waypoint = 0;
                retryAt = now;
            }
            if (path.isEmpty() && search == null && now >= retryAt) {
                search = new FlightPathSearch(this, destination);
                FlightPathScheduler.submit(search);
            }
        }
        Vec3 next = waypoint < path.size() ? path.get(waypoint) : direct ? destination : center;
        Vec3 delta = next.subtract(center);
        double speed = Math.min(0.55, delta.length() * 0.16);
        Vec3 direction = delta.normalize();
        Vec3 motion = getDeltaMovement().scale(0.6).add(direction.scale(speed * 0.4));
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        Vec3 aim = facing.subtract(center);
        if (aim.horizontalDistanceSqr() > 1e-6) setYRot((float) (Mth.atan2(-aim.x, aim.z) * Mth.RAD_TO_DEG));
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        interpolationTarget = new Vec3(x, y, z);
        interpolationYaw = yaw;
        interpolationTicks = Math.max(1, steps);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // The registered noSave visual has no persisted authority; the equipped component redeploys it.
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // noSave entities are derived from the equipped inventory; commands cannot restore a second owner.
        discard();
    }
}
