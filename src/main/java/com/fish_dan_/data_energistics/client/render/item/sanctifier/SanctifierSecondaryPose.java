package com.fish_dan_.data_energistics.client.render.item.sanctifier;

import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.data.DEItems;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Grip-preserving rigid poses for the left-hip mount and the left-hand pickup. */
public final class SanctifierSecondaryPose {

    private static final Vector3f GRIP = new Vector3f(0.000060625F, 0, -0.02057875F);

    private SanctifierSecondaryPose() {}

    public static boolean isCharging(LivingEntity entity) {
        ItemStack weapon = entity.getMainHandItem();
        return weapon.is(DEItems.DATA_SANCTIFIER.get()) && !weapon.has(DEDataComponents.SANCTIFIER_BLADE.get()) && entity.isUsingItem() && entity.getUseItem() == weapon;
    }

    public static float pickupProgress(LivingEntity entity, float partialTick) {
        if (!isCharging(entity)) return 0;
        float progress = Mth.clamp((entity.getTicksUsingItem() + partialTick) / 12, 0, 1);
        return progress * progress * (3 - 2 * progress);
    }

    public static Matrix4f mounted(Matrix4f body) {
        return new Matrix4f(body).translate(0.33F, 0.60F, -0.10F)
                .rotateY((float) Math.PI).rotateZ((float) Math.toRadians(8)).rotateX((float) Math.toRadians(-58))
                .scale(0.72F).translate(-GRIP.x, -GRIP.y, -GRIP.z);
    }

    /** Both endpoints share the player's render root; quaternion interpolation preserves blade length. */
    public static Matrix4f pickup(Matrix4f mounted, Matrix4f held, float progress) {
        Vector3f start = mounted.transformPosition(new Vector3f(GRIP));
        Vector3f end = held.transformPosition(new Vector3f(GRIP));
        Quaternionf rotation = mounted.getUnnormalizedRotation(new Quaternionf())
                .slerp(held.getUnnormalizedRotation(new Quaternionf()), progress);
        Vector3f point = start.lerp(end, progress);
        return new Matrix4f().translation(point).rotate(rotation).scale(0.72F).translate(-GRIP.x, -GRIP.y, -GRIP.z);
    }
}
