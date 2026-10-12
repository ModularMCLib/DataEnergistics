package com.fish_dan_.data_energistics.client.key;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.key.DataKey;
import com.fish_dan_.data_energistics.ae2.key.DigitalBiologicalResourceKey;
import com.fish_dan_.data_energistics.ae2.key.DigitalBiologicalResourceKeyType;
import com.fish_dan_.data_energistics.ae2.key.DigitalizationKey;
import com.fish_dan_.data_energistics.ae2.key.DigitalizationKeyType;
import com.fish_dan_.data_energistics.ae2.key.ManifestBinaryKeyType;

import appeng.api.client.AEKeyRendering;

public final class ClientAeKeyRenderers {

    private static final DigitalizationKeyRenderHandler DIGITALIZATION_RENDER_HANDLER = new DigitalizationKeyRenderHandler();
    private static final DataKeyRenderHandler DATA_RENDER_HANDLER = new DataKeyRenderHandler();
    private static final DigitalBiologicalResourceKeyRenderHandler DIGITAL_BIOLOGICAL_RESOURCE_RENDER_HANDLER = new DigitalBiologicalResourceKeyRenderHandler();

    private ClientAeKeyRenderers() {}

    public static void register() {
        AEKeyRendering.register(DigitalizationKeyType.TYPE, DigitalizationKey.class, DIGITALIZATION_RENDER_HANDLER);
        AEKeyRendering.register(ManifestBinaryKeyType.TYPE, DataKey.class, DATA_RENDER_HANDLER);
        AEKeyRendering.register(DigitalBiologicalResourceKeyType.TYPE, DigitalBiologicalResourceKey.class, DIGITAL_BIOLOGICAL_RESOURCE_RENDER_HANDLER);

        if (Data_Energistics.isModLoaded("goety")) {
            GoetyClientKeyRenderers.register();
        }
        if (Data_Energistics.isModLoaded("forbidden_arcanus")) {
            ForbiddenArcanusClientKeyRenderers.register();
        }
        if (Data_Energistics.isModLoaded("naturesaura")) {
            NaturesAuraClientKeyRenderers.register();
        }
        if (Data_Energistics.isModLoaded("astralsorcery")) {
            AstralSorceryClientKeyRenderers.register();
        }
    }
}
