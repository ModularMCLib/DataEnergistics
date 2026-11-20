package com.fish_dan_.data_energistics.common.weapon.platform.runtime;

import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalQuantumAccess;
import com.fish_dan_.data_energistics.common.weapon.platform.ability.PlatformAbilityRuntime;
import com.fish_dan_.data_energistics.common.weapon.platform.combat.GunFiringSolution;
import com.fish_dan_.data_energistics.common.weapon.platform.combat.GunShot;
import com.fish_dan_.data_energistics.common.weapon.platform.combat.GunTargetSelector;
import com.fish_dan_.data_energistics.common.weapon.platform.combat.GunTargeting;
import com.fish_dan_.data_energistics.common.weapon.platform.flight.FloatingGunFlight;
import com.fish_dan_.data_energistics.common.weapon.platform.flight.GunRallyPoint;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunAmmunition;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformChipUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.network.NetworkShotPayment;
import com.fish_dan_.data_energistics.common.weapon.platform.prospecting.PlatformOreSurvey;
import com.fish_dan_.data_energistics.common.weapon.platform.shield.PlatformShield;
import com.fish_dan_.data_energistics.common.weapon.platform.shield.ShieldState;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.GunConfiguration;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.GunThermalState;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.WeaponPlatformInventory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** One equipped stack's server-thread firing state. Item contents, settings and heat remain component-owned. */
public final class PlatformRuntime {

    public final ServerPlayer player;
    public final ServerLevel level;
    public final ItemStack platform;
    public final WeaponPlatformInventory inventory;
    public final ObjectArrayList<GunStatus> guns = new ObjectArrayList<>();
    public final ObjectLinkedOpenHashSet<UUID> locks = new ObjectLinkedOpenHashSet<>();
    public final PlatformRadar radar = new PlatformRadar();
    public final PlatformAbilityRuntime abilities = new PlatformAbilityRuntime();
    public final PlatformOreSurvey oreSurvey = new PlatformOreSurvey();
    private final @Nullable FloatingGunFlight[] flights = new FloatingGunFlight[8];
    private final GunTargetSelector[] selectors = new GunTargetSelector[8];
    public boolean connected;
    public boolean failed;
    public boolean snapshotSyncFailed;
    public boolean sweepingLocks;
    public boolean removingLocks;

    public PlatformRuntime(ServerPlayer player, ItemStack platform) {
        this.player = player;
        this.level = player.serverLevel();
        this.platform = platform;
        this.inventory = new WeaponPlatformInventory(platform);
        for (int slot = 0; slot < selectors.length; slot++) selectors[slot] = new GunTargetSelector();
    }

    public void tick() {
        guns.clear();
        ItemStack chip = inventory.getStackInSlot(WeaponPlatformInventory.CHIP_SLOT);
        int capacity = PlatformChipUpgrades.capacity(chip);
        double cooling = PlatformChipUpgrades.cooling(chip);
        GunTargeting targeting = new GunTargeting(player, platform.getOrDefault(DEDataComponents.PLATFORM_FRIENDLY_PROTECTION, true));
        int targetMask = platform.getOrDefault(DEDataComponents.PLATFORM_TARGETS, GunConfiguration.HOSTILE);
        boolean attackEnabled = platform.getOrDefault(DEDataComponents.PLATFORM_ATTACK_ENABLED, true);
        var connection = TerminalQuantumAccess.resolve(player);
        connected = connection != null;
        if (connection != null) NetworkShotPayment.refund(platform, connection);
        PlatformShield.tick(this, connection);
        abilities.tick(this, connection);
        for (var iterator = locks.iterator(); iterator.hasNext();) {
            UUID id = iterator.next();
            if (!(level.getEntity(id) instanceof LivingEntity living) || !targeting.canAttack(living) || player.distanceToSqr(living) > GunTargeting.FIRE_CONTROL_RADIUS_SQUARED) iterator.remove();
        }
        long now = player.level().getGameTime();
        var candidates = player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(GunTargeting.FIRE_CONTROL_RADIUS),
                entity -> player.distanceToSqr(entity) <= GunTargeting.FIRE_CONTROL_RADIUS_SQUARED);
        if (player.containerMenu != player.inventoryMenu) sweepingLocks = false;
        if (sweepingLocks) {
            LivingEntity aimed = targeting.aimed(candidates);
            if (aimed != null) {
                if (removingLocks) {
                    locks.remove(aimed.getUUID());
                } else locks.add(aimed.getUUID());
            }
        }
        for (int slot = 0; slot < flights.length; slot++) {
            if (slot >= capacity || inventory.getStackInSlot(slot).isEmpty()) {
                FloatingGunFlight flight = flights[slot];
                if (flight != null) flight.discard();
                flights[slot] = null;
                selectors[slot].clear();
            }
        }
        for (int slot = 0; slot < capacity; slot++) {
            ItemStack gun = inventory.getStackInSlot(slot);
            if (gun.isEmpty()) continue;
            GunForm form = GunForm.of(gun);
            FloatingGunFlight flight = flights[slot];
            if (flight == null || flight.isRemoved() || flight.level() != player.level()) {
                flight = new FloatingGunFlight(player, platform, slot, form);
                if (!player.serverLevel().addFreshEntity(flight)) throw new IllegalStateException("Floating gun could not deploy from slot " + slot);
                flights[slot] = flight;
                selectors[slot].clear();
            }
            GunConfiguration settings = gun.getOrDefault(DEDataComponents.GUN_SETTINGS, GunConfiguration.DEFAULT);
            boolean enabled = settings.enabled() && (form == GunForm.MEDICAL || attackEnabled);
            GunThermalState thermal = gun.getOrDefault(DEDataComponents.GUN_THERMAL, GunThermalState.COLD);
            double heat = thermal.currentHeat(now, cooling);
            boolean overheated = thermal.overheated() && heat > 0;
            GunAmmunition selected = form == GunForm.MEDICAL ? GunAmmunition.BINARY : settings.ammunition();
            if (!enabled) selectors[slot].clear();
            LivingEntity target = enabled ? selectors[slot].select(targeting, candidates, locks, form, settings, targetMask, flight, now) : null;
            GunRallyPoint rally = gun.get(DEDataComponents.GUN_RALLY_POINT);
            flight.command(form, connected && enabled && !overheated ? target : null, rally);
            if (target != null && !flight.canPursue(target)) target = null;
            boolean changed = rally != null && !flight.rallying();
            if (changed) gun.remove(DEDataComponents.GUN_RALLY_POINT);
            Vec3 origin = flight.getBoundingBox().getCenter();
            GunFiringSolution solution = target == null ? null : GunFiringSolution.aimed(form, origin, target);
            boolean approaching = solution != null && !solution.clear(level, flight);
            String state = failed ? "error" : !enabled ? "disabled" : overheated ? "overheated" : !connected ? "network" : target == null ? "searching" : approaching ? "approaching" : thermal.readyAt() > now ? "cooldown" : "ready";
            GunAmmunition actual = selected;
            long ready = thermal.readyAt();
            if (state.equals("ready")) {
                // Each shot re-resolves the same public quantum access as the terminal; it does not charge terminal
                // upkeep.
                connection = TerminalQuantumAccess.resolve(player);
                LivingEntity firingTarget = target;
                GunAmmunition paid = connection == null ? null : NetworkShotPayment.pay(platform, connection, form.energy, selected, form == GunForm.MEDICAL,
                        ammunition -> GunShot.fire(targeting, origin, firingTarget, form, ammunition));
                if (paid == null) state = "resources";
                else {
                    actual = paid;
                    heat = Math.min(100, heat + form.heat);
                    overheated = heat >= 100;
                    ready = now + form.interval;
                    state = overheated ? "overheated" : "firing";
                }
            }
            GunThermalState updated = new GunThermalState(heat, now, ready, overheated);
            if (heat != thermal.heat() || ready != thermal.readyAt() || overheated != thermal.overheated()) {
                gun.set(DEDataComponents.GUN_THERMAL, updated);
                changed = true;
            }
            if (changed) inventory.setStackInSlot(slot, gun);
            guns.add(new GunStatus(slot, form, heat, overheated, selected, actual, target == null ? -1 : target.getId(), state,
                    flight.getId(), origin, solution == null ? origin : solution.impact()));
        }
    }

    /** Releases only derived flight entities; the equipped item remains the source of assembly and heat. */
    public void close() {
        abilities.close(player);
        var shield = platform.getOrDefault(DEDataComponents.PLATFORM_SHIELD, ShieldState.EMPTY);
        if (shield.active()) platform.set(DEDataComponents.PLATFORM_SHIELD, new ShieldState(shield.charge(), shield.overloadedUntil(), false));
        sweepingLocks = false;
        for (FloatingGunFlight flight : flights) if (flight != null) flight.discard();
    }

    public void toggleLock() {
        GunTargeting targeting = new GunTargeting(player, platform.getOrDefault(DEDataComponents.PLATFORM_FRIENDLY_PROTECTION, true));
        var candidates = player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(GunTargeting.FIRE_CONTROL_RADIUS),
                entity -> player.distanceToSqr(entity) <= GunTargeting.FIRE_CONTROL_RADIUS_SQUARED);
        LivingEntity aimed = targeting.aimed(candidates);
        if (aimed != null && !locks.remove(aimed.getUUID())) locks.add(aimed.getUUID());
    }

    /** Resolves the pointed surface on the server; an unloaded chunk terminates the ray without loading it. */
    public void rally() {
        Vec3 start = player.getEyePosition(), look = player.getViewVector(1), end = start.add(look.scale(GunTargeting.FIRE_CONTROL_RADIUS));
        var ray = new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player);
        var miss = BlockHitResult.miss(end, Direction.getNearest(-look.x, -look.y, -look.z), BlockPos.containing(end));
        BlockHitResult hit = BlockGetter.traverseBlocks(start, end, ray, (context, position) -> {
            if (!level.getChunkSource().hasChunk(position.getX() >> 4, position.getZ() >> 4)) return miss;
            var state = level.getBlockState(position);
            return level.clipWithInteractionOverride(start, end, position, context.getBlockShape(state, level, position), state);
        }, context -> miss);
        if (hit.getType() != HitResult.Type.BLOCK) {
            player.displayClientMessage(Component.translatable("gui.data_energistics.weapon_platform.rally_no_surface", GunTargeting.FIRE_CONTROL_RADIUS), true);
            return;
        }
        Vec3 anchor = hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(1.5));
        BlockPos anchorBlock = BlockPos.containing(anchor);
        if (!level.getWorldBorder().isWithinBounds(anchorBlock) || level.isOutsideBuildHeight(anchorBlock) || !level.getChunkSource().hasChunk(anchorBlock.getX() >> 4, anchorBlock.getZ() >> 4)) return;
        rally(new GunRallyPoint(level.dimension(), anchor));
        player.displayClientMessage(Component.translatable("gui.data_energistics.weapon_platform.rally_set", hit.getBlockPos().getX(), hit.getBlockPos().getY(), hit.getBlockPos().getZ()), true);
    }

    private void rally(GunRallyPoint rally) {
        int capacity = inventory.capacity();
        for (int slot = 0; slot < capacity; slot++) {
            ItemStack gun = inventory.getStackInSlot(slot);
            if (gun.isEmpty()) continue;
            gun.set(DEDataComponents.GUN_RALLY_POINT, rally);
            inventory.setStackInSlot(slot, gun);
        }
    }

    public record GunStatus(int slot, GunForm form, double heat, boolean overheated, GunAmmunition selected, GunAmmunition actual, int target, String state,
                            int flight, Vec3 origin, Vec3 destination) {}
}
