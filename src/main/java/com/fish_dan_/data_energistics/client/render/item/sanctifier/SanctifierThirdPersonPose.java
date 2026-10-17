package com.fish_dan_.data_energistics.client.render.item.sanctifier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Places the support hand on the carrying handle while the main hand extracts the blade. */
public final class SanctifierThirdPersonPose {

    private SanctifierThirdPersonPose() {}

    /** Client render thread only; called by the native two-handed ArmPose extension. */
    public static void apply(HumanoidModel<?> model, LivingEntity entity, HumanoidArm mainArm) {
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        float draw = SanctifierAnimationStates.draw(entity, partial);
        float lift = SanctifierRig.lift(draw);
        ModelPart support = mainArm == HumanoidArm.RIGHT ? model.leftArm : model.rightArm;
        ModelPart main = mainArm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        float sign = mainArm == HumanoidArm.RIGHT ? 1 : -1;
        support.xRot += (-0.8F - support.xRot) * lift;
        support.yRot *= 1 - lift;
        support.zRot += (-sign * 0.12F - support.zRot) * lift;
        Matrix4f sword = new Matrix4f(SanctifierRig.carried(model, mainArm, draw))
                .translate(0, -SanctifierRig.EXTRACTION_DISTANCE * SanctifierRig.extraction(draw) / 16, 0);
        Vector3f supportPalm = SanctifierRig.palm(model, mainArm.getOpposite()).getTranslation(new Vector3f());
        Vector3f separation = sword.getTranslation(new Vector3f()).sub(supportPalm);
        Vector3f supportShoulder = shoulder(model, mainArm.getOpposite());
        Vector3f mainShoulder = shoulder(model, mainArm);
        // Both palms must be reachable by Minecraft's straight arms while preserving the blade axis.
        // Intersect the two shoulder spheres instead of stretching the sword away from either hand.
        Vector3f otherCenter = new Vector3f(mainShoulder).sub(separation);
        Vector3f normal = new Vector3f(otherCenter).sub(supportShoulder);
        float distance = normal.length();
        if (distance == 0 || distance >= 1.25F) return;
        normal.div(distance);
        Vector3f midpoint = new Vector3f(supportShoulder).add(otherCenter).mul(0.5F);
        Vector3f direction = supportPalm.sub(midpoint);
        direction.fma(-direction.dot(normal), normal);
        if (direction.lengthSquared() < 0.000001F) direction.set(0, 0, 1).cross(normal);
        float radius = (float) Math.sqrt(0.625F * 0.625F - distance * distance / 4);
        Vector3f supportTarget = midpoint.add(direction.normalize().mul(radius));
        float reach = lift * (1 - SanctifierRig.ready(draw));
        aim(support, new Vector3f(supportTarget).sub(supportShoulder), reach);
        aim(main, supportTarget.add(separation).sub(mainShoulder), reach);
    }

    private static Vector3f shoulder(HumanoidModel<?> model, HumanoidArm arm) {
        return SanctifierRig.palm(model, arm).transformPosition(new Vector3f(0, -0.625F, 0));
    }

    private static void aim(ModelPart arm, Vector3f direction, float reach) {
        float pitch = (float) Math.asin(Math.clamp(direction.z / direction.length(), -1, 1));
        float roll = (float) Math.atan2(-direction.x, direction.y);
        arm.xRot += (pitch - arm.xRot) * reach;
        arm.yRot *= 1 - reach;
        arm.zRot += (roll - arm.zRot) * reach;
    }
}
