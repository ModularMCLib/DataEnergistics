package com.fish_dan_.data_energistics.client.key;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.magic.goety.digitalsupply.GoetySoulKey;
import com.fish_dan_.data_energistics.integration.magic.goety.digitalsupply.GoetySoulKeyType;

import appeng.api.client.AEKeyRendering;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Client renderer registration kept behind Goety's optional class boundary. */
final class GoetyKeyRenderers {

    private static final ResourceLocation SOULS_SPRITE = Data_Energistics.id("key/goety_souls");

    private GoetyKeyRenderers() {}

    static void register() {
        if (AEKeyRendering.get(GoetySoulKeyType.INSTANCE) == null) {
            AEKeyRendering.register(GoetySoulKeyType.INSTANCE, GoetySoulKey.class,
                    new SpriteKeyRenderer<>(key -> Minecraft.getInstance().getGuiSprites().getSprite(SOULS_SPRITE)));
        }
    }
}
