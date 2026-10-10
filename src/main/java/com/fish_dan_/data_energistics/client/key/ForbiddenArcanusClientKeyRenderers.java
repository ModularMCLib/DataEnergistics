package com.fish_dan_.data_energistics.client.key;

import com.fish_dan_.data_energistics.integration.magic.forbiddenarcanus.digitalsupply.ForbiddenArcanusEssenceKey;
import com.fish_dan_.data_energistics.integration.magic.forbiddenarcanus.digitalsupply.ForbiddenArcanusEssenceKeyType;

import appeng.api.client.AEKeyRendering;

import net.minecraft.resources.ResourceLocation;

/** Client renderer registration kept behind Forbidden Arcanus' optional class boundary. */
final class ForbiddenArcanusClientKeyRenderers {

    private ForbiddenArcanusClientKeyRenderers() {}

    static void register() {
        if (AEKeyRendering.get(ForbiddenArcanusEssenceKeyType.TYPE) == null) {
            AEKeyRendering.register(ForbiddenArcanusEssenceKeyType.TYPE, ForbiddenArcanusEssenceKey.class,
                    new ExternalModKeyRenderHandler<>(key -> ResourceLocation.fromNamespaceAndPath(
                            "forbidden_arcanus", "gui/sprites/icon/" + key.getEssenceType().getSerializedName())));
        }
    }
}
