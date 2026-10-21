package com.fish_dan_.data_energistics.client.render.item.blade;

import com.fish_dan_.data_energistics.client.render.item.blade.animation.BladeAttackPlayback;
import com.fish_dan_.data_energistics.client.render.item.sanctifier.SanctifierCarryPlayback;
import com.fish_dan_.data_energistics.client.render.item.sanctifier.SanctifierCarryPose;
import com.fish_dan_.data_energistics.client.render.item.sanctifier.SanctifierExecutionPose;
import com.fish_dan_.data_energistics.client.render.item.sanctifier.SanctifierHandPresentation;
import com.fish_dan_.data_energistics.client.render.item.sanctifier.SanctifierSecondaryPose;
import com.fish_dan_.data_energistics.client.render.item.sanctifier.model.SanctifierBakedModel;
import com.fish_dan_.data_energistics.common.data.DEItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.ClientHooks;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Native arm poses follow the blade grip; reference motion is fitted to the vanilla player's arm reach. */
public final class BladeThirdPersonPose {

    private BladeThirdPersonPose() {}

    public static Matrix4f restingHand(HumanoidModel<?> model, LivingEntity entity, ItemStack weapon, HumanoidArm arm) {
        return shoulder(model, arm).rotateX(-Mth.PI / 10 + (model.crouching ? 0.4F : 0))
                .mul(handTransform(entity, weapon, arm));
    }

    public static Matrix4f attack(HumanoidModel<?> model, LivingEntity entity, ItemStack weapon, HumanoidArm arm, Matrix4f motion) {
        Matrix4f adjusted = arm == HumanoidArm.LEFT ? new Matrix4f().scaling(-1, 1, 1).mul(motion).scale(-1, 1, 1) : motion;
        Matrix4f transform = restingHand(model, entity, weapon, arm).mul(adjusted);
        Vector3f pivot = shoulder(model, arm).getTranslation(new Vector3f());
        float reach = handTransform(entity, weapon, arm).getTranslation(new Vector3f()).length();
        Vector3f offset = transform.getTranslation(new Vector3f()).sub(pivot);
        // A vanilla arm has no separate forearm bone. Keep the weapon gripped instead of stretching the shoulder.
        if (offset.lengthSquared() > 0.000001F) offset.normalize(reach);
        else restingHand(model, entity, weapon, arm).getTranslation(offset).sub(pivot);
        return transform.setTranslation(offset.add(pivot));
    }

    /** Called during HumanoidModel.setupAnim through NeoForge's ArmPose extension, before vanilla attack bobbing. */
    public static void apply(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        ItemStack weapon = entity.getMainHandItem();
        var execution = SanctifierHandPresentation.execution(entity);
        SanctifierCarryPlayback carry = entity instanceof Player player ? SanctifierCarryPlayback.find(player) : null;
        Matrix4f target;
        if (SanctifierSecondaryPose.isCharging(entity)) {
            ModelPart left = model.leftArm;
            left.xRot = Mth.lerp(SanctifierSecondaryPose.pickupProgress(entity, partialTick), 0.5F, -1.1F);
            left.yRot = -0.15F;
            left.zRot = -0.1F;
            model.rightArm.xRot = model.rightArm.xRot * 0.5F - Mth.PI / 10;
            model.rightArm.yRot = 0;
            model.attackTime = 0;
            // Vanilla invokes only the using hand's transformer. Sanctifier always lifts the physical left hand.
            arm = HumanoidArm.RIGHT;
        }
        if (arm == HumanoidArm.RIGHT && execution != null) {
            target = SanctifierExecutionPose.sample(execution.getPhaseTicks() + partialTick, 0.72F);
            target = new Matrix4f().translation(0, 1.5F - entity.getEyeHeight() + 0.3F, 0).rotateZ(Mth.PI).mul(target);
        } else if (arm == HumanoidArm.RIGHT && carry != null && carry.transitioning()) {
            Matrix4f body = new Matrix4f().translation(0, model.crouching ? 0.2F : 0, 0).rotateX(model.crouching ? 0.5F : 0);
            target = SanctifierCarryPose.draw(SanctifierCarryPose.mounted(body),
                    restingHand(model, entity, carry.weapon(), arm), body, carry.progress(partialTick));
        } else {
            Matrix4f motion = BladeAttackPlayback.motion(entity, partialTick);
            if (motion == null) return;
            target = attack(model, entity, weapon, arm, motion);
        }
        model.attackTime = 0;
        ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        Vector3f offset = target.getTranslation(new Vector3f()).sub(shoulder(model, arm).getTranslation(new Vector3f()));
        if (offset.lengthSquared() < 0.000001F) return;
        ItemStack posedWeapon = weapon.is(DEItems.DATA_SANCTIFIER.get()) || weapon.is(DEItems.DATA_LIGHT_SABER.get()) ? weapon : DEItems.DATA_SANCTIFIER.toStack();
        Vector3f localGrip = handTransform(entity, posedWeapon, arm).getTranslation(new Vector3f());
        Vector3f angles = new Quaternionf().rotationTo(localGrip, offset).getEulerAnglesZYX(new Vector3f());
        float sign = arm == HumanoidArm.RIGHT ? 1 : -1;
        float age = entity.tickCount + partialTick;
        part.xRot = angles.x - (model.crouching ? 0.4F : 0) - sign * Mth.sin(age * 0.067F) * 0.05F;
        part.yRot = angles.y;
        part.zRot = angles.z - sign * (Mth.cos(age * 0.09F) * 0.05F + 0.05F);
    }

    private static Matrix4f shoulder(HumanoidModel<?> model, HumanoidArm arm) {
        return new Matrix4f().translation(arm == HumanoidArm.RIGHT ? -0.3125F : 0.3125F, model.crouching ? 0.325F : 0.125F, 0);
    }

    private static Matrix4f handTransform(LivingEntity entity, ItemStack weapon, HumanoidArm arm) {
        boolean left = arm == HumanoidArm.LEFT;
        ItemDisplayContext context = left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        Matrix4f transform = new Matrix4f().rotateX(-Mth.HALF_PI).rotateY(Mth.PI).translate((left ? -1 : 1) / 16.0F, 0.125F, -0.625F);
        if (weapon.is(DEItems.DATA_SANCTIFIER.get())) {
            SanctifierBakedModel model = SanctifierBakedModel.current();
            if (model != null) transform.mul(model.primaryHandTransform(context));
        } else {
            var model = Minecraft.getInstance().getItemRenderer().getModel(weapon, entity.level(), entity, entity.getId());
            PoseStack poses = new PoseStack();
            ClientHooks.handleCameraTransforms(poses, model, context, left);
            transform.mul(poses.last().pose());
        }
        return transform;
    }
}
