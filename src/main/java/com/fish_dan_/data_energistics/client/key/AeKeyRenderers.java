package com.fish_dan_.data_energistics.client.key;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.ae.ae2.key.DataKey;
import com.fish_dan_.data_energistics.integration.ae.ae2.key.DigitalBiologicalResourceKey;
import com.fish_dan_.data_energistics.integration.ae.ae2.key.DigitalBiologicalResourceKeyType;
import com.fish_dan_.data_energistics.integration.ae.ae2.key.DigitalizationKey;
import com.fish_dan_.data_energistics.integration.ae.ae2.key.DigitalizationKeyType;
import com.fish_dan_.data_energistics.integration.ae.ae2.key.ManifestBinaryKeyType;

import appeng.api.client.AEKeyRendering;

public final class AeKeyRenderers {

    private static final DigitalizationKeyRenderer DIGITALIZATION_RENDERER = new DigitalizationKeyRenderer();
    private static final DataKeyRenderer DATA_RENDERER = new DataKeyRenderer();
    private static final DigitalBiologicalResourceKeyRenderer DIGITAL_BIOLOGICAL_RESOURCE_RENDERER = new DigitalBiologicalResourceKeyRenderer();

    private AeKeyRenderers() {}

    public static void register() {
        AEKeyRendering.register(DigitalizationKeyType.TYPE, DigitalizationKey.class, DIGITALIZATION_RENDERER);
        AEKeyRendering.register(ManifestBinaryKeyType.TYPE, DataKey.class, DATA_RENDERER);
        AEKeyRendering.register(DigitalBiologicalResourceKeyType.TYPE, DigitalBiologicalResourceKey.class, DIGITAL_BIOLOGICAL_RESOURCE_RENDERER);

        if (Data_Energistics.isModLoaded("goety")) {
            GoetyKeyRenderers.register();
        }
        if (Data_Energistics.isModLoaded("forbidden_arcanus")) {
            ForbiddenArcanusKeyRenderers.register();
        }
        if (Data_Energistics.isModLoaded("naturesaura")) {
            NaturesAuraKeyRenderers.register();
        }
        if (Data_Energistics.isModLoaded("astralsorcery")) {
            AstralSorceryKeyRenderers.register();
        }
    }
}
