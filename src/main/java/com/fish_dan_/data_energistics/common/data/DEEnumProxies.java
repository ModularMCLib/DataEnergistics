package com.fish_dan_.data_energistics.common.data;

import com.fish_dan_.data_energistics.client.render.item.crossbow.CrossbowThirdPersonPose;

import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;

/** Native enum constructor parameters, following GT's common/data enum proxy layout. */
@OnlyIn(Dist.CLIENT)
public final class DEEnumProxies {

    public static final EnumProxy<ArmPose> CROSSBOW_HOLD = new EnumProxy<>(ArmPose.class,
            true, (IArmPoseTransformer) CrossbowThirdPersonPose::apply);
    public static final EnumProxy<ArmPose> CROSSBOW_HOLD_SINGLE = new EnumProxy<>(ArmPose.class,
            false, (IArmPoseTransformer) CrossbowThirdPersonPose::apply);

    private DEEnumProxies() {}
}
