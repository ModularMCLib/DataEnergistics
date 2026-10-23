package com.fish_dan_.data_energistics.common.weapon.platform.combat;

import com.fish_dan_.data_energistics.common.weapon.platform.model.GunAmmunition;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;
import com.fish_dan_.data_energistics.common.weapon.platform.projectile.FloatingGunProjectile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class GunShot {

    private GunShot() {}

    public static void fire(GunTargeting targeting, Vec3 origin, LivingEntity target, GunForm form, GunAmmunition ammunition) {
        ServerLevel level = targeting.owner().serverLevel();
        Vec3 direction = target.getBoundingBox().getCenter().subtract(origin).normalize();
        if (form == GunForm.NORMAL || form == GunForm.SNIPER) {
            Vec3 end = target.getBoundingBox().getCenter().add(direction.scale(target.getBoundingBox().getSize()));
            var block = GunRayTrace.clip(level, targeting.owner(), origin, end);
            if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
            double radius = form == GunForm.SNIPER ? 0.15 : 0.035;
            var hits = GunRayTrace.collect(level, targeting.owner(), origin, end, radius, targeting::canAttack);
            Vec3 impact = ammunition == GunAmmunition.BINARY || hits.isEmpty() ? end : hits.getFirst().position();
            GunImpactEffects.trail(level, origin, impact, ammunition.color, form == GunForm.SNIPER ? 1.6f : 0.6f);
            for (GunRayTrace.Hit hit : hits) {
                GunImpactEffects.hit(targeting, hit.target(), form, ammunition, form.damage);
                if (ammunition != GunAmmunition.BINARY) break;
            }
            return;
        }
        var projectile = new FloatingGunProjectile(level, targeting, origin, target, form, ammunition);
        if (!level.addFreshEntity(projectile)) throw new IllegalStateException("Floating gun projectile could not enter the world");
    }
}
