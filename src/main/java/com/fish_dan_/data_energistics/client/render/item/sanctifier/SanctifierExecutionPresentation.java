package com.fish_dan_.data_energistics.client.render.item.sanctifier;

import com.fish_dan_.data_energistics.client.render.item.sanctifier.model.SanctifierBakedModel;
import com.fish_dan_.data_energistics.common.data.DEItems;
import com.fish_dan_.data_energistics.common.entity.projectile.SanctifierBladeEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.RenderHandEvent;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/** Uses the synchronized execution entity as the sole client animation clock. */
public final class SanctifierExecutionPresentation {

    private SanctifierExecutionPresentation() {}

    /** Returns this caster's visible execution in the current client world without retaining entities. */
    public static @Nullable SanctifierBladeEntity execution(LivingEntity caster) {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        for (var entity : level.entitiesForRendering()) {
            if (entity instanceof SanctifierBladeEntity blade && blade.getCasterId() == caster.getId()) return blade;
        }
        return null;
    }

    /** Replaces only the main-hand sword during the execution strike; ordinary item swings stay vanilla. */
    public static void renderHand(RenderHandEvent event) {
        var player = Minecraft.getInstance().player;
        if (player == null || event.getHand() != InteractionHand.MAIN_HAND || !player.getMainHandItem().is(DEItems.DATA_SANCTIFIER.get())) return;
        SanctifierBladeEntity execution = execution(player);
        if (execution == null || execution.getPhaseTicks() < 18) return;
        SanctifierBakedModel model = SanctifierBakedModel.current();
        if (model == null) return;
        float time = execution.getPhaseTicks() + event.getPartialTick();
        float progress = Math.clamp((time - 18) / 8, 0, 1);
        float side = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        Matrix4f transform = new Matrix4f().translation(side * 0.36F, -0.44F, -0.75F)
                .rotateZ((float) Math.toRadians(side * (-110 + 225 * progress)))
                .rotateX(-0.5F).scale(0.72F);
        event.setCanceled(true);
        model.renderPrimary(player.getMainHandItem(), event.getPoseStack(), event.getMultiBufferSource(),
                event.getPackedLight(), transform);
    }
}
