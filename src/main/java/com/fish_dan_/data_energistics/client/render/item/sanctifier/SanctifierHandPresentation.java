package com.fish_dan_.data_energistics.client.render.item.sanctifier;

import com.fish_dan_.data_energistics.client.render.item.sanctifier.model.SanctifierBakedModel;
import com.fish_dan_.data_energistics.common.data.DEItems;
import com.fish_dan_.data_energistics.common.entity.projectile.SanctifierBladeEntity;
import com.fish_dan_.data_energistics.common.item.powered.LightSaberColorData;
import com.fish_dan_.data_energistics.common.item.powered.PoweredSwordItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/** Presents the paired blades by default and uses the synchronized execution entity for skill timing. */
public final class SanctifierHandPresentation {

    private SanctifierHandPresentation() {}

    /** Returns this caster's visible execution in the current client world without retaining entities. */
    public static @Nullable SanctifierBladeEntity execution(LivingEntity caster) {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        for (var entity : level.entitiesForRendering()) {
            if (entity instanceof SanctifierBladeEntity blade && blade.getCasterId() == caster.getId()) return blade;
        }
        return null;
    }

    /** The empty inventory offhand still needs the arm pose of the weapon's built-in secondary blade. */
    public static void preparePlayerPose(RenderPlayerEvent.Pre event) {
        var player = event.getEntity();
        if (player.isSpectator() || !player.getMainHandItem().is(DEItems.DATA_SANCTIFIER.get())) return;
        var model = event.getRenderer().getModel();
        var pose = execution(player) == null ? HumanoidModel.ArmPose.ITEM : HumanoidModel.ArmPose.EMPTY;
        if (player.getMainArm() == HumanoidArm.RIGHT) model.leftArmPose = pose;
        else model.rightArmPose = pose;
    }

    /** Adds the built-in offhand blade and replaces the main blade only during the execution stroke. */
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
        // Render the secondary from the main-hand event: vanilla may suppress the offhand event for a carried bow.
        if (execution == null && !player.isScoping()) {
            boolean leftHand = player.getMainArm() == HumanoidArm.RIGHT;
            float side = leftHand ? -1 : 1;
            var poses = event.getPoseStack();
            poses.pushPose();
            poses.translate(side * 0.56F, -0.52F - event.getEquipProgress() * 0.6F, -0.72F);
            model.renderHeldSecondary(player.getMainHandItem(),
                    leftHand ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                    leftHand, poses, event.getMultiBufferSource(), event.getPackedLight(),
                    LightSaberColorData.getSanctifierAnimatedColor(player.level().getGameTime()),
                    ((PoweredSwordItem) player.getMainHandItem().getItem()).getAECurrentPower(player.getMainHandItem()) > 0);
            poses.popPose();
        }
        if (execution == null || execution.getPhaseTicks() < 18) return;
        float time = execution.getPhaseTicks() + event.getPartialTick();
        float progress = Math.clamp((time - 18) / 8, 0, 1);
        float side = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        Matrix4f transform = new Matrix4f().translation(side * 0.36F, -0.44F, -0.75F)
                .rotateZ((float) Math.toRadians(side * (-110 + 225 * progress)))
                .rotateX(-0.5F).rotateY((float) Math.PI).scale(0.72F);
        event.setCanceled(true);
        model.renderPrimary(player.getMainHandItem(), event.getPoseStack(), event.getMultiBufferSource(),
                event.getPackedLight(), transform);
    }
}
