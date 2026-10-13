package com.fish_dan_.data_energistics.client.key;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.magic.naturesaura.digitalsupply.NaturesAuraKey;
import com.fish_dan_.data_energistics.integration.magic.naturesaura.digitalsupply.NaturesAuraKeyType;

import appeng.api.client.AEKeyRendering;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Client renderer registration kept behind Nature's Aura's optional class boundary. */
final class NaturesAuraKeyRenderers {

    private static final ResourceLocation AURA_SPRITE = Data_Energistics.id("key/aura");

    private NaturesAuraKeyRenderers() {}

    static void register() {
        if (AEKeyRendering.get(NaturesAuraKeyType.TYPE) == null) {
            AEKeyRendering.register(NaturesAuraKeyType.TYPE, NaturesAuraKey.class,
                    new SpriteKeyRenderer<>(key -> Minecraft.getInstance().getGuiSprites().getSprite(AURA_SPRITE)));
        }
    }
}
