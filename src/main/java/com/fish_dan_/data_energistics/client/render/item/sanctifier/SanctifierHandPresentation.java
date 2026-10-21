package com.fish_dan_.data_energistics.client.render.item.sanctifier;

import com.fish_dan_.data_energistics.client.render.item.sanctifier.model.SanctifierBakedModel;
import com.fish_dan_.data_energistics.common.data.DEItems;
import com.fish_dan_.data_energistics.common.entity.projectile.SanctifierBladeEntity;
import com.fish_dan_.data_energistics.common.item.powered.LightSaberColorData;
import com.fish_dan_.data_energistics.common.item.powered.PoweredSwordItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

import com.mojang.math.Axis;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/** Presents left-hip carrying, left-hand pickup and the synchronized execution stroke. */
public final class SanctifierHandPresentation {

    private SanctifierHandPresentation() {}

    /** Returns this caster's visible execution in the current client world without retaining entities. */
    public static @Nullable SanctifierBladeEntity execution(LivingEntity caster) {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        for (var entity : level.entitiesForRendering()) {
            if (entity instanceof SanctifierBladeEntity blade && blade.getCasterId() == caster.getId() && blade.isExecutionActive()) return blade;
        }
        return null;
    }

    /** The physical left hand grips the secondary only while charging. */
    public static void preparePlayerPose(RenderPlayerEvent.Pre event) {
        var player = event.getEntity();
        if (player.isSpectator() || !player.getMainHandItem().is(DEItems.DATA_SANCTIFIER.get())) return;
        var model = event.getRenderer().getModel();
        model.rightArmPose = HumanoidModel.ArmPose.ITEM;
        if (SanctifierSecondaryPose.isCharging(player)) model.leftArmPose = HumanoidModel.ArmPose.ITEM;
        else if (player.getOffhandItem().isEmpty()) model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
    }

    /** Displays the secondary only while charging and samples the primary's ordinary or execution stroke. */
    public static void renderHand(RenderHandEvent event) {
        var player = Minecraft.getInstance().player;
        if (player == null || !player.getMainHandItem().is(DEItems.DATA_SANCTIFIER.get())) return;
        SanctifierBladeEntity execution = execution(player);
        SanctifierBakedModel model = SanctifierBakedModel.current();
        if (model == null) return;
        if (event.getHand() == InteractionHand.OFF_HAND) {
            event.setCanceled(true);
            return;
        }
        event.setCanceled(true);
        if (player.isScoping()) return;
        if (SanctifierSecondaryPose.isCharging(player)) {
            float progress = SanctifierSecondaryPose.pickupProgress(player, event.getPartialTick());
            var poses = event.getPoseStack();
            poses.pushPose();
            poses.translate(-0.56F - (1 - progress) * 0.15F, -0.52F - (1 - progress) * 0.8F, -0.72F);
            model.renderHeldSecondary(player.getMainHandItem(), ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                    true, poses, event.getMultiBufferSource(), event.getPackedLight(),
                    LightSaberColorData.getSanctifierAnimatedColor(player.level().getGameTime()),
                    ((PoweredSwordItem) player.getMainHandItem().getItem()).getAECurrentPower(player.getMainHandItem()) > 0);
            poses.popPose();
        }
        if (execution == null) {
            var poses = event.getPoseStack();
            float swing = event.getSwingProgress();
            float rootSwing = Mth.sqrt(swing);
            poses.pushPose();
            poses.translate(-0.4F * Mth.sin(rootSwing * Mth.PI), 0.2F * Mth.sin(rootSwing * Mth.TWO_PI), -0.2F * Mth.sin(swing * Mth.PI));
            poses.translate(0.56F, -0.52F - event.getEquipProgress() * 0.6F, -0.72F);
            poses.mulPose(Axis.YP.rotationDegrees(45 - 20 * Mth.sin(swing * swing * Mth.PI)));
            poses.mulPose(Axis.ZP.rotationDegrees(-20 * Mth.sin(rootSwing * Mth.PI)));
            poses.mulPose(Axis.XP.rotationDegrees(-80 * Mth.sin(rootSwing * Mth.PI)));
            poses.mulPose(Axis.YP.rotationDegrees(-45));
            model.renderHeldPrimary(player.getMainHandItem(), ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                    poses, event.getMultiBufferSource(), event.getPackedLight());
            poses.popPose();
            return;
        }
        float time = execution.getPhaseTicks() + event.getPartialTick();
        float progress = Math.clamp((time - 18) / 8, 0, 1);
        Matrix4f transform = new Matrix4f().translation(0.36F, -0.44F, -0.75F)
                .rotateZ((float) Math.toRadians(-110 + 225 * progress))
                .rotateX(-0.5F).rotateY((float) Math.PI).scale(0.72F);
        model.renderPrimary(player.getMainHandItem(), event.getPoseStack(), event.getMultiBufferSource(),
                event.getPackedLight(), transform);
    }
}
