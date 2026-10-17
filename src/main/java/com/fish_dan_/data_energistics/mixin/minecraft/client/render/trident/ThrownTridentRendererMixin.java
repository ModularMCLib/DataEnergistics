package com.fish_dan_.data_energistics.mixin.minecraft.client.render.trident;

import com.fish_dan_.data_energistics.client.render.trident.DataTridentModel;
import com.fish_dan_.data_energistics.entity.projectile.DataTridentAppearance;

import net.minecraft.client.model.TridentModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ThrownTridentRenderer;
import net.minecraft.world.entity.projectile.ThrownTrident;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Changes only the model draw call, retaining vanilla flight interpolation, return motion and rendering hooks. */
@Mixin(ThrownTridentRenderer.class)
public abstract class ThrownTridentRendererMixin {

    @WrapOperation(method = "render(Lnet/minecraft/world/entity/projectile/ThrownTrident;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
                   at = @At(value = "INVOKE",
                            target = "Lnet/minecraft/client/model/TridentModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V"))
    private void dataEnergistics$renderExportedModel(TridentModel model, PoseStack poses, VertexConsumer vertices,
                                                     int packedLight, int packedOverlay, Operation<Void> original,
                                                     ThrownTrident entity, float entityYaw, float partialTicks,
                                                     PoseStack renderPoses, MultiBufferSource buffers, int renderLight) {
        if (((DataTridentAppearance) entity).usesDataTridentTexture()) {
            DataTridentModel.render(poses, buffers, packedLight, packedOverlay, entity.isFoil());
        } else {
            original.call(model, poses, vertices, packedLight, packedOverlay);
        }
    }
}
