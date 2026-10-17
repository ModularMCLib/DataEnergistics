package com.fish_dan_.data_energistics.client.render.item.sanctifier;

import com.fish_dan_.data_energistics.common.data.DEEnumProxies;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Places the support hand on the carrying handle while the main hand extracts the blade. */
public final class SanctifierThirdPersonPose {

    private static final float PALM_REACH = (float) Math.sqrt(105.0F / 256);
    private static final float PALM_REACH_YZ = (float) Math.sqrt(104.0F / 256);

    private SanctifierThirdPersonPose() {}

    /** Selection has already changed when sheathing; the native pose still owns this render only. */
    public static void prepare(RenderPlayerEvent.Pre event) {
        var player = event.getEntity();
        if (!SanctifierAnimationStates.isSheathing(player) || !player.getOffhandItem().isEmpty()) return;
        var model = event.getRenderer().getModel();
        if (player.getMainArm() == HumanoidArm.RIGHT) {
            model.rightArmPose = DEEnumProxies.SANCTIFIER_DRAW.getValue();
            model.leftArmPose = ArmPose.EMPTY;
        } else {
            model.leftArmPose = DEEnumProxies.SANCTIFIER_DRAW.getValue();
            model.rightArmPose = ArmPose.EMPTY;
        }
    }

    /** Client render thread only; called by the native two-handed ArmPose extension. */
    public static void apply(HumanoidModel<?> model, LivingEntity entity, HumanoidArm mainArm) {
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        float draw = SanctifierAnimationStates.draw(entity, partial);
        float lift = SanctifierRig.support(draw);
        ModelPart support = mainArm == HumanoidArm.RIGHT ? model.leftArm : model.rightArm;
        ModelPart main = mainArm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        float sign = mainArm == HumanoidArm.RIGHT ? 1 : -1;
        support.xRot += (-0.8F - support.xRot) * lift;
        support.yRot *= 1 - lift;
        support.zRot += (-sign * 0.12F - support.zRot) * lift;
        float returned = SanctifierRig.holstered(draw);
        if (returned > 0) {
            Vector3f target = SanctifierRig.carryGrip(SanctifierRig.holster(model));
            aim(support, mainArm.getOpposite(), target.sub(SanctifierRig.shoulder(model, mainArm.getOpposite())), returned * lift);
        }
        Matrix4f sword = SanctifierRig.unhinged(SanctifierRig.carried(model, mainArm), draw);
        Vector3f supportPalm = SanctifierRig.palm(model, mainArm.getOpposite()).getTranslation(new Vector3f());
        Vector3f separation = SanctifierRig.swordGrip(sword).sub(supportPalm);
        Vector3f supportShoulder = SanctifierRig.shoulder(model, mainArm.getOpposite());
        Vector3f mainShoulder = SanctifierRig.shoulder(model, mainArm);
        // Both palms must be reachable by Minecraft's straight arms while preserving the blade axis.
        // Intersect the two shoulder spheres instead of stretching the sword away from either hand.
        Vector3f otherCenter = new Vector3f(mainShoulder).sub(separation);
        Vector3f normal = new Vector3f(otherCenter).sub(supportShoulder);
        float distance = normal.length();
        if (distance == 0 || distance >= 2 * PALM_REACH) return;
        normal.div(distance);
        Vector3f midpoint = new Vector3f(supportShoulder).add(otherCenter).mul(0.5F);
        Vector3f direction = supportPalm.sub(midpoint);
        direction.fma(-direction.dot(normal), normal);
        if (direction.lengthSquared() < 0.000001F) direction.set(0, 0, 1).cross(normal);
        float radius = (float) Math.sqrt(PALM_REACH * PALM_REACH - distance * distance / 4);
        Vector3f supportTarget = midpoint.add(direction.normalize().mul(radius));
        float reach = SanctifierRig.lift(draw) * (1 - SanctifierRig.ready(draw));
        aim(support, mainArm.getOpposite(), new Vector3f(supportTarget).sub(supportShoulder), reach);
        aim(main, mainArm, supportTarget.add(separation).sub(mainShoulder), reach);
    }

    private static void aim(ModelPart arm, HumanoidArm side, Vector3f direction, float reach) {
        direction.normalize(PALM_REACH);
        float pitch = (float) Math.asin(Math.clamp(direction.z / PALM_REACH_YZ, -1, 1)) + (float) Math.atan2(2, 10);
        float palmY = (float) Math.sqrt(Math.max(0, PALM_REACH_YZ * PALM_REACH_YZ - direction.z * direction.z));
        float palmX = (side == HumanoidArm.RIGHT ? -1 : 1) / 16.0F;
        float roll = (float) (Math.atan2(-direction.x, direction.y) - Math.atan2(-palmX, palmY));
        arm.xRot += (pitch - arm.xRot) * reach;
        arm.yRot *= 1 - reach;
        arm.zRot += (roll - arm.zRot) * reach;
    }
}
