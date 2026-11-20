package com.fish_dan_.data_energistics.common.weapon.platform.combat;

import com.fish_dan_.data_energistics.common.weapon.platform.flight.FloatingGunFlight;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.GunConfiguration;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.UUID;

/** Per-gun server-thread selection: usable shots precede pursuit, with a short stable selection interval. */
@NullMarked
public final class GunTargetSelector {

    private @Nullable LivingEntity current;
    private long selectAt;
    private final int[] scanOffsets = new int[3];

    public @Nullable LivingEntity select(GunTargeting targeting, Iterable<LivingEntity> candidates, ObjectSet<UUID> locks,
                                         GunForm form, GunConfiguration settings, int inheritedMask, FloatingGunFlight flight, long now) {
        int mask = settings.inheritTargets() ? inheritedMask : settings.targetMask();
        if (current != null && now < selectAt && eligible(targeting, current, locks, form, settings, mask, flight)) return current;
        Vec3 origin = flight.getBoundingBox().getCenter();
        var ranked = new ObjectArrayList<Candidate>();
        int[] groupSizes = new int[3];
        for (LivingEntity candidate : candidates) {
            if (!eligible(targeting, candidate, locks, form, settings, mask, flight)) continue;
            double priority;
            if (form == GunForm.MEDICAL) {
                double health = candidate.getHealth() / candidate.getMaxHealth();
                priority = health < 1 ? health : 2 + 1.0 / GunImpactEffects.missingSupport((Player) candidate);
            } else {
                boolean threat = targeting.owner().distanceToSqr(candidate) < 32 * 32 && (candidate instanceof Mob mob && mob.getTarget() == targeting.owner() || targeting.owner().getLastHurtByMob() == candidate && targeting.owner().tickCount - targeting.owner().getLastHurtByMobTimestamp() <= 100);
                priority = threat ? 0 : locks.contains(candidate.getUUID()) ? 1 : 2;
            }
            double distance = origin.distanceToSqr(candidate.getBoundingBox().getCenter());
            ranked.add(new Candidate(candidate, priority, distance * (candidate == current ? .8 : 1)));
            groupSizes[form == GunForm.MEDICAL ? 0 : (int) priority]++;
        }
        ranked.sort(Comparator.comparingDouble(Candidate::priority).thenComparingDouble(Candidate::distance));
        LivingEntity selected = ranked.isEmpty() ? null : ranked.getFirst().entity();
        // A dense mob farm must not turn one platform tick into an unbounded batch of ballistic traces.
        // Keep the nearest half of each category and rotate the rest so distant visible targets cannot starve.
        int head = form == GunForm.MEDICAL ? 12 : 4;
        int[] checked = new int[3];
        for (Candidate entry : ranked) {
            int group = form == GunForm.MEDICAL ? 0 : (int) entry.priority();
            int ordinal = checked[group]++;
            if (ordinal >= head && Math.floorMod(ordinal - head - scanOffsets[group], groupSizes[group] - head) >= head) continue;
            LivingEntity candidate = entry.entity();
            if (GunFiringSolution.aimed(form, origin, candidate).clear(flight.level(), flight)) {
                selected = candidate;
                break;
            }
        }
        for (int group = 0; group < scanOffsets.length; group++) scanOffsets[group] = (scanOffsets[group] + head) % Math.max(1, groupSizes[group] - head);
        current = selected;
        selectAt = now + 4 + (flight.getId() & 1);
        return selected;
    }

    private static boolean eligible(GunTargeting targeting, LivingEntity candidate, ObjectSet<UUID> locks, GunForm form,
                                    GunConfiguration settings, int mask, FloatingGunFlight flight) {
        if (targeting.owner().distanceToSqr(candidate) > GunTargeting.FIRE_CONTROL_RADIUS_SQUARED || !flight.canPursue(candidate)) return false;
        if (form != GunForm.MEDICAL) return targeting.canAttack(candidate) && (locks.contains(candidate.getUUID()) || targeting.matches(candidate, mask));
        return candidate instanceof Player ally && ally.isAlive() && ally.level() == flight.level() && !ally.isSpectator() && targeting.allied(ally) && (ally == targeting.owner() ? settings.supportSelf() : settings.supportTeam()) && (ally.getHealth() < ally.getMaxHealth() || GunImpactEffects.missingSupport(ally) > 0);
    }

    public void clear() {
        current = null;
        selectAt = 0;
        for (int group = 0; group < scanOffsets.length; group++) scanOffsets[group] = 0;
    }

    private record Candidate(LivingEntity entity, double priority, double distance) {}
}
