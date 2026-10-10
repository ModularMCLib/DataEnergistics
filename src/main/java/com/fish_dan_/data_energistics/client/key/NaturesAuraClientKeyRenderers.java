package com.fish_dan_.data_energistics.client.key;

import com.fish_dan_.data_energistics.integration.magic.naturesaura.digitalsupply.NaturesAuraKey;
import com.fish_dan_.data_energistics.integration.magic.naturesaura.digitalsupply.NaturesAuraKeyType;

import appeng.api.client.AEKeyRendering;

import net.minecraft.resources.ResourceLocation;

/** Client renderer registration kept behind Nature's Aura's optional class boundary. */
final class NaturesAuraClientKeyRenderers {

    private NaturesAuraClientKeyRenderers() {}

    static void register() {
        if (AEKeyRendering.get(NaturesAuraKeyType.TYPE) == null) {
            AEKeyRendering.register(NaturesAuraKeyType.TYPE, NaturesAuraKey.class,
                    new ExternalModKeyRenderHandler<>(key -> ResourceLocation.fromNamespaceAndPath(
                            "naturesaura", "item/aura_bottle")));
        }
    }
}
