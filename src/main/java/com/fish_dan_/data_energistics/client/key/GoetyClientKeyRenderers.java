package com.fish_dan_.data_energistics.client.key;

import com.fish_dan_.data_energistics.integration.magic.goety.digitalsupply.GoetySoulKey;
import com.fish_dan_.data_energistics.integration.magic.goety.digitalsupply.GoetySoulKeyType;

import appeng.api.client.AEKeyRendering;

import net.minecraft.resources.ResourceLocation;

/** Client renderer registration kept behind Goety's optional class boundary. */
final class GoetyClientKeyRenderers {

    private GoetyClientKeyRenderers() {}

    static void register() {
        if (AEKeyRendering.get(GoetySoulKeyType.INSTANCE) == null) {
            AEKeyRendering.register(GoetySoulKeyType.INSTANCE, GoetySoulKey.class,
                    new ExternalModKeyRenderHandler<>(key -> ResourceLocation.fromNamespaceAndPath(
                            "goety", "item/soul_energy")));
        }
    }
}
