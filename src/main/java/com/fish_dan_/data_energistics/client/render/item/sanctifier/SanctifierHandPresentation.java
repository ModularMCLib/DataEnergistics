package com.fish_dan_.data_energistics.client.render.item.sanctifier;

import com.fish_dan_.data_energistics.client.render.item.blade.BladeFirstPersonHand;
import com.fish_dan_.data_energistics.client.render.item.blade.BladeGripPose;
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
import net.minecraft.world.item.ItemStack;
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
        } else if (player.getMainHandItem().is(DEItems.DATA_SANCTIFIER.get())) model.rightArmPose = DEEnumProxies.BLADE_MOTION.getValue();
        else if (player.getMainHandItem().is(DEItems.DATA_LIGHT_SABER.get())) {
            if (player.getMainArm() == HumanoidArm.RIGHT) model.rightArmPose = DEEnumProxies.BLADE_MOTION.getValue();
            else model.leftArmPose = DEEnumProxies.BLADE_MOTION.getValue();
        }
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
        if (!selected && execution == null && (carry == null || !carry.state().carried() || !carry.transitioning())) return;
        SanctifierBakedModel model = SanctifierBakedModel.current();
        if (model == null) return;
        if (event.getHand() == InteractionHand.OFF_HAND) {
            if (selected) event.setCanceled(true);
            return;
        }
        event.setCanceled(true);
        if (player.isScoping()) return;
        var poses = event.getPoseStack();
        ItemStack weapon = selected ? player.getMainHandItem() : carry == null ? DEItems.DATA_SANCTIFIER.toStack() : carry.weapon();
        Matrix4f rest = new Matrix4f().translation(0.56F, -0.52F, -0.72F)
                .mul(model.primaryHandTransform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND));
        if (SanctifierSecondaryPose.isCharging(player)) {
            float progress = SanctifierSecondaryPose.pickupProgress(player, event.getPartialTick());
            Matrix4f secondaryRest = new Matrix4f().translation(-0.56F, -0.52F, -0.72F)
                    .mul(model.secondaryHandTransform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND, true));
            Matrix4f secondary = new Matrix4f().translation(-(1 - progress) * 0.15F, -(1 - progress) * 0.8F, 0).mul(secondaryRest);
            model.renderSecondary(weapon, poses, event.getMultiBufferSource(), event.getPackedLight(), secondary,
                    LightSaberColorData.getSanctifierAnimatedColor(player.level().getGameTime()),
                    ((PoweredSwordItem) weapon.getItem()).getAECurrentPower(weapon) > 0);
            BladeFirstPersonHand.render(player, poses, event.getMultiBufferSource(), event.getPackedLight(),
                    new Matrix4f(secondary).translate(0, 0, BladeGripPose.SANCTIFIER_SECONDARY_GRIP_Z), HumanoidArm.LEFT);
        }
        Matrix4f transform;
        if (execution != null) transform = SanctifierExecutionPose.sample(execution.getPhaseTicks() + event.getPartialTick(), rest);
        else if (carry != null && carry.transitioning()) transform = SanctifierCarryPose.firstPerson(carry.progress(event.getPartialTick()), rest);
        else {
            transform = new Matrix4f(rest);
            Matrix4f motion = BladeAttackPlayback.motion(player, event.getPartialTick());
            if (motion != null) transform.translate(0, 0, BladeGripPose.SANCTIFIER_PRIMARY_GRIP_Z).mul(motion)
                    .translate(0, 0, -BladeGripPose.SANCTIFIER_PRIMARY_GRIP_Z);
        }
        model.renderPrimary(weapon, poses, event.getMultiBufferSource(), event.getPackedLight(), transform);
        BladeFirstPersonHand.render(player, poses, event.getMultiBufferSource(), event.getPackedLight(),
                new Matrix4f(transform).translate(0, 0, BladeGripPose.SANCTIFIER_PRIMARY_GRIP_Z), HumanoidArm.RIGHT);
    }
}
