package com.fish_dan_.data_energistics.common.weapon.platform.combat;

import com.fish_dan_.data_energistics.common.combat.damage.DirectHealthDamage;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunAmmunition;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;
import com.fish_dan_.data_energistics.common.weapon.platform.projectile.FloatingGunProjectile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Comparator;

public final class GunShot {

    private GunShot() {}

    public static void fire(GunTargeting targeting, Vec3 origin, LivingEntity target, GunForm form, GunAmmunition ammunition) {
        ServerLevel level = targeting.owner().serverLevel();
        Vec3 direction = target.getBoundingBox().getCenter().subtract(origin).normalize();
        if (form == GunForm.NORMAL || form == GunForm.SNIPER) {
            Vec3 end = origin.add(direction.scale(form.range));
            var block = level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, targeting.owner()));
            if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
            var intersections = new Object2ObjectOpenHashMap<LivingEntity, Vec3>();
            double radius = form == GunForm.SNIPER ? 0.15 : 0.035;
            for (Entity candidate : level.getEntities(targeting.owner(), new AABB(origin, end).inflate(radius + 1))) {
                LivingEntity living = DirectHealthDamage.target(candidate);
                if (living == null || !targeting.canAttack(living)) continue;
                var hit = candidate.getBoundingBox().inflate(radius).clip(origin, end);
                if (hit.isPresent()) {
                    Vec3 previous = intersections.get(living);
                    if (previous == null || origin.distanceToSqr(hit.get()) < origin.distanceToSqr(previous)) intersections.put(living, hit.get());
                }
            }
            var hits = new ObjectArrayList<>(intersections.keySet());
            hits.sort(Comparator.comparingDouble(living -> origin.distanceToSqr(intersections.get(living))));
            Vec3 impact = ammunition == GunAmmunition.BINARY || hits.isEmpty() ? end : intersections.get(hits.getFirst());
            GunImpactEffects.trail(level, origin, impact, ammunition.color);
            for (LivingEntity hit : hits) {
                GunImpactEffects.hit(targeting, hit, form, ammunition, form.damage);
                if (ammunition != GunAmmunition.BINARY) break;
            }
            return;
        }
        var projectile = new FloatingGunProjectile(level, targeting, origin, target, form, ammunition);
        if (!level.addFreshEntity(projectile)) throw new IllegalStateException("Floating gun projectile could not enter the world");
    }
}
