package com.fish_dan_.data_energistics.client.render.item.crossbow;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

/** Uses the rendered head pose, so remote players and inventory previews share the same interpolated aim. */
public final class CrossbowThirdPersonPose {

    private CrossbowThirdPersonPose() {}

    /** Called by the native ArmPose extension; each occupied hand keeps its own pose. */
    public static void apply(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        InteractionHand other = entity.getMainArm() == arm ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        hold(model.rightArm, model.leftArm, model.head, arm == HumanoidArm.RIGHT, entity.getItemInHand(other).isEmpty());
    }

    public static void aim(ModelPart arm, ModelPart head) {
        arm.xRot = -Mth.HALF_PI + head.xRot;
        arm.yRot = head.yRot;
        arm.zRot = 0;
    }

    public static void hold(ModelPart rightArm, ModelPart leftArm, ModelPart head, boolean rightHanded, boolean support) {
        ModelPart holding = rightHanded ? rightArm : leftArm;
        ModelPart supporting = rightHanded ? leftArm : rightArm;
        aim(holding, head);
        if (support) {
            supporting.xRot = -1.5F + head.xRot;
            supporting.yRot = (rightHanded ? 0.6F : -0.6F) + head.yRot;
            supporting.zRot = 0;
        }
    }
}
