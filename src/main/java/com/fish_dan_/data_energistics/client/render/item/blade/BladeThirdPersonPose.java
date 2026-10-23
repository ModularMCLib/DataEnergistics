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
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
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
        return shoulder(model, arm).rotateX((float) Math.toRadians(-40) + (model.crouching ? 0.4F : 0))
                .mul(handTransform(entity, weapon, arm));
    }

    public static Matrix4f attack(HumanoidModel<?> model, LivingEntity entity, ItemStack weapon, HumanoidArm arm, Matrix4f motion) {
        Matrix4f adjusted = arm == HumanoidArm.LEFT ? new Matrix4f().scaling(-1, 1, 1).mul(motion).scale(-1, 1, 1) : motion;
        Vector3f grip = grip(weapon);
        Matrix4f transform = restingHand(model, entity, weapon, arm).translate(grip).mul(adjusted);
        Vector3f pivot = shoulder(model, arm).getTranslation(new Vector3f());
        float reach = handTransform(entity, weapon, arm).transformPosition(new Vector3f(grip)).length();
        Vector3f offset = transform.getTranslation(new Vector3f()).sub(pivot);
        // A vanilla arm has no separate forearm bone. Keep the weapon gripped instead of stretching the shoulder.
        if (offset.lengthSquared() > 0.000001F) offset.normalize(reach);
        else restingHand(model, entity, weapon, arm).transformPosition(new Vector3f(grip), offset).sub(pivot);
        return transform.setTranslation(offset.add(pivot)).translate(grip.negate());
    }

    /** Fits the synchronized execution to the same right-hand grip and arm reach as ordinary attacks. */
    public static Matrix4f execution(HumanoidModel<?> model, LivingEntity entity, ItemStack weapon, float tick) {
        Matrix4f camera = new Matrix4f().translation(0, 1.5F - entity.getEyeHeight() + 0.3F, 0).rotateZ(Mth.PI);
        Matrix4f rest = new Matrix4f(camera).invert().mul(restingHand(model, entity, weapon, HumanoidArm.RIGHT));
        Matrix4f target = camera.mul(SanctifierExecutionPose.sample(tick, rest));
        Vector3f grip = grip(weapon);
        Vector3f pivot = shoulder(model, HumanoidArm.RIGHT).getTranslation(new Vector3f());
        Vector3f offset = target.transformPosition(new Vector3f(grip)).sub(pivot);
        float reach = handTransform(entity, weapon, HumanoidArm.RIGHT).transformPosition(new Vector3f(grip)).length();
        if (offset.lengthSquared() > 0.000001F) offset.normalize(reach);
        else restingHand(model, entity, weapon, HumanoidArm.RIGHT).transformPosition(new Vector3f(grip), offset).sub(pivot);
        return target.setTranslation(offset.add(pivot)).translate(grip.negate());
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
            model.rightArm.xRot = (float) Math.toRadians(-40) - (model.crouching ? 0.4F : 0);
            model.rightArm.yRot = 0;
            model.attackTime = 0;
            // Vanilla invokes only the using hand's transformer. Sanctifier always lifts the physical left hand.
            arm = HumanoidArm.RIGHT;
        }
        if (arm == HumanoidArm.RIGHT && execution != null) {
            ItemStack primary = weapon.is(DEItems.DATA_SANCTIFIER.get()) ? weapon : DEItems.DATA_SANCTIFIER.toStack();
            target = execution(model, entity, primary, execution.getPhaseTicks() + partialTick);
        } else if (arm == HumanoidArm.RIGHT && carry != null && carry.transitioning()) {
            Matrix4f body = new Matrix4f().translation(0, model.crouching ? 0.2F : 0, 0).rotateX(model.crouching ? 0.5F : 0);
            target = SanctifierCarryPose.draw(SanctifierCarryPose.mounted(body),
                    restingHand(model, entity, carry.weapon(), arm), body, carry.progress(partialTick));
        } else {
            Matrix4f motion = BladeAttackPlayback.motion(entity, partialTick);
            if (motion != null) target = attack(model, entity, weapon, arm, motion);
            else if (weapon.is(DEItems.DATA_SANCTIFIER.get()) && arm == HumanoidArm.RIGHT || weapon.is(DEItems.DATA_LIGHT_SABER.get()) && arm == entity.getMainArm()) target = restingHand(model, entity, weapon, arm);
            else return;
        }
        model.attackTime = 0;
        ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        ItemStack posedWeapon = weapon.is(DEItems.DATA_SANCTIFIER.get()) || weapon.is(DEItems.DATA_LIGHT_SABER.get()) ? weapon : DEItems.DATA_SANCTIFIER.toStack();
        Vector3f grip = grip(posedWeapon);
        Vector3f offset = target.transformPosition(new Vector3f(grip)).sub(shoulder(model, arm).getTranslation(new Vector3f()));
        if (offset.lengthSquared() < 0.000001F) return;
        Vector3f localGrip = handTransform(entity, posedWeapon, arm).transformPosition(grip);
        Vector3f angles = new Quaternionf().rotationTo(localGrip, offset).getEulerAnglesZYX(new Vector3f());
        float sign = arm == HumanoidArm.RIGHT ? 1 : -1;
        float age = entity.tickCount + partialTick;
        part.xRot = angles.x - (model.crouching ? 0.4F : 0) - sign * Mth.sin(age * 0.067F) * 0.05F;
        part.yRot = angles.y;
        part.zRot = angles.z - sign * (Mth.cos(age * 0.09F) * 0.05F + 0.05F);
    }

    private static Matrix4f shoulder(HumanoidModel<?> model, HumanoidArm arm) {
        ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        return new Matrix4f().translation(part.x / 16, model.crouching ? 0.325F : 0.125F, part.z / 16);
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
        if (entity instanceof AbstractClientPlayer player && player.getSkin().model() == PlayerSkin.Model.SLIM) {
            transform.m30(transform.m30() + (left ? -0.03125F : 0.03125F));
        }
        return transform;
    }

    private static Vector3f grip(ItemStack weapon) {
        return weapon.is(DEItems.DATA_SANCTIFIER.get()) ? new Vector3f(0, 0, BladeGripPose.SANCTIFIER_PRIMARY_GRIP_Z) : new Vector3f(BladeGripPose.LIGHT_SABER_GRIP_X, BladeGripPose.LIGHT_SABER_GRIP_Y, BladeGripPose.LIGHT_SABER_GRIP_Z);
    }
}
