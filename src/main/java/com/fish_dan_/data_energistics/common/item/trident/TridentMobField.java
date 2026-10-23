package com.fish_dan_.data_energistics.common.item.trident;

import com.fish_dan_.data_energistics.common.item.powered.PoweredToolResearch;

import appeng.api.config.Actionable;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Applies the field only inside loaded terrain and with a clear path to the thrown weapon. */
public final class TridentMobField {

    private TridentMobField() {}

    public static void tick(ThrownTrident trident) {
        if (trident.level().isClientSide || trident.isRemoved()) return;
        ItemStack stack = trident.getWeaponItem();
        if (!(stack.getItem() instanceof DataTridentItem item) || !PoweredToolResearch.has(stack, PoweredToolResearch.TRIDENT_FIELD)) return;
        double cost = 160.0 / 20.0;
        if (item.extractAEPower(stack, cost, Actionable.SIMULATE) < cost) return;
        item.extractAEPower(stack, cost, Actionable.MODULATE);
        Vec3 origin = trident.position();
        for (LivingEntity target : trident.level().getEntitiesOfClass(LivingEntity.class,
                trident.getBoundingBox().inflate(4), target -> !(target instanceof Player) && target.isAlive())) {
            if (target.distanceToSqr(origin) > 16 || !loadedPath(trident.level(), origin, target.position())) continue;
            Vec3 center = target.getBoundingBox().getCenter();
            if (trident.level().clip(new ClipContext(origin, center, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, trident)).getType() != HitResult.Type.MISS)
                continue;
            Vec3 offset = center.subtract(origin);
            if (offset.lengthSqr() < 0.04) continue;
            Vec3 force = offset.normalize().scale(DataTridentItem.isAttracting(stack) ? -0.08 : 0.08);
            target.setDeltaMovement(target.getDeltaMovement().add(force));
            target.hasImpulse = true;
        }
    }

    private static boolean loadedPath(Level level, Vec3 start, Vec3 end) {
        int minX = Mth.floor(Math.min(start.x, end.x)) >> 4;
        int maxX = Mth.floor(Math.max(start.x, end.x)) >> 4;
        int minZ = Mth.floor(Math.min(start.z, end.z)) >> 4;
        int maxZ = Mth.floor(Math.max(start.z, end.z)) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!level.hasChunk(x, z)) return false;
            }
        }
        return true;
    }
}
