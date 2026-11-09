package com.fish_dan_.data_energistics.common.weapon.platform.flight;

import com.fish_dan_.data_energistics.common.data.DEEntities;
import com.fish_dan_.data_energistics.common.weapon.platform.combat.GunFiringSolution;
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
    private static final EntityDataAccessor<Float> AIM_YAW = SynchedEntityData.defineId(FloatingGunFlight.class, EntityDataSerializers.FLOAT);
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
    private @Nullable GunRallyPoint rallyCommand;
    private boolean rallyComplete;
    private long commandedAt;
    private int interpolationTicks;
    private Vec3 interpolationTarget = Vec3.ZERO;
    private float interpolationYaw;
    private float interpolationPitch;
    private float aimYaw;
    private float previousAimYaw;
    private Vec3 visualVelocity = Vec3.ZERO;
    private Vec3 previousVisualVelocity = Vec3.ZERO;
    private Vec3 visualAcceleration = Vec3.ZERO;
    private Vec3 previousVisualAcceleration = Vec3.ZERO;
    private Vec3 receivedVelocity = Vec3.ZERO;
    private float visualTurn;
    private float previousVisualTurn;
    private final GunFlightEngagement engagement = new GunFlightEngagement();
    private final GunFlightSpacing spacing = new GunFlightSpacing();

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
        destination = spacing.following(this, owner.position().add(GunFormation.offset(owner, slot)), owner.getEyePosition());
        setPos(destination.subtract(0, getBbHeight() / 2, 0));
        command(form, null, null);
    }

    public GunForm form() {
        return GunForm.values()[entityData.get(FORM)];
    }

    public @Nullable UUID ownerId() {
        return entityData.get(OWNER).orElse(null);
    }

    /** Client interpolation of the target bearing, independent of the hull's turning rate. */
    public float aimingYaw(float partialTick) {
        return Mth.rotLerp(partialTick, previousAimYaw, aimYaw);
    }

    /** Smooths the tracked server velocity independently of position-packet interpolation. */
    public Vec3 flightVelocity(float partialTick) {
        return previousVisualVelocity.lerp(visualVelocity, partialTick);
    }

    public Vec3 flightAcceleration(float partialTick) {
        return previousVisualAcceleration.lerp(visualAcceleration, partialTick);
    }

    public float flightTurnRate(float partialTick) {
        return Mth.lerp(partialTick, previousVisualTurn, visualTurn);
    }

    /** Failed pursuits cool down locally without removing the player's manual lock. */
    public boolean canPursue(LivingEntity target) {
        return engagement.available(target.getUUID(), level().getGameTime());
    }

    /** Whether this server-owned flight still has an unfinished rally command. */
    public boolean rallying() {
        return rallyCommand != null && !rallyComplete;
    }

    /**
     * Called once per platform tick, after target selection and before firing from this entity's current center.
     * The rally command belongs to this gun; null cancels it independently of the other guns.
     */
    public void command(GunForm form, @Nullable LivingEntity target, @Nullable GunRallyPoint rally) {
        ServerPlayer player = owner;
        if (player == null) throw new IllegalStateException("Only a server-owned floating gun can receive a flight command");
        entityData.set(FORM, form.ordinal());
        Vec3 following = spacing.following(this, player.position().add(GunFormation.offset(player, slot)), player.getEyePosition());
        if (rallyCommand != rally) {
            rallyCommand = rally;
            rallyComplete = false;
            if (rally != null) returning = false;
            if (search != null) search.cancel();
            search = null;
            path = ObjectList.of();
            waypoint = 0;
            retryAt = 0;
        }
        if (rally != null) {
            BlockPos anchor = BlockPos.containing(rally.position());
            if (!rally.dimension().equals(level().dimension()) || player.position().distanceToSqr(rally.position()) > 320 * 320 || !level().getChunkSource().hasChunk(anchor.getX() >> 4, anchor.getZ() >> 4)) {
                rallyComplete = true;
                rally = null;
            }
        }
        if (rally != null && getBoundingBox().getCenter().distanceToSqr(rally.formation(slot)) <= 1) rallyComplete = true;
        Vec3 home = rally == null || rallyComplete || returning ? following : rally.formation(slot);
        destination = home;
        facing = home.add(player.getViewVector(1).scale(10));
        // Commands also run while the gun's old chunk is no longer ticking.
        if (distanceToSqr(player) > 320 * 320 || previousOwnerPosition.distanceToSqr(player.position()) > 96 * 96) {
            rallyComplete = true;
            rally = null;
            destination = home = following;
            returning = false;
            setPos(following.subtract(0, getBbHeight() / 2, 0));
            setDeltaMovement(Vec3.ZERO);
            if (search != null) search.cancel();
            search = null;
            path = ObjectList.of();
            waypoint = 0;
            retryAt = 0;
            target = null;
            engagement.stop();
        }
        previousOwnerPosition = player.position();
        if (returning && getBoundingBox().getCenter().distanceToSqr(following) < 4) returning = false;
        if (target != null && !returning && canPursue(target)) {
            facing = GunFiringSolution.aimed(form, getBoundingBox().getCenter(), target).impact();
            if ((rally == null || rallyComplete) && target != player) {
                Vec3 firingPosition = engagement.destination(this, target, form, slot, level().getGameTime());
                if (firingPosition != null) destination = firingPosition;
            } else engagement.stop();
        } else engagement.stop();
        BlockPos destinationBlock = BlockPos.containing(destination);
        if (!level().getChunkSource().hasChunk(destinationBlock.getX() >> 4, destinationBlock.getZ() >> 4)) destination = home;
        commandedAt = level().getGameTime();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(FORM, GunForm.NORMAL.ordinal());
        builder.define(OWNER, Optional.empty());
        builder.define(AIM_YAW, 0.0f);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            previousVisualVelocity = visualVelocity;
            previousVisualAcceleration = visualAcceleration;
            Vec3 velocity = getDeltaMovement();
            visualVelocity = visualVelocity.lerp(velocity, .35);
            visualAcceleration = visualAcceleration.lerp(velocity.subtract(receivedVelocity), .35);
            receivedVelocity = velocity;
            previousAimYaw = aimYaw;
            aimYaw = tickCount == 1 ? entityData.get(AIM_YAW) : Mth.rotLerp(1.0f / 3, aimYaw, entityData.get(AIM_YAW));
            if (tickCount == 1) previousAimYaw = aimYaw;
            float previousYaw = getYRot();
            if (interpolationTicks > 0) {
                double amount = 1.0 / interpolationTicks--;
                setPos(position().lerp(interpolationTarget, amount));
                setYRot((float) (getYRot() + Mth.wrapDegrees(interpolationYaw - getYRot()) * amount));
                setXRot((float) Mth.lerp(amount, getXRot(), interpolationPitch));
            }
            previousVisualTurn = visualTurn;
            visualTurn = Mth.lerp(.35f, visualTurn, Mth.wrapDegrees(getYRot() - previousYaw));
            return;
        }
        ServerPlayer player = owner;
        if (player == null || !player.isAlive() || player.isSpectator() || player.level() != level() || EquippedWeaponPlatform.find(player) != platform) {
            discard();
            return;
        }
        if (level().getGameTime() - commandedAt > 10) command(form(), null, rallyCommand);
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
                    if (engagement.pathFailed(now)) {
                        destination = center;
                        retryAt = now + 1;
                    } else {
                        rallyComplete = true;
                        returning = true;
                        destination = spacing.following(this, player.position().add(GunFormation.offset(player, slot)), player.getEyePosition());
                    }
                }
            }
            while (waypoint < path.size() && center.distanceToSqr(path.get(waypoint)) < 0.25) waypoint++;
            for (int shortcut = 0; shortcut < 4 && waypoint + 1 < path.size() && FlightPathSearch.clear(this, center, path.get(waypoint + 1)); shortcut++) waypoint++;
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
        Vec3 motion = GunFlightSpacing.steer(this, getDeltaMovement().scale(0.6).add(direction.scale(speed * 0.4)));
        setDeltaMovement(motion);
        Vec3 beforeMove = position();
        move(MoverType.SELF, motion);
        setDeltaMovement(position().subtract(beforeMove));
        Vec3 directionToTarget = facing.subtract(getBoundingBox().getCenter());
        GunAim aim = GunAim.fromDirection(directionToTarget.x, directionToTarget.y, directionToTarget.z, getYRot());
        entityData.set(AIM_YAW, aim.yaw());
        setYRot(Mth.approachDegrees(getYRot(), aim.yaw(), 12));
        setXRot(aim.pitch());
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        interpolationTarget = new Vec3(x, y, z);
        interpolationYaw = yaw;
        interpolationPitch = pitch;
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
