package com.fish_dan_.data_energistics.client.render.item.sanctifier;

import com.fish_dan_.data_energistics.client.render.item.blade.animation.BladeAttackPlayback;
import com.fish_dan_.data_energistics.client.render.item.sanctifier.model.SanctifierBakedModel;
import com.fish_dan_.data_energistics.common.data.DEEnumProxies;
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
        if (player.isSpectator()) return;
        var model = event.getRenderer().getModel();
        SanctifierCarryPlayback carry = SanctifierCarryPlayback.find(player);
        if (execution(player) != null || carry != null && carry.state().carried() && carry.transitioning()) model.rightArmPose = DEEnumProxies.BLADE_MOTION.getValue();
        else if (BladeAttackPlayback.motion(player, event.getPartialTick()) != null) {
            if (player.getMainHandItem().is(DEItems.DATA_SANCTIFIER.get()) || player.getMainArm() == HumanoidArm.RIGHT) model.rightArmPose = DEEnumProxies.BLADE_MOTION.getValue();
            else model.leftArmPose = DEEnumProxies.BLADE_MOTION.getValue();
        } else if (player.getMainHandItem().is(DEItems.DATA_SANCTIFIER.get())) model.rightArmPose = HumanoidModel.ArmPose.ITEM;
        if (SanctifierSecondaryPose.isCharging(player)) {
            model.rightArmPose = DEEnumProxies.BLADE_MOTION.getValue();
            model.leftArmPose = DEEnumProxies.BLADE_MOTION.getValue();
        } else if (player.getMainHandItem().is(DEItems.DATA_SANCTIFIER.get()) && player.getOffhandItem().isEmpty()) model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
    }

    /** Displays the secondary only while charging and samples the primary's ordinary or execution stroke. */
    public static void renderHand(RenderHandEvent event) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        SanctifierCarryPlayback carry = SanctifierCarryPlayback.find(player);
        SanctifierBladeEntity execution = execution(player);
        boolean selected = player.getMainHandItem().is(DEItems.DATA_SANCTIFIER.get());
        if (!selected) {
            if (event.getHand() == InteractionHand.MAIN_HAND && (execution != null || carry != null && carry.state().carried() && carry.transitioning())) {
                SanctifierBakedModel model = SanctifierBakedModel.current();
                if (model != null) {
                    event.setCanceled(true);
                    var weapon = carry == null ? DEItems.DATA_SANCTIFIER.toStack() : carry.weapon();
                    Matrix4f transform = execution != null ? SanctifierExecutionPose.sample(execution.getPhaseTicks() + event.getPartialTick(), 0.65F) : SanctifierCarryPose.firstPerson(carry.progress(event.getPartialTick()));
                    model.renderPrimary(weapon, event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), transform);
                }
            }
            return;
        }
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
            model.renderSecondary(player.getMainHandItem(), poses, event.getMultiBufferSource(), event.getPackedLight(),
                    model.secondaryHandTransform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND, true),
                    LightSaberColorData.getSanctifierAnimatedColor(player.level().getGameTime()),
                    ((PoweredSwordItem) player.getMainHandItem().getItem()).getAECurrentPower(player.getMainHandItem()) > 0);
            poses.popPose();
        }
        if (execution == null) {
            var poses = event.getPoseStack();
            if (carry != null && carry.transitioning()) {
                model.renderPrimary(player.getMainHandItem(), poses, event.getMultiBufferSource(), event.getPackedLight(),
                        SanctifierCarryPose.firstPerson(carry.progress(event.getPartialTick())));
                return;
            }
            Matrix4f motion = BladeAttackPlayback.motion(player, event.getPartialTick());
            Matrix4f transform = model.primaryHandTransform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);
            if (motion != null) transform.mul(motion);
            poses.pushPose();
            poses.translate(0.56F, -0.52F - event.getEquipProgress() * 0.6F, -0.72F);
            model.renderPrimary(player.getMainHandItem(), poses, event.getMultiBufferSource(), event.getPackedLight(),
                    transform);
            poses.popPose();
            return;
        }
        float time = execution.getPhaseTicks() + event.getPartialTick();
        Matrix4f transform = SanctifierExecutionPose.sample(time, 0.65F);
        model.renderPrimary(player.getMainHandItem(), event.getPoseStack(), event.getMultiBufferSource(),
                event.getPackedLight(), transform);
    }
}
